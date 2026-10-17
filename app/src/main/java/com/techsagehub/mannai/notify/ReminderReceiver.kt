package com.techsagehub.mannai.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.techsagehub.mannai.MainActivity
import com.techsagehub.mannai.data.db.MannAIDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fires when an AlarmManager reminder goes off: marks the row fired so it can
 * never double-fire, then posts the notification. Runs off the main thread.
 */
class ReminderReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_FIRED) return
        val pending = goAsync()
        scope.launch {
            try {
                val kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND) ?: "reminder"
                val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1L)
                val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "MannAI reminder"
                val text = intent.getStringExtra(ReminderScheduler.EXTRA_TEXT) ?: ""
                val db = MannAIDatabase.get(context)
                when (kind) {
                    "task" -> {
                        val task = db.taskDao().getById(id)
                        if (task != null && !task.done) {
                            db.taskDao().update(task.copy(reminderFired = true, reminderAt = null))
                            MannAINotifier.showReminder(
                                context,
                                ReminderScheduler.codeFor(kind, id),
                                title, text.ifEmpty { task.title },
                                MainActivity.SCREEN_TASKS
                            )
                        }
                    }
                    else -> {
                        val reminder = db.reminderDao().getById(id)
                        if (reminder != null && reminder.enabled && !reminder.fired) {
                            db.reminderDao().markFiredSafe(reminder)
                            MannAINotifier.showReminder(
                                context,
                                ReminderScheduler.codeFor(kind, id),
                                title, text.ifEmpty { reminder.description.ifEmpty { reminder.title } },
                                MainActivity.SCREEN_REMINDERS
                            )
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

private suspend fun com.techsagehub.mannai.data.db.ReminderDao.markFiredSafe(
    r: com.techsagehub.mannai.data.db.ReminderEntity
) = update(r.copy(fired = true))
