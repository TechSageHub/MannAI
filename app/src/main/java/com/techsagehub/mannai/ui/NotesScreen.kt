package com.techsagehub.mannai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techsagehub.mannai.data.db.NoteEntity

@Composable
fun NotesScreen(vm: NotesViewModel) {
    val notes by vm.notes.collectAsState()
    var query by remember { mutableStateOf("") }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<NoteEntity?>(null) }
    var toDelete by remember { mutableStateOf<NoteEntity?>(null) }

    val shown = vm.searchResults(notes, query)

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
            SectionHeading("Notes", "${notes.size} saved on this phone")
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search notes...", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PrimaryText, unfocusedTextColor = PrimaryText,
                    focusedBorderColor = Accent, unfocusedBorderColor = PanelLight,
                    focusedPlaceholderColor = MutedText, unfocusedPlaceholderColor = MutedText,
                    cursorColor = Accent
                )
            )
            if (shown.isEmpty()) {
                EmptyState(
                    if (notes.isEmpty()) "No notes yet." else "No notes match.",
                    "Tap + to write one, or say \"create a note about…\"."
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(shown, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onOpen = { editing = note; showEditor = true },
                            onDelete = { toDelete = note }
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        NoteEditor(
            existing = editing,
            onSave = { title, content ->
                vm.save(editing?.id, title, content)
                showEditor = false
            },
            onDismiss = { showEditor = false }
        )
    }
    toDelete?.let { n ->
        ConfirmDialog("Delete note?", "This note will be removed permanently.",
            onConfirm = { vm.delete(n); toDelete = null },
            onDismiss = { toDelete = null })
    }
}

@Composable
private fun NoteCard(note: NoteEntity, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    note.title.ifBlank { "Untitled" },
                    color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDelete) { Text("Del", color = Red, fontSize = 12.sp) }
            }
            Text(
                note.content.take(220),
                color = MutedText, fontSize = 13.sp, lineHeight = 19.sp
            )
            Text(
                "Updated ${formatDateTime(note.updatedAt)}",
                color = MutedText, fontSize = 10.sp
            )
        }
    }
}

@Composable
fun NoteEditor(existing: NoteEntity?, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var content by remember { mutableStateOf(existing?.content ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelLight,
        titleContentColor = PrimaryText,
        textContentColor = PrimaryText,
        title = { Text(if (existing == null) "New note" else "Edit note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (error != null) Text(error!!, color = Red, fontSize = 12.sp)
                Field(value = title, onChange = { title = it }, label = "Title")
                Field(value = content, onChange = { content = it }, label = "Content", singleLine = false)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank() && content.isBlank()) {
                    error = "Note can't be empty."
                    return@TextButton
                }
                onSave(title.trim(), content.trim())
            }) { Text("Save", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MutedText) } }
    )
}
