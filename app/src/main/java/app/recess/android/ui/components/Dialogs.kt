package app.recess.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.recess.android.ui.theme.Palette
import app.recess.core.Child
import app.recess.core.Format
import app.recess.core.Grant

@Composable
private fun Eyebrow(text: String) =
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Faint)

@Composable
fun GiveTimeDialog(child: Child, onDismiss: () -> Unit, onGive: (Int) -> Unit) {
    var minutes by rememberSaveable { mutableIntStateOf(15) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface2,
        title = {
            Column {
                Eyebrow("Bonus time")
                Text("Give extra time", style = MaterialTheme.typography.headlineSmall)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Records bonus minutes for ${child.firstName}’s ${child.device} today. Add the same amount in Family Link to put it on the device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 15, 30, 45).forEach { n ->
                        val active = minutes == n
                        Text(
                            "$n",
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            color = if (active) Palette.AccentFg else Palette.InkSoft,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) Palette.Accent else Palette.Bg)
                                .clickable(role = Role.RadioButton) { minutes = n }
                                .padding(vertical = 13.dp),
                        )
                    }
                }
                Text("Selected ${Format.minutesLabel(minutes)}", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            }
        },
        confirmButton = { PrimaryButton("Give +$minutes min", { onGive(minutes) }, small = true) },
        dismissButton = { GhostButton("Cancel", onDismiss) },
    )
}

@Composable
fun ApproveDialog(grants: List<Grant>, childrenById: Map<String, Child>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val total = grants.sumOf { it.minutes }
    val first = grants.firstOrNull()?.let { childrenById[it.childId] }
    val oneChild = grants.all { it.childId == grants.firstOrNull()?.childId }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface2,
        title = {
            Column {
                Eyebrow("Bonus time")
                Text("Approve extra time", style = MaterialTheme.typography.headlineSmall)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (oneChild && first != null) "Approves bonus minutes for ${first.firstName}’s ${first.device} today."
                    else "Approves bonus minutes for each child.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Muted,
                )
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Bg).padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("+$total", style = MaterialTheme.typography.displaySmall)
                    Text("minutes bonus", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                }
                grants.forEach { g ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "${childrenById[g.childId]?.firstName ?: "Child"} · ${g.reason}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.InkSoft,
                            modifier = Modifier.weight(1f),
                        )
                        Text("+${Format.minutesLabel(g.minutes)}", style = MaterialTheme.typography.bodyMedium, color = Palette.Accent)
                    }
                }
                Text("Then add it in the Family Link app — Google offers no way for other apps to do that.", style = MaterialTheme.typography.bodySmall, color = Palette.Faint)
            }
        },
        confirmButton = { PrimaryButton("Approve", onConfirm, small = true) },
        dismissButton = { GhostButton("Cancel", onDismiss) },
    )
}

data class NewChild(val name: String, val age: Int, val device: String, val limit: Int, val email: String?)

@Composable
fun AddChildDialog(onDismiss: () -> Unit, onAdd: (NewChild) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("10") }
    var device by rememberSaveable { mutableStateOf("Pixel Tablet") }
    var email by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableIntStateOf(90) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface2,
        title = { Text("Add a child", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Daily limits and bonus rules apply immediately.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                RTextField("Name", name, { name = it })
                RTextField("Age", age, { age = it.filter(Char::isDigit).take(2) })
                RTextField("Device", device, { device = it })
                RTextField("School email (optional)", email, { email = it.trim() }, placeholder = "student@school.edu")
                LabeledSlider("Daily limit", Format.minutesLabel(limit), limit, 30..240, 15, { limit = it })
            }
        },
        confirmButton = {
            PrimaryButton(
                "Add",
                { onAdd(NewChild(name.trim(), age.toIntOrNull() ?: 10, device.trim().ifBlank { "Android device" }, limit, email.ifBlank { null })) },
                enabled = name.isNotBlank(),
                small = true,
            )
        },
        dismissButton = { GhostButton("Cancel", onDismiss) },
    )
}

@Composable
fun LinkEmailDialog(child: Child, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var email by rememberSaveable { mutableStateOf(child.classroomEmail.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface2,
        title = { Text("Link ${child.firstName} to Classroom", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Enter ${child.firstName}’s school Google account. Recess reads their classes through your teacher or admin account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Muted,
                )
                androidx.compose.material3.OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    label = { Text("School email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Their current demo or hand-entered work is replaced on the next sync.", style = MaterialTheme.typography.bodySmall, color = Palette.Faint)
            }
        },
        confirmButton = { PrimaryButton("Save", { onSave(email.ifBlank { null }) }, enabled = email.isBlank() || '@' in email, small = true) },
        dismissButton = { GhostButton("Cancel", onDismiss) },
    )
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface2,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted) },
        confirmButton = { PrimaryButton(confirm, onConfirm, small = true) },
        dismissButton = { GhostButton("Cancel", onDismiss) },
    )
}

