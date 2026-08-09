package com.calebjcox.countdownwidgets.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RenderingTest {

    private fun values(vararg pairs: Pair<TimeField, Long>) =
        pairs.map { FieldValue(it.first, it.second) }

    @Test
    fun `short labels distinguish months from minutes`() {
        val v = values(TimeField.MONTH to 2L, TimeField.MINUTE to 2L)
        assertEquals("2mo 2m", Rendering.formatFields(v, LabelStyle.SHORT))
    }

    @Test
    fun `long labels are pluralized`() {
        assertEquals(
            "1 year, 2 months, 1 day",
            Rendering.formatFields(
                values(TimeField.YEAR to 1L, TimeField.MONTH to 2L, TimeField.DAY to 1L),
                LabelStyle.LONG,
            ),
        )
    }

    @Test
    fun `leading zeros are dropped but trailing ones are kept`() {
        val v = values(TimeField.YEAR to 0L, TimeField.MONTH to 0L, TimeField.DAY to 5L)
        assertEquals("5d", Rendering.formatFields(v, LabelStyle.SHORT))

        val trailing = values(TimeField.DAY to 3L, TimeField.HOUR to 0L)
        assertEquals("3d 0h", Rendering.formatFields(trailing, LabelStyle.SHORT))
    }

    @Test
    fun `an all zero head survives on its own but vanishes ahead of a clock`() {
        val v = values(TimeField.YEAR to 0L, TimeField.DAY to 0L)
        assertEquals("0d", Rendering.formatFields(v, LabelStyle.SHORT))
        assertEquals("", Rendering.formatFields(v, LabelStyle.SHORT, allowEmpty = true))
    }

    @Test
    fun `clock format matches DateUtils formatElapsedTime`() {
        assertEquals("00:00", Rendering.formatClock(0))
        assertEquals("00:00", Rendering.formatClock(-5))
        assertEquals("00:59", Rendering.formatClock(59_999))
        assertEquals("01:00", Rendering.formatClock(60_000))
        assertEquals("59:59", Rendering.formatClock(3_599_999))
        assertEquals("1:00:00", Rendering.formatClock(3_600_000))
        assertEquals("8760:00:00", Rendering.formatClock(8_760L * 3_600_000))
    }

    @Test
    fun `clock truncates rather than rounds so it matches the ticking widget`() {
        assertEquals("00:04", Rendering.formatClock(4_999))
    }

    @Test
    fun `display drops a zero head in front of the clock`() {
        val display = Display(
            direction = Direction.COUNTDOWN,
            staticValues = values(TimeField.DAY to 0L),
            tailMillis = 3_600_000,
            nextChangeMillis = null,
        )
        assertEquals("1:00:00", Rendering.formatDisplay(display, LabelStyle.SHORT))
    }
}
