package app.recess.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/**
 * Merges real Google Classroom data for one child into the family state.
 *
 * The child's courses and tasks are replaced by what Classroom reports. The first import for a
 * child is a baseline: work already turned in is recorded but earns nothing retroactively. After
 * that, every transition Classroom reports (assigned → turned in, turned in → returned with a
 * grade) runs through the same [Engine] rules as a manual turn-in.
 */
object ClassroomImport {
    data class Result(val state: FamilyState, val completed: List<Engine.SyncItem>)

    const val PREFIX = "gc_"

    /** Work due (or, if undated, created) longer ago than this is left out. */
    const val HISTORY_DAYS = 14L

    fun courseId(childId: String, remoteCourseId: String) = "$PREFIX${remoteCourseId}_$childId"
    fun taskId(childId: String, remoteCourseId: String, workId: String) = "$PREFIX${remoteCourseId}_${workId}_$childId"

    fun apply(input: FamilyState, childId: String, remote: List<RemoteCourse>, now: ZonedDateTime): Result {
        val child = input.children.firstOrNull { it.id == childId } ?: return Result(input, emptyList())
        val previous = input.tasks.filter { it.childId == childId && it.fromClassroom }.associateBy { it.id }
        val baseline = previous.isEmpty()

        val courses = remote.map { rc ->
            Course(
                id = courseId(childId, rc.course.id),
                childId = childId,
                name = rc.course.name.ifBlank { "Classroom" },
                teacher = rc.teacherName.orEmpty(),
                room = rc.course.room ?: rc.course.section.orEmpty(),
            )
        }

        // What Classroom reports now, the version the engine starts from, and the grade to apply.
        data class Incoming(val remote: ClassroomTask, val staged: ClassroomTask, val runTurnIn: Boolean, val grade: Double?)

        val cutoff = now.minusDays(HISTORY_DAYS).toInstant().toEpochMilli()
        fun recent(work: Gc.CourseWork): Boolean {
            val at = dueAt(work) ?: work.creationTime?.let(::parseInstant) ?: return true
            return at >= cutoff
        }

        val incoming = remote.flatMap { rc ->
            rc.work.filter { (it.state == null || it.state == "PUBLISHED") && recent(it) }.map { work ->
                val id = taskId(childId, rc.course.id, work.id)
                val sub = rc.submissions.firstOrNull { it.courseWorkId == work.id }
                val remoteState = when (sub?.state) {
                    "TURNED_IN" -> TaskState.TurnedIn
                    "RETURNED" -> TaskState.Returned
                    else -> TaskState.Assigned
                }
                val max = work.maxPoints
                val grade = sub?.assignedGrade?.let { g -> max?.takeIf { it > 0 }?.let { g / it * 100.0 } }
                val prev = previous[id]
                val remoteTask = ClassroomTask(
                    id = id,
                    childId = childId,
                    courseId = courseId(childId, rc.course.id),
                    title = work.title.ifBlank { "Untitled work" },
                    kind = kindOf(work),
                    dueAt = dueAt(work),
                    state = remoteState,
                    gradePct = grade ?: prev?.gradePct,
                    maxPoints = max?.roundToInt() ?: 0,
                    turnedInAt = prev?.turnedInAt
                        ?: sub?.updateTime?.let(::parseInstant).takeIf { remoteState != TaskState.Assigned },
                    turnInGrantId = prev?.turnInGrantId,
                    gradeGrantId = prev?.gradeGrantId,
                )
                if (baseline) return@map Incoming(remoteTask, remoteTask, runTurnIn = false, grade = null)

                val runTurnIn = (prev == null || prev.state == TaskState.Assigned) &&
                    remoteState != TaskState.Assigned && prev?.turnInGrantId == null
                val newGrade = grade?.takeIf { remoteState == TaskState.Returned && it != prev?.gradePct }
                val staged = when {
                    runTurnIn -> remoteTask.copy(state = TaskState.Assigned, turnedInAt = null, gradePct = prev?.gradePct)
                    newGrade != null -> remoteTask.copy(state = TaskState.TurnedIn, gradePct = prev?.gradePct)
                    else -> remoteTask
                }
                Incoming(remoteTask, staged, runTurnIn, newGrade)
            }
        }

        var state = input.copy(
            courses = input.courses.filterNot { it.childId == childId } + courses,
            tasks = input.tasks.filterNot { it.childId == childId } + incoming.map { it.staged },
        )

        val completed = mutableListOf<Engine.SyncItem>()
        for (item in incoming) {
            if (!item.runTurnIn && item.grade == null) continue
            val grants = mutableListOf<Grant>()
            if (item.runTurnIn) {
                Engine.applyTurnIn(state, item.remote.id, now).let { state = it.state; grants += it.grants }
            }
            if (item.grade != null) {
                Engine.applyGrade(state, item.remote.id, item.grade, now).let { state = it.state; grants += it.grants }
            }
            // The engine's bookkeeping (grant ids, turn-in time) stays; the state is Classroom's.
            state = state.copy(tasks = state.tasks.map {
                if (it.id == item.remote.id) it.copy(state = item.remote.state, gradePct = item.remote.gradePct) else it
            })
            if (item.runTurnIn) {
                val live = grants.filter { it.status == GrantStatus.Pending || it.status == GrantStatus.Provisioned }
                completed += Engine.SyncItem(child.firstName, item.remote.title, live.sumOf { it.minutes }, grants.any { it.status == GrantStatus.Pending })
            }
        }
        return Result(state, completed)
    }

    /** Classroom has no quiz type: quizzes are assignments with a Google Form attached. */
    fun kindOf(work: Gc.CourseWork): TaskKind = when {
        work.materials.any { it.form != null } -> TaskKind.Quiz
        work.workType == "SHORT_ANSWER_QUESTION" -> TaskKind.ShortAnswer
        work.workType == "MULTIPLE_CHOICE_QUESTION" -> TaskKind.Question
        else -> TaskKind.Assignment
    }

    /** Classroom due dates and times are UTC; a missing time means end of that day. */
    fun dueAt(work: Gc.CourseWork): EpochMs? {
        val d = work.dueDate ?: return null
        val date = runCatching { LocalDate.of(d.year ?: return null, d.month ?: return null, d.day ?: return null) }.getOrNull()
            ?: return null
        val time = work.dueTime?.let { LocalTime.of((it.hours ?: 0).coerceIn(0, 23), (it.minutes ?: 0).coerceIn(0, 59)) }
            ?: LocalTime.of(23, 59)
        return date.atTime(time).toInstant(ZoneOffset.UTC).toEpochMilli()
    }

    private fun parseInstant(text: String): EpochMs? = runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
}
