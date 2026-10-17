package com.techsagehub.mannai.assistant

import java.time.LocalDateTime

/**
 * Deterministic command interpretation. Maps recognized/typed text to an
 * explicit [Command]. No network, no AI service needed. Pure + unit-tested.
 */
sealed interface Command {
    data class AddTask(val title: String, val dueAt: LocalDateTime?, val priority: Int) : Command
    data class ListTasks(val scope: TaskScope) : Command
    data class CompleteTask(val ref: TaskRef) : Command
    data class DeleteTask(val ref: TaskRef) : Command
    data class AddNote(val title: String, val content: String) : Command
    object ListNotes : Command
    data class SearchNotes(val query: String) : Command
    data class AddReminder(val title: String, val at: LocalDateTime) : Command
    data class ListReminders(val todayOnly: Boolean) : Command
    data class CancelReminder(val ref: ReminderRef) : Command
    data class SetWake(val enabled: Boolean) : Command
    data class SetSpeech(val enabled: Boolean) : Command
    object StopListening : Command
    object Confirm : Command
    object CancelConfirm : Command
    object OpenReply : Command
    object Help : Command
    object Time : Command
    object Date : Command
    object TaskCount : Command
    data class Chat(val text: String) : Command
}

enum class TaskScope { TODAY, PENDING, DONE, ALL }

sealed interface TaskRef {
    data class Index(val oneBased: Int) : TaskRef
    data class Title(val text: String) : TaskRef
}

sealed interface ReminderRef {
    data class Index(val oneBased: Int) : ReminderRef
    data class Title(val text: String) : ReminderRef
}

object CommandParser {
    private val ORDINALS = mapOf(
        "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5,
        "sixth" to 6, "seventh" to 7, "eighth" to 8, "ninth" to 9, "tenth" to 10,
        "1st" to 1, "2nd" to 2, "3rd" to 3
    )

