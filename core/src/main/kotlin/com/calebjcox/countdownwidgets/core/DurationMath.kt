package com.calebjcox.countdownwidgets.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.Temporal

/**
 * Calendar-aware decomposition of the span between now and a target into whichever
 * units the user picked.
 *
 * Everything is done with `java.time` on zoned values, so month lengths, leap days
 * and daylight saving transitions are handled by the platform rather than by
 * arithmetic on millisecond counts.
 */
object DurationMath {

    /**
     * Never schedule further out than a day. Nothing breaks if we wake up and find
     * the text unchanged, but a widget that has silently missed a system clock
     * change should not stay wrong for a month.
     */
    const val MAX_SCHEDULE_AHEAD_MILLIS: Long = 24L * 60 * 60 * 1000

    fun compute(nowMillis: Long, zone: ZoneId, spec: TimerSpec): Display =
        when (spec.precision) {
            Precision.DATE -> computeDate(nowMillis, zone, spec)
            Precision.DATE_TIME -> computeDateTime(nowMillis, zone, spec)
        }

    /**
     * Date-only timers compare calendar dates, not instants: the day count ticks
     * over at local midnight, not at the time of day the target was created.
     */
    private fun computeDate(nowMillis: Long, zone: ZoneId, spec: TimerSpec): Display {
        val today: LocalDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val targetDate: LocalDate = spec.target.toLocalDate()

        val direction =
            if (targetDate.isAfter(today)) Direction.COUNTDOWN else Direction.COUNTUP
        val start = if (direction == Direction.COUNTDOWN) today else targetDate
        val end = if (direction == Direction.COUNTDOWN) targetDate else today

        val nextMidnight = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        return Display(
            direction = direction,
            staticValues = walk(start, end, spec.orderedFields).values,
            tailMillis = null,
            nextChangeMillis = nextMidnight,
        )
    }

    private fun computeDateTime(nowMillis: Long, zone: ZoneId, spec: TimerSpec): Display {
        val now: ZonedDateTime = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val target: ZonedDateTime = spec.target.atZone(zone)

        val countdown = target.toInstant().isAfter(now.toInstant())
        val direction = if (countdown) Direction.COUNTDOWN else Direction.COUNTUP
        val start = if (countdown) now else target
        val end = if (countdown) target else now

        val staticFields = spec.staticFields
        val static = walk(start, end, staticFields)

        val remainder = Duration.between(static.cursor, end).toMillis().coerceAtLeast(0)

        return Display(
            direction = direction,
            staticValues = static.values,
            tailMillis = if (spec.usesChronometer) remainder else null,
            nextChangeMillis = nextChange(
                nowMillis = nowMillis,
                smallestStatic = staticFields.lastOrNull(),
                cursor = static.cursor,
                remainderMillis = remainder,
                countdown = countdown,
            ),
        )
    }

    /**
     * When the static text next differs.
     *
     * Counting down, the values hold until the sub-unit remainder runs out: at
     * exactly `now + remainder` the smallest unit still reads the same, and one
     * millisecond later it drops by one — so that instant plus a millisecond is the
     * first moment the widget is stale.
     *
     * Counting up, the walk started at the target, so the smallest unit increments a
     * whole unit after the cursor. That has to be calendar arithmetic rather than a
     * fixed offset, or a "months since" widget would drift across February.
     */
    private fun nextChange(
        nowMillis: Long,
        smallestStatic: TimeField?,
        cursor: Temporal,
        remainderMillis: Long,
        countdown: Boolean,
    ): Long? {
        if (smallestStatic == null) return null

        val raw = if (countdown) {
            nowMillis + remainderMillis + 1
        } else {
            val next = cursor.plus(1, smallestStatic.unit)
            Instant.from(next).toEpochMilli()
        }

        return raw
            .coerceAtLeast(nowMillis + 1)
            .coerceAtMost(nowMillis + MAX_SCHEDULE_AHEAD_MILLIS)
    }

    private data class Walk<T : Temporal>(val values: List<FieldValue>, val cursor: T)

    /**
     * Greedily allocates [fields] from largest to smallest.
     *
     * `ChronoUnit.between` already returns the number of *complete* units and stays
     * consistent with the matching `plus`, so no search or correction is needed:
     * on a `ZonedDateTime` a date-based unit compares local date-times (a day is a
     * day, even the 23-hour one) while a time-based unit measures real elapsed time.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Temporal> walk(start: T, end: T, fields: List<TimeField>): Walk<T> {
        var cursor = start
        val values = ArrayList<FieldValue>(fields.size)
        for (field in fields) {
            val count = field.unit.between(cursor, end).coerceAtLeast(0)
            if (count > 0) cursor = cursor.plus(count, field.unit) as T
            values.add(FieldValue(field, count))
        }
        return Walk(values, cursor)
    }
}
