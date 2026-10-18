package com.techsagehub.mannai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techsagehub.mannai.MannAIApp
import com.techsagehub.mannai.assistant.AiProvider
import com.techsagehub.mannai.assistant.AiProviderFactory
import com.techsagehub.mannai.assistant.Command
import com.techsagehub.mannai.assistant.CommandExecutor
import com.techsagehub.mannai.assistant.CommandParser
import com.techsagehub.mannai.assistant.ConfirmAction
import com.techsagehub.mannai.assistant.DisabledAiProvider
import com.techsagehub.mannai.assistant.Effect
import com.techsagehub.mannai.assistant.OpenAiCompatibleProvider
import com.techsagehub.mannai.assistant.PendingConfirm
import com.techsagehub.mannai.assistant.ReplyDrafter
import com.techsagehub.mannai.assistant.ReplyPromptBuilder
import com.techsagehub.mannai.assistant.ReplyTone
import com.techsagehub.mannai.assistant.WhatsAppContextSelector
import com.techsagehub.mannai.assistant.WhatsAppDraftResult
import com.techsagehub.mannai.assistant.WhatsAppMessageEvent
import com.techsagehub.mannai.assistant.WhatsAppNotificationParser
import com.techsagehub.mannai.assistant.WhatsAppReplyGenerator
import com.techsagehub.mannai.data.db.ChatMessageEntity
import com.techsagehub.mannai.data.db.NoteEntity
import com.techsagehub.mannai.data.db.ReminderEntity
import com.techsagehub.mannai.data.db.TaskEntity
import com.techsagehub.mannai.notify.ReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class AppViewModels(val app: MannAIApp) {
    val tasksVm = TasksViewModel(app)
    val notesVm = NotesViewModel(app)
    val remindersVm = RemindersViewModel(app)
    val chatVm = ChatViewModel(app)
    val replyVm = ReplyViewModel(app)
}

/** One-shot UI events (snackbars, navigation, permission prompts). */
sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class OpenScreen(val screen: String) : UiEvent
    data object RequestMic : UiEvent
    data object RequestNotifications : UiEvent
    data object RequestStartListening : UiEvent
    data object RequestStopListening : UiEvent
}

abstract class BaseViewModel : ViewModel() {
    protected val _events = MutableStateFlow<UiEvent?>(null)
    val events: StateFlow<UiEvent?> = _events.asStateFlow()
    fun consumeEvent() { _events.value = null }
    protected fun emit(e: UiEvent) { _events.value = e }
}

// ---------------- Tasks ----------------

class TasksViewModel(private val app: MannAIApp) : BaseViewModel() {
    val tasks: StateFlow<List<TaskEntity>> =
        app.tasks.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleDone(t: TaskEntity) = viewModelScope.launch {
        runCatching { app.tasks.toggleDone(t) }.onFailure { emit(UiEvent.Message("Couldn't update task.")) }
    }

    fun delete(t: TaskEntity) = viewModelScope.launch {
        runCatching {
            app.tasks.delete(t)
            ReminderScheduler.cancel(app, "task", t.id)
        }.onFailure { emit(UiEvent.Message("Couldn't delete task.")) }
    }

    fun add(title: String, description: String, dueAtMillis: Long?, priority: Int, reminderAtMillis: Long?) =
        viewModelScope.launch {
            if (title.isBlank()) {
                emit(UiEvent.Message("Task title can't be empty."))
                return@launch
            }
            runCatching {
                val id = app.tasks.add(title.trim(), description.trim(), dueAtMillis, priority, reminderAtMillis)
                if (reminderAtMillis != null) {
                    ReminderScheduler.schedule(app, "task", id, reminderAtMillis, "Task reminder", title.trim())
                }
            }.onFailure { emit(UiEvent.Message("Couldn't save task.")) }
        }

    fun update(t: TaskEntity) = viewModelScope.launch {
        runCatching { app.tasks.update(t) }.onFailure { emit(UiEvent.Message("Couldn't save changes.")) }
    }
}

// ---------------- Notes ----------------

