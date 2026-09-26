package app.recess.core

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/** The demo family ("The Chens"), ported from the web app's `seed.ts`. Dates are relative to [now]. */
object Seed {
    const val MAYA = "child_maya"
    const val JONAH = "child_jonah"

    val DEFAULT_RULES = listOf(
        Rule("rule_turnin", true, RuleTrigger.TurnedIn, 15, 90, "Assignment turned in", "Bonus time when a Classroom assignment is submitted."),
        Rule("rule_quiz", true, RuleTrigger.QuizTurnedIn, 20, 90, "Quiz submitted", "Quizzes take more focus, so they earn a larger grant."),
        Rule("rule_grade", true, RuleTrigger.HighGrade, 10, 90, "High score returned", "Extra minutes when a returned grade meets the threshold."),
        Rule("rule_alldue", true, RuleTrigger.AllDueTodayDone, 25, 90, "All due-today work done", "A once-a-day bonus when nothing due today is still open."),
    )

    fun create(now: ZonedDateTime = ZonedDateTime.now()): FamilyState {
        fun atDay(offset: Long, hours: Int, minutes: Int = 0): EpochMs =
            now.plusDays(offset).toLocalDate().atTime(hours, minutes).atZone(now.zone).toInstant().toEpochMilli()

        fun hoursAgo(hours: Double): EpochMs =
            now.truncatedTo(ChronoUnit.HOURS).minusHours(hours.roundToLong()).toInstant().toEpochMilli()

        val children = listOf(
            Child(
                id = MAYA, name = "Maya Chen", age = 11, grade = "Grade 6", device = "Pixel Tablet", tone = Tone.Sage,
                dailyLimitMin = 90, usedTodayMin = 41, bonusTodayMin = 30, autoApply = true,
                bedtime = "20:30", downtimeStart = "20:30", downtimeEnd = "07:00", school = "Lincoln Middle School",
                weeklyUsed = listOf(78, 82, 64, 90, 71, 55, 41), lastProvisionedAt = hoursAgo(3.2),
            ),
            Child(
                id = JONAH, name = "Jonah Chen", age = 13, grade = "Grade 8", device = "Pixel 8a", tone = Tone.Slate,
                dailyLimitMin = 120, usedTodayMin = 88, bonusTodayMin = 15, autoApply = false,
                bedtime = "21:15", downtimeStart = "21:15", downtimeEnd = "07:00", school = "Lincoln Middle School",
                weeklyUsed = listOf(110, 98, 120, 104, 95, 80, 88), lastProvisionedAt = hoursAgo(3.9),
            ),
        )

        val apps = listOf(
            SupervisedApp("a_m_yt", MAYA, "YouTube", AppCategory.Video, 18, 30, false),
            SupervisedApp("a_m_ch", MAYA, "Chrome", AppCategory.Other, 11, null, false),
            SupervisedApp("a_m_cl", MAYA, "Classroom", AppCategory.Learning, 24, null, false),
            SupervisedApp("a_m_rb", MAYA, "Roblox", AppCategory.Games, 0, 20, true),
            SupervisedApp("a_m_gm", MAYA, "Gmail", AppCategory.Other, 4, null, false),
            SupervisedApp("a_j_yt", JONAH, "YouTube", AppCategory.Video, 38, 45, false),
            SupervisedApp("a_j_dc", JONAH, "Discord", AppCategory.Social, 16, 20, false),
            SupervisedApp("a_j_ch", JONAH, "Chrome", AppCategory.Other, 22, null, false),
            SupervisedApp("a_j_mc", JONAH, "Minecraft", AppCategory.Games, 27, 30, false),
            SupervisedApp("a_j_cl", JONAH, "Classroom", AppCategory.Learning, 12, null, false),
        )

        val courses = listOf(
            Course("c_m_math", MAYA, "Math 6", "Ms. Patel", "B12"),
            Course("c_m_sci", MAYA, "Earth Science", "Mr. Okonkwo", "Lab 2"),
            Course("c_m_ela", MAYA, "English Language Arts", "Mrs. Hale", "A4"),
            Course("c_m_art", MAYA, "Studio Art", "Mx. Rivera", "Studio"),
            Course("c_j_alg", JONAH, "Algebra I", "Mr. Cho", "C8"),
            Course("c_j_hist", JONAH, "U.S. History", "Ms. Brennan", "D3"),
            Course("c_j_span", JONAH, "Spanish 2", "Sr. Alvarez", "A9"),
            Course("c_j_cs", JONAH, "Computer Science", "Dr. Nguyen", "Lab 1"),
        )

        val tasks = listOf(
            ClassroomTask("t_m_volcano", MAYA, "c_m_sci", "Volcano lab report", TaskKind.Assignment, atDay(0, 16), TaskState.TurnedIn, maxPoints = 20, turnedInAt = hoursAgo(3.2), turnInGrantId = "g_m_volcano"),
            ClassroomTask("t_m_ela", MAYA, "c_m_ela", "Chapter 12 reader response", TaskKind.ShortAnswer, atDay(0, 15), TaskState.TurnedIn, maxPoints = 10, turnedInAt = hoursAgo(5.0), turnInGrantId = "g_m_ela"),
            ClassroomTask("t_m_quiz", MAYA, "c_m_math", "Fractions checkpoint", TaskKind.Quiz, atDay(0, 23, 59), TaskState.Assigned, maxPoints = 25),
            ClassroomTask("t_m_practice", MAYA, "c_m_math", "Practice set 4.2", TaskKind.Assignment, atDay(1, 8), TaskState.Assigned, maxPoints = 12),
            ClassroomTask("t_m_art", MAYA, "c_m_art", "Still-life sketch", TaskKind.Assignment, atDay(2, 15), TaskState.Assigned, maxPoints = 15),
            ClassroomTask("t_j_alg", JONAH, "c_j_alg", "Linear systems worksheet", TaskKind.Assignment, atDay(0, 15, 30), TaskState.TurnedIn, maxPoints = 20, turnedInAt = hoursAgo(4.0), turnInGrantId = "g_j_alg"),
            ClassroomTask("t_j_span", JONAH, "c_j_span", "Unidad 4 vocabulary quiz", TaskKind.Quiz, atDay(0, 14), TaskState.TurnedIn, maxPoints = 30, turnedInAt = hoursAgo(1.4), turnInGrantId = "g_j_span"),
            ClassroomTask("t_j_hist", JONAH, "c_j_hist", "DBQ: Reconstruction", TaskKind.Assignment, atDay(1, 8), TaskState.Assigned, maxPoints = 40),
            ClassroomTask("t_j_cs", JONAH, "c_j_cs", "App prototype milestone", TaskKind.Assignment, atDay(2, 16), TaskState.Assigned, maxPoints = 50),
            ClassroomTask("t_j_khan", JONAH, "c_j_alg", "Khan Academy: 20 minutes", TaskKind.Question, atDay(0, 21), TaskState.Assigned, maxPoints = 5),
        )

        val grants = listOf(
            Grant("g_m_volcano", MAYA, 15, "Turned in Volcano lab report", "t_m_volcano", "rule_turnin", GrantStatus.Provisioned, hoursAgo(3.2), hoursAgo(3.2)),
            Grant("g_m_ela", MAYA, 15, "Turned in Chapter 12 reader response", "t_m_ela", "rule_turnin", GrantStatus.Provisioned, hoursAgo(5.0), hoursAgo(5.0)),
            Grant("g_j_alg", JONAH, 15, "Turned in Linear systems worksheet", "t_j_alg", "rule_turnin", GrantStatus.Provisioned, hoursAgo(4.0), hoursAgo(3.9)),
            Grant("g_j_span", JONAH, 20, "Submitted Unidad 4 vocabulary quiz", "t_j_span", "rule_quiz", GrantStatus.Pending, hoursAgo(1.4)),
        )

        val activity = listOf(
            Activity("a1", hoursAgo(5.0), MAYA, ActivityKind.Complete, "Maya turned in Chapter 12 reader response. +15 min provisioned to Pixel Tablet."),
            Activity("a2", hoursAgo(4.0), JONAH, ActivityKind.Complete, "Jonah turned in Linear systems worksheet."),
            Activity("a3", hoursAgo(3.9), JONAH, ActivityKind.Approve, "You approved +15 min for Jonah’s Pixel 8a."),
            Activity("a4", hoursAgo(3.2), MAYA, ActivityKind.Grant, "Maya turned in Volcano lab report. +15 min auto-applied."),
            Activity("a5", hoursAgo(1.4), JONAH, ActivityKind.Grant, "Jonah submitted Unidad 4 quiz. +20 min waiting for your approval."),
        )

        return FamilyState(
            children = children,
            courses = courses,
            tasks = tasks,
            rules = DEFAULT_RULES,
            grants = grants,
            activity = activity,
            apps = apps,
            settings = FamilySettings(
                familyName = "The Chens",
                parentName = "Alex",
                schoolName = "Lincoln Middle School",
                dailyBonusCapMin = 60,
                applyAfterBedtime = false,
                lastSyncAt = hoursAgo(0.35),
            ),
        )
    }

    /** A newly added child starts with the same three supervised apps as the web app. */
    fun newChild(
        state: FamilyState,
        name: String,
        age: Int,
        device: String,
        dailyLimitMin: Int,
        id: String = "child_${Engine.newId().take(8)}",
    ): FamilyState {
        val child = Child(
            id = id, name = name, age = age, grade = "", device = device,
            tone = if (state.children.size % 2 == 0) Tone.Sage else Tone.Slate,
            dailyLimitMin = dailyLimitMin, usedTodayMin = 0, bonusTodayMin = 0, autoApply = false,
            bedtime = "20:30", downtimeStart = "20:30", downtimeEnd = "07:00", school = state.settings.schoolName,
            weeklyUsed = List(7) { 0 },
        )
        val apps = listOf(
            SupervisedApp("a_${id}_yt", id, "YouTube", AppCategory.Video, 0, 30, false),
            SupervisedApp("a_${id}_ch", id, "Chrome", AppCategory.Other, 0, null, false),
            SupervisedApp("a_${id}_cl", id, "Classroom", AppCategory.Learning, 0, null, false),
        )
        return state.copy(children = state.children + child, apps = state.apps + apps)
    }
}
