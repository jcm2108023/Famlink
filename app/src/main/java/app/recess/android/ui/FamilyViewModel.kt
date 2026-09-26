package app.recess.android.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.recess.android.data.ClassroomClient
import app.recess.android.data.FamilyStore
import app.recess.android.data.GoogleAuth
import app.recess.android.data.UsageReader
import app.recess.core.Child
import app.recess.core.ClassroomImport
import app.recess.core.Engine
import app.recess.core.FamilySettings
import app.recess.core.FamilyState
import app.recess.core.GrantStatus
import app.recess.core.Rule
import app.recess.core.Seed
import app.recess.core.SupervisedApp
import app.recess.core.UsageImport
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.ZonedDateTime

data class UiMessage(val title: String, val detail: String? = null)

class FamilyViewModel(app: Application) : AndroidViewModel(app) {
    private val store = FamilyStore(File(app.filesDir, "family.json"), viewModelScope)
    private val auth = GoogleAuth(app)
    private val usage = UsageReader(app)

    val state: StateFlow<FamilyState> = store.state

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _usageAccess = MutableStateFlow(usage.hasAccess())
    val usageAccess: StateFlow<Boolean> = _usageAccess.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    /** Google's account chooser / consent screen, launched by the activity. */
    private val _consent = Channel<IntentSender>(Channel.BUFFERED)
    val consentRequests: Flow<IntentSender> = _consent.receiveAsFlow()
    private var afterConsent: (suspend (String) -> Unit)? = null

    private fun now() = ZonedDateTime.now()
    private fun say(title: String, detail: String? = null) { _messages.trySend(UiMessage(title, detail)) }
    private fun firstName(childId: String) = state.value.children.firstOrNull { it.id == childId }?.firstName ?: "Child"

    // ---- Classroom work ------------------------------------------------------------------------

    fun completeTask(taskId: String) {
        val task = state.value.tasks.firstOrNull { it.id == taskId } ?: return
        var grants = emptyList<app.recess.core.Grant>()
        store.update { s -> Engine.applyTurnIn(s, taskId, now()).also { grants = it.grants }.state }
        val live = grants.filter { it.status == GrantStatus.Pending || it.status == GrantStatus.Provisioned }
        val minutes = live.sumOf { it.minutes }
        val detail = when {
            minutes == 0 -> "No bonus (cap reached)"
            live.any { it.status == GrantStatus.Pending } -> "+$minutes min waiting for approval"
            else -> "+$minutes min earned"
        }
        say("${firstName(task.childId)} turned in ${task.title}", detail)
    }

    fun gradeTask(taskId: String, pct: Int) {
        val task = state.value.tasks.firstOrNull { it.id == taskId } ?: return
        store.update { Engine.applyGrade(it, taskId, pct.toDouble(), now()).state }
        say("Returned ${task.title} at $pct%")
    }

    // ---- Grants --------------------------------------------------------------------------------

    fun approve(grantIds: List<String>) {
        val minutes = state.value.grants.filter { it.id in grantIds && it.status == GrantStatus.Pending }.sumOf { it.minutes }
        store.update { s -> grantIds.fold(s) { acc, id -> Engine.approveGrant(acc, id, now()) } }
        say("Approved +$minutes min", "Add it in Family Link to put it on the device.")
    }

    fun skip(grantId: String) {
        store.update { Engine.skipGrant(it, grantId, now()) }
        say("Grant skipped")
    }

    fun giveTime(childId: String, minutes: Int) {
        var grant: app.recess.core.Grant? = null
        store.update { s -> Engine.grantManual(s, childId, minutes, now()).also { grant = it.grant }.state }
        val g = grant
        when {
            g == null -> Unit
            g.status == GrantStatus.Capped -> say("Daily bonus cap reached", "No extra time was added.")
            else -> say("+${g.minutes} min for ${firstName(childId)}", "Add it in Family Link to put it on the device.")
        }
    }

    // ---- Settings, rules, children -------------------------------------------------------------

    fun patchRule(ruleId: String, patch: (Rule) -> Rule) = store.update { Engine.updateRule(it, ruleId, patch) }

