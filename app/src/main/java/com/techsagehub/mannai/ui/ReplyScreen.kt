package com.techsagehub.mannai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techsagehub.mannai.assistant.ReplyTone

@Composable
fun ReplyScreen(vm: ReplyViewModel, onShare: (String) -> Unit) {
    val original by vm.original.collectAsState()
    val intent by vm.intent.collectAsState()
    val tone by vm.tone.collectAsState()
    val draft by vm.draft.collectAsState()
    val error by vm.error.collectAsState()
    val generating by vm.generating.collectAsState()

    // WhatsApp notification-driven state.
    val whatsAppSender by vm.whatsAppSender.collectAsState()
    val whatsAppMessage by vm.whatsAppMessage.collectAsState()
    val whatsAppContextNote by vm.whatsAppContextNote.collectAsState()
    val whatsAppContextFound by vm.whatsAppContextFound.collectAsState()
    val whatsAppIsAmbiguous by vm.whatsAppIsAmbiguous.collectAsState()
    val whatsAppIsGroup by vm.whatsAppIsGroup.collectAsState()
    val whatsAppActive = whatsAppMessage.isNotBlank()

    val clipboard = LocalClipboardManager.current
    val isWhatsAppDraft = whatsAppActive

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isWhatsAppDraft) {
            SectionHeading("WhatsApp Reply Draft", "Review and edit before sending — nothing is sent automatically")
            Text(
                "Replying to a WhatsApp message. Drafts are editable and require your explicit approval.",
                color = MutedText, fontSize = 12.sp, lineHeight = 17.sp
            )

            // Incoming message being replied to.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Panel)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Incoming message${if (whatsAppIsGroup) " (group)" else ""} from ${whatsAppSender}",
                        color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(whatsAppMessage, color = PrimaryText, fontSize = 14.sp)
                }
            }

            // Context / consent notice.
            if (whatsAppContextNote.isNotBlank()) {
                val contextColor = if (whatsAppIsAmbiguous) Red else (if (whatsAppContextFound) Green else MutedText)
                Text(
                    whatsAppContextNote,
                    color = contextColor, fontSize = 12.sp, lineHeight = 17.sp
                )
            }

            Text(
                "Generating a draft will send the incoming message and the selected imported context above " +
                    "to the configured AI provider (Gemini by default). Imported chats are not uploaded in full.",
                color = MutedText, fontSize = 11.sp, lineHeight = 16.sp
            )
        } else {
            SectionHeading("Message Reply Assistant", "Drafts stay on your phone until you share them")
            Text(
                "1. Paste the message you received. 2. Describe what you want to say. " +
                    "3. Pick a tone. Nothing is ever sent automatically.",
                color = MutedText, fontSize = 12.sp, lineHeight = 17.sp
            )
            Field(value = original, onChange = { vm.original.value = it },
                label = "Message you received", singleLine = false)
            Field(value = intent, onChange = { vm.intent.value = it },
                label = "What do you want to say?", singleLine = false)
        }

        Text("Tone", color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        ToneGrid(selected = tone, onSelect = { vm.tone.value = it })

        if (error != null) Text(error!!, color = Red, fontSize = 12.sp)

        val generateLabel = if (isWhatsAppDraft) "Generate AI draft" else "Generate draft"
        Button(
            onClick = {
                if (isWhatsAppDraft) vm.generateWhatsAppDraft() else vm.generate()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !generating,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)
        ) { Text(if (generating) "Generating…" else generateLabel) }

        if (generating) {
            OutlinedButton(
                onClick = { vm.cancelGeneration() },
                enabled = true,
                colors = ButtonDefaults.buttonColors(containerColor = PanelLight, contentColor = Red)
            ) { Text("Cancel", fontSize = 12.sp) }
        }

        if (draft.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Panel)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your draft${if (isWhatsAppDraft) " (editable)" else ""} (${tone.label})",
                        color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Field(value = draft, onChange = { vm.draft.value = it }, label = "Edit before sharing", singleLine = false)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (isWhatsAppDraft) vm.generateWhatsAppDraft() else vm.generate()
                            },
                            enabled = !generating
                        ) {
                            Text("Regenerate", color = Accent, fontSize = 12.sp)
                        }
                        if (isWhatsAppDraft) {
                            OutlinedButton(
                                onClick = { vm.refineWithAi() },
                                enabled = !generating
                            ) {
                                Text(if (generating) "Refining…" else "Refine with AI", color = Accent, fontSize = 12.sp)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { clipboard.setText(AnnotatedString(draft)) },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)
                        ) { Text("Copy", fontSize = 12.sp) }
                        Button(
                            onClick = { onShare(draft) },
                            colors = ButtonDefaults.buttonColors(containerColor = PanelLight, contentColor = PrimaryText)
                        ) { Text("Share to WhatsApp…", fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToneGrid(selected: ReplyTone, onSelect: (ReplyTone) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReplyTone.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    val isSel = t == selected
                    Button(
                        onClick = { onSelect(t) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSel) Accent else Panel,
                            contentColor = if (isSel) Background else PrimaryText
                        )
                    ) { Text(t.label, fontSize = 12.sp) }
                }
            }
        }
    }
}
