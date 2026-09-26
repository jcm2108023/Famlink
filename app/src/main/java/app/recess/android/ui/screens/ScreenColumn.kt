package app.recess.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.recess.android.ui.FamilyViewModel
import app.recess.android.ui.components.TaskRow
import app.recess.core.ClassroomTask
import app.recess.core.Course
import app.recess.core.FamilyState
import java.time.ZonedDateTime

/** Scrolling page body with the web app's 16dp gutters and roomy section spacing. */
@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        content = content,
    )
}

/** A task row wired to the view model; Classroom-synced tasks are read-only (Classroom owns them). */
@Composable
fun FamilyTaskRow(task: ClassroomTask, course: Course?, state: FamilyState, now: ZonedDateTime, vm: FamilyViewModel) {
    val editable = !task.fromClassroom
    TaskRow(
        task = task,
        course = course,
        rules = state.rules,
        now = now,
        onComplete = if (editable) ({ vm.completeTask(task.id) }) else null,
        onGrade = if (editable) ({ pct -> vm.gradeTask(task.id, pct) }) else null,
    )
}
