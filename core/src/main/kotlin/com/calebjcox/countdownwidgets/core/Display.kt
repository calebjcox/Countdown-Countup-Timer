package com.calebjcox.countdownwidgets.core

/**
 * Everything the widget needs in order to draw itself once, plus when to come back.
 *
 * The split into [staticValues] and [tailMillis] is what lets a seconds-precision
 * widget tick without the app ever running: the calendar part is text that only
 * changes at a day/month/year boundary, and the sub-day remainder is handed to a
 * `Chronometer`, which ticks inside the launcher's process at no cost to us.
 */
data class Display(
    val direction: Direction,
    /** Units rendered as plain text, largest first. Empty for a clock-only timer. */
    val staticValues: List<FieldValue>,
    /**
     * Milliseconds the `Chronometer` should show right now, or null when seconds
     * were not selected and no `Chronometer` is used. Always the remainder below
     * the smallest unit in [staticValues].
     */
    val tailMillis: Long?,
    /**
     * Epoch millis at which [staticValues] next differs, or null when it never
     * will — a clock-only timer needs no alarms at all for the rest of time.
     */
    val nextChangeMillis: Long?,
) {
    val isCountdown: Boolean get() = direction == Direction.COUNTDOWN
}
