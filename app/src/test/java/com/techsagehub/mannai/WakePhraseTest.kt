package com.techsagehub.mannai

import com.techsagehub.mannai.assistant.WakePhrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakePhraseTest {
    @Test fun exactMatch() {
        assertTrue(WakePhrase.matches("hey mannai", "MannAI"))
    }

    @Test fun matchInsideSentence() {
        assertTrue(WakePhrase.matches("hey manny what's the time", "Manny"))
    }

    @Test fun caseAndPunctuationIgnored() {
        assertTrue(WakePhrase.matches("Hey, Manny!", "manny"))
    }

    @Test fun wrongNameRejected() {
        assertFalse(WakePhrase.matches("hey google", "Manny"))
    }

    @Test fun noHeyRejected() {
        assertFalse(WakePhrase.matches("manny add a task", "Manny"))
    }

    @Test fun smallMisrecognitionTolerated() {
        // "many" for "manny" is a one-letter difference.
        assertTrue(WakePhrase.matches("hey many", "Manny"))
    }

    @Test fun phraseForName() {
        assertEquals("Hey Manny", WakePhrase.phraseFor("Manny"))
        assertEquals("Hey MannAI", WakePhrase.phraseFor(""))
    }
}
