package com.calebjcox.countdownwidgets.widget

import android.content.Context
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.calebjcox.countdownwidgets.R
import com.calebjcox.countdownwidgets.core.Display
import com.calebjcox.countdownwidgets.core.DurationMath
import com.calebjcox.countdownwidgets.core.Precision
import com.calebjcox.countdownwidgets.core.Rendering
import com.calebjcox.countdownwidgets.data.Timer
import com.calebjcox.countdownwidgets.ui.EditTimerActivity
import com.calebjcox.countdownwidgets.ui.WidgetConfigActivity
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Turns a timer into the `RemoteViews` the launcher draws.
 *
 * Two things are worth knowing about the result:
 *
 * The seconds case is rendered by a `Chronometer`, not by text this app refreshes.
 * Its base is set on the `SystemClock.elapsedRealtime` timebase so it ticks in the
 * launcher's process with the app not running at all. The calendar part of the
 * string rides along in the chronometer's format string, so the whole value stays
 * one auto-sizing line instead of two views that have to be kept aligned.
 *
 * Size handling is declarative. Rather than reacting to resize callbacks, three
 * variants are handed to the platform at once and the launcher picks whichever fits
 * the cell it was given.
 */
object WidgetRenderer {

    /** How much of the widget to show, chosen by the launcher from the cell size. */
    private enum class Detail { VALUE, VALUE_AND_NAME, EVERYTHING }

    private val BREAKPOINTS = listOf(
        SizeF(100f, 40f) to Detail.VALUE,
        SizeF(150f, 70f) to Detail.VALUE_AND_NAME,
        SizeF(200f, 110f) to Detail.EVERYTHING,
    )

    fun build(
        context: Context,
        appWidgetId: Int,
        timer: Timer?,
        nowMillis: Long,
        zone: ZoneId,
    ): RemoteViews {
        if (timer == null) return unconfigured(context, appWidgetId)

        val display = DurationMath.compute(nowMillis, zone, timer.spec)
        // Sampled once so all three variants agree to the millisecond.
        val elapsedRealtime = SystemClock.elapsedRealtime()

        return RemoteViews(
            BREAKPOINTS.associate { (size, detail) ->
                size to variant(context, appWidgetId, timer, display, detail, elapsedRealtime)
            },
        )
    }

    private fun variant(
        context: Context,
        appWidgetId: Int,
        timer: Timer,
        display: Display,
        detail: Detail,
        elapsedRealtime: Long,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutFor(timer))

        // Resolved rather than left to the layout: on the wallpaper the choice
        // depends on the wallpaper itself, which no resource qualifier can express.
        val palette = WidgetPalette.forTimer(context, timer)
        views.setTextColor(R.id.widget_name, palette.secondary)
        views.setTextColor(R.id.widget_value, palette.primary)
        views.setTextColor(R.id.widget_ticker, palette.primary)
        views.setTextColor(R.id.widget_footer, palette.secondary)

        val showName = detail != Detail.VALUE && timer.name.isNotBlank()
        views.setViewVisibility(R.id.widget_name, if (showName) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.widget_name, timer.name)

        val head = Rendering.formatFields(
            values = display.staticValues,
            style = timer.labelStyle,
            allowEmpty = display.tailMillis != null,
        )

        val tailMillis = display.tailMillis
        if (tailMillis == null) {
            views.setViewVisibility(R.id.widget_ticker, View.GONE)
            views.setViewVisibility(R.id.widget_value, View.VISIBLE)
            views.setTextViewText(R.id.widget_value, head)
        } else {
            views.setViewVisibility(R.id.widget_value, View.GONE)
            views.setViewVisibility(R.id.widget_ticker, View.VISIBLE)
            views.setChronometerCountDown(R.id.widget_ticker, display.isCountdown)
            val base = if (display.isCountdown) {
                elapsedRealtime + tailMillis
            } else {
                elapsedRealtime - tailMillis
            }
            views.setChronometer(R.id.widget_ticker, base, chronometerFormat(head), true)
        }

        val showFooter = detail == Detail.EVERYTHING
        views.setViewVisibility(R.id.widget_footer, if (showFooter) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.widget_footer, targetSummary(context, timer, display))

        views.setOnClickPendingIntent(
            R.id.widget_root,
            EditTimerActivity.widgetTapIntent(context, appWidgetId, timer.id),
        )
        return views
    }

    /**
     * The two layouts differ only in having a background and a text shadow. They
     * are separate files rather than one file configured at runtime because
     * `RemoteViews` dispatches only single-argument remotable methods, and
     * `setShadowLayer` takes four — so a shadow can only be declared in XML.
     */
    private fun layoutFor(timer: Timer): Int =
        if (timer.showBackground) R.layout.widget_timer else R.layout.widget_timer_wallpaper

    /**
     * Embeds the calendar part into the chronometer's own format string. The `%s` is
     * where `Chronometer` substitutes the running clock; any literal percent in the
     * head has to be escaped or `String.format` would choke on it.
     */
    private fun chronometerFormat(head: String): String? =
        if (head.isEmpty()) null else head.replace("%", "%%") + "  %s"

    private fun targetSummary(context: Context, timer: Timer, display: Display): String {
        val formatter = when (timer.spec.precision) {
            Precision.DATE -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            Precision.DATE_TIME ->
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        }
        val target = timer.spec.target.format(formatter)
        val template = if (display.isCountdown) R.string.until_target else R.string.since_target
        return context.getString(template, target)
    }

    /**
     * What a widget shows when it has no timer behind it — after the configuration
     * screen was cancelled, or once the timer it pointed at was deleted. Tapping it
     * reopens the picker rather than leaving a dead tile on the home screen.
     *
     * Always the panel layout, whatever the eventual timer prefers: a prompt to tap
     * has to be findable, and there is no timer yet to say how it should look.
     */
    private fun unconfigured(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_timer)
        views.setViewVisibility(R.id.widget_name, View.GONE)
        views.setViewVisibility(R.id.widget_ticker, View.GONE)
        views.setViewVisibility(R.id.widget_footer, View.GONE)
        views.setViewVisibility(R.id.widget_value, View.VISIBLE)
        views.setTextViewText(R.id.widget_value, context.getString(R.string.widget_unconfigured))
        views.setOnClickPendingIntent(
            R.id.widget_root,
            WidgetConfigActivity.reconfigureIntent(context, appWidgetId),
        )
        return views
    }
}
