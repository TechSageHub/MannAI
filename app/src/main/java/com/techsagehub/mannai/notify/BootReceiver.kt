package com.techsagehub.mannai.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.techsagehub.mannai.data.db.MannAIDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * After reboot, app update, or clock/timezone change: re-schedule every
 * enabled, unfired reminder (and task reminder) whose time is still in the
 * future. Overdue ones are marked fired so they never pile up. This receiver
 * never starts microphone capture — autostarting the mic is not permitted.
 */
class BootReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                rescheduleAll(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        suspend fun rescheduleAll(context: Context) {
            val db = MannAIDatabase.get(context)
            val now = System.currentTimeMillis()
            db.reminderDao().pendingScheduled().forEach { r ->
                if (r.triggerAt > now) {
                    ReminderScheduler.schedule(
                        context, "reminder", r.id, r.triggerAt, r.title,
                        r.description.ifEmpty { r.title }
                    )
                } else {
                    db.reminderDao().update(r.copy(fired = true))
                }
            }
            db.taskDao().pendingTaskReminders().forEach { t ->
                val at = t.reminderAt
                if (at != null && at > now) {
                    ReminderScheduler.schedule(
                        context, "task", t.id, at, "Task reminder", t.title
                    )
                } else {
                    db.taskDao().update(t.copy(reminderFired = true, reminderAt = null))
                }
            }
        }
    }
}