class NotesViewModel(private val app: MannAIApp) : BaseViewModel() {
    val notes: StateFlow<List<NoteEntity>> =
        app.notes.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val query = MutableStateFlow("")
    val filtered: StateFlow<List<NoteEntity>> =
        app.notes.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(id: Long?, title: String, content: String) = viewModelScope.launch {
        if (title.isBlank() && content.isBlank()) {
            emit(UiEvent.Message("Note can't be empty."))
            return@launch
        }
        runCatching {
            if (id == null) app.notes.add(title, content)
            else {
                val existing = app.notes.observeAll()
                // one-shot fetch via DAO would be nicer; update from list copy instead:
                app.notes.update(NoteEntity(id = id, title = title, content = content,
                    createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
                existing
            }
        }.onFailure { emit(UiEvent.Message("Couldn't save note.")) }
    }

    fun delete(n: NoteEntity) = viewModelScope.launch {
        runCatching { app.notes.delete(n) }.onFailure { emit(UiEvent.Message("Couldn't delete note.")) }
    }

    fun searchResults(all: List<NoteEntity>, q: String): List<NoteEntity> {
        if (q.isBlank()) return all
        return all.filter { it.title.contains(q, true) || it.content.contains(q, true) }
    }
}

// ---------------- Reminders ----------------

class RemindersViewModel(private val app: MannAIApp) : BaseViewModel() {
    val reminders: StateFlow<List<ReminderEntity>> =
        app.reminders.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(title: String, description: String, triggerAtMillis: Long) = viewModelScope.launch {
        if (title.isBlank()) {
            emit(UiEvent.Message("Reminder title can't be empty."))
            return@launch
        }
        if (triggerAtMillis <= System.currentTimeMillis()) {
            emit(UiEvent.Message("Pick a time in the future."))
            return@launch
        }
        runCatching {
            val id = app.reminders.add(title.trim(), description.trim(), triggerAtMillis)
            ReminderScheduler.schedule(app, "reminder", id, triggerAtMillis, title.trim(), description.trim())
        }.onFailure { emit(UiEvent.Message(it.message ?: "Couldn't save reminder.")) }
    }

    fun toggleEnabled(r: ReminderEntity) = viewModelScope.launch {
        val next = r.copy(enabled = !r.enabled, fired = if (!r.enabled) false else r.fired)
        runCatching {
            app.reminders.update(next)
            if (next.enabled && !next.fired && next.triggerAt > System.currentTimeMillis()) {
                ReminderScheduler.schedule(app, "reminder", next.id, next.triggerAt, next.title, next.description)
            } else {
                ReminderScheduler.cancel(app, "reminder", next.id)
            }
        }.onFailure { emit(UiEvent.Message("Couldn't update reminder.")) }
    }

    fun delete(r: ReminderEntity) = viewModelScope.launch {
        runCatching {
            app.reminders.delete(r)
            ReminderScheduler.cancel(app, "reminder", r.id)
        }.onFailure { emit(UiEvent.Message("Couldn't delete reminder.")) }
    }
}

// ---------------- Chat ----------------

class ChatViewModel(val app: MannAIApp) : BaseViewModel() {
    val messages: StateFlow<List<ChatMessageEntity>> =
        app.chat.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val busy = MutableStateFlow(false)
    private var pending: PendingConfirm? = null

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || busy.value) return
        viewModelScope.launch {
            busy.value = true
            try {
                app.chat.addUser(text)
                // Resolve a pending confirmation first.
                val parsed = CommandParser.parse(text, LocalDateTime.now())
                if (pending != null && (parsed is Command.Confirm || parsed is Command.CancelConfirm)) {
                    if (parsed is Command.Confirm) {
                        val res = app.executor.confirm(pending!!.action)
                        app.chat.addAssistant(res.text)
                    } else {
                        app.chat.addAssistant("Cancelled — nothing was changed.")
                    }
                    pending = null
                    return@launch
                }
                pending = null
                val result = runCatching { app.executor.execute(parsed) }
                    .getOrElse { com.techsagehub.mannai.assistant.CommandResult("Something went wrong. Please try again.") }
                if (result.confirm != null) pending = result.confirm
                val display = when {
                    result.text.startsWith("__ai__:") -> {
                        val reply = askAi(result.text.removePrefix("__ai__:"))
                        reply
                    }
                    result.text == "__confirm__" || result.text == "__cancel__" ->
                        "There's nothing waiting for confirmation right now."
                    else -> result.text
                }
                app.chat.addAssistant(display)
                handleEffect(result.effect)
            } finally {
                busy.value = false
            }
        }
    }

    private suspend fun askAi(prompt: String): String {
        val provider = aiProvider()
        if (!provider.isConfigured) return "Online AI isn't configured. Your offline commands still work — say \"help\"."
        val system = "You are ${app.settings.assistantNameValue}, a concise personal assistant on the user's phone. " +
            "Keep answers short (under 120 words) unless asked for detail."
        return provider.chat(system, prompt).getOrElse {
            "I couldn't reach the online AI (${it.message}). Offline commands still work — say \"help\"."
        }
    }

    fun aiProvider(): AiProvider {
        return AiProviderFactory.create(app.settings)
    }

    private fun handleEffect(effect: com.techsagehub.mannai.assistant.Effect) {
        when (effect) {
            is Effect.RequestStartListening -> emit(UiEvent.RequestStartListening)
            is Effect.RequestStopListening -> emit(UiEvent.RequestStopListening)
            is Effect.OpenReply -> emit(UiEvent.OpenScreen(com.techsagehub.mannai.MainActivity.SCREEN_REPLY))
            is Effect.AskAi -> { /* handled inline */ }
            is Effect.None -> {}
        }
    }

