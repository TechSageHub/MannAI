package com.techsagehub.mannai.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techsagehub.mannai.data.db.ReminderEntity
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar

@Composable
fun RemindersScreen(vm: RemindersViewModel, onGrantNotifications: () -> Unit) {
    val reminders by vm.reminders.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<ReminderEntity?>(null) }

    Scaffold(containerColor = Background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showEditor = true },
                containerColor = Accent, contentColor = Background
            ) { Text("+", fontSize = 24.sp) }
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeading("Reminders", "Alerts fire even when the app is closed")
            if (reminders.isEmpty()) {
                EmptyState(
                    "No reminders yet.",
                    "Tap + to schedule one, or say \"remind me tomorrow at 8 AM to…\"."
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(reminders, key = { it.id }) { r ->
                        ReminderCard(
                            r = r,
                            onToggle = { vm.toggleEnabled(r) },
                            onDelete = { toDelete = r }
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        ReminderEditor(
            onSave = { title, desc, at ->
                vm.add(title, desc, at)
                onGrantNotifications()
                showEditor = false
            },
            onDismiss = { showEditor = false }
        )
    }
    toDelete?.let { r ->
        ConfirmDialog("Delete reminder?", "\"${r.title}\" will be cancelled permanently.",
            onConfirm = { vm.delete(r); toDelete = null },
            onDismiss = { toDelete = null })
    }
}

@Composable
private fun ReminderCard(r: ReminderEntity, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(r.title, color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                val status = when {
                    r.fired -> "Delivered"
                    !r.enabled -> "Paused"
                    r.triggerAt <= System.currentTimeMillis() -> "Due now"
                    else -> formatDateTime(r.triggerAt)
                }
                Text(
                    (if (r.description.isNotBlank()) r.description + "\n" else "") + status,
                    color = MutedText, fontSize = 12.sp, lineHeight = 17.sp
                )
            }
            Switch(
                checked = r.enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedTrackColor = Accent)
            )
            TextButton(onClick = onDelete) { Text("Del", color = Red, fontSize = 12.sp) }
        }
    }
}

@Composable
fun ReminderEditor(onSave: (String, String, Long) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var at by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    fun pick() {
        val cal = Calendar.getInstance().apply {
            if (at != null) timeInMillis = at!!
            else add(Calendar.HOUR_OF_DAY, 1)
        }
        DatePickerDialog(context,
            { _, y, m, d ->
                TimePickerDialog(context,
                    { _, h, min ->
                        at = LocalDateTime.of(y, m + 1, d, h, min)
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    },
                    cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false
                ).show()
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelLight,
        titleContentColor = PrimaryText,
        textContentColor = PrimaryText,
        title = { Text("New reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (error != null) Text(error!!, color = Red, fontSize = 12.sp)
                Field(value = title, onChange = { title = it }, label = "Title")
                Field(value = desc, onChange = { desc = it }, label = "Description (optional)")
                Button(
                    onClick = { pick() },
                    colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = PrimaryText)
                ) {
                    Text(if (at == null) "Pick date & time" else "⏰ ${formatDateTime(at)}", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) {
                    error = "Title can't be empty."
                    return@TextButton
                }
                if (at == null) {
                    error = "Pick a date and time."
                    return@TextButton
                }
                if (at!! <= System.currentTimeMillis()) {
                    error = "Pick a time in the future."
                    return@TextButton
                }
                onSave(title.trim(), desc.trim(), at!!)
            }) { Text("Schedule", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MutedText) } }
    )
}
