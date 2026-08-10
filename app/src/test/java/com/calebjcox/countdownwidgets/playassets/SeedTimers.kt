package com.calebjcox.countdownwidgets.playassets

import android.content.Context
import com.calebjcox.countdownwidgets.core.LabelStyle
import com.calebjcox.countdownwidgets.core.Precision
import com.calebjcox.countdownwidgets.core.TextTheme
import com.calebjcox.countdownwidgets.core.TimeField
import com.calebjcox.countdownwidgets.core.TimerSpec
import com.calebjcox.countdownwidgets.data.Timer
import com.calebjcox.countdownwidgets.data.TimerStore
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The timers the screenshots show.
 *
 * Between them they exercise all seven [TimeField]s, both directions, both label
 * styles and both precisions. Direction is not a setting — [com.calebjcox.countdownwidgets.core.DurationMath]
 * derives it from whether the target is in the past — so the count-up timers here are
 * simply the ones with targets behind us.
 *
 * Every target is built by offsetting *now* by an exact number of the units that
 * timer displays, which is what makes the rendered values reproducible without
 * meddling with the clock: a target set to now + 3 months + 2 weeks + 4 days reads
 * `3mo 2w 4d` whenever it is rendered, while the footer date stays in the future and
 * never goes stale. Do not swap these for fixed calendar dates; the values would
 * drift with the date the generator ran.
 */
object SeedTimers {

    /** Id of the timer the editor screenshots open, so the two stay in step. */
    const val EDITOR_TIMER_ID = "product-launch"

    fun all(zone: ZoneId, nowMillis: Long): List<Timer> {
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)

        return listOf(
            // Countdown, calendar units, short labels. Months, weeks and days.
            timer(
                id = "vacation",
                name = "Vacation",
                target = now.plusMonths(3).plusWeeks(2).plusDays(4),
                precision = Precision.DATE,
                fields = setOf(TimeField.MONTH, TimeField.WEEK, TimeField.DAY),
            ),
            // Count-up, and the one that spells its units out in full.
            timer(
                id = "anniversary",
                name = "Wedding anniversary",
                target = now.minusYears(12).minusMonths(3).minusDays(5),
                precision = Precision.DATE,
                fields = setOf(TimeField.YEAR, TimeField.MONTH, TimeField.DAY),
                labelStyle = LabelStyle.LONG,
            ),
            // Countdown to a time of day: days, hours and minutes, no ticking. The
            // spare 45 seconds are slack, not decoration: the breakdown reports whole
            // *completed* minutes, so without them the seconds that pass between this
            // call and the render would turn 30m into 29m.
            timer(
                id = "marathon",
                name = "Marathon",
                target = now.plusDays(45).plusHours(6).plusMinutes(30).plusSeconds(45),
                precision = Precision.DATE_TIME,
                fields = setOf(TimeField.DAY, TimeField.HOUR, TimeField.MINUTE),
            ),
            // Seconds, so this is the one that renders through a live Chronometer.
            // Also the timer the editor screenshots open: with a background switched
            // off it shows the most of the form, text-colour toggle included.
            //
            // The seconds digit is the one value here that cannot be pinned — it
            // counts down while the generator works — so it lands a second or two
            // below 20 rather than exactly on it. The half-second centres the
            // Chronometer's own rounding, which works in whole seconds either side.
            timer(
                id = EDITOR_TIMER_ID,
                name = "Product launch",
                target = now.plusDays(12).plusHours(3).plusMinutes(20)
                    .plusSeconds(20).plusNanos(500_000_000),
                precision = Precision.DATE_TIME,
                fields = setOf(TimeField.DAY, TimeField.HOUR, TimeField.MINUTE, TimeField.SECOND),
            ),
            timer(
                id = "japan",
                name = "Trip to Japan",
                target = now.plusWeeks(8).plusDays(3),
                precision = Precision.DATE,
                fields = setOf(TimeField.WEEK, TimeField.DAY),
            ),
            // Count-up in years, weeks and days — the one combination that skips months.
            timer(
                id = "moved-in",
                name = "Since we moved in",
                target = now.minusYears(2).minusWeeks(5).minusDays(1),
                precision = Precision.DATE,
                fields = setOf(TimeField.YEAR, TimeField.WEEK, TimeField.DAY),
            ),
            // A single unit, spelled out. The plainest thing the app can do.
            timer(
                id = "semester",
                name = "Semester ends",
                target = now.plusDays(143),
                precision = Precision.DATE,
                fields = setOf(TimeField.DAY),
                labelStyle = LabelStyle.LONG,
            ),
        )
    }

    /** Writes the seed set into the store the activities read from. */
    fun install(context: Context, zone: ZoneId, nowMillis: Long): List<Timer> {
        val timers = all(zone, nowMillis)
        TimerStore(context).replaceAll(timers)
        return timers
    }

    private fun timer(
        id: String,
        name: String,
        target: LocalDateTime,
        precision: Precision,
        fields: Set<TimeField>,
        labelStyle: LabelStyle = LabelStyle.SHORT,
        textTheme: TextTheme = TextTheme.AUTO,
        showBackground: Boolean = false,
    ) = Timer(
        id = id,
        name = name,
        spec = TimerSpec.of(target, precision, fields),
        labelStyle = labelStyle,
        textTheme = textTheme,
        showBackground = showBackground,
    )
}
