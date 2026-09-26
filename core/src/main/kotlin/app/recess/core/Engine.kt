package app.recess.core

import java.time.ZonedDateTime
import java.util.UUID

/**
 * The bonus-time rules engine, ported from the web app's `engine.ts`.
 *
 * Every public function is pure: it takes a [FamilyState] and returns a new one. Internally each
 * call works on a mutable [Draft] so the port can follow the original step by step.
 */
object Engine {
    private const val ACTIVITY_LIMIT = 80

    data class TurnInResult(val state: FamilyState, val grants: List<Grant>)

    data class ManualGrantResult(val state: FamilyState, val grant: Grant?)

    data class SyncItem(val childName: String, val title: String, val minutes: Int, val pending: Boolean)

    data class SyncSummary(val state: FamilyState, val completed: List<SyncItem>) {
        val caughtUp: Boolean get() = completed.isEmpty()
    }

    data class EarnableItem(val taskId: String, val title: String, val minutes: Int, val courseId: String)

    data class EarnableRow(
        val childId: String,
        val childName: String,
        val device: String,
        val capLeft: Int,
        val allDueMinutes: Int,
        val items: List<EarnableItem>,
    )

    // ---- Queries -------------------------------------------------------------------------------

    fun isDueTodayOrOverdue(task: ClassroomTask, now: ZonedDateTime): Boolean =
        Format.isToday(task.dueAt, now) || task.dueAt < now.toInstant().toEpochMilli()

    fun dueTodayOpen(state: FamilyState, childId: String, now: ZonedDateTime): List<ClassroomTask> =
        dueTodayOpen(state.tasks, childId, now)

    private fun dueTodayOpen(tasks: List<ClassroomTask>, childId: String, now: ZonedDateTime) =
        tasks.filter { it.childId == childId && it.state == TaskState.Assigned && isDueTodayOrOverdue(it, now) }

    /** Minutes already provisioned or waiting today — both count against the daily cap. */
    fun bonusCommittedToday(state: FamilyState, childId: String, now: ZonedDateTime): Int =
        committedToday(state.grants, childId, now)

    private fun committedToday(grants: List<Grant>, childId: String, now: ZonedDateTime): Int {
        val start = now.toLocalDate().atStartOfDay(now.zone).toInstant().toEpochMilli()
        return grants
            .filter {
                it.childId == childId &&
                    (it.status == GrantStatus.Provisioned || it.status == GrantStatus.Pending) &&
                    it.createdAt >= start
            }
            .sumOf { it.minutes }
    }

    fun remainingBonusCap(state: FamilyState, childId: String, now: ZonedDateTime): Int =
        maxOf(0, state.settings.dailyBonusCapMin - bonusCommittedToday(state, childId, now))

    fun matchingRuleFor(task: ClassroomTask, rules: List<Rule>): Rule? {
        val trigger = if (task.kind == TaskKind.Quiz) RuleTrigger.QuizTurnedIn else RuleTrigger.TurnedIn
        return rules.firstOrNull { it.trigger == trigger }
    }

    fun earnableTonight(state: FamilyState, now: ZonedDateTime): List<EarnableRow> {
        val allDue = state.rules.firstOrNull { it.trigger == RuleTrigger.AllDueTodayDone }
        val today = Format.todayKey(now)
        return state.children.mapNotNull { child ->
            val open = dueTodayOpen(state, child.id, now)
            if (open.isEmpty()) return@mapNotNull null
            val items = open.map { task ->
                val rule = matchingRuleFor(task, state.rules)
                EarnableItem(task.id, task.title, if (rule?.enabled == true) rule.minutes else 0, task.courseId)
            }
            val allDueMinutes =
                if (allDue != null && allDue.enabled && child.allDueBonusDate != today) allDue.minutes else 0
            EarnableRow(child.id, child.firstName, child.device, remainingBonusCap(state, child.id, now), allDueMinutes, items)
        }
    }

    // ---- Commands ------------------------------------------------------------------------------

    fun applyTurnIn(input: FamilyState, taskId: String, now: ZonedDateTime): TurnInResult {
        val d = Draft(input)
        val task = d.tasks.firstOrNull { it.id == taskId }
        if (task == null || task.state != TaskState.Assigned) return TurnInResult(d.build(), emptyList())
        val nowMs = now.toInstant().toEpochMilli()
        d.updateTask(taskId) { it.copy(state = TaskState.TurnedIn, turnedInAt = nowMs) }

        val name = d.child(task.childId)?.firstName ?: "Child"
        val isQuiz = task.kind == TaskKind.Quiz
        d.pushActivity(nowMs, task.childId, ActivityKind.Complete, "$name ${if (isQuiz) "submitted" else "turned in"} ${task.title}.")

        val issued = mutableListOf<String>()
        val trigger = if (isQuiz) RuleTrigger.QuizTurnedIn else RuleTrigger.TurnedIn
        d.rules.firstOrNull { it.trigger == trigger }?.let { rule ->
            val verb = if (isQuiz) "Submitted" else "Turned in"
            d.tryIssue(task.childId, rule, rule.minutes, "$verb ${task.title}", now, task.id)?.let { grantId ->
                if (d.grant(grantId)?.status != GrantStatus.Capped) {
                    d.updateTask(taskId) { it.copy(turnInGrantId = grantId) }
                }
                issued += grantId
            }
        }

        val allDueRule = d.rules.firstOrNull { it.trigger == RuleTrigger.AllDueTodayDone }
        val child = d.child(task.childId)
        val today = Format.todayKey(now)
        if (allDueRule != null && child != null && child.allDueBonusDate != today &&
            dueTodayOpen(d.tasks, child.id, now).isEmpty()
        ) {
            d.tryIssue(child.id, allDueRule, allDueRule.minutes, "All due-today Classroom work complete", now)
                ?.let { grantId ->
                    if (d.grant(grantId)?.status != GrantStatus.Capped) {
                        d.updateChild(child.id) { it.copy(allDueBonusDate = today) }
                        issued += grantId
                    }
                }
        }

        return TurnInResult(d.build(), issued.mapNotNull(d::grant))
    }

