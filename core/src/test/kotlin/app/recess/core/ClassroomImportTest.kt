package app.recess.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ClassroomImportTest {
    private val zone = ZoneId.of("UTC")
    private val now = ZonedDateTime.of(2026, 3, 10, 14, 0, 0, 0, zone)
    private val kid = "child_ava"

    /** An empty family with one auto-apply child, linked to Classroom. */
    private fun family(): FamilyState {
        val base = Seed.newChild(Seed.empty(Seed.create(now).settings), "Ava Chen", 12, "Pixel 8", 90, "ava@school.edu", id = kid)
        return base.copy(children = base.children.map { it.copy(autoApply = true) })
    }

    private fun work(id: String, day: Int = 10, form: Boolean = false, max: Double? = 20.0) = Gc.CourseWork(
        id = id, courseId = "c1", title = "Work $id", workType = "ASSIGNMENT", state = "PUBLISHED",
        dueDate = Gc.Date(2026, 3, day), dueTime = Gc.TimeOfDay(23, 0), maxPoints = max,
        materials = if (form) listOf(Gc.Material(Gc.Form("https://forms"))) else emptyList(),
    )

    private fun sub(workId: String, state: String, grade: Double? = null) =
        Gc.Submission("s_$workId", workId, "u1", state, grade, "2026-03-10T12:00:00Z")

    private fun remote(vararg subs: Gc.Submission, works: List<Gc.CourseWork> = listOf(work("w1"), work("w2", form = true))) =
        listOf(RemoteCourse(Gc.Course("c1", "Biology", room = "Lab 3"), "Ms. Ruiz", works, subs.toList()))

    private fun FamilyState.task(workId: String) = tasks.first { it.id == ClassroomImport.taskId(kid, "c1", workId) }

    @Test fun `first import is a baseline with no retroactive grants`() {
        val r = ClassroomImport.apply(family(), kid, remote(sub("w1", "TURNED_IN"), sub("w2", "CREATED")), now)
        assertEquals(0, r.state.grants.size)
        assertEquals(0, r.completed.size)
        assertEquals(TaskState.TurnedIn, r.state.task("w1").state)
        assertEquals(TaskKind.Quiz, r.state.task("w2").kind)
        assertEquals("Biology", r.state.courses.single().name)
        assertEquals("Ms. Ruiz", r.state.courses.single().teacher)
    }

    @Test fun `a new turn-in after the baseline runs the rules engine`() {
        val base = ClassroomImport.apply(family(), kid, remote(sub("w1", "CREATED"), sub("w2", "CREATED")), now).state
        val r = ClassroomImport.apply(base, kid, remote(sub("w1", "CREATED"), sub("w2", "TURNED_IN")), now)
        val quizGrant = r.state.grants.first { it.ruleId == "rule_quiz" }
        assertEquals(20, quizGrant.minutes)
        assertEquals(GrantStatus.Provisioned, quizGrant.status)
        assertEquals(quizGrant.id, r.state.task("w2").turnInGrantId)
        assertEquals(1, r.completed.size)
        assertEquals(20, r.completed.single().minutes)
    }

    @Test fun `a returned high grade earns the grade bonus exactly once`() {
        val base = ClassroomImport.apply(family(), kid, remote(sub("w1", "TURNED_IN")), now).state
        val graded = ClassroomImport.apply(base, kid, remote(sub("w1", "RETURNED", 19.0)), now).state
        assertEquals(TaskState.Returned, graded.task("w1").state)
        assertEquals(95.0, graded.task("w1").gradePct!!, 0.001)
        assertEquals(1, graded.grants.count { it.ruleId == "rule_grade" })
        val again = ClassroomImport.apply(graded, kid, remote(sub("w1", "RETURNED", 19.0)), now).state
        assertEquals(1, again.grants.count { it.ruleId == "rule_grade" })
    }

    @Test fun `turned in and graded between syncs earns both bonuses`() {
        val base = ClassroomImport.apply(family(), kid, remote(sub("w1", "CREATED")), now).state
        val r = ClassroomImport.apply(base, kid, remote(sub("w1", "RETURNED", 20.0)), now)
        assertNotNull(r.state.grants.firstOrNull { it.ruleId == "rule_turnin" })
        assertNotNull(r.state.grants.firstOrNull { it.ruleId == "rule_grade" })
        assertEquals(TaskState.Returned, r.state.task("w1").state)
    }

    @Test fun `reclaim and resubmit does not grant twice`() {
        val base = ClassroomImport.apply(family(), kid, remote(sub("w1", "CREATED")), now).state
        val done = ClassroomImport.apply(base, kid, remote(sub("w1", "TURNED_IN")), now).state
        val reclaimed = ClassroomImport.apply(done, kid, remote(sub("w1", "RECLAIMED_BY_STUDENT")), now).state
        val again = ClassroomImport.apply(reclaimed, kid, remote(sub("w1", "TURNED_IN")), now).state
        assertEquals(1, again.grants.count { it.ruleId == "rule_turnin" })
    }

    @Test fun `demo work for the child is replaced by Classroom`() {
        val demo = Seed.create(now)
        val r = ClassroomImport.apply(demo, Seed.MAYA, remote(), now).state
        assertTrue(r.tasks.filter { it.childId == Seed.MAYA }.all { it.id.startsWith("gc_") })
        assertTrue(r.tasks.any { it.childId == Seed.JONAH && !it.id.startsWith("gc_") })
    }

    @Test fun `due dates are UTC and undated work has none`() {
        assertEquals(ZonedDateTime.of(2026, 3, 10, 23, 0, 0, 0, zone).toInstant().toEpochMilli(), ClassroomImport.dueAt(work("w1")))
        assertNull(ClassroomImport.dueAt(work("w1").copy(dueDate = null)))
        val noTime = work("w1").copy(dueTime = null)
        assertEquals(ZonedDateTime.of(2026, 3, 10, 23, 59, 0, 0, zone).toInstant().toEpochMilli(), ClassroomImport.dueAt(noTime))
    }

    @Test fun `api json decodes with unknown fields`() {
        val json = """{"courseWork":[{"id":"9","courseId":"c1","title":"Lab","workType":"ASSIGNMENT","state":"PUBLISHED",
            "dueDate":{"year":2026,"month":3,"day":11},"maxPoints":10,"alternateLink":"x","materials":[{"link":{"url":"u"}}]}],
            "nextPageToken":"p2"}"""
        val list = FamilyJson.json.decodeFromString(Gc.CourseWorkList.serializer(), json)
        assertEquals("p2", list.nextPageToken)
        assertEquals(TaskKind.Assignment, ClassroomImport.kindOf(list.courseWork.single()))
    }
}
