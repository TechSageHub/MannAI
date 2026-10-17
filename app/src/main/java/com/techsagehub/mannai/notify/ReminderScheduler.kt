package com.techsagehub.mannai.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Schedules reminders with [AlarmManager.setAndAllowWhileIdle] — no exact-alarm
 * permission required, works in Doze, survives the app being closed.
 * Each reminder gets exactly one PendingIntent keyed by its stable id, so
 * re-scheduling never creates duplicates (edit = cancel + schedule).
 */
object ReminderScheduler {
    const val ACTION_FIRED = "com.techsagehub.mannai.ACTION_REMINDER_FIRED"
    const val EXTRA_KIND = "kind"          // "reminder" or "task"
    const val EXTRA_ID = "entry_id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"

    /** Stable notification id / PendingIntent request code derived from the row id. */
    fun codeFor(kind: String, id: Long): Int = (kind.hashCode() * 31 + id).toInt()

    fun schedule(
        context: Context,
        kind: String,
        id: Long,
        triggerAtMillis: Long,
        title: String,
        text: String
    ): Boolean {
        if (triggerAtMillis <= System.currentTimeMillis()) return false
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, kind, id, title, text)
        // setAndAllowWhileIdle needs no SCHEDULE_EXACT_ALARM grant and still fires in Doze.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        return true
    }

    fun cancel(context: Context, kind: String, id: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, kind, id, null, null))
    }

    private fun pendingIntent(
        context: Context, kind: String, id: Long, title: String?, text: String?
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_FIRED
            putExtra(EXTRA_KIND, kind)
            putExtra(EXTRA_ID, id)
            if (title != null) putExtra(EXTRA_TITLE, title)
            if (text != null) putExtra(EXTRA_TEXT, text)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, codeFor(kind, id), intent, flags)
    }

    /** Informational: whether the app holds the exact-alarm grant (not required). */
    fun hasExactAlarmGrant(context: Context): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else true
    }
}
