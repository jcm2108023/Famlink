package app.recess.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.recess.android.ui.FamilyViewModel
import app.recess.android.ui.components.AddChildDialog
import app.recess.android.ui.components.Badge
import app.recess.android.ui.components.BadgeTone
import app.recess.android.ui.components.CardHeader
import app.recess.android.ui.components.ChipRow
import app.recess.android.ui.components.ConfirmDialog
import app.recess.android.ui.components.Divider
import app.recess.android.ui.components.FilterPill
import app.recess.android.ui.components.GhostButton
import app.recess.android.ui.components.LabeledSlider
import app.recess.android.ui.components.LinkEmailDialog
import app.recess.android.ui.components.LinkText
import app.recess.android.ui.components.OutlineButton
import app.recess.android.ui.components.PrimaryButton
import app.recess.android.ui.components.RCard
import app.recess.android.ui.components.RTextField
import app.recess.android.ui.components.ScreenHeader
import app.recess.android.ui.components.SecondaryButton
import app.recess.android.ui.components.TimeField
import app.recess.android.ui.theme.Palette
import app.recess.core.Child
import app.recess.core.FamilyState
import app.recess.core.Format

@Composable
fun SettingsScreen(state: FamilyState, syncing: Boolean, usageAccess: Boolean, vm: FamilyViewModel) {
    val context = LocalContext.current
    val settings = state.settings
    var adding by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf<Child?>(null) }
    var removing by remember { mutableStateOf<Child?>(null) }
    var confirmFresh by remember { mutableStateOf(false) }

    ScreenColumn {
        ScreenHeader("Settings", subtitle = "Family, Google account, and this phone.")

        RCard {
            RTextField("Family name", settings.familyName, { v -> vm.patchSettings { it.copy(familyName = v) } })
            RTextField("Your name", settings.parentName, { v -> vm.patchSettings { it.copy(parentName = v) } })
            RTextField("School", settings.schoolName, { v -> vm.patchSettings { it.copy(schoolName = v) } })
        }

        // --- Google Classroom -------------------------------------------------------------------
        RCard {
            val account = settings.classroomAccount
            CardHeader("Google Classroom") {
                Badge(if (account != null) "Connected" else "Not connected", if (account != null) BadgeTone.Default else BadgeTone.Muted)
            }
            if (account != null) {
                Text("Signed in as $account", style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
                val linked = state.children.count { it.classroomEmail != null }
                Text(
                    "$linked of ${state.children.size} ${if (state.children.size == 1) "child" else "children"} linked. Sync reads their courses, coursework and submissions (read-only).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Sync now", vm::sync, enabled = !syncing, small = true)
                    GhostButton("Disconnect", vm::disconnectGoogle)
                }
            } else {
                Text(
                    "Sign in with the Google account that teaches your children’s classes — if you created the classes in Classroom yourself, that’s your own account. A school’s Workspace admin account also works. Google’s guardian (parent) access can’t read coursework.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Muted,
                )
                PrimaryButton(if (syncing) "Connecting…" else "Sign in with Google", vm::connectGoogle, Modifier.fillMaxWidth(), enabled = !syncing)
            }
        }

        // --- This phone ---------------------------------------------------------------------------
        RCard {
            CardHeader("This phone’s screen time", "If Recess is installed on a child’s phone, it can read real app usage here.")
            Text("Whose phone is this?", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            ChipRow {
                FilterPill("A parent’s", settings.deviceChildId == null) { vm.setDeviceChild(null) }
                state.children.forEach { c -> FilterPill(c.firstName, settings.deviceChildId == c.id) { vm.setDeviceChild(c.id) } }
            }
            if (settings.deviceChildId != null) {
                if (usageAccess) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Badge("Usage access on", BadgeTone.Good)
                        LinkText("Refresh", vm::refreshUsage)
                    }
                } else {
                    Text(
                        "Allow Usage access for Recess in Android settings. Recess only reads time per app; it can’t see content.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Muted,
                    )
                    SecondaryButton("Open Usage access settings", { context.startActivity(vm.usageSettingsIntent()) })
                }
            }
            Text(
                "Family Link has no public API, so Recess can’t read or change its limits. Earned minutes are a ledger: add them in the Family Link app.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Faint,
            )
        }

        // --- Children -------------------------------------------------------------------------------
        RCard {
            CardHeader("Children") { SecondaryButton("Add child", { adding = true }) }
            if (state.children.isEmpty()) Text("No children yet.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            state.children.forEach { child ->
                Divider()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(child.name, style = MaterialTheme.typography.titleSmall)
                            Text(child.device, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                        }
                        LinkText("Remove") { removing = child }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            child.classroomEmail?.let { "Classroom: $it" } ?: "Classroom: not linked",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.Muted,
                            modifier = Modifier.weight(1f),
                        )
                        LinkText(if (child.classroomEmail == null) "Link" else "Edit") { linking = child }
                    }
                    LabeledSlider(
                        "Daily limit", Format.minutesLabel(child.dailyLimitMin), child.dailyLimitMin, 30..240, 15,
                        { v -> vm.patchChild(child.id) { it.copy(dailyLimitMin = v) } },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TimeField("Bedtime", child.bedtime, { t -> vm.patchChild(child.id) { it.copy(bedtime = t, downtimeStart = t) } }, Modifier.weight(1f))
                        TimeField("Downtime ends", child.downtimeEnd, { t -> vm.patchChild(child.id) { it.copy(downtimeEnd = t) } }, Modifier.weight(1f))
                    }
                }
            }
        }

        RCard {
            CardHeader("Demo family", "Restore Maya, Jonah and today’s sample work, or clear it to start with your own family.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton("Reset demo", vm::resetDemo)
                OutlineButton("Start fresh", { confirmFresh = true }, color = Palette.Danger)
            }
        }
    }

    if (adding) {
        AddChildDialog(onDismiss = { adding = false }) {
            vm.addChild(it.name, it.age, it.device, it.limit, it.email)
            adding = false
        }
    }
    linking?.let { child ->
        LinkEmailDialog(child, onDismiss = { linking = null }) { email ->
            vm.patchChild(child.id) { it.copy(classroomEmail = email) }
            linking = null
        }
    }
    removing?.let { child ->
        ConfirmDialog(
            "Remove ${child.firstName}?",
            "Their tasks, grants and app records are deleted from this phone.",
            "Remove",
            onDismiss = { removing = null },
        ) {
            vm.removeChild(child.id)
            removing = null
        }
    }
    if (confirmFresh) {
        ConfirmDialog(
            "Start fresh?",
            "Removes every child, task and grant. Your name, school, rules and Google account stay.",
            "Start fresh",
            onDismiss = { confirmFresh = false },
        ) {
            vm.startFresh()
            confirmFresh = false
        }
    }
}
