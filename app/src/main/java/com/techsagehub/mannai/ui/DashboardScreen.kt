package com.techsagehub.mannai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import com.techsagehub.mannai.MainActivity
import com.techsagehub.mannai.assistant.WakePhrase
import com.techsagehub.mannai.data.SettingsStore
import com.techsagehub.mannai.service.AssistantService
import com.techsagehub.mannai.service.AssistantState
import com.techsagehub.mannai.voice.SpeechInput
import com.techsagehub.mannai.voice.VoiceState
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    assistantName: String,
    tasksVm: TasksViewModel,
    notesVm: NotesViewModel,
    remindersVm: RemindersViewModel,
    serviceState: AssistantState,
    serviceRunning: Boolean,
    onOpen: (String) -> Unit,
    onQuickChat: () -> Unit
) {
    val tasks by tasksVm.tasks.collectAsState()
    val notes by notesVm.notes.collectAsState()
    val reminders by remindersVm.reminders.collectAsState()
    val pending = tasks.filter { !it.done }
    val upcoming = reminders.filter { !it.fired }.take(3)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        DashboardHeader(assistantName, serviceState, serviceRunning)

        // Welcome card (MannAI identity preserved)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = PanelLight)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("YOUR DAY, SIMPLIFIED", color = Accent, fontSize = 10.sp,
                    letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold)
                Text("Assalamu Alaikum!", color = PrimaryText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(
                    "I'm $assistantName, your personal assistant. " +
                        "You have ${pending.size} pending task${if (pending.size == 1) "" else "s"}" +
                        (if (upcoming.isNotEmpty()) " and ${upcoming.size} upcoming reminder${if (upcoming.size == 1) "" else "s"}" else "") + ".",
                    color = MutedText, fontSize = 14.sp, lineHeight = 21.sp
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading("Quick actions", "What can I help you with?")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("✎", "New note", Modifier.weight(1f)) { onOpen(MainActivity.SCREEN_NOTES) }
                QuickAction("✓", "Add task", Modifier.weight(1f)) { onOpen(MainActivity.SCREEN_TASKS) }
                QuickAction("◷", "Reminder", Modifier.weight(1f)) { onOpen(MainActivity.SCREEN_REMINDERS) }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("✉", "Reply help", Modifier.weight(1f)) { onOpen(MainActivity.SCREEN_REPLY) }
                QuickAction("💬", "Chat", Modifier.weight(1f)) { onQuickChat() }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading("Your workspace", "Live data from your phone")
            InfoCard("✓", "My tasks",
                if (pending.isEmpty()) "No pending tasks. All clear!"
                else "${pending.size} pending — next: ${pending.first().title}",
                onClick = { onOpen(MainActivity.SCREEN_TASKS) })
            InfoCard("◷", "Reminders",
                if (upcoming.isEmpty()) "No upcoming reminders."
                else upcoming.joinToString("\n") { "• ${it.title} — ${formatDateTime(it.triggerAt)}" },
                onClick = { onOpen(MainActivity.SCREEN_REMINDERS) })
            val recent = notes.take(2)
            InfoCard("✎", "Notes",
                if (recent.isEmpty()) "No notes yet."
                else "${notes.size} saved — latest: ${recent.first().title.ifBlank { recent.first().content.take(30) }}",
                onClick = { onOpen(MainActivity.SCREEN_NOTES) })
            InfoCard("✉", "Message assistant",
                "Draft replies in any tone, then copy or share to WhatsApp.",
                onClick = { onOpen(MainActivity.SCREEN_REPLY) })
        }

        Text("PRIVATE BY DESIGN  •  WORKS OFFLINE", color = MutedText, fontSize = 10.sp,
            letterSpacing = 1.2.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
    }
}

@Composable
private fun DashboardHeader(name: String, serviceState: AssistantState, running: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = PrimaryText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("YOUR PERSONAL ASSISTANT", color = MutedText, fontSize = 9.sp, letterSpacing = 1.1.sp)
        }
        Card(shape = RoundedCornerShape(50), colors = CardDefaults.cardColors(containerColor = PanelLight)) {
            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusDot(active = running && serviceState != AssistantState.UNAVAILABLE)
                Text(
                    when {
                        !running -> "Idle"
                        serviceState == AssistantState.STANDBY -> "Listening"
                        else -> serviceState.label
                    },
                    color = PrimaryText, fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun Avatar(name: String) {
    Card(
        shape = androidx.compose.foundation.shape.CircleShape,
        colors = CardDefaults.cardColors(containerColor = Accent)
    ) {
        Text(
            name.take(1).uppercase(), color = Background, fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun QuickAction(emoji: String, title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = modifier,
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(emoji, color = Accent, fontSize = 23.sp)
            Text(title, color = PrimaryText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}
