package com.techsagehub.mannai.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.techsagehub.mannai.MainActivity
import com.techsagehub.mannai.R

object MannAIChannels {
    const val REMINDERS = "mannai_reminders"
    const val SERVICE = "mannai_service"
    const val GENERAL = "mannai_general"

    fun createAll(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val list = listOf(
            NotificationChannel(REMINDERS, "Reminders",
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Task and reminder alerts from MannAI"
            },
            NotificationChannel(SERVICE, "Assistant service",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while the MannAI assistant is listening"
            },
            NotificationChannel(GENERAL, "General",
                NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "General MannAI messages"
            }
        )
        // createNotificationChannels is idempotent — safe to call on every launch.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) nm.createNotificationChannels(list)
    }
}

object MannAINotifier {
    private fun openAppIntent(context: Context, screen: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (screen != null) putExtra(MainActivity.EXTRA_SCREEN, screen)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, screen.hashCode(), intent, flags)
    }

    fun showReminder(
        context: Context,
        notificationId: Int,
        title: String,
        text: String,
        screen: String = MainActivity.SCREEN_REMINDERS
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, MannAIChannels.REMINDERS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, screen))
            .build()
        nm.notify(notificationId, notification)
    }

    fun showGeneral(context: Context, notificationId: Int, title: String, text: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, MannAIChannels.GENERAL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, null))
            .build()
        nm.notify(notificationId, notification)
    }
}