    fun parse(raw: String, now: LocalDateTime = LocalDateTime.now()): Command {
        val text = raw.trim()
        val lower = text.lowercase()
        if (text.isBlank()) return Command.Chat("")

        // --- confirmations (checked first so "yes" after a prompt resolves it) ---
        if (lower in setOf("yes", "yeah", "yep", "yes please", "confirm", "do it", "sure")) return Command.Confirm
        if (lower in setOf("no", "nope", "cancel", "never mind", "nevermind", "don't")) return Command.CancelConfirm

        // --- assistant settings ---
        if (containsAny(lower, "stop listening", "disable background listening", "turn off listening",
                "disable wake", "stop background listening")) return Command.StopListening
        if (containsAny(lower, "start listening", "enable background listening", "turn on listening",
                "enable wake", "start background listening")) return Command.SetWake(true)
        if (containsAny(lower, "disable background listening").not() &&
            containsAny(lower, "disable wake word", "turn off wake")
        ) return Command.SetWake(false)
        if (containsAny(lower, "turn off spoken responses", "disable spoken responses",
                "mute yourself", "stop speaking", "be quiet")) return Command.SetSpeech(false)
        if (containsAny(lower, "turn on spoken responses", "enable spoken responses",
                "speak your responses", "unmute")) return Command.SetSpeech(true)

        // --- small talk / local info ---
        if (containsAny(lower, "what time is it", "tell me the time", "current time")) return Command.Time
        if (containsAny(lower, "what day is it", "what is the date", "what's the date",
                "todays date", "today's date")) return Command.Date
        if (containsAny(lower, "how many tasks", "task count", "tasks do i have")) return Command.TaskCount
        if (lower == "help" || containsAny(lower, "what can you do", "show commands",
                "list commands", "how do i use you")) return Command.Help
        if (containsAny(lower, "help me reply", "help me respond", "message assistant",
                "draft a reply", "write a reply", "open message assistant")) return Command.OpenReply

        // --- reminders ---
        if (lower.startsWith("remind me") || lower.startsWith("set a reminder") ||
            lower.startsWith("add a reminder") || lower.startsWith("set reminder") ||
            lower.startsWith("add reminder") || containsAny(lower, "remind me to", "remind me that")
        ) {
            val whenFound = DateTimeParser.find(text, now)
            val title = extractAfter(text, listOf(
                "remind me to", "remind me that", "remind me",
                "set a reminder to", "set a reminder", "add a reminder to", "add a reminder",
                "set reminder to", "set reminder"
            ), whenFound?.matchedText, whenFound?.parts ?: emptyList())
            return if (whenFound != null && title.isNotBlank()) {
                Command.AddReminder(title, whenFound.dateTime)
            } else if (title.isNotBlank() && !lower.startsWith("remind me to").not() &&
                whenFound == null
            ) {
                // "remind me to X" with no time -> keep as a plain task instead of a wrong alarm.
                Command.AddTask(title, null, 0)
            } else if (whenFound != null) {
                Command.AddReminder("Reminder", whenFound.dateTime)
            } else {
                Command.Chat(text)
            }
        }
        if (containsAny(lower, "cancel my", "delete my", "remove my", "cancel the", "delete the")
            && lower.contains("reminder")
        ) {
            return Command.CancelReminder(parseReminderRef(lower))
        }
        if ((lower.contains("reminder") || lower.contains("reminders")) &&
            containsAny(lower, "what", "show", "list", "do i have", "any", "today", "tomorrow")
        ) {
            return Command.ListReminders(todayOnly = lower.contains("today"))
        }

        // --- tasks ---
        if (startsWithAny(lower, "add a task", "add task", "create a task", "create task",
                "new task", "add a todo", "add todo")
        ) {
            val whenFound = DateTimeParser.find(text, now)
            val title = extractAfter(text, listOf(
                "add a task to", "add a task", "add task to", "add task",
                "create a task to", "create a task", "create task",
                "new task to", "new task", "add a todo", "add todo"
            ), whenFound?.matchedText, whenFound?.parts ?: emptyList())
            val priority = if (containsAny(lower, "urgent", "asap", "important")) 2
                else if (containsAny(lower, "high priority")) 1 else 0
            if (title.isNotBlank()) return Command.AddTask(title, whenFound?.dateTime, priority)
            return Command.Chat(text)
        }
        if ((lower.contains("task") || lower.contains("todo") || lower.contains("to-do") ||
                containsAny(lower, "what do i need to do", "what do i have to do"))
        ) {
            val isListingQuery = startsWithAny(lower, "show", "list", "what", "view",
                "display", "which", "any")
            // "delete all completed tasks" needs confirmation; executor will ask.
            if (!isListingQuery && containsAny(lower, "all completed", "completed tasks", "done tasks") &&
                containsAny(lower, "delete", "remove", "clear")
            ) {
                return Command.DeleteTask(TaskRef.Title("__completed__"))
            }
            if (!isListingQuery && containsAny(lower, "mark", "complete", "finish", "done", "check off", "tick off")) {
                if (containsAny(lower, "delete", "remove")) {
                    return Command.DeleteTask(parseTaskRef(lower))
                }
                return Command.CompleteTask(parseTaskRef(lower))
            }
            if (!isListingQuery && containsAny(lower, "delete", "remove", "clear completed")
            ) {
                return Command.DeleteTask(parseTaskRef(lower))
            }
            if (containsAny(lower, "what do i need to do today", "due today", "for today", "today")) {
                return Command.ListTasks(TaskScope.TODAY)
            }
            if (containsAny(lower, "completed", "finished", "done")) {
                return Command.ListTasks(TaskScope.DONE)
            }
            if (containsAny(lower, "all")) return Command.ListTasks(TaskScope.ALL)
            if (containsAny(lower, "show", "list", "pending", "need to do", "have to do",
                    "my tasks", "my todos", "to-do", "todo")
            ) return Command.ListTasks(TaskScope.PENDING)
        }

        // --- notes ---
        if (startsWithAny(lower, "create a note", "create note", "add a note", "add note",
                "take a note", "save a note", "save this as a note", "note:")
            || containsAny(lower, "save this as a note", "create a note about", "write a note")
        ) {
            val content = extractAfter(text, listOf(
                "save this as a note", "create a note about", "create a note",
                "add a note about", "add a note", "take a note about", "take a note",
                "save a note", "write a note about", "write a note", "note:"
            ), null)
            if (content.isNotBlank()) {
                val split = content.split(Regex("\\s+[—–-]\\s+|:\\s+"), limit = 2)
                return if (split.size == 2 && split[0].length <= 60) {
                    Command.AddNote(split[0].trim(), split[1].trim())
                } else Command.AddNote("", content)
            }
            return Command.Chat(text)
        }
        if (lower.startsWith("search my notes for") || lower.startsWith("search notes for")) {
            val q = extractAfter(text, listOf("search my notes for", "search notes for"), null,
                emptyList(), capitalize = false)
            return Command.SearchNotes(q.ifBlank { "" })
        }
        if ((lower.contains("note") || lower.contains("notes")) &&
            containsAny(lower, "show", "list", "recent", "read", "my notes")
        ) return Command.ListNotes

        return Command.Chat(text)
    }

