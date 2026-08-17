package com.calebjcox.countdownwidgets.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class TimerSpecTest {

    @Test
    fun `date only specs drop clock units and sit at midnight`() {
        val spec = TimerSpec.of(
            localDateTime("2026-12-25T18:45"),
            Precision.DATE,
            setOf(TimeField.MONTH, TimeField.DAY, TimeField.HOUR, TimeField.SECOND),
        )

        assertEquals(setOf(TimeField.MONTH, TimeField.DAY), spec.fields)
        assertEquals(LocalTime.MIDNIGHT, spec.target.toLocalTime())
        assertFalse(spec.usesChronometer)
    }

    @Test
    fun `seconds pull in the clock units above them`() {
        val spec = TimerSpec.of(
            localDateTime("2026-12-25T18:45"),
            Precision.DATE_TIME,
            setOf(TimeField.DAY, TimeField.SECOND),
        )

        assertEquals(
            setOf(TimeField.DAY, TimeField.HOUR, TimeField.MINUTE, TimeField.SECOND),
            spec.fields,
        )
        assertTrue(spec.usesChronometer)
        assertEquals(listOf(TimeField.DAY), spec.staticFields)
    }

    @Test
    fun `clock units without seconds are left exactly as chosen`() {
        val spec = TimerSpec.of(
            localDateTime("2026-12-25T18:45"),
            Precision.DATE_TIME,
            setOf(TimeField.WEEK, TimeField.HOUR),
        )

        assertEquals(setOf(TimeField.WEEK, TimeField.HOUR), spec.fields)
        assertFalse(spec.usesChronometer)
        // Nothing ticks, so every chosen unit is static text.
        assertEquals(listOf(TimeField.WEEK, TimeField.HOUR), spec.staticFields)
    }

    @Test
    fun `an empty selection falls back to a sensible default`() {
        val date = TimerSpec.of(localDateTime("2026-12-25T00:00"), Precision.DATE, emptySet())
        assertEquals(TimerSpec.DEFAULT_DATE_FIELDS, date.fields)

        val dateTime =
            TimerSpec.of(localDateTime("2026-12-25T18:45"), Precision.DATE_TIME, emptySet())
        assertEquals(TimerSpec.DEFAULT_DATE_TIME_FIELDS, dateTime.fields)
    }

    @Test
    fun `a time of day defaults to the calendar units plus hours and minutes`() {
        assertEquals(
            setOf(
                TimeField.YEAR,
                TimeField.MONTH,
                TimeField.DAY,
                TimeField.HOUR,
                TimeField.MINUTE,
            ),
            TimerSpec.DEFAULT_DATE_TIME_FIELDS,
        )

        // Seconds would replace the whole clock portion with a Chronometer's H:MM:SS.
        val spec =
            TimerSpec.of(localDateTime("2026-12-25T18:45"), Precision.DATE_TIME, emptySet())
        assertFalse(spec.usesChronometer)
    }

    @Test
    fun `fields are always ordered largest first`() {
        val spec = TimerSpec.of(
            localDateTime("2026-12-25T18:45"),
            Precision.DATE_TIME,
            setOf(TimeField.MINUTE, TimeField.YEAR, TimeField.HOUR, TimeField.WEEK),
        )

        assertEquals(
            listOf(TimeField.YEAR, TimeField.WEEK, TimeField.HOUR, TimeField.MINUTE),
            spec.orderedFields,
        )
    }

    @Test
    fun `normalizing twice changes nothing`() {
        val spec = TimerSpec.of(
            localDateTime("2026-12-25T18:45"),
            Precision.DATE_TIME,
            setOf(TimeField.DAY, TimeField.SECOND),
        )
        assertEquals(spec, spec.normalized())
    }
}
