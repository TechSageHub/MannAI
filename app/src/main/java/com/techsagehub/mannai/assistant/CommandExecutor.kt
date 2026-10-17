package com.techsagehub.mannai.assistant

import android.content.Context
import com.techsagehub.mannai.data.ReminderRepository
import com.techsagehub.mannai.data.NoteRepository
import com.techsagehub.mannai.data.SettingsStore
import com.techsagehub.mannai.data.TaskRepository
import com.techsagehub.mannai.notify.ReminderScheduler
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Executes parsed [Command]s against real repositories. Never reports success
 * before the underlying operation completes. Offline-first.
 */
class CommandExecutor(
    private val context: Context,
    private val tasks: TaskRepository,
    private val notes: NoteRepository,
    private val reminders: ReminderRepository,
    private val settings: SettingsStore
) {
    private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM")
    private val dateTimeFmt = DateTimeFormatter.ofPattern("EEE, d MMM h:mm a")

    suspend fun execute(cmd: Command): CommandResult = when (cmd) {
        is Command.AddTask -> addTask(cmd)
        is Command.ListTasks -> listTasks(cmd)
        is Command.CompleteTask -> completeTask(cmd)
        is Command.DeleteTask -> deleteTask(cmd)
        is Command.AddNote -> addNote(cmd)
        is Command.ListNotes -> listNotes()
        is Command.SearchNotes -> searchNotes(cmd)
        is Command.AddReminder -> addReminder(cmd)
        is Command.ListReminders -> listReminders(cmd)
        is Command.CancelReminder -> cancelReminder(cmd)
        is Command.SetWake -> {
            settings.wakeEnabledValue = cmd.enabled
            if (cmd.enabled) CommandResult(
                "Background listening is enabled. Open MannAI once and tap " +
                    "\"Start listening\" so I can wait for \"${settings.wakePhrase}\".",
                Effect.RequestStartListening
            ) else CommandResult("Background listening is off.", Effect.RequestStopListening)
        }
        is Command.SetSpeech -> {
            settings.speakResponsesValue = cmd.enabled
            CommandResult(if (cmd.enabled) "Spoken responses are on." else "Spoken responses are off. I'll stay silent.")
        }
        is Command.StopListening -> {
            settings.wakeEnabledValue = false
            CommandResult("Listening stopped.", Effect.RequestStopListening)
        }
        is Command.Confirm -> CommandResult("__confirm__", Effect.None)
        is Command.CancelConfirm -> CommandResult("__cancel__", Effect.None)
        is Command.OpenReply -> CommandResult("Opening the Message Reply Assistant.", Effect.OpenReply)
        is Command.Help -> CommandResult(helpText())
        is Command.Time -> CommandResult(
            "It's ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("h:mm a"))}."
        )
        is Command.Date -> CommandResult(
            "Today is ${LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"))}."
        )
        is Command.TaskCount -> taskCount()
        is Command.Chat -> chat(cmd.text)
    }

    // ---------- tasks ----------

    private suspend fun addTask(cmd: Command.AddTask): CommandResult {
        if (cmd.title.isBlank()) return CommandResult("What should the task say? Please try again.")
        val dueMillis = cmd.dueAt?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        val id = tasks.add(cmd.title, dueAt = dueMillis, priority = cmd.priority)
        if (cmd.dueAt != null) {
            // Optional same-time nudge for tasks with a due time.
            ReminderScheduler.schedule(context, "task", id,
                dueMillis ?: 0L, "Task due", cmd.title)
        }
        val whenText = cmd.dueAt?.let { " (due ${it.format(dateTimeFmt)})" } ?: ""
        return CommandResult("Task saved: \"${cmd.title}\"$whenText.")
    }

    private suspend fun listTasks(cmd: Command.ListTasks): CommandResult {
        val all = snapshotTasks()
        val pending = all.filter { !it.done }
        val filtered = when (cmd.scope) {
            TaskScope.PENDING -> pending
            TaskScope.ALL -> all
            TaskScope.DONE -> all.filter { it.done }.takeLast(10)
            TaskScope.TODAY -> {
                val today = LocalDate.now()
                pending.filter { t ->
                    t.dueAt?.let {
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
                            .toLocalDate() <= today
                    } ?: false
                }
            }
        }
        if (filtered.isEmpty()) {
            return CommandResult(
                when (cmd.scope) {
                    TaskScope.TODAY -> "Nothing is due today. Enjoy the calm — or add a task."
                    TaskScope.DONE -> "No completed tasks yet."
                    else -> "No tasks here yet. Say \"add a task\" to create one."
                }
            )
        }
        val lines = filtered.take(10).mapIndexed { i, t ->
            val due = t.dueAt?.let {
                " — due " + LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
                    .format(dayFmt)
            } ?: ""
            val mark = if (t.done) "✓" else "${i + 1}."
            "$mark ${t.title}$due"
        }
        val scopeName = when (cmd.scope) {
            TaskScope.TODAY -> "due today"
            TaskScope.DONE -> "completed"
            TaskScope.ALL -> "in total"
            TaskScope.PENDING -> "pending"
        }
        return CommandResult(
            "You have ${filtered.size} $scopeName task${if (filtered.size == 1) "" else "s"}:\n" +
                lines.joinToString("\n")
        )
    }

    private suspend fun resolveTask(
        ref: TaskRef
    ): ResolveResult<com.techsagehub.mannai.data.db.TaskEntity> {
        val pending = snapshotTasks().filter { !it.done }
        return when (ref) {
            is TaskRef.Index -> {
                val t = pending.getOrNull(ref.oneBased - 1)
                if (t == null) ResolveResult.None("I couldn't find task ${ref.oneBased}. You have ${pending.size} pending.")
                else ResolveResult.One(t)
            }
            is TaskRef.Title -> {
                if (ref.text == "__completed__") return ResolveResult.Marker
                val hits = pending.filter { it.title.contains(ref.text, ignoreCase = true) }
                when {
                    hits.isEmpty() -> ResolveResult.None("I couldn't find a task matching \"$ref\".")
                    hits.size == 1 -> ResolveResult.One(hits[0])
                    else -> ResolveResult.Many(hits, "Which one? " + hits.take(5).mapIndexed { i, t ->
                        "${i + 1}. ${t.title}"
                    }.joinToString("; "))
                }
            }
        }
    }

    private suspend fun completeTask(cmd: Command.CompleteTask): CommandResult {
        return when (val r = resolveTask(cmd.ref)) {
            is ResolveResult.One -> {
                tasks.update(r.value.copy(done = true))
                ReminderScheduler.cancel(context, "task", r.value.id)
                CommandResult("Marked complete: \"${r.value.title}\". Well done.")
            }
            is ResolveResult.Many -> CommandResult(r.message)
            is ResolveResult.None -> CommandResult(r.message)
            is ResolveResult.Marker -> CommandResult("Please name the task to complete.")
        }
    }

    private suspend fun deleteTask(cmd: Command.DeleteTask): CommandResult {
        if (cmd.ref is TaskRef.Title && cmd.ref.text == "__completed__") {
            val done = snapshotTasks().count { it.done }
            if (done == 0) return CommandResult("There are no completed tasks to clear.")
            return CommandResult(
                "Delete all $done completed task${if (done == 1) "" else "s"}?",
                confirm = PendingConfirm("clear_completed", ConfirmAction.ClearCompleted)
            )
        }
        return when (val r = resolveTask(cmd.ref)) {
            is ResolveResult.One -> CommandResult(
                "Delete \"${r.value.title}\"?",
                confirm = PendingConfirm("delete_task", ConfirmAction.DeleteTask(r.value.id))
            )
            is ResolveResult.Many -> CommandResult(r.message)
            is ResolveResult.None -> CommandResult(r.message)
            is ResolveResult.Marker -> CommandResult("Please name the task to delete.")
        }
    }

    /** Executes a previously prompted confirmation. */
    suspend fun confirm(action: ConfirmAction): CommandResult = when (action) {
        is ConfirmAction.DeleteTask -> {
            val t = tasks.get(action.id)
            if (t == null) CommandResult("That task is already gone.")
            else {
                tasks.delete(t)
                ReminderScheduler.cancel(context, "task", t.id)
                CommandResult("Deleted \"${t.title}\".")
            }
        }
        is ConfirmAction.ClearCompleted -> {
            var n = 0
            snapshotTasks().filter { it.done }.forEach {
                tasks.delete(it)
                ReminderScheduler.cancel(context, "task", it.id)
                n++
            }
            CommandResult("Cleared $n completed task${if (n == 1) "" else "s"}.")
        }
        is ConfirmAction.DeleteReminder -> {
            val r = reminders.get(action.id)
            if (r == null) CommandResult("That reminder is already gone.")
            else {
                reminders.delete(r)
                ReminderScheduler.cancel(context, "reminder", r.id)
                CommandResult("Cancelled \"${r.title}\".")
            }
        }
    }

    // ---------- notes ----------

    private suspend fun addNote(cmd: Command.AddNote): CommandResult {
        val title = cmd.title.ifBlank { cmd.content.take(50) }
        val content = if (cmd.title.isBlank()) cmd.content else cmd.content
        if (content.isBlank() && title.isBlank()) return CommandResult("I didn't catch the note. Please try again.")
        notes.add(title, content.ifBlank { title })
        return CommandResult("Note saved${if (title.isNotBlank()) ": \"$title\"" else ""}.")
    }

    private suspend fun listNotes(): CommandResult {
        val all = snapshotNotes()
        if (all.isEmpty()) return CommandResult("No notes yet. Say \"create a note\" to start one.")
        val lines = all.take(5).map { "• ${it.title.ifBlank { it.content.take(40) }}" }
        return CommandResult("Your ${all.size} most recent note${if (all.size == 1) "" else "s"}:\n" + lines.joinToString("\n"))
    }

    private suspend fun searchNotes(cmd: Command.SearchNotes): CommandResult {
        if (cmd.query.isBlank()) return CommandResult("What should I search your notes for?")
        val hits = notes.search(cmd.query).first()
        if (hits.isEmpty()) return CommandResult("No notes mention \"${cmd.query}\".")
        return CommandResult("Found ${hits.size} note${if (hits.size == 1) "" else "s"}:\n" +
            hits.take(5).joinToString("\n") { "• ${it.title.ifBlank { it.content.take(60) }}" })
    }

    // ---------- reminders ----------

    private suspend fun addReminder(cmd: Command.AddReminder): CommandResult {
        val millis = cmd.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (millis <= System.currentTimeMillis()) {
            return CommandResult("That time is in the past. Please pick a future time.")
        }
        return try {
            val id = reminders.add(cmd.title, "", millis)
            val ok = ReminderScheduler.schedule(context, "reminder", id, millis,
                cmd.title, "Due ${cmd.at.format(dateTimeFmt)}")
            if (ok) CommandResult("Reminder set: \"${cmd.title}\" — ${cmd.at.format(dateTimeFmt)}.")
            else CommandResult("I saved \"${cmd.title}\" but couldn't schedule its alert.", Effect.None)
        } catch (e: IllegalArgumentException) {
            CommandResult(e.message ?: "Couldn't set that reminder.")
        }
    }

    private suspend fun listReminders(cmd: Command.ListReminders): CommandResult {
        val all = snapshotReminders().filter { !it.fired }
        val filtered = if (cmd.todayOnly) {
            val today = LocalDate.now()
            all.filter {
                LocalDateTime.ofInstant(Instant.ofEpochMilli(it.triggerAt), ZoneId.systemDefault())
                    .toLocalDate() == today
            }
        } else all
        if (filtered.isEmpty()) {
            return CommandResult(if (cmd.todayOnly) "No reminders for today." else "No upcoming reminders.")
        }
        val lines = filtered.take(10).mapIndexed { i, r ->
            "${i + 1}. ${r.title} — " + LocalDateTime.ofInstant(
                Instant.ofEpochMilli(r.triggerAt), ZoneId.systemDefault()).format(dateTimeFmt)
        }
        return CommandResult("Upcoming reminders:\n" + lines.joinToString("\n"))
    }

    private suspend fun cancelReminder(cmd: Command.CancelReminder): CommandResult {
        val upcoming = snapshotReminders().filter { !it.fired }
        when (val ref = cmd.ref) {
            is ReminderRef.Index -> {
                val r = upcoming.getOrNull(ref.oneBased - 1)
                    ?: return CommandResult("I couldn't find reminder ${ref.oneBased}.")
                return CommandResult(
                    "Cancel \"${r.title}\"?",
                    confirm = PendingConfirm("delete_reminder", ConfirmAction.DeleteReminder(r.id))
                )
            }
            is ReminderRef.Title -> {
                if (ref.text.startsWith("__time__:")) {
                    val target = runCatching {
                        LocalDateTime.parse(ref.text.removePrefix("__time__:"))
                    }.getOrNull() ?: return CommandResult("Which reminder should I cancel?")
                    val hit = upcoming.minByOrNull {
                        kotlin.math.abs(
                            LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(it.triggerAt), ZoneId.systemDefault()
                            ).until(target, java.time.temporal.ChronoUnit.MINUTES)
                        )
                    }
                    if (hit == null) return CommandResult("No upcoming reminders to cancel.")
                    return CommandResult(
                        "Cancel \"${hit.title}\"?",
                        confirm = PendingConfirm("delete_reminder", ConfirmAction.DeleteReminder(hit.id))
                    )
                }
                val hits = upcoming.filter { it.title.contains(ref.text, ignoreCase = true) }
                return when {
                    hits.isEmpty() -> CommandResult("I couldn't find a reminder matching that.")
                    hits.size == 1 -> CommandResult(
                        "Cancel \"${hits[0].title}\"?",
                        confirm = PendingConfirm("delete_reminder", ConfirmAction.DeleteReminder(hits[0].id))
                    )
                    else -> CommandResult("Which one? " + hits.take(5).mapIndexed { i, r ->
                        "${i + 1}. ${r.title}"
                    }.joinToString("; "))
                }
            }
        }
    }

    // ---------- misc ----------

    private suspend fun taskCount(): CommandResult {
        val n = tasks.countPending()
        return CommandResult(
            if (n == 0) "You have no pending tasks. All clear."
            else "You have $n pending task${if (n == 1) "" else "s"}."
        )
    }

    private fun chat(text: String): CommandResult {
        val lower = text.lowercase()
        val name = settings.assistantNameValue
        if (lower.isBlank()) return CommandResult("I didn't catch that. Please try again.")
        if (lower.contains("assalam")) return CommandResult("Wa Alaikum Assalam! How can I help you today?")
        if (lower.matches(Regex(".*\\b(hello|hi|hey|salam)\\b.*"))) {
            return CommandResult("Hello! I can manage tasks, notes and reminders, or help draft a message. What do you need?")
        }
        if (lower.contains("who are you") || lower.contains("your name")) {
            return CommandResult("I'm $name, your personal assistant. I work offline on your phone and keep everything private.")
        }
        if (lower.contains("thank")) return CommandResult("You're welcome!")
        if (lower.contains("how are you")) return CommandResult("Running smoothly and ready to help. What can I do for you?")
        if (settings.isAiConfigured) {
            // Route to the optional online provider via the ViewModel.
            return CommandResult("__ai__:$text", Effect.AskAi(text))
        }
        return CommandResult(
            "I work best with direct requests. Try:\n" +
                "• \"Add a task to call Ahmed\"\n" +
                "• \"Remind me tomorrow at 8 AM to study\"\n" +
                "• \"Create a note about today's meeting\"\n" +
                "• \"What do I need to do today?\"\n" +
                "Say \"help\" any time for examples."
        )
    }

    private fun helpText(): String = """
        Here's what I can do — all offline, on your phone:
        • Tasks: "Add a task to finish my project", "Show my pending tasks", "Mark my first task as complete"
        • Notes: "Create a note about today's meeting", "Search my notes for project"
        • Reminders: "Remind me tomorrow at 8 AM to prepare for class", "What reminders do I have today?"
        • Replies: "Help me reply" opens the Message Reply Assistant
        • Voice: "Stop listening", "Turn off spoken responses"
    """.trimIndent()

    // ---------- snapshots (one-shot reads of Flows) ----------

    private suspend fun snapshotTasks() = tasks.observeAll().first()

    private suspend fun snapshotNotes() = notes.observeAll().first()

    private suspend fun snapshotReminders() = reminders.observeAll().first()

    // ---------- result types ----------

    sealed interface ResolveResult<out T> {
        data class One<T>(val value: T) : ResolveResult<T>
        data class Many<T>(val values: List<T>, val message: String) : ResolveResult<T>
        data class None(val message: String) : ResolveResult<Nothing>
        data object Marker : ResolveResult<Nothing>
    }
}

data class CommandResult(
    val text: String,
    val effect: Effect = Effect.None,
    val confirm: PendingConfirm? = null
)

sealed interface Effect {
    data object None : Effect
    data object RequestStartListening : Effect
    data object RequestStopListening : Effect
    data object OpenReply : Effect
    data class AskAi(val prompt: String) : Effect
}

data class PendingConfirm(val id: String, val action: ConfirmAction)

sealed interface ConfirmAction {
    data class DeleteTask(val id: Long) : ConfirmAction
    data object ClearCompleted : ConfirmAction
    data class DeleteReminder(val id: Long) : ConfirmAction
}