    fun applyGrade(input: FamilyState, taskId: String, gradePct: Double, now: ZonedDateTime): TurnInResult {
        val d = Draft(input)
        val task = d.tasks.firstOrNull { it.id == taskId }
        if (task == null || task.state == TaskState.Assigned) return TurnInResult(d.build(), emptyList())
        d.updateTask(taskId) { it.copy(gradePct = gradePct, state = TaskState.Returned) }
        if (task.gradeGrantId != null) return TurnInResult(d.build(), emptyList())

        val rule = d.rules.firstOrNull { it.trigger == RuleTrigger.HighGrade }
        if (rule == null || gradePct < rule.highGradeThreshold) return TurnInResult(d.build(), emptyList())

        val reason = "${Math.round(gradePct)}% on ${task.title}"
        val grantId = d.tryIssue(task.childId, rule, rule.minutes, reason, now, task.id)
            ?: return TurnInResult(d.build(), emptyList())
        if (d.grant(grantId)?.status != GrantStatus.Capped) {
            d.updateTask(taskId) { it.copy(gradeGrantId = grantId) }
        }
        return TurnInResult(d.build(), listOfNotNull(d.grant(grantId)))
    }

    fun approveGrant(input: FamilyState, grantId: String, now: ZonedDateTime): FamilyState =
        Draft(input).apply { provision(grantId, now, auto = false) }.build()

    fun approveAllPending(input: FamilyState, now: ZonedDateTime): FamilyState {
        val d = Draft(input)
        d.grants.filter { it.status == GrantStatus.Pending }.map { it.id }.forEach { d.provision(it, now, auto = false) }
        return d.build()
    }

    fun skipGrant(input: FamilyState, grantId: String, now: ZonedDateTime): FamilyState {
        val d = Draft(input)
        val grant = d.grant(grantId)
        if (grant == null || grant.status != GrantStatus.Pending) return input
        d.updateGrant(grantId) { it.copy(status = GrantStatus.Skipped) }
        val forName = d.child(grant.childId)?.let { " for ${it.firstName}" } ?: ""
        d.pushActivity(now.toInstant().toEpochMilli(), grant.childId, ActivityKind.Skip, "Skipped +${grant.minutes} min$forName.")
        return d.build()
    }

    fun setUsage(input: FamilyState, childId: String, minutes: Int): FamilyState {
        val d = Draft(input)
        d.child(childId) ?: return input
        val used = maxOf(0, minutes)
        d.updateChild(childId) { c ->
            val week = c.weeklyUsed.toMutableList().apply { if (isNotEmpty()) this[lastIndex] = used }
            c.copy(usedTodayMin = used, weeklyUsed = week)
        }
        return d.build()
    }

    /** Parent-initiated extra time: capped like any other grant, then provisioned immediately. */
    fun grantManual(input: FamilyState, childId: String, minutes: Int, now: ZonedDateTime): ManualGrantResult {
        val d = Draft(input)
        if (d.child(childId) == null || minutes <= 0) return ManualGrantResult(input, null)
        val rule = Rule(
            id = "rule_manual",
            enabled = true,
            trigger = RuleTrigger.TurnedIn,
            minutes = minutes,
            highGradeThreshold = 90,
            title = "Parent grant",
            detail = "Extra time given from the parent dashboard.",
        )
        val grantId = d.tryIssue(childId, rule, minutes, "Extra time from parent", now)
        if (grantId != null && d.grant(grantId)?.status == GrantStatus.Pending) {
            d.provision(grantId, now, auto = false)
        }
        return ManualGrantResult(d.build(), grantId?.let(d::grant))
    }

    fun patchApp(input: FamilyState, appId: String, patch: (SupervisedApp) -> SupervisedApp): FamilyState =
        input.copy(apps = input.apps.map { if (it.id == appId) patch(it) else it })

    fun updateRule(input: FamilyState, ruleId: String, patch: (Rule) -> Rule): FamilyState =
        input.copy(rules = input.rules.map { if (it.id == ruleId) patch(it) else it })

