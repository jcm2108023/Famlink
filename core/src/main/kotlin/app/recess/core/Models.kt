package app.recess.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Timestamps are epoch milliseconds throughout; calendar maths uses a [java.time.ZoneId]. */
typealias EpochMs = Long

@Serializable
enum class TaskState { @SerialName("assigned") Assigned, @SerialName("turnedIn") TurnedIn, @SerialName("returned") Returned }

@Serializable
enum class TaskKind { @SerialName("assignment") Assignment, @SerialName("quiz") Quiz, @SerialName("question") Question, @SerialName("shortAnswer") ShortAnswer }

@Serializable
enum class AppCategory { @SerialName("learning") Learning, @SerialName("social") Social, @SerialName("games") Games, @SerialName("video") Video, @SerialName("other") Other }

@Serializable
enum class RuleTrigger { @SerialName("turnedIn") TurnedIn, @SerialName("quizTurnedIn") QuizTurnedIn, @SerialName("highGrade") HighGrade, @SerialName("allDueTodayDone") AllDueTodayDone }

@Serializable
enum class GrantStatus { @SerialName("pending") Pending, @SerialName("provisioned") Provisioned, @SerialName("skipped") Skipped, @SerialName("capped") Capped }

@Serializable
enum class ActivityKind { @SerialName("sync") Sync, @SerialName("complete") Complete, @SerialName("grant") Grant, @SerialName("approve") Approve, @SerialName("skip") Skip, @SerialName("cap") Cap, @SerialName("rule") Rule, @SerialName("usage") Usage }

@Serializable
enum class Tone { @SerialName("sage") Sage, @SerialName("slate") Slate }

@Serializable
data class Child(
    val id: String,
    val name: String,
    val age: Int,
    val grade: String,
    val device: String,
    val tone: Tone,
    val dailyLimitMin: Int,
    val usedTodayMin: Int,
    val bonusTodayMin: Int,
    val autoApply: Boolean,
    /** "HH:mm", 24h. */
    val bedtime: String,
    val downtimeStart: String,
    val downtimeEnd: String,
    val school: String,
    /** "yyyy-MM-dd" of the last day the all-due-today bonus was issued. */
    val allDueBonusDate: String? = null,
    /** Seven days of minutes used, oldest first; the last entry is today. */
    val weeklyUsed: List<Int>,
    val lastProvisionedAt: EpochMs? = null,
) {
    val firstName: String get() = name.substringBefore(' ')
}

@Serializable
data class SupervisedApp(
    val id: String,
    val childId: String,
    val name: String,
    val category: AppCategory,
    val usedMin: Int,
    val limitMin: Int? = null,
    val blocked: Boolean,
)

@Serializable
data class Course(
    val id: String,
    val childId: String,
    val name: String,
    val teacher: String,
    val room: String,
)

@Serializable
data class ClassroomTask(
    val id: String,
    val childId: String,
    val courseId: String,
    val title: String,
    val kind: TaskKind,
    val dueAt: EpochMs,
    val state: TaskState,
    val gradePct: Double? = null,
    val maxPoints: Int,
    val turnedInAt: EpochMs? = null,
    val turnInGrantId: String? = null,
    val gradeGrantId: String? = null,
)

@Serializable
data class Rule(
    val id: String,
    val enabled: Boolean,
    val trigger: RuleTrigger,
    val minutes: Int,
    val highGradeThreshold: Int,
    val title: String,
    val detail: String,
)

@Serializable
data class Grant(
    val id: String,
    val childId: String,
    val minutes: Int,
    val reason: String,
    val taskId: String? = null,
    val ruleId: String,
    val status: GrantStatus,
    val createdAt: EpochMs,
    val provisionedAt: EpochMs? = null,
)

@Serializable
data class Activity(
    val id: String,
    val at: EpochMs,
    val childId: String? = null,
    val kind: ActivityKind,
    val message: String,
)

@Serializable
data class FamilySettings(
    val familyName: String,
    val parentName: String,
    val schoolName: String,
    val dailyBonusCapMin: Int,
    val applyAfterBedtime: Boolean,
    val lastSyncAt: EpochMs? = null,
)

@Serializable
data class FamilyState(
    val children: List<Child>,
    val courses: List<Course>,
    val tasks: List<ClassroomTask>,
    val rules: List<Rule>,
    val grants: List<Grant>,
    val activity: List<Activity>,
    val settings: FamilySettings,
    val apps: List<SupervisedApp>,
)
