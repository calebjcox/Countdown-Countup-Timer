package com.calebjcox.countdownwidgets.core

import java.time.temporal.ChronoUnit

/**
 * A unit the user can choose to show. Declared largest to smallest; the ordinal
 * ordering is relied on throughout, so do not reorder.
 */
enum class TimeField(val unit: ChronoUnit, val shortLabel: String, val singular: String) {
    YEAR(ChronoUnit.YEARS, "y", "year"),
    MONTH(ChronoUnit.MONTHS, "mo", "month"),
    WEEK(ChronoUnit.WEEKS, "w", "week"),
    DAY(ChronoUnit.DAYS, "d", "day"),
    HOUR(ChronoUnit.HOURS, "h", "hour"),
    MINUTE(ChronoUnit.MINUTES, "m", "minute"),
    SECOND(ChronoUnit.SECONDS, "s", "second");

    /** Y / MO / W / D — the units a plain calendar date can express. */
    val isCalendar: Boolean get() = ordinal <= DAY.ordinal

    /** H / M / S — the units that need a time of day to be meaningful. */
    val isClock: Boolean get() = !isCalendar

    val plural: String get() = singular + "s"

    companion object {
        val CALENDAR: List<TimeField> = entries.filter { it.isCalendar }
        val CLOCK: List<TimeField> = entries.filter { it.isClock }
    }
}

/** A single unit and how many of it the breakdown allocated. */
data class FieldValue(val field: TimeField, val value: Long)

/** Which way the timer runs, derived from the target rather than configured. */
enum class Direction { COUNTDOWN, COUNTUP }
