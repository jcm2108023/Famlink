package app.recess.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class UsageImportTest {
    private val now = ZonedDateTime.of(2026, 3, 10, 14, 0, 0, 0, ZoneId.of("UTC"))

    private fun snapshot(vararg apps: AppUsage) = UsageSnapshot(
        todayMinutes = apps.sumOf { it.minutesToday },
        last7Days = listOf(1, 2, 3, 4, 5, 6, 7, apps.sumOf { it.minutesToday }),
        apps = apps.toList(),
    )

    @Test fun `measured usage replaces hand-entered apps and totals`() {
        val s = UsageImport.apply(
            Seed.create(now), Seed.MAYA,
            snapshot(AppUsage("com.google.android.youtube", "YouTube", AppCategory.Video, 25), AppUsage("x.idle", "Idle", AppCategory.Other, 0)),
        )
        val maya = s.children.first { it.id == Seed.MAYA }
        assertEquals(25, maya.usedTodayMin)
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 25), maya.weeklyUsed)
        val apps = s.apps.filter { it.childId == Seed.MAYA }
        assertEquals(listOf("YouTube"), apps.map { it.name })
        assertTrue(s.apps.any { it.childId == Seed.JONAH })
    }

    @Test fun `parent limits and blocks survive later snapshots`() {
        val yt = AppUsage("com.google.android.youtube", "YouTube", AppCategory.Video, 25)
        val game = AppUsage("com.game", "Game", AppCategory.Games, 10)
        var s = UsageImport.apply(Seed.create(now), Seed.MAYA, snapshot(yt, game))
        s = Engine.patchApp(s, UsageImport.appId(Seed.MAYA, "com.game")) { it.copy(blocked = true) }
        s = Engine.patchApp(s, UsageImport.appId(Seed.MAYA, yt.packageName)) { it.copy(limitMin = 30) }

        s = UsageImport.apply(s, Seed.MAYA, snapshot(yt.copy(minutesToday = 40)))
        val apps = s.apps.filter { it.childId == Seed.MAYA }.associateBy { it.packageName }
        assertEquals(30, apps.getValue(yt.packageName).limitMin)
        assertEquals(40, apps.getValue(yt.packageName).usedMin)
        assertTrue(apps.getValue("com.game").blocked)
        assertEquals(0, apps.getValue("com.game").usedMin)
    }

    @Test fun `removing a child clears their data and the device link`() {
        val s = Seed.create(now).let { it.copy(settings = it.settings.copy(deviceChildId = Seed.MAYA)) }
        val r = Engine.removeChild(s, Seed.MAYA)
        assertTrue(r.children.none { it.id == Seed.MAYA })
        assertTrue(r.tasks.none { it.childId == Seed.MAYA } && r.apps.none { it.childId == Seed.MAYA })
        assertEquals(null, r.settings.deviceChildId)
    }
}
