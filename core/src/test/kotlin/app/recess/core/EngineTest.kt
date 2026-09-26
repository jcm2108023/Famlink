package app.recess.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Port of the web app's `engine.test.ts`, pinned to a fixed clock so it never depends on wall time. */
class EngineTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val daytime = ZonedDateTime.of(2026, 3, 10, 14, 0, 0, 0, zone)
    private val night = daytime.withHour(22).withMinute(40)

    private fun seed() = Seed.create(daytime)
    private fun FamilyState.child(id: String) = children.first { it.id == id }

    @Test fun `turning in an assignment issues bonus minutes`() {
        val (state, grants) = Engine.applyTurnIn(seed(), "t_j_khan", daytime)
        assertEquals(TaskState.TurnedIn, state.tasks.first { it.id == "t_j_khan" }.state)
        assertTrue(grants.any { it.minutes == 15 })
        assertEquals(GrantStatus.Pending, grants.first { it.ruleId == "rule_turnin" }.status)
    }

    @Test fun `quiz submission uses the larger quiz rule`() {
        val quiz = Engine.applyTurnIn(seed(), "t_m_quiz", daytime).grants.first { it.ruleId == "rule_quiz" }
        assertEquals(20, quiz.minutes)
        assertEquals(GrantStatus.Provisioned, quiz.status)
    }

    @Test fun `auto-apply waits after bedtime unless allowed`() {
        val quiz = Engine.applyTurnIn(seed(), "t_m_quiz", night).grants.first { it.ruleId == "rule_quiz" }
        assertEquals(GrantStatus.Pending, quiz.status)

        val allowed = seed().let { it.copy(settings = it.settings.copy(applyAfterBedtime = true)) }
        val quiz2 = Engine.applyTurnIn(allowed, "t_m_quiz", night).grants.first { it.ruleId == "rule_quiz" }
        assertEquals(GrantStatus.Provisioned, quiz2.status)
    }

    @Test fun `all due-today work complete awards the extra grant`() {
        val first = Engine.applyTurnIn(seed(), "t_j_khan", daytime)
        val allDue = first.grants.first { it.ruleId == "rule_alldue" }
        // Jonah had 35 min committed against a 60 min cap: 15 for the turn-in leaves room for 10.
        assertEquals(10, allDue.minutes)
        assertEquals(Format.todayKey(daytime), first.state.child(Seed.JONAH).allDueBonusDate)
    }

    @Test fun `high grade above threshold issues extra minutes`() {
        val (state, grants) = Engine.applyGrade(seed(), "t_m_volcano", 96.0, daytime)
        assertEquals(10, grants[0].minutes)
        assertEquals(GrantStatus.Provisioned, grants[0].status)
        assertEquals(TaskState.Returned, state.tasks.first { it.id == "t_m_volcano" }.state)
    }

    @Test fun `grade below threshold does not grant`() {
        assertEquals(0, Engine.applyGrade(seed(), "t_m_volcano", 80.0, daytime).grants.size)
    }

    @Test fun `regrading a task never grants twice`() {
        val once = Engine.applyGrade(seed(), "t_m_volcano", 96.0, daytime).state
        assertEquals(0, Engine.applyGrade(once, "t_m_volcano", 100.0, daytime).grants.size)
    }

    @Test fun `approve provisions pending grant onto the device`() {
        val s = seed()
        val before = s.child(Seed.JONAH).bonusTodayMin
        val next = Engine.approveGrant(s, "g_j_span", daytime)
        assertEquals(GrantStatus.Provisioned, next.grants.first { it.id == "g_j_span" }.status)
        assertEquals(before + 20, next.child(Seed.JONAH).bonusTodayMin)
    }

    @Test fun `skip leaves bonus off the device`() {
        val s = seed()
        val before = s.child(Seed.JONAH).bonusTodayMin
        val next = Engine.skipGrant(s, "g_j_span", daytime)
        assertEquals(GrantStatus.Skipped, next.grants.first { it.id == "g_j_span" }.status)
        assertEquals(before, next.child(Seed.JONAH).bonusTodayMin)
    }

    @Test fun `daily cap blocks further classroom grants`() {
        val s = seed().let { it.copy(settings = it.settings.copy(dailyBonusCapMin = 30)) }
        assertTrue(Engine.applyTurnIn(s, "t_j_khan", daytime).grants.any { it.status == GrantStatus.Capped })
    }

    @Test fun `parent extra time provisions immediately`() {
        val s = seed()
        val before = s.child(Seed.JONAH).bonusTodayMin
        val (state, grant) = Engine.grantManual(s, Seed.JONAH, 10, daytime)
        assertEquals(GrantStatus.Provisioned, grant?.status)
        assertEquals(before + 10, state.child(Seed.JONAH).bonusTodayMin)
    }

    @Test fun `classroom sync turns in due work`() {
        val summary = Engine.syncClassroom(seed(), daytime)
        assertFalse(summary.caughtUp)
        assertTrue(summary.completed.isNotEmpty())
        assertEquals(daytime.toInstant().toEpochMilli(), summary.state.settings.lastSyncAt)
    }

    @Test fun `approve all pending provisions every held grant`() {
        val s = seed()
        val before = s.child(Seed.JONAH).bonusTodayMin
        val next = Engine.approveAllPending(s, daytime)
        assertEquals(0, next.grants.count { it.status == GrantStatus.Pending })
        assertEquals(before + 20, next.child(Seed.JONAH).bonusTodayMin)
    }

    @Test fun `earnable tonight lists remaining due work`() {
        val rows = Engine.earnableTonight(seed(), daytime)
        assertTrue(rows.first { it.childId == Seed.MAYA }.items.any { it.taskId == "t_m_quiz" })
        val jonah = rows.first { it.childId == Seed.JONAH }
        assertTrue(jonah.items.any { it.taskId == "t_j_khan" })
        assertEquals(25, jonah.allDueMinutes)
    }

    @Test fun `bonus cap remaining accounts for pending grants`() {
        assertEquals(35, Engine.bonusCommittedToday(seed(), Seed.JONAH, daytime))
        assertEquals(25, Engine.remainingBonusCap(seed(), Seed.JONAH, daytime))
    }

    @Test fun `logging usage updates today and the weekly chart`() {
        val next = Engine.setUsage(seed(), Seed.MAYA, 60)
        assertEquals(60, next.child(Seed.MAYA).usedTodayMin)
        assertEquals(60, next.child(Seed.MAYA).weeklyUsed.last())
    }

    @Test fun `activity log is capped at 80 entries`() {
        var s = seed()
        repeat(100) { s = Engine.syncClassroom(s, daytime).state }
        assertEquals(80, s.activity.size)
    }

    @Test fun `new child gets default supervised apps`() {
        val next = Seed.newChild(seed(), "Ava Chen", 9, "Pixel 7", 60, id = "child_ava")
        assertNotNull(next.children.firstOrNull { it.id == "child_ava" })
        assertEquals(3, next.apps.count { it.childId == "child_ava" })
    }

    @Test fun `state survives a json round trip`() {
        val s = Engine.applyTurnIn(seed(), "t_j_khan", daytime).state
        assertEquals(s, FamilyJson.decode(FamilyJson.encode(s)))
    }
}
