package com.calebjcox.countdownwidgets.ui

import android.content.Context
import com.calebjcox.countdownwidgets.R
import com.calebjcox.countdownwidgets.core.DurationMath
import com.calebjcox.countdownwidgets.core.Precision
import com.calebjcox.countdownwidgets.core.Rendering
import com.calebjcox.countdownwidgets.core.TimerSpec
import com.calebjcox.countdownwidgets.data.Timer
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The in-app rendering of a timer, shared by the list, the widget picker and the
 * editor's live preview.
 *
 * It deliberately produces the same string the widget shows — including the clock
 * portion, formatted the way `Chronometer` formats it — so what you see while
 * editing is what lands on the home screen. The difference is only that this one is
 * a snapshot and the widget's ticks.
 */
object TimerSummary {

    fun value(timer: Timer, nowMillis: Long = System.currentTimeMillis()): String {
        val display = DurationMath.compute(nowMillis, ZoneId.systemDefault(), timer.spec)
        return Rendering.formatDisplay(display, timer.labelStyle)
    }

    fun target(context: Context, spec: TimerSpec, nowMillis: Long = System.currentTimeMillis()): String {
        val display = DurationMath.compute(nowMillis, ZoneId.systemDefault(), spec)
        val formatter = when (spec.precision) {
            Precision.DATE -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            Precision.DATE_TIME ->
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        }
        val template = if (display.isCountdown) R.string.until_target else R.string.since_target
        return context.getString(template, spec.target.format(formatter))
    }
}
