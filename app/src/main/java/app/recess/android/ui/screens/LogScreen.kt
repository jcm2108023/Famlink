package app.recess.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.recess.android.ui.components.Badge
import app.recess.android.ui.components.BadgeTone
import app.recess.android.ui.components.Divider
import app.recess.android.ui.components.EmptyCard
import app.recess.android.ui.components.RCard
import app.recess.android.ui.components.ScreenHeader
import app.recess.android.ui.theme.Palette
import app.recess.core.ActivityKind
import app.recess.core.FamilyState
import app.recess.core.Format
import java.time.ZonedDateTime

private fun kindLabel(kind: ActivityKind) = when (kind) {
    ActivityKind.Sync -> "Sync"
    ActivityKind.Complete -> "Classroom"
    ActivityKind.Grant -> "Auto-applied"
    ActivityKind.Approve -> "Approved"
    ActivityKind.Skip -> "Skipped"
    ActivityKind.Cap -> "Cap"
    ActivityKind.Rule -> "Rule"
    ActivityKind.Usage -> "Usage"
}

@Composable
fun LogScreen(state: FamilyState, now: ZonedDateTime) {
    val names = state.children.associate { it.id to it.firstName }
    ScreenColumn {
        ScreenHeader("Activity", subtitle = "A running record of Classroom completions and bonus grants.")
        if (state.activity.isEmpty()) EmptyCard("No activity yet", "Turn in Classroom work or approve a grant to start the log.")
        state.activity.groupBy { Format.dayLabel(it.at, now) }.forEach { (day, items) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(day.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Faint)
                RCard(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), spacing = 0) {
                    items.forEachIndexed { i, a ->
                        if (i > 0) Divider()
                        Row(Modifier.padding(vertical = 12.dp)) {
                            Text(Format.clockLabel(a.at, now.zone), style = MaterialTheme.typography.bodySmall, color = Palette.Faint, modifier = Modifier.width(64.dp).padding(top = 2.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Badge(kindLabel(a.kind), BadgeTone.Muted)
                                    a.childId?.let(names::get)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted) }
                                }
                                Text(a.message, style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
