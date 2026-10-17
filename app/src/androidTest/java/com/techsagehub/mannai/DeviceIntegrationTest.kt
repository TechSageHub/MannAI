package com.techsagehub.mannai

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.techsagehub.mannai.data.db.MannAIDatabase
import com.techsagehub.mannai.data.db.ReminderEntity
import com.techsagehub.mannai.data.db.TaskEntity
import com.techsagehub.mannai.data.db.NoteEntity
import com.techsagehub.mannai.notify.ReminderReceiver
import com.techsagehub.mannai.notify.ReminderScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device integration tests (run on the Infinix via connectedAndroidTest):
 * real Room database + real ReminderReceiver -> real notification.
 */
@RunWith(AndroidJUnit4::class)
class DeviceIntegrationTest {
    private fun target(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    /** Shell-level runtime grants so reinstalls can't silently break these tests. */
    private fun grant(vararg perms: String) {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        perms.forEach { p ->
            ui.executeShellCommand("pm grant ${target().packageName} $p").close()
        }
        Thread.sleep(500)
    }

    @Test
    fun database_crud_roundtrip() = runBlocking {
        val db = MannAIDatabase.get(target())
        val taskId = db.taskDao().insert(TaskEntity(title = "Device test task"))
        assertTrue(taskId > 0)
        assertEquals("Device test task", db.taskDao().getById(taskId)?.title)
        db.taskDao().update(db.taskDao().getById(taskId)!!.copy(done = true))
        assertEquals(true, db.taskDao().getById(taskId)?.done)
        db.taskDao().delete(db.taskDao().getById(taskId)!!)

        val noteId = db.noteDao().insert(
            NoteEntity(title = "T", content = "C",
                createdAt = 1L, updatedAt = 1L))
        assertTrue(noteId > 0)
        db.noteDao().delete(db.noteDao().getById(noteId)!!)

        val remId = db.reminderDao().insert(
            ReminderEntity(title = "T", triggerAt = System.currentTimeMillis() + 3_600_000))
        assertTrue(remId > 0)
        db.reminderDao().delete(db.reminderDao().getById(remId)!!)

        val chatId = db.chatDao().insert(
            com.techsagehub.mannai.data.db.ChatMessageEntity(role = "user", text = "hi"))
        assertTrue(chatId > 0)
        db.chatDao().clearAll()
    }

    @Test
    fun scheduled_alarm_fires_notification() {
        grant("android.permission.POST_NOTIFICATIONS")
        val ctx = target()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val title = "MannAI alarm test " + System.currentTimeMillis()
        val trigger = System.currentTimeMillis() + 45_000
        val id = runBlocking {
            MannAIDatabase.get(ctx).reminderDao().insert(
                ReminderEntity(title = title, description = "alarm probe", triggerAt = trigger))
        }
        try {
            assertTrue(ReminderScheduler.schedule(ctx, "reminder", id, trigger, title, "alarm probe"))
            var found = false
            val deadline = System.currentTimeMillis() + 90_000
            while (System.currentTimeMillis() < deadline && !found) {
                Thread.sleep(1000)
                found = nm.activeNotifications.any { sbn ->
                    sbn.notification.extras.getString("android.title") == title
                }
            }
            assertTrue("Expected the scheduled alarm to fire with [$title]", found)
            val row = runBlocking { MannAIDatabase.get(ctx).reminderDao().getById(id) }
            assertNotNull(row)
            assertEquals(true, row!!.fired)
        } finally {
            ReminderScheduler.cancel(ctx, "reminder", id)
            runBlocking {
                MannAIDatabase.get(ctx).reminderDao().getById(id)?.let {
                    MannAIDatabase.get(ctx).reminderDao().delete(it)
                }
            }
            nm.cancel(ReminderScheduler.codeFor("reminder", id))
        }
    }

    @Test
    fun reminder_broadcast_posts_notification_and_marks_fired() {
        grant("android.permission.POST_NOTIFICATIONS")
        val ctx = target()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val title = "MannAI device test " + System.currentTimeMillis()
        val id = runBlocking {
            val db = MannAIDatabase.get(ctx)
            db.reminderDao().insert(
                ReminderEntity(title = title, description = "integration probe",
                    triggerAt = System.currentTimeMillis() + 3_600_000))
        }
        try {
            val intent = Intent(ctx, ReminderReceiver::class.java).apply {
                action = ReminderScheduler.ACTION_FIRED
                putExtra(ReminderScheduler.EXTRA_KIND, "reminder")
                putExtra(ReminderScheduler.EXTRA_ID, id)
                putExtra(ReminderScheduler.EXTRA_TITLE, title)
                putExtra(ReminderScheduler.EXTRA_TEXT, "integration probe")
            }
            ctx.sendBroadcast(intent)

            var found = false
            val deadline = System.currentTimeMillis() + 15_000
            while (System.currentTimeMillis() < deadline && !found) {
                Thread.sleep(500)
                found = nm.activeNotifications.any { sbn ->
                    sbn.notification.extras.getString("android.title") == title ||
                        sbn.notification.extras.getString("android.text")?.contains("integration probe") == true
                }
            }
            assertTrue("Expected a notification titled [$title]", found)

            val row = runBlocking { MannAIDatabase.get(ctx).reminderDao().getById(id) }
            assertNotNull(row)
            assertEquals(true, row!!.fired)
        } finally {
            runBlocking {
                MannAIDatabase.get(ctx).reminderDao().getById(id)?.let {
                    MannAIDatabase.get(ctx).reminderDao().delete(it)
                }
            }
            nm.cancel(ReminderScheduler.codeFor("reminder", id))
        }
    }
}
