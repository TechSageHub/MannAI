package com.techsagehub.mannai

import com.techsagehub.mannai.assistant.ReplyDrafter
import com.techsagehub.mannai.assistant.ReplyTone
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyDrafterTest {
    private val original = "Can you finish the report by Friday?"
    private val intent = "say yes, will send it Thursday evening"

    @Test fun allTonesProduceDrafts() {
        for (tone in ReplyTone.entries) {
            val res = ReplyDrafter.draft(original, intent, tone)
            assertNull("tone $tone should not error", res.error)
            assertNotNull("tone $tone should draft", res.draft)
            assertTrue(res.draft!!.isNotBlank())
        }
    }

    @Test fun professionalToneMentionsContent() {
        val res = ReplyDrafter.draft(original, intent, ReplyTone.Professional)
        assertTrue(res.draft!!.contains("Thursday", ignoreCase = true))
    }

    @Test fun shortToneIsBrief() {
        val res = ReplyDrafter.draft(original, intent, ReplyTone.Short)
        assertTrue(res.draft!!.length < 200)
    }

    @Test fun blankOriginalErrors() {
        val res = ReplyDrafter.draft("", intent, ReplyTone.Friendly)
        assertNotNull(res.error)
        assertNull(res.draft)
    }

    @Test fun blankIntentErrors() {
        val res = ReplyDrafter.draft(original, "   ", ReplyTone.Friendly)
        assertNotNull(res.error)
        assertNull(res.draft)
    }
}