    fun patchSettings(patch: (FamilySettings) -> FamilySettings) = store.update { it.copy(settings = patch(it.settings)) }

    fun patchChild(childId: String, patch: (Child) -> Child) =
        store.update { s -> s.copy(children = s.children.map { if (it.id == childId) patch(it) else it }) }

    fun patchApp(appId: String, patch: (SupervisedApp) -> SupervisedApp) = store.update { Engine.patchApp(it, appId, patch) }

    fun logUsage(childId: String, minutes: Int) = store.update { Engine.setUsage(it, childId, minutes) }

    fun addChild(name: String, age: Int, device: String, dailyLimitMin: Int, classroomEmail: String?) {
        store.update { Seed.newChild(it, name, age, device, dailyLimitMin, classroomEmail?.trim()?.ifBlank { null }) }
        say("$name added")
    }

    fun removeChild(childId: String) {
        val name = firstName(childId)
        store.update { Engine.removeChild(it, childId) }
        say("$name removed")
    }

    fun resetDemo() {
        store.update { s -> Seed.create(now()).let { it.copy(settings = it.settings.copy(classroomAccount = s.settings.classroomAccount)) } }
        say("Demo family restored")
    }

    fun startFresh() {
        store.update { Seed.empty(it.settings) }
        say("Demo family removed", "Add your children in Settings.")
    }

    // ---- This phone's usage --------------------------------------------------------------------

    fun usageSettingsIntent(): Intent = usage.accessSettingsIntent()

    fun setDeviceChild(childId: String?) {
        patchSettings { it.copy(deviceChildId = childId) }
        refreshUsage()
    }

    /** Pulls measured screen time into the child this phone belongs to. Safe to call often. */
    fun refreshUsage() {
        _usageAccess.value = usage.hasAccess()
        val childId = state.value.settings.deviceChildId ?: return
        if (!_usageAccess.value) return
        viewModelScope.launch {
            val snapshot = runCatching { withContext(Dispatchers.IO) { usage.read(now()) } }.getOrNull() ?: return@launch
            store.update { UsageImport.apply(it, childId, snapshot) }
        }
    }

    // ---- Google account & sync -----------------------------------------------------------------

    fun connectGoogle() = withToken { token ->
        val me = ClassroomClient(token).me()
        val email = me.emailAddress ?: me.name?.fullName ?: "Google account"
        patchSettings { it.copy(classroomAccount = email) }
        say("Connected as $email", "Link each child's school email to sync their work.")
    }

    fun disconnectGoogle() {
        patchSettings { it.copy(classroomAccount = null) }
        say("Google account disconnected", "To revoke access fully, remove Recess at myaccount.google.com/connections.")
    }

    fun sync() {
        if (_syncing.value) return
        val s = state.value
        val linked = s.children.any { !it.classroomEmail.isNullOrBlank() }
        when {
            s.settings.classroomAccount == null -> demoSync()
            !linked -> say("No child is linked to Classroom", "Add a child's school email in Settings.")
            else -> withToken { token -> classroomSync(token, retryOnAuthError = true) }
        }
    }

    private fun demoSync() {
        _syncing.value = true
        viewModelScope.launch {
            delay(600)
            var summary: Engine.SyncSummary? = null
            store.update { s -> Engine.syncClassroom(s, now()).also { summary = it }.state }
            _syncing.value = false
            reportSync(summary?.completed.orEmpty(), demo = true)
            refreshUsage()
        }
    }

