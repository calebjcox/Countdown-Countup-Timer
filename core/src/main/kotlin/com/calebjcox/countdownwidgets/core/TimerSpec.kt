package com.calebjcox.countdownwidgets.core

import java.time.LocalDateTime
import java.time.LocalTime

/** Whether the target is a bare date or a date with a time of day. */
enum class Precision { DATE, DATE_TIME }

/**
 * What to count to, and which units to say it in.
 *
 * The target is a *wall clock* [LocalDateTime], not an instant. A birthday on
 * December 25th stays December 25th after the phone crosses a time zone, which is
 * what people mean by a countdown; storing an instant would slide it by the offset
 * difference instead. The zone is supplied at render time, always the device's
 * current one.
 *
 * Instances are always [normalized]; construct through [of].
 */
data class TimerSpec(
    val target: LocalDateTime,
    val precision: Precision,
    val fields: Set<TimeField>,
) {
    /** Fields shown as static text, largest first. Empty for a purely ticking timer. */
    val staticFields: List<TimeField>
        get() = orderedFields.filter { !usesChronometer || it.isCalendar }

    /** All selected fields, largest first. */
    val orderedFields: List<TimeField>
        get() = fields.sortedBy { it.ordinal }

    /**
     * True when the smallest selected unit is seconds, which is the only case that
     * needs a self-ticking `Chronometer` rather than plain text.
     */
    val usesChronometer: Boolean
        get() = TimeField.SECOND in fields

    companion object {
        val DEFAULT_DATE_FIELDS: Set<TimeField> =
            setOf(TimeField.YEAR, TimeField.MONTH, TimeField.DAY)

        val DEFAULT_DATE_TIME_FIELDS: Set<TimeField> =
            setOf(TimeField.DAY, TimeField.HOUR, TimeField.MINUTE, TimeField.SECOND)

        /** Builds a spec, repairing any combination the UI or stored data can present. */
        fun of(
            target: LocalDateTime,
            precision: Precision,
            fields: Set<TimeField>,
        ): TimerSpec = TimerSpec(target, precision, fields).normalized()
    }

    /**
     * Applies the two invariants the rest of the code may then assume:
     *
     *  1. A date-only target carries only calendar units, and sits at midnight.
     *  2. If seconds are shown, the clock units above them are shown too. A
     *     `Chronometer` renders through `DateUtils.formatElapsedTime`, which always
     *     spills into hours — so a minutes-and-seconds-only timer would read
     *     `8760:00:00` regardless. Filling in the gap keeps what is displayed and
     *     what was asked for the same thing.
     */
    fun normalized(): TimerSpec {
        var out = fields.toMutableSet()
        var targetOut = target

        if (precision == Precision.DATE) {
            out.retainAll(TimeField.CALENDAR.toSet())
            targetOut = LocalDateTime.of(target.toLocalDate(), LocalTime.MIDNIGHT)
        } else if (TimeField.SECOND in out) {
            out.add(TimeField.MINUTE)
            out.add(TimeField.HOUR)
        }

        if (out.isEmpty()) {
            out = when (precision) {
                Precision.DATE -> DEFAULT_DATE_FIELDS
                Precision.DATE_TIME -> DEFAULT_DATE_TIME_FIELDS
            }.toMutableSet()
        }

        return TimerSpec(targetOut, precision, out)
    }
}