    /**
     * Simulated Classroom sync: turns in up to two open tasks (due today / overdue first), exactly
     * like the web preview's demo replay.
     */
    fun syncClassroom(input: FamilyState, now: ZonedDateTime): SyncSummary {
        var state = input
        val assigned = state.tasks.filter { it.state == TaskState.Assigned }.sortedBy { it.dueAt }
        val due = assigned.filter { isDueTodayOrOverdue(it, now) }
        val targets = due.ifEmpty { assigned }.take(2)

        val completed = targets.map { task ->
            val result = applyTurnIn(state, task.id, now)
            state = result.state
            val live = result.grants.filter { it.status == GrantStatus.Pending || it.status == GrantStatus.Provisioned }
            SyncItem(
                childName = state.children.firstOrNull { it.id == task.childId }?.firstName ?: "Child",
                title = task.title,
                minutes = live.sumOf { it.minutes },
                pending = result.grants.any { it.status == GrantStatus.Pending },
            )
        }

        val d = Draft(state)
        val nowMs = now.toInstant().toEpochMilli()
        d.settings = d.settings.copy(lastSyncAt = nowMs)
        val message = if (completed.isEmpty()) {
            "Classroom sync: every assignment is already turned in."
        } else {
            "Classroom sync found ${completed.size} new submission${if (completed.size == 1) "" else "s"}."
        }
        d.pushActivity(nowMs, null, ActivityKind.Sync, message)
        return SyncSummary(d.build(), completed)
    }

    // ---- Mutable working copy ------------------------------------------------------------------

    private class Draft(state: FamilyState) {
        val children = state.children.toMutableList()
        val courses = state.courses
        val tasks = state.tasks.toMutableList()
        val rules = state.rules
        val grants = state.grants.toMutableList()
        val activity = state.activity.toMutableList()
        var settings = state.settings
        val apps = state.apps

        fun build() = FamilyState(children.toList(), courses, tasks.toList(), rules, grants.toList(), activity.toList(), settings, apps)

        fun child(id: String) = children.firstOrNull { it.id == id }
        fun grant(id: String) = grants.firstOrNull { it.id == id }

        fun updateChild(id: String, f: (Child) -> Child) = children.replaceAll { if (it.id == id) f(it) else it }
        fun updateTask(id: String, f: (ClassroomTask) -> ClassroomTask) = tasks.replaceAll { if (it.id == id) f(it) else it }
        fun updateGrant(id: String, f: (Grant) -> Grant) = grants.replaceAll { if (it.id == id) f(it) else it }

        fun pushActivity(at: EpochMs, childId: String?, kind: ActivityKind, message: String) {
            activity.add(0, Activity(newId(), at, childId, kind, message))
            while (activity.size > ACTIVITY_LIMIT) activity.removeAt(activity.lastIndex)
        }

        /** Issues a grant against the daily cap; returns its id, or null if the rule is off. */
        fun tryIssue(
            childId: String,
            rule: Rule,
            minutesWanted: Int,
            reason: String,
            now: ZonedDateTime,
            taskId: String? = null,
        ): String? {
            if (!rule.enabled || minutesWanted <= 0) return null
            val nowMs = now.toInstant().toEpochMilli()
            val room = maxOf(0, settings.dailyBonusCapMin - committedToday(grants, childId, now))
            if (room <= 0) {
                val capped = Grant(newId(), childId, 0, reason, taskId, rule.id, GrantStatus.Capped, nowMs)
                grants.add(0, capped)
                pushActivity(nowMs, childId, ActivityKind.Cap, "Daily bonus cap reached. $reason was not granted.")
                return capped.id
            }
            val grant = Grant(newId(), childId, minOf(minutesWanted, room), reason, taskId, rule.id, GrantStatus.Pending, nowMs)
            grants.add(0, grant)
            maybeAutoProvision(grant.id, now)
            return grant.id
        }

        private fun maybeAutoProvision(grantId: String, now: ZonedDateTime) {
            val grant = grant(grantId) ?: return
            if (grant.status != GrantStatus.Pending || grant.minutes <= 0) return
            val child = child(grant.childId) ?: return
            if (!child.autoApply) return
            if (Format.isAfterBedtime(child.bedtime, now) && !settings.applyAfterBedtime) return
            provision(grantId, now, auto = true)
        }

        fun provision(grantId: String, now: ZonedDateTime, auto: Boolean) {
            val grant = grant(grantId) ?: return
            if (grant.status != GrantStatus.Pending) return
            val nowMs = now.toInstant().toEpochMilli()
            updateGrant(grantId) { it.copy(status = GrantStatus.Provisioned, provisionedAt = nowMs) }
            updateChild(grant.childId) { it.copy(bonusTodayMin = it.bonusTodayMin + grant.minutes, lastProvisionedAt = nowMs) }
            val child = child(grant.childId)
            val name = child?.firstName ?: "Child"
            val device = child?.device ?: "device"
            val verb = if (auto) "Auto-applied" else "You approved"
            pushActivity(
                nowMs,
                grant.childId,
                if (auto) ActivityKind.Grant else ActivityKind.Approve,
                "$verb +${grant.minutes} min for $name’s $device · ${grant.reason}.",
            )
        }
    }

    internal fun newId(): String = UUID.randomUUID().toString()
}
