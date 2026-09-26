package app.recess.android.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import app.recess.core.AppCategory
import app.recess.core.AppUsage
import app.recess.core.UsageSnapshot
import java.time.ZonedDateTime

/**
 * Reads real screen time on this phone from Android's usage stats (needs the Usage Access special
 * permission). This measures the device Recess is installed on — it cannot see Family Link.
 */
class UsageReader(private val context: Context) {

    fun hasAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun accessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Foreground minutes per app for today, plus daily totals for the last seven days. */
    fun read(now: ZonedDateTime): UsageSnapshot {
        val days = 7
        val dayStarts = (days - 1 downTo 0).map { back ->
            now.toLocalDate().minusDays(back.toLong()).atStartOfDay(now.zone).toInstant().toEpochMilli()
        }
        val nowMs = now.toInstant().toEpochMilli()
        val todayStart = dayStarts.last()
        val ignored = ignoredPackages()

        val perDay = LongArray(days)
        val todayByPackage = HashMap<String, Long>()

        fun record(pkg: String, from: Long, to: Long) {
            if (pkg in ignored || to <= from) return
            for (i in 0 until days) {
                val start = dayStarts[i]
                val end = if (i == days - 1) nowMs else dayStarts[i + 1]
                val overlap = minOf(to, end) - maxOf(from, start)
                if (overlap > 0) {
                    perDay[i] += overlap
                    if (i == days - 1) todayByPackage.merge(pkg, overlap, Long::plus)
                }
            }
        }

        val usm = context.getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(dayStarts.first(), nowMs)
        val event = UsageEvents.Event()
        val openSince = HashMap<String, Long>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                RESUMED -> openSince.putIfAbsent(pkg, event.timeStamp)
                PAUSED, STOPPED -> openSince.remove(pkg)?.let { record(pkg, it, event.timeStamp) }
            }
        }
        // Whatever is still in the foreground counts up to now.
        openSince.forEach { (pkg, since) -> record(pkg, since, nowMs) }

        val apps = todayByPackage.map { (pkg, ms) ->
            AppUsage(pkg, label(pkg), category(pkg), toMinutes(ms))
        }
        return UsageSnapshot(
            todayMinutes = toMinutes(perDay.last()),
            last7Days = perDay.map(::toMinutes),
            apps = apps,
        )
    }

    /** Recess itself, the launcher and system UI aren't screen time anyone chose. */
    private fun ignoredPackages(): Set<String> {
        val pm = context.packageManager
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launchers = pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo.packageName }
        return launchers.toSet() + context.packageName + "com.android.systemui"
    }

    private fun label(pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() })

    private fun category(pkg: String): AppCategory {
        if (LEARNING.any { pkg.contains(it) }) return AppCategory.Learning
        val info = runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.getOrNull() ?: return AppCategory.Other
        return when (info.category) {
            ApplicationInfo.CATEGORY_GAME -> AppCategory.Games
            ApplicationInfo.CATEGORY_VIDEO -> AppCategory.Video
            ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.Social
            else -> AppCategory.Other
        }
    }

    private fun toMinutes(ms: Long) = (ms / 60_000L).toInt()

    private companion object {
        // UsageEvents.Event constants by value: ACTIVITY_RESUMED / MOVE_TO_FOREGROUND, ACTIVITY_PAUSED /
        // MOVE_TO_BACKGROUND, and ACTIVITY_STOPPED (API 29+, never reported on older releases).
        const val RESUMED = 1
        const val PAUSED = 2
        const val STOPPED = 23
        val LEARNING = listOf("classroom", "khanacademy", "duolingo", "quizlet", "brainly", "photomath")
    }
}
