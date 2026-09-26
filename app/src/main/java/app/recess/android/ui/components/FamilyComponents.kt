package app.recess.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.recess.android.ui.theme.Palette
import app.recess.core.AppCategory
import app.recess.core.Child
import app.recess.core.ClassroomImport
import app.recess.core.ClassroomTask
import app.recess.core.Course
import app.recess.core.Engine
import app.recess.core.Format
import app.recess.core.Grant
import app.recess.core.GrantStatus
import app.recess.core.Rule
import app.recess.core.SupervisedApp
import app.recess.core.TaskState
import app.recess.core.remainingMinutes
import java.time.LocalDate
import java.time.ZonedDateTime

/** Ring of today's budget: used minutes in the child's colour, bonus share in soft sage. */
@Composable
fun TimeRing(child: Child, size: Int = 112) {
    val budget = child.dailyLimitMin + child.bonusTodayMin
    val used by animateFloatAsState(if (budget > 0) (child.usedTodayMin / budget.toFloat()).coerceIn(0f, 1f) else 0f, tween(500), label = "used")
    val color = Palette.tone(child.tone)
    val remaining = remainingMinutes(child)
    Box(
        Modifier.size(size.dp).semantics { contentDescription = "$remaining minutes left" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size.dp)) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(Palette.BgWarm, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (child.bonusTodayMin > 0) {
                drawArc(Palette.AccentSoft, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            if (used > 0f) drawArc(color, -90f, 360f * used, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$remaining", style = MaterialTheme.typography.headlineMedium)
            Text("MIN LEFT", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        }
    }
}

/** Seven days of minutes used, one bar per child per day. */
@Composable
fun WeekChart(children: List<Child>, today: LocalDate) {
    val max = children.flatMap { it.weeklyUsed }.maxOrNull()?.coerceAtLeast(1) ?: 1
    Column {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val groups = 7
            val groupWidth = size.width / groups
            val barWidth = minOf(14.dp.toPx(), groupWidth * 0.72f / children.size.coerceAtLeast(1))
            val gap = 2.dp.toPx()
            val totalBars = children.size * barWidth + (children.size - 1).coerceAtLeast(0) * gap
            for (day in 0 until groups) {
                var x = day * groupWidth + (groupWidth - totalBars) / 2
                children.forEach { child ->
                    val v = child.weeklyUsed.getOrElse(day) { 0 }
                    val h = size.height * v / max
                    drawRoundRect(
                        color = Palette.tone(child.tone).copy(alpha = if (child.tone == app.recess.core.Tone.Slate) 0.55f else 0.9f),
                        topLeft = Offset(x, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                    x += barWidth + gap
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            for (day in 0 until 7) {
                Text(
                    Format.weekdayShort(day, today),
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Muted,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChildCard(child: Child, pendingMin: Int, committed: Int, cap: Int, now: ZonedDateTime, onClick: () -> Unit) {
    val remaining = remainingMinutes(child)
    val downtime = Format.inDowntime(child.downtimeStart, child.downtimeEnd, now)
    RCard(Modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(child.name.take(1), Palette.tone(child.tone))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(child.firstName, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PhoneAndroid, null, Modifier.size(12.dp), tint = Palette.Muted)
                    Text(" ${child.device}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Open ${child.firstName}", tint = Palette.Faint)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimeRing(child)
            Column(Modifier.weight(1f).padding(start = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyValue("Daily limit", Format.minutesLabel(child.dailyLimitMin))
                KeyValue("Used today", Format.minutesLabel(child.usedTodayMin))
                KeyValue("Bonus earned", "+${Format.minutesLabel(child.bonusTodayMin)}")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (downtime) Badge("In downtime", BadgeTone.Warn)
                    else Badge("${Format.minutesLabel(remaining)} remaining", if (remaining == 0) BadgeTone.Warn else BadgeTone.Default)
                    if (pendingMin > 0) Badge("+${Format.minutesLabel(pendingMin)} waiting", BadgeTone.Outline)
                    Badge(if (child.autoApply) "Auto-apply" else "Needs approval", BadgeTone.Muted)
                }
            }
        }
        CapMeter(committed, cap)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskRow(
    task: ClassroomTask,
    course: Course?,
    rules: List<Rule>,
    now: ZonedDateTime,
    onComplete: (() -> Unit)?,
    onGrade: ((Int) -> Unit)?,
) {
    val open = task.state == TaskState.Assigned
    val rule = Engine.matchingRuleFor(task, rules)
    val earn = if (rule?.enabled == true) rule.minutes else 0
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        IconButton(onClick = { onComplete?.invoke() }, enabled = open && onComplete != null) {
            Icon(
                if (open) Icons.Outlined.Circle else Icons.Outlined.CheckCircle,
                if (open) "Mark ${task.title} turned in" else "Turned in",
                tint = if (open) Palette.Accent else Palette.Good,
            )
        }
        Column(Modifier.weight(1f).padding(start = 4.dp, top = 10.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleSmall, color = if (open) Palette.Ink else Palette.InkSoft)
                Badge(Format.kindLabel(task.kind), BadgeTone.Muted)
                val grade = task.gradePct
                if (task.state == TaskState.Returned && grade != null) Badge("${Math.round(grade)}%", BadgeTone.Good)
            }
            Text(
                "${course?.name ?: "Classroom"} · ${Format.dueLabel(task.dueAt, now)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Muted,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (open && onComplete != null) {
                PrimaryButton(if (earn > 0) "Turn in · +$earn min" else "Turn in", onComplete, Modifier.padding(top = 8.dp), small = true)
            }
            if (task.state == TaskState.TurnedIn && onGrade != null) {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(100, 92, 84).forEach { pct -> SecondaryButton("Return $pct%", { onGrade(pct) }) }
                }
            }
        }
    }
}

@Composable
fun GrantQueue(grants: List<Grant>, childrenById: Map<String, Child>, onApply: (List<Grant>) -> Unit, onSkip: (String) -> Unit) {
    if (grants.isEmpty()) return
    val total = grants.sumOf { it.minutes }
    RCard {
        CardHeader("Waiting for approval", "Approval-required children hold bonus time here until you approve it.")
        if (grants.size > 1) PrimaryButton("Approve all · +${Format.minutesLabel(total)}", { onApply(grants) }, small = true)
        grants.forEachIndexed { i, g ->
            if (i > 0) Divider()
            val child = childrenById[g.childId]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${child?.firstName ?: "Child"} · ${child?.device.orEmpty()}", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(Icons.Outlined.AccessTime, null, Modifier.size(14.dp), tint = Palette.Muted)
                        Text(" ${g.reason}", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text("+${Format.minutesLabel(g.minutes)}", style = MaterialTheme.typography.titleLarge, color = Palette.Accent, modifier = Modifier.padding(horizontal = 8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Approve", { onApply(listOf(g)) }, small = true)
                GhostButton("Skip") { onSkip(g.id) }
            }
        }
    }
}

@Composable
fun EarnablePanel(rows: List<Engine.EarnableRow>, courses: Map<String, Course>, onComplete: (String) -> Unit, onAll: () -> Unit) {
    if (rows.isEmpty()) return
    RCard {
        CardHeader("Earnable tonight", "Due Classroom work that still earns bonus time.") { LinkText("All work", onAll) }
        rows.forEachIndexed { i, row ->
            if (i > 0) Divider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(row.childName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${Format.minutesLabel(row.capLeft)} left on today’s cap", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
            row.items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val course = courses[item.courseId]?.name ?: "Classroom"
                        Text(course + if (item.minutes > 0) " · +${Format.minutesLabel(item.minutes)}" else "", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                    if (!item.taskId.startsWith(ClassroomImport.PREFIX)) {
                        PrimaryButton("Turn in", { onComplete(item.taskId) }, small = true)
                    }
                }
            }
            if (row.allDueMinutes > 0) {
                Text("Finish remaining due work for +${Format.minutesLabel(row.allDueMinutes)} more", style = MaterialTheme.typography.bodySmall, color = Palette.Accent)
            }
        }
    }
}

/** What the child's phone would show, mirroring the web app's device mock-up. */
@Composable
fun DevicePreview(child: Child, now: ZonedDateTime) {
    val remaining = remainingMinutes(child)
    val downtime = Format.inDowntime(child.downtimeStart, child.downtimeEnd, now)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.width(230.dp).clip(RoundedCornerShape(26.dp)).background(Palette.Ink).padding(8.dp)) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.Surface2).padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(Format.clockFromHhmm(Format.hhmm(now.toLocalTime())), style = MaterialTheme.typography.labelSmall, color = Palette.Faint)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.width(52.dp).height(6.dp).clip(CircleShape).background(Palette.BgWarm))
                    Spacer(Modifier.weight(1f))
                    Text("LTE", style = MaterialTheme.typography.labelSmall, color = Palette.Faint)
                }
                Text("FAMILY LINK", style = MaterialTheme.typography.labelSmall, color = Palette.Faint, modifier = Modifier.padding(top = 14.dp))
                Text(child.firstName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
                Text(child.device, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                if (remaining == 0 || downtime) {
                    Column(
                        Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Bg).padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(if (downtime) "Downtime" else "Time is up", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            if (downtime) "Until ${Format.clockFromHhmm(child.downtimeEnd)}" else "Daily limit reached",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.Muted,
                        )
                    }
                } else {
                    Text("$remaining", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 16.dp))
                    Text("minutes remaining", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
                Box(Modifier.padding(top = 12.dp)) { Badge("+${Format.minutesLabel(child.bonusTodayMin)} bonus") }
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SmallLine("Daily limit", Format.minutesLabel(child.dailyLimitMin))
                    SmallLine("Used", Format.minutesLabel(child.usedTodayMin))
                    SmallLine("Downtime", Format.clockFromHhmm(child.downtimeStart))
                }
            }
        }
    }
}

@Composable
private fun SmallLine(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        Text(v, style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
    }
}

private fun categoryIcon(category: AppCategory): ImageVector = when (category) {
    AppCategory.Learning -> Icons.AutoMirrored.Outlined.MenuBook
    AppCategory.Social -> Icons.AutoMirrored.Outlined.Chat
    AppCategory.Games -> Icons.Outlined.SportsEsports
    AppCategory.Video -> Icons.Outlined.Movie
    AppCategory.Other -> Icons.Outlined.Language
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppActivity(apps: List<SupervisedApp>, measured: Boolean, onBlock: (String, Boolean) -> Unit, onLimit: (String, Int?) -> Unit) {
    RCard {
        CardHeader(
            "Apps",
            if (measured) "Measured on this phone. Limits and blocks are a record: set them in Family Link to enforce them."
            else "Record the limits you set in Family Link.",
        )
        if (apps.isEmpty()) Text("No app usage yet today.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        apps.forEachIndexed { i, app ->
            if (i > 0) Divider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Bg), contentAlignment = Alignment.Center) {
                    Icon(categoryIcon(app.category), null, Modifier.size(18.dp), tint = Palette.Accent)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(app.name, style = MaterialTheme.typography.titleSmall)
                        Badge(Format.categoryLabel(app.category), BadgeTone.Muted)
                        if (app.blocked) Badge("Blocked", BadgeTone.Warn)
                    }
                    val limit = app.limitMin
                    Text(
                        "${Format.minutesLabel(app.usedMin)} used" + if (limit != null) " · ${Format.minutesLabel(limit)} limit" else " · no limit",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Muted,
                    )
                    if (limit != null && limit > 0 && !app.blocked) {
                        ProgressBar(app.usedMin / limit.toFloat(), Modifier.padding(top = 6.dp), height = 4)
                    }
                }
                RSwitch(!app.blocked, { on -> onBlock(app.id, !on) })
            }
            if (!app.blocked) {
                Box(Modifier.padding(start = 52.dp)) {
                    LabeledSlider(
                        "Daily app limit",
                        app.limitMin?.takeIf { it > 0 }?.let(Format::minutesLabel) ?: "None",
                        app.limitMin ?: 0,
                        0..180,
                        15,
                        { v -> onLimit(app.id, v.takeIf { it > 0 }) },
                    )
                }
            }
        }
    }
}

fun grantStatusLabel(status: GrantStatus): Pair<String, BadgeTone> = when (status) {
    GrantStatus.Provisioned -> "Approved" to BadgeTone.Good
    GrantStatus.Pending -> "Waiting" to BadgeTone.Outline
    GrantStatus.Capped -> "Capped" to BadgeTone.Warn
    GrantStatus.Skipped -> "Skipped" to BadgeTone.Muted
}
