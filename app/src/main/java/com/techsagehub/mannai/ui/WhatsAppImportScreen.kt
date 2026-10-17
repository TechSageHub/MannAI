package com.techsagehub.mannai.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.techsagehub.mannai.assistant.WhatsAppChatParser
import com.techsagehub.mannai.data.WhatsAppImportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Entry point: shows import picker button + imported conversations management. */
@Composable
fun WhatsAppImportScreen(
    repository: WhatsAppImportRepository,
    onImportComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    val contacts by repository.observeContacts().collectAsState(emptyList())
    var messageCounts by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }

    LaunchedEffect(contacts) {
        scope.launch {
            val counts = repository.messageCountsByContact().associate { it.contactId to it.cnt.toInt() }
            messageCounts = counts
        }
    }

    var pickerUri by remember { mutableStateOf<Uri?>(null) }
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        pickerUri = uri
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeading("Import WhatsApp chat history", "Import exported .txt conversations to your phone")

        Text(
            "Export a chat from WhatsApp (Export chat → Without media), then select the .txt file here. " +
                "You will review and assign each conversation before anything is saved.",
            color = MutedText, fontSize = 11.sp, lineHeight = 16.sp
        )

        Button(
            onClick = { pickLauncher.launch(arrayOf("text/plain")) },
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Choose exported .txt file", fontSize = 12.sp) }

        pickerUri?.let { uri ->
            WhatsAppImportPreview(
                uri = uri,
                repository = repository,
                onBack = { pickerUri = null },
                onComplete = {
                    pickerUri = null
                    onImportComplete()
                }
            )
        }

        Divider()

        SectionHeading("Imported conversations", "${contacts.size} conversation(s) stored locally")
        if (contacts.isEmpty()) {
            Text("No imported conversations yet.", color = MutedText, fontSize = 12.sp)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(contacts, key = { it.id }) { contact ->
                    val count = messageCounts[contact.id] ?: 0
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = PanelLight)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(contact.displayName, color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("$count messages", color = MutedText, fontSize = 12.sp)
                            }
                            Text("Imported ${formatDateTime(contact.importedAt)}", color = MutedText, fontSize = 11.sp)
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        repository.deleteContact(contact.id)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PanelLight, contentColor = Red)
                            ) { Text("Delete conversation", fontSize = 11.sp) }
                        }
                    }
                }
            }
        }

        Text(
            "Imported chats stay on this phone. They are never sent to any AI provider " +
                "and are not used to send WhatsApp replies automatically.",
            color = MutedText, fontSize = 10.sp, lineHeight = 15.sp
        )
    }
}

/** Preview + attribution step: review parsed messages, assign contact + user sender. */
@Composable
private fun WhatsAppImportPreview(
    uri: Uri,
    repository: WhatsAppImportRepository,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    var parseResult by remember { mutableStateOf<WhatsAppChatParser.ParseResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    // Attribution state.
    var contactNameDraft by remember { mutableStateOf("") }
    var userSenderDraft by remember { mutableStateOf("") }
    var uniqueSenders by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(uri) {
        scope.launch {
            loading = true
            error = null
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: run {
                    error = "Could not open the selected file."
                    loading = false
                    return@launch
                }
                val rawText = inputStream.bufferedReader().use { it.readText() }
                val result = WhatsAppChatParser.parse(rawText)
                parseResult = result
                uniqueSenders = WhatsAppChatParser.senderNames(result)
                if (uniqueSenders.size == 1) userSenderDraft = uniqueSenders[0]
            } catch (e: Exception) {
                error = "Failed to read file: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeading("Review import", "Check the parsed chat before saving")

        if (error != null) Text(error!!, color = Red, fontSize = 12.sp)

        if (loading) {
            LoadingRow("Parsing chat export…")
        } else if (parseResult != null) {
            val result = parseResult!!
            val stats = result.stats

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Messages:", color = PrimaryText, fontSize = 12.sp)
                Text("${stats.parsedMessages}", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("System:", color = PrimaryText, fontSize = 12.sp)
                Text("${stats.systemMessages}", color = MutedText, fontSize = 12.sp)
                Text("Media:", color = PrimaryText, fontSize = 12.sp)
                Text("${stats.mediaMessages}", color = MutedText, fontSize = 12.sp)
                Text("Unrecognized:", color = PrimaryText, fontSize = 12.sp)
                Text("${stats.unrecognized}", color = Red, fontSize = 12.sp)
            }

            Text("Senders found:", color = PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            uniqueSenders.forEach { sender ->
                Text("• $sender", color = MutedText, fontSize = 11.sp)
            }

            Divider()

            Text("Assign conversation", color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Give this conversation a contact name, and pick which sender is you. " +
                    "This is never inferred automatically.",
                color = MutedText, fontSize = 11.sp, lineHeight = 16.sp
            )
            OutlinedTextField(
                value = contactNameDraft,
                onValueChange = { contactNameDraft = it },
                label = { Text("Contact name") },
                singleLine = true,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = userSenderDraft,
                onValueChange = { userSenderDraft = it },
                label = { Text("Your sender name (as shown in export)") },
                singleLine = true,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Preview (first 5 messages):", color = PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            result.lines.take(5).forEach { line ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Panel)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                line.senderName ?: "System",
                                color = if (line.isSystem) MutedText else Accent,
                                fontSize = 11.sp, fontWeight = FontWeight.Medium
                            )
                            Text(formatDateTime(line.timestampMs), color = MutedText, fontSize = 10.sp)
                        }
                        Text(line.message, color = PrimaryText, fontSize = 12.sp)
                    }
                }
            }

            if (result.lines.size > 5) {
                Text("…and ${result.lines.size - 5} more message(s).", color = MutedText, fontSize = 11.sp)
            }

            if (result.unrecognizedLines.isNotEmpty()) {
                Text("Unrecognized lines (will be skipped):", color = Red, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                result.unrecognizedLines.take(3).forEach { line ->
                    Text("• ${line.take(60)}${if (line.length > 60) "…" else ""}", color = Red, fontSize = 10.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = PanelLight, contentColor = PrimaryText)
                ) { Text("Back", fontSize = 12.sp) }
                Button(
                    onClick = {
                        if (contactNameDraft.isBlank()) {
                            error = "Please enter a contact name."
                            return@Button
                        }
                        if (userSenderDraft.isBlank()) {
                            error = "Please specify your sender name."
                            return@Button
                        }
                        scope.launch {
                            try {
                                val contactId = repository.addContact(contactNameDraft.trim())
                                val importMsgs = result.lines
                                    .filter { it.senderName != null }
                                    .map { WhatsAppImportRepository.ImportedMessage(
                                        senderName = it.senderName!!,
                                        message = it.message,
                                        timestampMs = it.timestampMs
                                    ) }
                                repository.commitMessages(contactId, userSenderDraft.trim(), importMsgs)
                                onComplete()
                            } catch (e: Exception) {
                                error = "Import failed: ${e.message}"
                            }
                        }
                    },
                    enabled = contactNameDraft.isNotBlank() && userSenderDraft.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Import conversation", fontSize = 12.sp) }
            }
        }
    }
}