    // ---------- helpers ----------

    private fun containsAny(haystack: String, vararg needles: String): Boolean =
        needles.any { it in haystack }

    private fun startsWithAny(haystack: String, vararg needles: String): Boolean =
        needles.any { haystack.startsWith(it) }

    internal fun extractAfter(
        text: String,
        prefixes: List<String>,
        dateText: String?,
        parts: List<String> = emptyList(),
        capitalize: Boolean = true
    ): String {
        var rest = text
        val lower = text.lowercase()
        outer@ for (p in prefixes.sortedByDescending { it.length }) {
            // Match at the start (with a word boundary so "remind me to" does
            // not match "remind me tomorrow").
            if (lower.startsWith(p) && boundaryAfter(lower, p.length)) {
                rest = text.substring(p.length)
                break
            }
            var from = 0
            while (true) {
                val idx = lower.indexOf(p, from)
                if (idx < 0) break
                if (boundaryBefore(lower, idx) && boundaryAfter(lower, idx + p.length)) {
                    rest = text.substring(idx + p.length)
                    break@outer
                }
                from = idx + 1
            }
        }
        var out = rest.trim()
        // Remove each matched date/time fragment separately ("tomorrow",
        // "8 am") since they rarely sit next to each other ("tomorrow at 8 am").
        val fragments = if (parts.isNotEmpty()) parts else listOfNotNull(dateText)
        for (frag in fragments.sortedByDescending { it.length }) {
            if (frag.isBlank()) continue
            out = out.replace(Regex("(?i)\\s*" + Regex.escape(frag) + "\\s*"), " ")
        }
        out = out.trim().replace(Regex("\\s+"), " ")
        // Strip connectors left dangling by the removal ("at", leading "to").
        var prev: String
        do {
            prev = out
            out = out.replace(Regex("(?i)^(at|on|by|for|to|from)\\b\\s*"), "").trim()
        } while (out != prev)
        out = out.removePrefix(",").trim()
        out = out.trimEnd('.', '!', '?', ',', ';', ' ').trim()
        // dangling connectors left after date removal
        out = out.replace(Regex("(?i)\\s+(at|on|by|for|tomorrow|today)$"), "").trim()
        if (!capitalize) return out
        // Capitalize first letter for display.
        return out.replaceFirstChar { c -> c.uppercase() }
    }

    private fun boundaryBefore(s: String, idx: Int): Boolean =
        idx <= 0 || !s[idx - 1].isLetterOrDigit()

    private fun boundaryAfter(s: String, idx: Int): Boolean =
        idx >= s.length || !s[idx].isLetterOrDigit()

    private fun parseTaskRef(lower: String): TaskRef {
        for ((word, n) in ORDINALS) {
            if (word in lower) return TaskRef.Index(n)
        }
        Regex("""task\s+(\d+)""").find(lower)?.let {
            return TaskRef.Index(it.groupValues[1].toInt().coerceAtLeast(1))
        }
        Regex("""(?<!\d)(\d+)(?:st|nd|rd|th)?\s+(task|one)""").find(lower)?.let {
            return TaskRef.Index(it.groupValues[1].toInt().coerceAtLeast(1))
        }
        var title = lower
            .replace(Regex("^(please\\s+)?(mark|complete|finish|delete|remove|cancel)\\s+"), "")
            .replace(Regex("^(my|the|this|that)\\s+"), "")
            .replace(Regex("^(first|1st)\\s+task\\s*"), "")
            .replace(Regex("\\btasks?\\b"), " ")
            .replace(Regex("\\s+(as\\s+complete(d)?|done|off)$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (title.isEmpty()) title = lower
        // "my first task" without explicit ordinal map above
        if (title.contains("first")) return TaskRef.Index(1)
        return TaskRef.Title(title)
    }

    private fun parseReminderRef(lower: String): ReminderRef {
        for ((word, n) in ORDINALS) {
            if (word in lower) return ReminderRef.Index(n)
        }
        Regex("""reminder\s+(\d+)""").find(lower)?.let {
            return ReminderRef.Index(it.groupValues[1].toInt().coerceAtLeast(1))
        }
        val m = DateTimeParser.find(lower)
        if (m != null) return ReminderRef.Title("__time__:${m.dateTime}")
        var title = lower
            .replace(Regex("^(please\\s+)?(cancel|delete|remove)\\s+"), "")
            .replace(Regex("^(my|the|this|that)\\s+"), "")
            .replace(Regex("\\s*reminders?\\s*"), " ")
            .trim()
        return ReminderRef.Title(title.ifEmpty { lower })
    }
}
