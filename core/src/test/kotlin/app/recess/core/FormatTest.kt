package app.recess.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class FormatTest {
    private val zone = ZoneId.of("UTC")
    private val now = ZonedDateTime.of(2026, 3, 10, 14, 0, 0, 0, zone)
    private fun ms(day: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 3, day, h, m, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun minutesLabel() {
        assertEquals("0 min", Format.minutesLabel(0))
        assertEquals("45 min", Format.minutesLabel(45))
        assertEquals("1h", Format.minutesLabel(60))
        assertEquals("1h 19m", Format.minutesLabel(79))
        assertEquals("0 min", Format.minutesLabel(-5))
    }

    @Test fun dueLabel() {
        assertEquals("Overdue · 9:00 AM", Format.dueLabel(ms(10, 9), now))
        assertEquals("Today · 11:59 PM", Format.dueLabel(ms(10, 23, 59), now))
        assertEquals("Tomorrow · 8:00 AM", Format.dueLabel(ms(11, 8), now))
        assertEquals("Yesterday · 8:00 AM", Format.dueLabel(ms(9, 8), now))
        assertEquals("Thu 12 Mar · 3:00 PM", Format.dueLabel(ms(12, 15), now))
    }

    @Test fun downtimeWrapsMidnight() {
        assertTrue(Format.inDowntime("20:30", "07:00", now.withHour(22)))
        assertTrue(Format.inDowntime("20:30", "07:00", now.withHour(6)))
        assertFalse(Format.inDowntime("20:30", "07:00", now))
        assertTrue(Format.inDowntime("13:00", "15:00", now))
        assertFalse(Format.inDowntime("10:00", "10:00", now))
    }

    @Test fun clocks() {
        assertEquals("8:30 PM", Format.clockFromHhmm("20:30"))
        assertEquals("20:30", Format.hhmm(Format.parseHhmm("20:30")))
        assertTrue(Format.isAfterBedtime("13:59", now))
        assertFalse(Format.isAfterBedtime("14:01", now))
        assertEquals("Good afternoon", Format.greetingFor(now))
    }
}
