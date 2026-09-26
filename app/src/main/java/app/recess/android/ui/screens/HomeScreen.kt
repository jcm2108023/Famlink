package app.recess.android.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import app.recess.android.ui.FamilyViewModel
import app.recess.android.ui.components.ApproveDialog
import app.recess.android.ui.components.CardHeader
import app.recess.android.ui.components.ChildCard
import app.recess.android.ui.components.Divider
import app.recess.android.ui.components.EarnablePanel
import app.recess.android.ui.components.GrantQueue
import app.recess.android.ui.components.LinkText
import app.recess.android.ui.components.RCard
import app.recess.android.ui.components.StatTile
import app.recess.android.ui.components.WeekChart
import app.recess.android.ui.theme.Palette
import app.recess.core.Engine
import app.recess.core.FamilyState
import app.recess.core.Format
import app.recess.core.Grant
import app.recess.core.GrantStatus
import app.recess.core.TaskState
import java.time.ZonedDateTime

@Composable
fun HomeScreen(
    state: FamilyState,
    now: ZonedDateTime,
    syncing: Boolean,
    vm: FamilyViewModel,
    openChild: (String) -> Unit,
    openClassroom: () -> Unit,
    openLog: () -> Unit,
    openSettings: () -> Unit,
) {
    var approving by remember { mutableStateOf<List<Grant>?>(null) }
    val children = state.children
    val childrenById = children.associateBy { it.id }
    val courses = state.courses.associateBy { it.id }
    val pending = state.grants.filter { it.status == GrantStatus.Pending }
    val openToday = state.tasks.count { it.state == TaskState.Assigned && Engine.isDueTodayOrOverdue(it, now) }
    val doneToday = state.tasks.count { it.state != TaskState.Assigned && it.turnedInAt?.let { t -> Format.isToday(t, now) } == true }
    val bonusToday = children.sumOf { it.bonusTodayMin }
    val pendingMin = pending.sumOf { it.minutes }
    val allInDowntime = children.isNotEmpty() && children.all { Format.inDowntime(it.downtimeStart, it.downtimeEnd, now) }
    val todayTasks = state.tasks
        .filter { it.state == TaskState.Assigned || it.dueAt?.let { d -> Format.isToday(d, now) } == true }
        .sortedWith(compareBy<app.recess.core.ClassroomTask> { if (it.state == TaskState.Assigned) 0 else 1 }.then(Engine.byDue))
        .take(6)

    ScreenColumn {
        Column {
            val synced = state.settings.lastSyncAt?.let { " · synced ${Format.clockLabel(it, now.zone)}" }.orEmpty()
            Text(Format.headerDate(now) + synced, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            Text("${Format.greetingFor(now)}, ${state.settings.parentName}", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 4.dp))
            val subtitle = when {
                children.isEmpty() -> "Add your children in Settings to start tracking Classroom work and bonus time."
                allInDowntime -> "Devices are in downtime. Completions still earn bonus time for the morning."
                openToday == 0 -> "All due Classroom work is in."
                else -> "$openToday Classroom ${if (openToday == 1) "task" else "tasks"} still open today. Turn one in, or sync to pull new submissions."
            }
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.padding(top = 8.dp))
        }

        SyncButton(syncing, demo = state.settings.classroomAccount == null, onClick = vm::sync)

        if (children.isEmpty()) {
            RCard {
                CardHeader("No children yet", "Add a child and link their school email to sync Classroom.") { LinkText("Settings", openSettings) }
            }
            return@ScreenColumn
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Open work", "$openToday", "Due today", Modifier.weight(1f))
            StatTile("Bonus today", "+${Format.minutesLabel(bonusToday)}", "Approved", Modifier.weight(1f))
            StatTile("Waiting", if (pendingMin > 0) "+${Format.minutesLabel(pendingMin)}" else "None", "Needs approval", Modifier.weight(1f))
        }

        children.forEach { child ->
            ChildCard(
                child = child,
                pendingMin = pending.filter { it.childId == child.id }.sumOf { it.minutes },
                committed = Engine.bonusCommittedToday(state, child.id, now),
                cap = state.settings.dailyBonusCapMin,
                now = now,
                onClick = { openChild(child.id) },
            )
        }

        GrantQueue(pending, childrenById, onApply = { approving = it }, onSkip = vm::skip)

        EarnablePanel(Engine.earnableTonight(state, now), courses, vm::completeTask, openClassroom)

        RCard(spacing = 0) {
            CardHeader("Classroom today", "$doneToday submitted · $openToday still open") { LinkText("All work", openClassroom) }
            todayTasks.forEachIndexed { i, task ->
                if (i > 0) Divider()
                FamilyTaskRow(task, courses[task.courseId], state, now, vm)
            }
            if (todayTasks.isEmpty()) Text("Nothing due.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.padding(top = 12.dp))
        }

        RCard {
            CardHeader("This week", "Minutes used each day")
            WeekChart(children, now.toLocalDate())
            children.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.tone(c.tone)))
                    Text(" ${c.firstName}", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
                    Text("${Format.minutesLabel(c.usedTodayMin)} today", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                }
            }
        }

        RCard {
            CardHeader("How Recess works", "Classroom work becomes bonus screen time")
            Step("1", "A task is turned in", "Recess reads each linked child’s Google Classroom when you sync.")
            Step("2", "Your rules fire", "Turn-ins, quizzes, high scores, and a clean due-today list each earn set minutes.")
            Step("3", "You add the minutes", "Approve or auto-apply the bonus here, then give the same time in Family Link.")
        }

        RCard {
            CardHeader("Recent activity", "Completions, grants, and approvals") { LinkText("Full log", openLog) }
            state.activity.take(4).forEachIndexed { i, a ->
                if (i > 0) Divider()
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(a.message, style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft, modifier = Modifier.weight(1f))
                    Text(Format.clockLabel(a.at, now.zone), style = MaterialTheme.typography.bodySmall, color = Palette.Faint)
                }
            }
        }
    }

    approving?.let { grants ->
        ApproveDialog(grants, childrenById, onDismiss = { approving = null }) {
            vm.approve(grants.map { it.id })
            approving = null
        }
    }
}

@Composable
private fun SyncButton(syncing: Boolean, demo: Boolean, onClick: () -> Unit) {
    val spin = rememberInfiniteTransition(label = "sync")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    Button(onClick = onClick, enabled = !syncing, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
        Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp).rotate(if (syncing) angle else 0f))
        Text(
            when {
                syncing -> "  Syncing Classroom"
                demo -> "  Sync Classroom (demo)"
                else -> "  Sync Classroom"
            },
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun Step(n: String, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Palette.AccentSoft), contentAlignment = Alignment.Center) {
            Text(n, style = MaterialTheme.typography.titleLarge, color = Palette.Accent)
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
