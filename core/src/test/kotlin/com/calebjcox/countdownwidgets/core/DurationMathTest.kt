package com.calebjcox.countdownwidgets.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class DurationMathTest {

    // ---------------------------------------------------------------- date only

    @Test
    fun `date countdown in years months days`() {
        val spec = dateSpec("2026-12-25T00:00", TimeField.YEAR, TimeField.MONTH, TimeField.DAY)
        // 2026-08-09 -> 2026-12-09 is four months, then sixteen days to the 25th.
        assertEquals("4mo 16d", shortText(at(UTC, "2026-08-09T13:37"), UTC, spec))
    }

    @Test
    fun `same span reads differently depending on chosen units`() {
        val now = at(UTC, "2026-08-09T13:37")
        val target = "2026-12-25T00:00"

        assertEquals(
            "4mo 16d",
            shortText(now, UTC, dateSpec(target, TimeField.YEAR, TimeField.MONTH, TimeField.DAY)),
        )
        assertEquals(
            "19w 5d",
            shortText(now, UTC, dateSpec(target, TimeField.WEEK, TimeField.DAY)),
        )
        assertEquals(
            "138d",
            shortText(now, UTC, dateSpec(target, TimeField.DAY)),
        )
    }

    @Test
    fun `date timer ignores time of day and rolls at local midnight`() {
        val spec = dateSpec("2026-08-10T00:00", TimeField.DAY)

        assertEquals("1d", shortText(at(DENVER, "2026-08-09T00:00"), DENVER, spec))
        assertEquals("1d", shortText(at(DENVER, "2026-08-09T23:59:59"), DENVER, spec))
        assertEquals("0d", shortText(at(DENVER, "2026-08-10T00:00"), DENVER, spec))

        val display = DurationMath.compute(at(DENVER, "2026-08-09T13:37"), DENVER, spec)
        assertEquals(at(DENVER, "2026-08-10T00:00"), display.nextChangeMillis)
    }

    @Test
    fun `date timer never uses a chronometer`() {
        val spec = dateSpec("2026-12-25T00:00", TimeField.DAY)
        assertNull(DurationMath.compute(at(UTC, "2026-08-09T13:37"), UTC, spec).tailMillis)
    }

    // ------------------------------------------------------------- calendar math

    @Test
    fun `month arithmetic clamps to shorter months`() {
        // Jan 31 + 1 month is Feb 28/29, so the leftover days count from there.
        val spec = dateSpec("2028-03-30T00:00", TimeField.MONTH, TimeField.DAY)
        assertEquals("1mo 30d", shortText(at(UTC, "2028-01-31T00:00"), UTC, spec))

        // Trailing zeros are kept: "2mo 0d" says exactly two months, where a bare
        // "2mo" would be ambiguous about the days the user asked to see.
        val whole = dateSpec("2028-03-31T00:00", TimeField.MONTH, TimeField.DAY)
        assertEquals("2mo 0d", shortText(at(UTC, "2028-01-31T00:00"), UTC, whole))
    }

    @Test
    fun `leap day is counted`() {
        val spec = dateSpec("2028-03-01T00:00", TimeField.DAY)
        // 2028 is a leap year: Feb 28 -> Mar 1 is two days, not one.
        assertEquals("2d", shortText(at(UTC, "2028-02-28T00:00"), UTC, spec))

        val nonLeap = dateSpec("2027-03-01T00:00", TimeField.DAY)
        assertEquals("1d", shortText(at(UTC, "2027-02-28T00:00"), UTC, nonLeap))
    }

    @Test
    fun `anniversary of a leap day counts up correctly`() {
        val spec = dateSpec("2020-02-29T00:00", TimeField.YEAR, TimeField.MONTH, TimeField.DAY)
        val display = DurationMath.compute(at(UTC, "2026-08-09T12:00"), UTC, spec)

        assertEquals(Direction.COUNTUP, display.direction)
        // 2020-02-29 + 6y clamps to 2026-02-28, + 5mo is 2026-07-28, then 12 days.
        assertEquals("6y 5mo 12d", Rendering.formatFields(display.staticValues, LabelStyle.SHORT))
    }

    // ---------------------------------------------------------- daylight saving

    @Test
    fun `a day stays a day across spring forward`() {
        // Denver springs forward at 02:00 on 2026-03-08; that day is 23 hours long.
        val spec = dateTimeSpec("2026-03-09T00:00", TimeField.DAY)
        assertEquals("2d", shortText(at(DENVER, "2026-03-07T00:00"), DENVER, spec))

        val hours = dateTimeSpec("2026-03-09T00:00", TimeField.HOUR)
        assertEquals("47h", shortText(at(DENVER, "2026-03-07T00:00"), DENVER, hours))
    }

    @Test
    fun `a day stays a day across fall back`() {
        // Denver falls back at 02:00 on 2026-11-01; that day is 25 hours long.
        val spec = dateTimeSpec("2026-11-02T00:00", TimeField.DAY)
        assertEquals("2d", shortText(at(DENVER, "2026-10-31T00:00"), DENVER, spec))

        val hours = dateTimeSpec("2026-11-02T00:00", TimeField.HOUR)
        assertEquals("49h", shortText(at(DENVER, "2026-10-31T00:00"), DENVER, hours))
    }

    @Test
    fun `chronometer remainder is real elapsed time across a DST gap`() {
        val spec = dateTimeSpec(
            "2026-03-09T00:00",
            TimeField.DAY, TimeField.HOUR, TimeField.MINUTE, TimeField.SECOND,
        )
        val now = at(DENVER, "2026-03-08T00:30")
        val display = DurationMath.compute(now, DENVER, spec)

        // Still the 8th locally, so no whole day remains; the clock shows the real
        // 22.5 hours left rather than the 23.5 the wall clock suggests.
        assertEquals(0L, display.staticValues.single().value)
        assertEquals(Duration.ofMinutes(22 * 60 + 30).toMillis(), display.tailMillis)
        assertEquals("22:30:00", shortText(now, DENVER, spec))
    }

    @Test
    fun `count up boundary is calendar based across a DST gap`() {
        val spec = dateTimeSpec("2026-03-07T09:00", TimeField.DAY)
        val now = at(DENVER, "2026-03-08T00:30")
        val display = DurationMath.compute(now, DENVER, spec)

        assertEquals(Direction.COUNTUP, display.direction)
        assertEquals(0L, display.staticValues.single().value)
        // The count ticks to "1d" at 09:00 wall clock on the 8th, 7.5 real hours
        // later because an hour vanished, not 24 hours after the start.
        assertEquals(at(DENVER, "2026-03-08T09:00"), display.nextChangeMillis)
    }

    // ------------------------------------------------------- head / tail split

    @Test
    fun `seconds put everything below the smallest calendar unit in the tail`() {
        val spec = dateTimeSpec(
            "2026-08-11T04:05:06",
            TimeField.DAY, TimeField.HOUR, TimeField.MINUTE, TimeField.SECOND,
        )
        val now = at(UTC, "2026-08-09T00:00")
        val display = DurationMath.compute(now, UTC, spec)

        assertEquals(listOf(FieldValue(TimeField.DAY, 2L)), display.staticValues)
        assertEquals(Duration.ofHours(4).plusMinutes(5).plusSeconds(6).toMillis(), display.tailMillis)
        assertEquals("2d 4:05:06", shortText(now, UTC, spec))
    }

    @Test
    fun `a clock only timer never needs to be woken`() {
        val spec = dateTimeSpec(
            "2027-08-09T10:00",
            TimeField.HOUR, TimeField.MINUTE, TimeField.SECOND,
        )
        val now = at(UTC, "2026-08-09T10:00")
        val display = DurationMath.compute(now, UTC, spec)

        assertTrue(display.staticValues.isEmpty())
        assertNull(display.nextChangeMillis)
        assertEquals("8760:00:00", shortText(now, UTC, spec))
    }

    @Test
    fun `tail is always smaller than one of the smallest static unit`() {
        val spec = dateTimeSpec(
            "2027-05-17T08:09:10",
            TimeField.YEAR, TimeField.MONTH, TimeField.DAY,
            TimeField.HOUR, TimeField.MINUTE, TimeField.SECOND,
        )
        val start = at(DENVER, "2026-03-01T00:00")
        var offset = 0L
        while (offset < Duration.ofDays(400).toMillis()) {
            val display = DurationMath.compute(start + offset, DENVER, spec)
            val tail = display.tailMillis!!
            assertTrue("tail $tail negative at offset $offset", tail >= 0)
            // A calendar day is at most 25 hours where DST is observed.
            assertTrue(
                "tail $tail exceeded a day at offset $offset",
                tail < Duration.ofHours(25).toMillis(),
            )
            offset += Duration.ofMinutes(97).toMillis()
        }
    }

    // ------------------------------------------------------------- rescheduling

    @Test
    fun `next change lands exactly on the minute boundary`() {
        val spec = dateTimeSpec("2026-08-09T10:05:00", TimeField.MINUTE)
        val now = at(UTC, "2026-08-09T10:00:00.500")

        val display = DurationMath.compute(now, UTC, spec)
        assertEquals(4L, display.staticValues.single().value)

        val next = display.nextChangeMillis!!
        assertEquals(
            4L,
            DurationMath.compute(next - 1, UTC, spec).staticValues.single().value,
        )
        assertEquals(
            3L,
            DurationMath.compute(next, UTC, spec).staticValues.single().value,
        )
    }

    @Test
    fun `next change lands exactly on the day boundary for a ticking timer`() {
        val spec = dateTimeSpec(
            "2026-12-25T07:30:00",
            TimeField.DAY, TimeField.HOUR, TimeField.MINUTE, TimeField.SECOND,
        )
        val now = at(DENVER, "2026-08-09T13:37:42.250")

        val display = DurationMath.compute(now, DENVER, spec)
        val days = display.staticValues.single().value
        val next = display.nextChangeMillis!!

        assertEquals(days, DurationMath.compute(next - 1, DENVER, spec).staticValues.single().value)
        assertEquals(
            days - 1,
            DurationMath.compute(next, DENVER, spec).staticValues.single().value,
        )
    }

    @Test
    fun `next change is capped at a day out`() {
        val spec = dateTimeSpec("2030-01-01T00:00", TimeField.YEAR)
        val now = at(UTC, "2026-08-09T13:37")
        val display = DurationMath.compute(now, UTC, spec)

        assertEquals(now + DurationMath.MAX_SCHEDULE_AHEAD_MILLIS, display.nextChangeMillis)
    }

    @Test
    fun `next change is always in the future`() {
        val spec = dateTimeSpec("2026-08-09T10:00:00", TimeField.MINUTE)
        // Exactly on the target, and one millisecond either side of it.
        for (delta in listOf(-1L, 0L, 1L)) {
            val now = at(UTC, "2026-08-09T10:00:00") + delta
            val next = DurationMath.compute(now, UTC, spec).nextChangeMillis!!
            assertTrue("next=$next now=$now", next > now)
        }
    }

    // ---------------------------------------------------------------- direction

    @Test
    fun `direction flips at the target`() {
        val spec = dateTimeSpec("2026-08-09T10:00:00", TimeField.HOUR, TimeField.MINUTE)
        val target = at(UTC, "2026-08-09T10:00:00")

        assertEquals(Direction.COUNTDOWN, DurationMath.compute(target - 1, UTC, spec).direction)
        assertEquals(Direction.COUNTUP, DurationMath.compute(target, UTC, spec).direction)
        assertEquals(Direction.COUNTUP, DurationMath.compute(target + 1, UTC, spec).direction)
    }

    @Test
    fun `count up and count down are symmetric`() {
        val fields = arrayOf(TimeField.YEAR, TimeField.MONTH, TimeField.DAY)
        val down = dateSpec("2027-08-09T00:00", *fields)
        val up = dateSpec("2025-08-09T00:00", *fields)
        val now = at(UTC, "2026-08-09T12:00")

        assertEquals("1y 0mo 0d", shortText(now, UTC, down))
        assertEquals("1y 0mo 0d", shortText(now, UTC, up))
    }

    @Test
    fun `zero span shows zeros rather than nothing`() {
        val spec = dateTimeSpec("2026-08-09T10:00:00", TimeField.DAY, TimeField.HOUR)
        val display = DurationMath.compute(at(UTC, "2026-08-09T10:00:00"), UTC, spec)

        assertEquals(listOf(0L, 0L), display.staticValues.map { it.value })
        assertEquals("0h", Rendering.formatFields(display.staticValues, LabelStyle.SHORT))
    }
}
