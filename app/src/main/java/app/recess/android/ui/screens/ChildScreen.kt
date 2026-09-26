package app.recess.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.recess.android.ui.FamilyViewModel
import app.recess.android.ui.components.AppActivity
import app.recess.android.ui.components.ApproveDialog
import app.recess.android.ui.components.Badge
import app.recess.android.ui.components.BadgeTone
import app.recess.android.ui.components.CapMeter
import app.recess.android.ui.components.CardHeader
import app.recess.android.ui.components.DevicePreview
import app.recess.android.ui.components.Divider
import app.recess.android.ui.components.EmptyCard
import app.recess.android.ui.components.GiveTimeDialog
import app.recess.android.ui.components.LabeledSlider
import app.recess.android.ui.components.LinkEmailDialog
import app.recess.android.ui.components.LinkText
import app.recess.android.ui.components.PrimaryButton
import app.recess.android.ui.components.RCard
import app.recess.android.ui.components.TimeField
import app.recess.android.ui.components.TimeRing
import app.recess.android.ui.components.ToggleRow
import app.recess.android.ui.components.grantStatusLabel
import app.recess.android.ui.theme.Palette
import app.recess.core.Engine
import app.recess.core.FamilyState
import app.recess.core.Format
import app.recess.core.GrantStatus
import app.recess.core.remainingMinutes
import java.time.ZonedDateTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChildScreen(state: FamilyState, childId: String, now: ZonedDateTime, usageAccess: Boolean, vm: FamilyViewModel) {
    val child = state.children.firstOrNull { it.id == childId }
    if (child == null) {
        ScreenColumn { EmptyCard("Not in this family", "That child was removed.") }
        return
    }
    var giving by remember { mutableStateOf(false) }
    var approving by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf(false) }

    val pending = state.grants.filter { it.childId == childId && it.status == GrantStatus.Pending }
    val courses = state.courses.associateBy { it.id }
    val tasks = state.tasks.filter { it.childId == childId }.sortedWith(Engine.byDue)
    val apps = state.apps.filter { it.childId == childId }
    val measured = state.settings.deviceChildId == childId && usageAccess
    val downtime = Format.inDowntime(child.downtimeStart, child.downtimeEnd, now)

    ScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(listOf(child.grade, child.school).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                Text(child.name, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 4.dp))
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge(child.device)
                    Badge(if (child.autoApply) "Auto-apply" else "Approval required", BadgeTone.Muted)
                    if (downtime) Badge("Downtime until ${Format.clockFromHhmm(child.downtimeEnd)}", BadgeTone.Warn)
                    else Badge("Bedtime ${Format.clockFromHhmm(child.bedtime)}", BadgeTone.Outline)
                }
            }
            TimeRing(child, 96)
        }

        RCard {
            Text("Remaining today", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            Text(Format.minutesLabel(remainingMinutes(child)), style = MaterialTheme.typography.displaySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat("Limit", Format.minutesLabel(child.dailyLimitMin), Modifier.weight(1f))
                MiniStat("Used", Format.minutesLabel(child.usedTodayMin), Modifier.weight(1f))
                MiniStat("Bonus", "+${Format.minutesLabel(child.bonusTodayMin)}", Modifier.weight(1f))
            }
            CapMeter(Engine.bonusCommittedToday(state, childId, now), state.settings.dailyBonusCapMin)
            PrimaryButton("Give extra time", { giving = true }, Modifier.fillMaxWidth())
            if (measured) {
                Text("Usage is measured on this phone.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            } else {
                LabeledSlider(
                    "Log usage", Format.minutesLabel(child.usedTodayMin), child.usedTodayMin,
                    0..(child.dailyLimitMin + child.bonusTodayMin + 30), 1, { vm.logUsage(childId, it) },
                )
            }
            Divider()
            ToggleRow("Auto-apply bonus", "Skip the approval queue for ${child.firstName}.", child.autoApply, { on -> vm.patchChild(childId) { it.copy(autoApply = on) } })
        }

        DevicePreview(child, now)

        if (pending.isNotEmpty()) {
            RCard {
                CardHeader("Held grants", "Approve these, then add them for ${child.device} in Family Link.")
                pending.forEach { g ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(g.reason, style = MaterialTheme.typography.titleSmall)
                            Text("+${Format.minutesLabel(g.minutes)}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                        }
                    }
                }
                PrimaryButton("Approve · +${Format.minutesLabel(pending.sumOf { it.minutes })}", { approving = true }, small = true)
            }
        }

        RCard {
            CardHeader(
                "Google Classroom",
                child.classroomEmail?.let { "Linked to $it" } ?: "Not linked. Tasks here are demo or hand-entered.",
            ) { LinkText(if (child.classroomEmail == null) "Link" else "Edit") { linking = true } }
        }

        RCard {
            CardHeader("Downtime", "Record the bedtime you set in Family Link.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField("Starts", child.downtimeStart, { t -> vm.patchChild(childId) { it.copy(downtimeStart = t, bedtime = t) } }, Modifier.weight(1f))
                TimeField("Ends", child.downtimeEnd, { t -> vm.patchChild(childId) { it.copy(downtimeEnd = t) } }, Modifier.weight(1f))
            }
        }

        AppActivity(
            apps, measured,
            onBlock = { id, blocked -> vm.patchApp(id) { it.copy(blocked = blocked) } },
            onLimit = { id, limit -> vm.patchApp(id) { it.copy(limitMin = limit) } },
        )

        RCard(spacing = 0) {
            CardHeader("Classroom work", if (child.classroomEmail != null) "Synced from Google Classroom." else "Turn in a task to run bonus rules.")
            if (tasks.isEmpty()) Text("No work yet.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.padding(top = 12.dp))
            tasks.forEachIndexed { i, task ->
                if (i > 0) Divider()
                FamilyTaskRow(task, courses[task.courseId], state, now, vm)
            }
        }

        val history = state.grants.filter { it.childId == childId }.take(8)
        if (history.isNotEmpty()) {
            RCard {
                CardHeader("Bonus history", "Grants earned from Classroom and parent time.")
                history.forEachIndexed { i, g ->
                    if (i > 0) Divider()
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(g.reason, style = MaterialTheme.typography.bodyMedium)
                            Text(Format.clockLabel(g.createdAt, now.zone), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("+${Format.minutesLabel(g.minutes)}", style = MaterialTheme.typography.bodyMedium, color = Palette.Accent)
                            val (label, tone) = grantStatusLabel(g.status)
                            Badge(label, tone)
                        }
                    }
                }
            }
        }
    }

    if (giving) GiveTimeDialog(child, onDismiss = { giving = false }) { vm.giveTime(childId, it); giving = false }
    if (approving) {
        ApproveDialog(pending, mapOf(child.id to child), onDismiss = { approving = false }) {
            vm.approve(pending.map { it.id })
            approving = false
        }
    }
    if (linking) {
        LinkEmailDialog(child, onDismiss = { linking = false }) { email ->
            vm.patchChild(childId) { it.copy(classroomEmail = email) }
            linking = false
        }
    }
}

@Composable
private fun MiniStat(k: String, v: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Palette.Bg).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(k, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, textAlign = TextAlign.Center)
        Text(v, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
    }
}
