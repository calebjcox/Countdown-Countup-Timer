package com.calebjcox.countdownwidgets.core

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prints the exact strings the widget would show for a handful of realistic
 * timers. Not a strict assertion of wording — the tests above pin the arithmetic —
 * but it makes the end result reviewable without a device, which matters because
 * the Android half of this project cannot be run outside an emulator.
 */
class WidgetTextSamplesTest {

    private val now = at(DENVER, "2026-08-09T13:37:42")

    @Test
    fun `sample widget text`() {
        val samples = listOf(
            "Christmas (date, y/mo/d)" to
                dateSpec("2026-12-25T00:00", TimeField.YEAR, TimeField.MONTH, TimeField.DAY),
            "Christmas (date, w/d)" to
                dateSpec("2026-12-25T00:00", TimeField.WEEK, TimeField.DAY),
            "Christmas (date, d only)" to
                dateSpec("2026-12-25T00:00", TimeField.DAY),
            "Wedding anniversary (past date, y/mo/d)" to
                dateSpec("2019-06-15T00:00", TimeField.YEAR, TimeField.MONTH, TimeField.DAY),
            "Launch (date+time, d/h/m/s)" to
                dateTimeSpec(
                    "2026-08-11T09:00:00",
                    TimeField.DAY, TimeField.HOUR, TimeField.MINUTE, TimeField.SECOND,
                ),
            "Standup (date+time, h/m/s)" to
                dateTimeSpec(
                    "2026-08-09T15:00:00",
                    TimeField.HOUR, TimeField.MINUTE, TimeField.SECOND,
                ),
            "Sobriety (past date+time, y/d/h/m/s)" to
                dateTimeSpec(
                    "2023-01-01T00:00:00",
                    TimeField.YEAR, TimeField.DAY,
                    TimeField.HOUR, TimeField.MINUTE, TimeField.SECOND,
                ),
            "Deadline (date+time, w/d/h)" to
                dateTimeSpec(
                    "2026-09-30T17:00:00",
                    TimeField.WEEK, TimeField.DAY, TimeField.HOUR,
                ),
        )

        println("now = 2026-08-09T13:37:42 America/Denver")
        println("%-42s %-10s %-22s %s".format("timer", "direction", "widget text", "refresh in"))
        for ((label, spec) in samples) {
            val display = DurationMath.compute(now, DENVER, spec)
            val refresh = display.nextChangeMillis
                ?.let { "${(it - now) / 1000}s" }
                ?: "never"
            println(
                "%-42s %-10s %-22s %s".format(
                    label,
                    if (display.isCountdown) "down" else "up",
                    Rendering.formatDisplay(display, LabelStyle.SHORT),
                    refresh,
                ),
            )
            assertTrue(
                "$label produced empty text",
                Rendering.formatDisplay(display, LabelStyle.SHORT).isNotEmpty(),
            )
        }
    }
}
