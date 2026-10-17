package com.techsagehub.mannai

import com.techsagehub.mannai.assistant.Command
import com.techsagehub.mannai.assistant.CommandParser
import com.techsagehub.mannai.assistant.ReminderRef
import com.techsagehub.mannai.assistant.TaskRef
import com.techsagehub.mannai.assistant.TaskScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CommandParserTest {
    private val now = LocalDateTime.of(2026, 10, 16, 10, 0)

    @Test fun addTaskBasic() {
        val c = CommandParser.parse("Add a task to finish my project", now)
        assertTrue(c is Command.AddTask)
        assertEquals("Finish my project", (c as Command.AddTask).title)
    }

    @Test fun addTaskWithDueDate() {
        val c = CommandParser.parse("Add a task to finish my project tomorrow at 5 pm", now)
        assertTrue(c is Command.AddTask)
        val t = c as Command.AddTask
        assertEquals("Finish my project", t.title)
        assertEquals(LocalDateTime.of(2026, 10, 17, 17, 0), t.dueAt)
    }

    @Test fun remindMeWithTimeIsReminder() {
        val c = CommandParser.parse("Remind me tomorrow at 8 AM to prepare for class", now)
        assertTrue(c is Command.AddReminder)
        val r = c as Command.AddReminder
        assertEquals("Prepare for class", r.title)
        assertEquals(LocalDateTime.of(2026, 10, 17, 8, 0), r.at)
    }

    @Test fun remindMeWithoutTimeBecomesTask() {
        val c = CommandParser.parse("Remind me to call someone", now)
        assertTrue(c is Command.AddTask)
        assertEquals("Call someone", (c as Command.AddTask).title)
    }

    @Test fun listToday() {
        assertEquals(Command.ListTasks(TaskScope.TODAY),
            CommandParser.parse("What do I need to do today?", now))
    }

    @Test fun listPending() {
        assertEquals(Command.ListTasks(TaskScope.PENDING),
            CommandParser.parse("Show my pending tasks", now))
    }

    @Test fun completeByOrdinal() {
        val c = CommandParser.parse("Mark my first task as complete", now)
        assertEquals(Command.CompleteTask(TaskRef.Index(1)), c)
    }

    @Test fun completeByNumber() {
        val c = CommandParser.parse("Complete task 2", now)
        assertEquals(Command.CompleteTask(TaskRef.Index(2)), c)
    }

    @Test fun deleteByTitle() {
        val c = CommandParser.parse("Delete the task I just created", now)
        assertTrue(c is Command.DeleteTask)
    }

    @Test fun createNote() {
        val c = CommandParser.parse("Create a note about today's meeting", now)
        assertTrue(c is Command.AddNote)
        assertTrue((c as Command.AddNote).content.isNotBlank())
    }

    @Test fun searchNotes() {
        val c = CommandParser.parse("Search my notes for project details", now)
        assertEquals(Command.SearchNotes("project details"), c)
    }

    @Test fun listRemindersToday() {
        assertEquals(Command.ListReminders(true),
            CommandParser.parse("What reminders do I have today?", now))
    }

    @Test fun cancelReminderByTime() {
        val c = CommandParser.parse("Cancel my 5 PM reminder", now)
        assertTrue(c is Command.CancelReminder)
        val ref = (c as Command.CancelReminder).ref
        assertTrue(ref is ReminderRef.Title)
    }

    @Test fun stopListening() {
        assertEquals(Command.StopListening, CommandParser.parse("Stop listening", now))
    }

    @Test fun disableSpeech() {
        assertEquals(Command.SetSpeech(false),
            CommandParser.parse("Turn off spoken responses", now))
    }

    @Test fun confirmations() {
        assertEquals(Command.Confirm, CommandParser.parse("yes", now))
        assertEquals(Command.CancelConfirm, CommandParser.parse("no", now))
    }

    @Test fun invalidFallsBackToChat() {
        val c = CommandParser.parse("blorple the fnord", now)
        assertTrue(c is Command.Chat)
    }

    @Test fun openReply() {
        assertEquals(Command.OpenReply, CommandParser.parse("Help me reply to this message", now))
    }

    @Test fun ambiguousDeleteStaysSpecific() {
        // "delete all completed tasks" maps to a confirmation flow, not silent wipe.
        val c = CommandParser.parse("Delete all completed tasks", now)
        assertEquals(Command.DeleteTask(TaskRef.Title("__completed__")), c)
    }

    @Test fun showCompletedListsDone() {
        assertEquals(Command.ListTasks(TaskScope.DONE),
            CommandParser.parse("Show my completed tasks", now))
    }

    @Test fun remindWithDateOnlySetsMorningReminder() {
        val c = CommandParser.parse("Remind me to stretch tomorrow", now)
        assertTrue(c is Command.AddReminder)
        assertEquals(LocalDateTime.of(2026, 10, 17, 9, 0), (c as Command.AddReminder).at)
        assertEquals("Stretch", c.title)
    }
}
