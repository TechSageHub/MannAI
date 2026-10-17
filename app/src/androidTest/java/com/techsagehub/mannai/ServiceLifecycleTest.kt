package com.techsagehub.mannai

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.techsagehub.mannai.service.AssistantService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Starts the assistant the same way the UI does (visible activity ->
 * foreground mic service) and checks the persistent notification + shutdown.
 * Requires RECORD_AUDIO + POST_NOTIFICATIONS granted (done via adb).
 */
@RunWith(AndroidJUnit4::class)
class ServiceLifecycleTest {
    @Test
    fun service_starts_notification_and_stops() {
        val instr = InstrumentationRegistry.getInstrumentation()
        val ctx = instr.targetContext
        instr.uiAutomation.executeShellCommand(
            "pm grant ${ctx.packageName} android.permission.RECORD_AUDIO").close()
        instr.uiAutomation.executeShellCommand(
            "pm grant ${ctx.packageName} android.permission.POST_NOTIFICATIONS").close()
        Thread.sleep(500)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.startListening() }

            var shown = false
            val deadline = System.currentTimeMillis() + 20_000
            while (System.currentTimeMillis() < deadline && !shown) {
                Thread.sleep(500)
                shown = nm.activeNotifications.any { sbn ->
                    sbn.notification.extras.getString("android.title") == "MannAI is listening"
                }
            }
            assertTrue("Expected the persistent 'MannAI is listening' notification", shown)
            assertTrue(AssistantService.isRunning.value)

            scenario.onActivity { it.stopListening() }
            Thread.sleep(2000)
            assertFalse(AssistantService.isRunning.value)
            val stillThere = nm.activeNotifications.any { sbn ->
                sbn.notification.extras.getString("android.title") == "MannAI is listening"
            }
            assertFalse("Stop should remove the persistent notification", stillThere)
        }
    }
}