    private suspend fun classroomSync(token: String, retryOnAuthError: Boolean) {
        val client = ClassroomClient(token)
        val completed = mutableListOf<Engine.SyncItem>()
        for (child in state.value.children.filter { !it.classroomEmail.isNullOrBlank() }) {
            val remote = try {
                client.fetchStudent(child.classroomEmail!!.trim())
            } catch (e: ClassroomClient.ApiException) {
                if (e.code == 401 && retryOnAuthError) {
                    // Cached token expired or was revoked: drop it and fetch a fresh one once.
                    auth.clear(token)
                    when (val outcome = auth.authorize()) {
                        is GoogleAuth.Outcome.Token -> return classroomSync(outcome.accessToken, retryOnAuthError = false)
                        is GoogleAuth.Outcome.NeedsConsent -> {
                            say("Sign in to Google again", "Open Settings and tap Connect Google account.")
                            return
                        }
                    }
                }
                say("Couldn't sync ${child.firstName}", explain(e))
                continue
            } catch (e: IOException) {
                say("Couldn't reach Classroom", e.message)
                return
            }
            var result: ClassroomImport.Result? = null
            store.update { s -> ClassroomImport.apply(s, child.id, remote, now()).also { result = it }.state }
            completed += result?.completed.orEmpty()
        }
        store.update { Engine.recordSync(it, completed.size, now()) }
        reportSync(completed, demo = false)
        refreshUsage()
    }

    private fun reportSync(completed: List<Engine.SyncItem>, demo: Boolean) {
        if (completed.isEmpty()) {
            say(if (demo) "Demo Classroom is up to date" else "Classroom is up to date")
            return
        }
        for (item in completed) {
            val detail = when {
                item.minutes == 0 -> "No bonus (cap reached)"
                item.pending -> "+${item.minutes} min waiting for approval"
                else -> "+${item.minutes} min earned"
            }
            say("${item.childName} turned in ${item.title}", detail)
        }
    }

    private fun explain(e: ClassroomClient.ApiException): String = when (e.code) {
        403 -> "Your account can't see this student's classes. Sign in as their teacher or a Workspace admin. (${e.message})"
        404 -> "Classroom found no student with that email in classes you can see."
        else -> e.message ?: "Classroom error ${e.code}"
    }

    /** Runs [block] with a Classroom access token, showing Google's consent screen first if needed. */
    private fun withToken(block: suspend (String) -> Unit) {
        _syncing.value = true
        viewModelScope.launch {
            var waitingForConsent = false
            try {
                when (val outcome = auth.authorize()) {
                    is GoogleAuth.Outcome.Token -> block(outcome.accessToken)
                    is GoogleAuth.Outcome.NeedsConsent -> {
                        afterConsent = block
                        waitingForConsent = true
                        _consent.send(outcome.intentSender)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportAuthError(e)
            } finally {
                if (!waitingForConsent) _syncing.value = false
            }
        }
    }

    fun onConsentResult(resultCode: Int, data: Intent?) {
        val next = afterConsent ?: return
        afterConsent = null
        viewModelScope.launch {
            try {
                // Even a cancelled result usually carries Google's status; read it so a setup
                // problem (e.g. unregistered SHA-1, account not a test user) isn't shown as "cancelled".
                if (resultCode != Activity.RESULT_OK && data == null) {
                    say("Google sign-in cancelled", SETUP_HINT)
                    return@launch
                }
                next(auth.tokenFrom(data))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportAuthError(e)
            } finally {
                _syncing.value = false
            }
        }
    }

    private fun reportAuthError(e: Exception) = when {
        e is ApiException && e.statusCode == CommonStatusCodes.DEVELOPER_ERROR -> say(
            "Google sign-in isn't set up for this build (code 10)",
            "Register package app.recess.famlink with this app's SHA-1 as an Android OAuth client (see README).",
        )
        e is ApiException && e.statusCode == CommonStatusCodes.CANCELED -> say("Google sign-in cancelled (code 16)", SETUP_HINT)
        e is ApiException && e.statusCode == CommonStatusCodes.NETWORK_ERROR -> say("No connection", "Check your internet and try again.")
        e is ApiException -> say(
            "Google sign-in failed (code ${e.statusCode})",
            "${CommonStatusCodes.getStatusCodeString(e.statusCode)}. $SETUP_HINT",
        )
        e is ClassroomClient.ApiException -> say("Classroom error", explain(e))
        e is IOException -> say("Couldn't reach Google", e.message)
        else -> say("Google sign-in failed", e.message)
    }

    private companion object {
        const val SETUP_HINT = "If you didn't close it yourself: check the Android OAuth client " +
            "(package app.recess.famlink + SHA-1), that the Classroom API is enabled, and that your account " +
            "is a test user on the OAuth consent screen."
    }
}