    fun clearHistory() = viewModelScope.launch {
        runCatching { app.chat.clear() }.onFailure { emit(UiEvent.Message("Couldn't clear history.")) }
    }
}

// ---------------- Reply assistant ----------------

class ReplyViewModel(val app: MannAIApp) : BaseViewModel() {
    val original = MutableStateFlow("")
    val intent = MutableStateFlow("")
    val tone = MutableStateFlow(ReplyTone.Friendly)
    val draft = MutableStateFlow("")
    val error = MutableStateFlow<String?>(null)
    val generating = MutableStateFlow(false)

    // ---- WhatsApp notification-driven draft state ----
    val whatsAppSender = MutableStateFlow("")
    val whatsAppMessage = MutableStateFlow("")
    val whatsAppContextNote = MutableStateFlow("")
    val whatsAppContextFound = MutableStateFlow(false)
    val whatsAppIsGroup = MutableStateFlow(false)
    val whatsAppIsAmbiguous = MutableStateFlow(false)

    private var generateJob: Job? = null

    fun generate() {
        error.value = null
        val res = ReplyDrafter.draft(original.value, intent.value, tone.value)
        if (res.error != null) {
            error.value = res.error
            return
        }
        draft.value = res.draft.orEmpty()
    }

    /** Populate the reply screen from an allowlisted WhatsApp notification event. Does not start generation. */
    fun setNotificationEvent(event: WhatsAppMessageEvent) {
        cancelGeneration()
        error.value = null
        draft.value = ""
        whatsAppSender.value = event.sender
        whatsAppMessage.value = event.message
        whatsAppIsGroup.value = event.isGroup
        whatsAppContextFound.value = false
        whatsAppIsAmbiguous.value = false
        whatsAppContextNote.value = ""
        original.value = event.message
    }

    fun cancelGeneration() {
        generateJob?.cancel()
        generateJob = null
        if (!generating.value) return
        generating.value = false
    }

    /**
     * Generate a context-aware WhatsApp reply draft using the configured AI provider.
     * Requires an allowlisted sender and an online AI provider. Never sends a message.
     */
    fun generateWhatsAppDraft() {
        if (whatsAppMessage.value.isBlank()) {
            error.value = "No message to reply to."
            return
        }
        error.value = null
        generating.value = true
        generateJob = viewModelScope.launch {
            try {
                val event = WhatsAppMessageEvent(
                    sender = whatsAppSender.value,
                    message = whatsAppMessage.value,
                    timestampMs = System.currentTimeMillis(),
                    isGroup = whatsAppIsGroup.value,
                    packageName = WhatsAppNotificationParser.WHATSAPP_PACKAGE
                )
                val (contacts, messagesByContact) = app.whatsAppImport.loadContactsWithMessages()
                val generator = WhatsAppReplyGenerator()
                val provider = AiProviderFactory.create(app.settings)
                val result = generator.generateDraft(
                    event = event,
                    tone = tone.value,
                    contacts = contacts,
                    messagesByContact = messagesByContact,
                    provider = provider
                )
                whatsAppContextFound.value = result.context.hasContext
                whatsAppIsAmbiguous.value = result.context.isAmbiguous
                whatsAppContextNote.value = result.context.matchNote
                if (result.success) {
                    draft.value = result.draft.orEmpty()
                    original.value = event.message
                } else {
                    error.value = result.error
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = "Draft generation failed: ${e.message ?: "unknown error"}"
            } finally {
                generating.value = false
            }
        }
    }

    fun refineWithAi() {
        val current = draft.value
        if (current.isBlank()) {
            error.value = "Generate a draft first."
            return
        }
        val provider: AiProvider = if (app.settings.isAiConfigured) {
            AiProviderFactory.create(app.settings)
        } else {
            error.value = "Online AI isn't configured — the offline draft above is ready to use. Add an AI endpoint in Settings for AI refinement."
            return
        }
        generating.value = true
        viewModelScope.launch {
            try {
                val system = "Rewrite the user's draft message in a ${tone.value.label.lowercase()} tone. " +
                    "Return only the rewritten message, nothing else."
                val out = provider.chat(system, "Original received: ${original.value}\nDraft: $current")
                    .getOrElse { throw it }
                draft.value = out
            } catch (e: Exception) {
                error.value = "AI refinement failed: ${e.message}"
            } finally {
                generating.value = false
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
class VmFactory(private val app: MannAIApp) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(TasksViewModel::class.java) -> TasksViewModel(app)
        modelClass.isAssignableFrom(NotesViewModel::class.java) -> NotesViewModel(app)
        modelClass.isAssignableFrom(RemindersViewModel::class.java) -> RemindersViewModel(app)
        modelClass.isAssignableFrom(ChatViewModel::class.java) -> ChatViewModel(app)
        modelClass.isAssignableFrom(ReplyViewModel::class.java) -> ReplyViewModel(app)
        else -> throw IllegalArgumentException("Unknown VM $modelClass")
    } as T
}
