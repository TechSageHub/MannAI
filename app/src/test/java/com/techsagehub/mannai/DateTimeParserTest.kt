package com.techsagehub.mannai

import com.techsagehub.mannai.assistant.DateTimeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DateTimeParserTest {
    private val now = LocalDateTime.of(2026, 10, 16, 10, 0) // Friday

    @Test fun todayAt5pm() {
        val m = DateTimeParser.find("remind me today at 5 pm to call mom", now)
        assertNotNull(m)
        assertEquals(LocalDateTime.of(2026, 10, 16, 17, 0), m!!.dateTime)
    }

    @Test fun tomorrowMorning() {
        val m = DateTimeParser.find("remind me tomorrow at 8 AM to study", now)
        assertNotNull(m)
        assertEquals(LocalDateTime.of(2026, 10, 17, 8, 0), m!!.dateTime)
    }

    @Test fun pastTimeRollsToTomorrow() {
        val m = DateTimeParser.find("add task at 5 AM", now)
        assertNotNull(m)
        assertEquals(LocalDateTime.of(2026, 10, 17, 5, 0), m!!.dateTime)
    }

    @Test fun futureTimeStaysToday() {
        val m = DateTimeParser.find("add task at 5 PM", now)
        assertNotNull(m)
        assertEquals(LocalDateTime.of(2026, 10, 16, 17, 0), m!!.dateTime)
    }

    @Test fun inTwoHours() {
        val m = DateTimeParser.find("remind me in 2 hours", now)
        assertNotNull(m)
        assertEquals(LocalDateTime.of(2026, 10, 16, 12, 0), m!!.dateTime)
    }

    @Test fun nextMonday() {
        val m = DateTimeParser.find("finish report by next monday at 9 am", now)
        assertNotNull(m)
        // Upcoming Monday is Oct 19; "next" pushes a week out -> Oct 26.
        assertEquals(LocalDateTime.of(2026, 10, 26, 9, 0), m!!.dateTime)
    }

    @Test fun dateOnlyDefaultsToMorning() {
        val m = DateTimeParser.find("remind me tomorrow to stretch", now)
        assertNotNull(m)
        assertEquals(LocalDateTime.of(2026, 10, 17, 9, 0), m!!.dateTime)
    }

    @Test fun twentyFourHour() {
        val m = DateTimeParser.find("meeting today at 17:30", now)
        assertNotNull(m)
        assertEquals(LocalDateTime.of(2026, 10, 16, 17, 30), m!!.dateTime)
    }

    @Test fun noDateTimeReturnsNull() {
        assertNull(DateTimeParser.find("just a plain task with no time", now))
    }

    @Test fun dayAfterTomorrow() {
        val m = DateTimeParser.find("call dentist day after tomorrow morning", now)
        assertNotNull(m)
        assertTrue(m!!.dateTime.toLocalDate() == now.toLocalDate().plusDays(2))
    }
}
