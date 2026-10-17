package com.techsagehub.mannai.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import com.techsagehub.mannai.data.db.TaskEntity
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar

@Composable
fun TasksScreen(vm: TasksViewModel, filterDone: Boolean? = null) {
    val tasks by vm.tasks.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var toDelete by remember { mutableStateOf<TaskEntity?>(null) }

    val visible = remember(tasks, filterDone) {
        when (filterDone) {
            true -> tasks.filter { it.done }
            false -> tasks.filter { !it.done }
            null -> tasks
        }
    }

    Scaffold(containerColor = Background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editing = null; showEditor = true },
                containerColor = Accent, contentColor = Background
            ) { Text("+", fontSize = 24.sp) }
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeading(
                "Tasks",
                "${tasks.count { !it.done }} pending • ${tasks.count { it.done }} done"
            )
            var query by remember { mutableStateOf("") }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search tasks...", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PrimaryText, unfocusedTextColor = PrimaryText,
                    focusedBorderColor = Accent, unfocusedBorderColor = PanelLight,
                    focusedPlaceholderColor = MutedText, unfocusedPlaceholderColor = MutedText,
                    cursorColor = Accent
                )
            )
            val shown = if (query.isBlank()) visible
                else visible.filter {
                    it.title.contains(query, true) || it.description.contains(query, true)
                }
            if (shown.isEmpty()) {
                EmptyState(
                    if (tasks.isEmpty()) "No tasks yet." else "Nothing matches.",
                    "Tap + to add a task, or say \"add a task to…\"."
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(shown, key = { it.id }) { task ->
                        TaskRowFull(
                            task = task,
                            onToggle = { vm.toggleDone(task) },
                            onEdit = { editing = task; showEditor = true },
                            onDelete = { toDelete = task }
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        TaskEditor(
            existing = editing,
            onSave = { title, desc, due, prio, rem ->
                if (editing == null) vm.add(title, desc, due, prio, rem)
                else vm.update(editing!!.copy(title = title, description = desc, dueAt = due,
                    priority = prio, reminderAt = rem, reminderFired = false))
                showEditor = false
            },
            onDismiss = { showEditor = false }
        )
    }
    toDelete?.let { t ->
        ConfirmDialog("Delete task?", "\"${t.title}\" will be removed permanently.",
            onConfirm = { vm.delete(t); toDelete = null },
            onDismiss = { toDelete = null })
    }
}

@Composable
private fun TaskRowFull(task: TaskEntity, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = task.done,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = Accent, uncheckedColor = MutedText)
            )
            Column(modifier = Modifier.weight(1f).clickable { onToggle() }) {
                Text(
                    task.title, color = if (task.done) MutedText else PrimaryText,
                    fontSize = 14.sp, fontWeight = FontWeight.Medium
                )
                val sub = buildList {
                    if (task.description.isNotBlank()) add(task.description)
                    if (task.dueAt != null) add("Due ${formatDateTime(task.dueAt)}")
                    if (task.reminderAt != null) add("⏰ ${formatDateTime(task.reminderAt)}")
                    if (task.priority == 2) add("Urgent")
                    else if (task.priority == 1) add("High priority")
                }.joinToString(" • ")
                if (sub.isNotEmpty()) Text(sub, color = MutedText, fontSize = 12.sp)
            }
            TextButton(onClick = onEdit) { Text("Edit", color = Accent, fontSize = 12.sp) }
            TextButton(onClick = onDelete) { Text("Del", color = Red, fontSize = 12.sp) }
        }
    }
}

@Composable
fun TaskEditor(
    existing: TaskEntity?,
    onSave: (title: String, desc: String, due: Long?, prio: Int, rem: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var desc by remember { mutableStateOf(existing?.description ?: "") }
    var prio by remember { mutableStateOf(existing?.priority ?: 0) }
    var due by remember { mutableStateOf(existing?.dueAt) }
    var rem by remember { mutableStateOf(existing?.reminderAt) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    fun pickDateTime(current: Long?, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { if (current != null) timeInMillis = current }
        DatePickerDialog(context,
            { _, y, m, d ->
                TimePickerDialog(context,
                    { _, h, min ->
                        val ldt = LocalDateTime.of(y, m + 1, d, h, min)
                        onPicked(ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
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
        title = { Text(if (existing == null) "New task" else "Edit task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (error != null) Text(error!!, color = Red, fontSize = 12.sp)
                Field(value = title, onChange = { title = it }, label = "Title")
                Field(value = desc, onChange = { desc = it }, label = "Description (optional)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Normal", "High", "Urgent").forEachIndexed { i, label ->
                        val selected = prio == i
                        Button(
                            onClick = { prio = i },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) Accent else Panel,
                                contentColor = if (selected) Background else PrimaryText
                            )
                        ) { Text(label, fontSize = 11.sp) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { pickDateTime(due) { due = it } },
                        colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = PrimaryText)
                    ) { Text(if (due == null) "Set due date" else "Due: ${formatDateTime(due)}", fontSize = 11.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { pickDateTime(rem) { rem = it } },
                        colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = PrimaryText)
                    ) { Text(if (rem == null) "Set reminder" else "⏰ ${formatDateTime(rem)}", fontSize = 11.sp) }
                    if (rem != null) TextButton(onClick = { rem = null }) {
                        Text("Clear", color = MutedText, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) {
                    error = "Title can't be empty."
                    return@TextButton
                }
                if (due != null && (existing == null || due != existing.dueAt)) {
                    // past due dates allowed (overdue), no validation needed
                }
                if (rem != null && rem!! <= System.currentTimeMillis()) {
                    error = "Reminder must be in the future."
                    return@TextButton
                }
                onSave(title.trim(), desc.trim(), due, prio, rem)
            }) { Text("Save", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MutedText) } }
    )
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 12.sp) },
        singleLine = singleLine,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = PrimaryText, unfocusedTextColor = PrimaryText,
            focusedBorderColor = Accent, unfocusedBorderColor = MutedText,
            focusedLabelColor = Accent, unfocusedLabelColor = MutedText,
            cursorColor = Accent
        )
    )
}
