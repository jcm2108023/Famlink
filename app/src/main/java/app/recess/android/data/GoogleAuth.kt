package app.recess.android.data

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google sign-in for Classroom, using Play services' AuthorizationClient. The first call shows the
 * account chooser and consent screen; later calls return a fresh access token silently.
 *
 * Requires an Android OAuth client in Google Cloud for this package name and signing SHA-1
 * (see README).
 */
class GoogleAuth(private val context: Context) {
    sealed interface Outcome {
        data class Token(val accessToken: String) : Outcome
        data class NeedsConsent(val intentSender: IntentSender) : Outcome
    }

    private val client get() = Identity.getAuthorizationClient(context)

    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(SCOPES.map(::Scope))
        .build()

    suspend fun authorize(): Outcome = suspendCancellableCoroutine { cont ->
        client.authorize(request)
            .addOnSuccessListener { result ->
                val pending = result.pendingIntent
                val token = result.accessToken
                when {
                    result.hasResolution() && pending != null -> cont.resume(Outcome.NeedsConsent(pending.intentSender))
                    token != null -> cont.resume(Outcome.Token(token))
                    else -> cont.resumeWithException(IllegalStateException("Google returned no access token"))
                }
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    /** Reads the token from the consent screen's result intent. */
    fun tokenFrom(data: Intent?): String =
        client.getAuthorizationResultFromIntent(data).accessToken
            ?: throw IllegalStateException("Google returned no access token")

    /** Drops the cached token so the next call fetches a new one (used after a 401). */
    suspend fun clear(token: String) = withContext(Dispatchers.IO) {
        runCatching { GoogleAuthUtil.clearToken(context, token) }
    }

    companion object {
        /** Read-only access; enough for a teacher (own classes) or Workspace admin (any class). */
        val SCOPES = listOf(
            "https://www.googleapis.com/auth/classroom.courses.readonly",
            "https://www.googleapis.com/auth/classroom.coursework.students.readonly",
            "https://www.googleapis.com/auth/classroom.rosters.readonly",
            "https://www.googleapis.com/auth/classroom.profile.emails",
        )
    }
}
