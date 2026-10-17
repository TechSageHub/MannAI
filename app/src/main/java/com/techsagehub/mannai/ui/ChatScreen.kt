package com.techsagehub.mannai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.content.ContextCompat
import com.techsagehub.mannai.data.db.ChatMessageEntity
import com.techsagehub.mannai.voice.SpeechInput
import com.techsagehub.mannai.voice.VoiceState
import kotlinx.coroutines.delay

@Composable
fun ChatScreen(vm: ChatViewModel, assistantName: String, onSpeak: (String) -> Unit) {
    val messages by vm.messages.collectAsState()
    val busy by vm.busy.collectAsState()
    var input by remember { mutableStateOf("") }
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Voice input bound to this screen's lifecycle.
    var voiceError by remember { mutableStateOf<String?>(null) }
    var voiceState by remember { mutableStateOf(VoiceState.IDLE) }
    val speech = remember {
        SpeechInput(context) { heard, isFinal ->
            if (isFinal && heard.isNotBlank()) {
                voiceState = VoiceState.IDLE
                vm.send(heard)
            }
        }
    }
    DisposableEffect(Unit) { onDispose { speech.destroy() } }
    LaunchedEffect(Unit) {
        while (true) {
            voiceState = speech.state.value
            voiceError = speech.error.value
            delay(200)
        }
    }

    var micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) speech.start() else voiceError = "Microphone permission is required for voice input."
    }

    // Speak each new assistant message (if enabled by the activity).
    var spokenCount by remember { mutableStateOf(0) }
    LaunchedEffect(messages.size) {
        val last = messages.lastOrNull()
        if (last != null && last.role == "assistant" && messages.size > spokenCount) {
            spokenCount = messages.size
            onSpeak(last.text)
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            SectionHeading("Chat with $assistantName", "Offline commands • optional online AI")
            TextButton(onClick = { vm.clearHistory() }) { Text("Clear", color = MutedText, fontSize = 12.sp) }
        }

        if (messages.isEmpty()) {
            EmptyState(
                "Say hello to $assistantName.",
                "Try \"add a task to call Ahmed\", \"remind me at 5 PM to…\", or tap the mic and talk."
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { m -> ChatBubble(m, assistantName) }
            }
        }

        if (voiceState == VoiceState.LISTENING || voiceState == VoiceState.STARTING) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = PanelLight)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("🎙 Listening… speak now", color = Accent, fontSize = 13.sp,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        speech.stop()
                        voiceState = VoiceState.IDLE
                    }) { Text("Cancel", color = MutedText, fontSize = 12.sp) }
                }
            }
        }
        if (voiceError != null) {
            Text(voiceError!!, color = Red, fontSize = 12.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = {
                voiceError = null
                if (voiceState == VoiceState.LISTENING) {
                    speech.stop()
                    voiceState = VoiceState.IDLE
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    speech.start()
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }) {
                Text("🎙", fontSize = 24.sp, color = Accent)
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message $assistantName...", fontSize = 13.sp) },
                shape = RoundedCornerShape(18.dp),
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PrimaryText, unfocusedTextColor = PrimaryText,
                    focusedBorderColor = Accent, unfocusedBorderColor = PanelLight,
                    focusedPlaceholderColor = MutedText, unfocusedPlaceholderColor = MutedText,
                    cursorColor = Accent
                )
            )
            Button(
                onClick = {
                    if (input.isNotBlank()) {
                        vm.send(input)
                        input = ""
                    }
                },
                enabled = input.isNotBlank() && !busy,
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background),
                modifier = Modifier.height(52.dp)
            ) { Text("↑", fontSize = 20.sp) }
        }
    }
}

@Composable
private fun ChatBubble(m: ChatMessageEntity, assistantName: String) {
    val isUser = m.role == "user"
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Card(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (isUser) PanelLight else Panel)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!isUser) Text(assistantName, color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(m.text, color = PrimaryText, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
    }
}
