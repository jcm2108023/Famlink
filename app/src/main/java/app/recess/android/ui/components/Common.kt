package app.recess.android.ui.components

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.recess.android.ui.theme.Palette
import app.recess.core.Format
import kotlin.math.roundToInt

/** Card surface matching the web app's rounded, hairline-bordered cards. */
@Composable
fun RCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(20.dp),
    spacing: Int = 16,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Surface2)
            .border(1.dp, Palette.Line.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing.dp),
        content = content,
    )
}

@Composable
fun CardHeader(title: String, description: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.padding(top = 2.dp))
            }
        }
        action?.invoke()
    }
}

@Composable
fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Palette.Accent,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

@Composable
fun ScreenHeader(title: String, eyebrow: String? = null, subtitle: String? = null) {
    Column(Modifier.padding(top = 8.dp)) {
        if (eyebrow != null) Text(eyebrow, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 4.dp))
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

enum class BadgeTone { Default, Muted, Good, Warn, Outline }

@Composable
fun Badge(text: String, tone: BadgeTone = BadgeTone.Default) {
    val (bg, fg) = when (tone) {
        BadgeTone.Default -> Palette.AccentSoft to Palette.Accent
        BadgeTone.Muted -> Palette.BgWarm to Palette.Muted
        BadgeTone.Good -> Palette.AccentSoft to Palette.Good
        BadgeTone.Warn -> Palette.WarnSoft to Palette.Warn
        BadgeTone.Outline -> Color.Transparent to Palette.InkSoft
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .then(if (tone == BadgeTone.Outline) Modifier.border(1.dp, Palette.Line, CircleShape) else Modifier)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, small: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = if (small) 36.dp else 48.dp),
        shape = RoundedCornerShape(if (small) 10.dp else 14.dp),
        contentPadding = if (small) PaddingValues(horizontal = 14.dp, vertical = 6.dp) else ButtonDefaults.ContentPadding,
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, small: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (small) 36.dp else 48.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.BgWarm, contentColor = Palette.InkSoft),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Palette.InkSoft) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 44.dp), shape = RoundedCornerShape(12.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, shape = RoundedCornerShape(10.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.InkSoft)
    }
}

@Composable
fun StatTile(label: String, value: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Surface2)
            .border(1.dp, Palette.Line.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        Text(hint, style = MaterialTheme.typography.bodySmall, color = Palette.Faint, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun KeyValue(key: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, height: Int = 6) {
    Box(modifier.fillMaxWidth().height(height.dp).clip(CircleShape).background(Palette.BgWarm)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height.dp).clip(CircleShape).background(Palette.Accent))
    }
}

@Composable
fun CapMeter(used: Int, cap: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Daily bonus cap", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            Text("${Format.minutesLabel(used)} / ${Format.minutesLabel(cap)}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        ProgressBar(if (cap > 0) used / cap.toFloat() else 0f)
    }
}

@Composable
fun ToggleRow(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        }
        RSwitch(checked, onChange, enabled)
    }
}

@Composable
fun RSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = Palette.Accent,
            checkedThumbColor = Palette.AccentFg,
            uncheckedTrackColor = Palette.BgWarm,
            uncheckedThumbColor = Palette.Surface2,
            uncheckedBorderColor = Palette.Line,
        ),
    )
}

/** Slider with a label row; snaps to [step] like the web sliders. */
@Composable
fun LabeledSlider(
    label: String,
    valueText: String,
    value: Int,
    range: IntRange,
    step: Int,
    onChange: (Int) -> Unit,
    enabled: Boolean = true,
    valueColor: Color = Palette.InkSoft,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = valueColor)
        }
        val steps = ((range.last - range.first) / step - 1).coerceAtLeast(0)
        Slider(
            value = value.coerceIn(range).toFloat(),
            onValueChange = { v ->
                val snapped = (((v - range.first) / step).roundToInt() * step + range.first).coerceIn(range)
                if (snapped != value) onChange(snapped)
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = if (step > 1) steps else 0,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Palette.Surface2,
                activeTrackColor = Palette.Accent,
                inactiveTrackColor = Palette.BgWarm,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun RTextField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.Accent,
            unfocusedBorderColor = Palette.Line,
            focusedContainerColor = Palette.Surface2,
            unfocusedContainerColor = Palette.Surface2,
        ),
    )
}

/** Shows an "HH:mm" value and opens the platform time picker. */
@Composable
fun TimeField(label: String, hhmm: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(14.dp))
            .background(Palette.Surface2)
            .clickable(role = Role.Button) {
                val t = Format.parseHhmm(hhmm)
                TimePickerDialog(context, { _, h, m -> onChange(Format.hhmm(java.time.LocalTime.of(h, m))) }, t.hour, t.minute, false).show()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        Text(Format.clockFromHhmm(hhmm), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
fun FilterPill(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (active) Palette.AccentFg else Palette.InkSoft,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) Palette.Accent else Palette.Surface2)
            .then(if (active) Modifier else Modifier.border(1.dp, Palette.Line, CircleShape))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    )
}

@Composable
fun Divider() = HorizontalDivider(color = Palette.Line.copy(alpha = 0.7f))

@Composable
fun Avatar(initial: String, color: Color, size: Int = 40) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(initial, style = MaterialTheme.typography.titleLarge, color = Palette.AccentFg)
    }
}

@Composable
fun EmptyCard(title: String, detail: String) {
    RCard(spacing = 4) {
        Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
