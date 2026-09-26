package app.recess.android.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.recess.android.ui.FamilyViewModel
import app.recess.android.ui.components.ChipRow
import app.recess.android.ui.components.Divider
import app.recess.android.ui.components.EmptyCard
import app.recess.android.ui.components.FilterPill
import app.recess.android.ui.components.RCard
import app.recess.android.ui.components.ScreenHeader
import app.recess.android.ui.theme.Palette
import app.recess.core.Engine
import app.recess.core.FamilyState
import app.recess.core.TaskState
import java.time.ZonedDateTime

private const val ALL = "all"
private const val DUE_TODAY = "dueToday"

@Composable
fun ClassroomScreen(state: FamilyState, now: ZonedDateTime, vm: FamilyViewModel) {
    var childId by rememberSaveable { mutableStateOf(ALL) }
    var status by rememberSaveable { mutableStateOf(ALL) }

    val scoped = state.tasks.filter { childId == ALL || it.childId == childId }
    val open = scoped.count { it.state == TaskState.Assigned }
    val dueToday = scoped.count { it.state == TaskState.Assigned && Engine.isDueTodayOrOverdue(it, now) }
    val filtered = scoped.filter {
        when (status) {
            ALL -> true
            DUE_TODAY -> it.state == TaskState.Assigned && Engine.isDueTodayOrOverdue(it, now)
            else -> it.state.name == status
        }
    }.sortedWith(Engine.byDue)
    val courseById = state.courses.associateBy { it.id }
    val grouped = filtered.groupBy { it.courseId }

    ScreenColumn {
        ScreenHeader(
            "Classroom",
            eyebrow = state.settings.schoolName.ifBlank { null },
            subtitle = "Work from each child’s Google Classroom. Marking a task turned in runs your bonus rules automatically.",
        )
        ChipRow {
            FilterPill("Everyone · $open open", childId == ALL) { childId = ALL }
            state.children.forEach { c -> FilterPill(c.firstName, childId == c.id) { childId = c.id } }
        }
        ChipRow {
            listOf(
                ALL to "All states",
                DUE_TODAY to "Due today · $dueToday",
                TaskState.Assigned.name to "Open",
                TaskState.TurnedIn.name to "Turned in",
                TaskState.Returned.name to "Returned",
            ).forEach { (id, label) -> FilterPill(label, status == id) { status = id } }
        }
        if (grouped.isEmpty()) {
            EmptyCard("Nothing in this view", "Try another child or status filter.")
        }
        grouped.forEach { (courseId, tasks) ->
            val course = courseById[courseId]
            val child = state.children.firstOrNull { it.id == course?.childId }
            RCard(spacing = 0) {
                Text(course?.name ?: "Classroom", style = MaterialTheme.typography.titleLarge)
                Text(
                    listOfNotNull(child?.firstName, course?.teacher?.ifBlank { null }, course?.room?.ifBlank { null }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Muted,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                )
                tasks.forEachIndexed { i, task ->
                    if (i > 0) Divider()
                    FamilyTaskRow(task, course, state, now, vm)
                }
            }
        }
    }
}

