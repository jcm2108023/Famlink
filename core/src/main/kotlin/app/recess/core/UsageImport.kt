package app.recess.core

/** Screen time measured on this phone (Android usage stats), ready to merge into a child. */
data class UsageSnapshot(
    val todayMinutes: Int,
    /** Seven days, oldest first; the last entry is today. */
    val last7Days: List<Int>,
    val apps: List<AppUsage>,
)

data class AppUsage(val packageName: String, val label: String, val category: AppCategory, val minutesToday: Int)

object UsageImport {
    const val MAX_APPS = 12

    /**
     * Replaces a child's usage numbers and app list with measured data. Parent-set app limits and
     * blocks are kept, and apps carrying one stay listed even on days they go unused.
     */
    fun apply(input: FamilyState, childId: String, snapshot: UsageSnapshot): FamilyState {
        if (input.children.none { it.id == childId }) return input
        val existing = input.apps.filter { it.childId == childId && it.packageName != null }.associateBy { it.packageName }

        val measured = snapshot.apps.filter { it.minutesToday > 0 }.sortedByDescending { it.minutesToday }.take(MAX_APPS)
        val measuredApps = measured.map { usage ->
            val prev = existing[usage.packageName]
            SupervisedApp(
                id = appId(childId, usage.packageName),
                childId = childId,
                name = usage.label,
                category = usage.category,
                usedMin = usage.minutesToday,
                limitMin = prev?.limitMin,
                blocked = prev?.blocked ?: false,
                packageName = usage.packageName,
            )
        }
        val shown = measured.map { it.packageName }.toSet()
        val keptControls = existing.values
            .filter { it.packageName !in shown && (it.limitMin != null || it.blocked) }
            .map { it.copy(usedMin = 0) }

        val week = List(7) { i -> snapshot.last7Days.getOrNull(snapshot.last7Days.size - 7 + i) ?: 0 }
        return input.copy(
            children = input.children.map {
                if (it.id == childId) it.copy(usedTodayMin = maxOf(0, snapshot.todayMinutes), weeklyUsed = week) else it
            },
            // Measured apps replace the hand-entered ones for this child.
            apps = input.apps.filterNot { it.childId == childId } + measuredApps + keptControls,
        )
    }

    fun appId(childId: String, packageName: String) = "pkg_${childId}_$packageName"
}
