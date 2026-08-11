package com.calebjcox.countdownwidgets.widget

import android.content.Context
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.SizeF
import android.util.TypedValue
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
 * Size handling is declarative. Rather than reacting to resize callbacks, every
 * variant in [BANDS] is handed to the platform at once and the launcher picks
 * whichever best fits the cell it was given.
 */
object WidgetRenderer {

    /** Which rows to show, chosen by the launcher from the cell size. */
    private enum class Detail { VALUE, VALUE_AND_NAME, EVERYTHING }

    /**
     * A cell size the widget is built for, and what it shows there.
     *
     * [heightDp] and [widthDp] are dp of *content box* — what `AppWidgetHostView`
     * measures after the launcher's own inset, not the nominal cell. Every other
     * number in the layout is derived from [heightDp] by [metrics]; this list is the
     * only place a size is chosen by hand.
     */
    private data class Band(val widthDp: Float, val heightDp: Float, val detail: Detail)

    /**
     * The size ladder handed to the launcher.
     *
     * Two things about it are easy to get wrong.
     *
     * The platform does not pick the largest variant that fits. `findBestFitLayout`
     * takes every entry that fits inside the cell and keeps the one nearest it by
     * squared distance. Every width here is the same 110dp — the provider's own
     * `minWidth` — which is what makes that rule predictable: with the width term equal
     * across candidates, the nearest fit is simply the tallest band the cell can hold.
     * It also means width can never demote a legally-sized widget, which is how a 2x1
     * used to end up showing a bare number. The fit table lives in
     * `WidgetVariantSizeTest`, asserted against the platform's own selection code.
     *
     * The heights are a ladder rather than one entry per detail level because the
     * layout is scaled, not just switched. A 68dp cell and a 180dp cell both show all
     * three rows, but they want different padding, different label sizes and a
     * different share of the height for the value; a band every ~25% keeps each of
     * those close to right without a variant per pixel.
     */
    private val BANDS = listOf(
        Band(110f, 40f, Detail.VALUE),
        Band(110f, 52f, Detail.VALUE_AND_NAME),
        Band(110f, 68f, Detail.EVERYTHING),
        Band(110f, 88f, Detail.EVERYTHING),
        Band(110f, 112f, Detail.EVERYTHING),
        Band(110f, 144f, Detail.EVERYTHING),
        Band(110f, 184f, Detail.EVERYTHING),
    )

    /** The resolved sizes for one band, all in dp except [labelSp]. */
    private data class Metrics(val paddingDp: Float, val labelSp: Float, val valueDp: Float)

    /**
     * Turns a band's cell height into the sizes its rows are drawn at.
     *
     * The rule the whole layout hangs on: **the rows are packed tight and the leftover
     * becomes border.** A vertical `LinearLayout` centres each child's text inside that
     * child's box, so any box taller than its text shows up as a gap between rows —
     * and a gap between rows always looks like two half-gaps at the edges, i.e. twice
     * as wide. Giving the value a weighted slot, as this used to, hands it every spare
     * pixel and produces exactly that: rows further from each other than from the
     * widget's own edges, which reads as three stacked things instead of one tile. So
     * the value gets a box near the size of its text and no more, and what is left over
     * is spread by the root's `gravity="center"` where it belongs — outside everything.
     *
     * [VALUE_SHARE] is what keeps a border there at all: without it the value would
     * take the entire remainder and the rows would touch the padding.
     *
     * The cap matters as much as the share. The value can only be auto-sized between
     * the bounds its style declares, so a box taller than [MAX_VALUE_SP] can never be
     * filled and every dp above that would come back as the gap this is trying to
     * remove.
     *
     * Font scale is read rather than assumed. The labels are sp, so a user running
     * large text makes them taller; without accounting for that the rows would grow
     * past the cell and the footer would be the one clipped off.
     */
    private fun metrics(band: Band, fontScale: Float): Metrics {
        val height = band.heightDp
        val padding = (height * PADDING_SHARE).coerceIn(4f, 10f)
        val labelSp = (height * LABEL_SHARE).coerceIn(MIN_LABEL_SP, MAX_LABEL_SP)

        val labelRows = when (band.detail) {
            Detail.VALUE -> 0
            Detail.VALUE_AND_NAME -> 1
            Detail.EVERYTHING -> 2
        }
        val labelsDp = labelRows * labelSp * LINE_HEIGHT * fontScale
        val free = height - 2 * padding - labelsDp

        val value = (free * VALUE_SHARE).coerceAtMost(MAX_VALUE_SP * LINE_HEIGHT * fontScale)
        return Metrics(paddingDp = padding, labelSp = labelSp, valueDp = value)
    }

    /** Roughly what a single line of text occupies, as a multiple of its size. */
    private const val LINE_HEIGHT = 1.25f

    /** Has to agree with `autoSizeMaxTextSize` on `@style/WidgetValue`. */
    private const val MAX_VALUE_SP = 44f

    private const val PADDING_SHARE = 0.07f

    /**
     * The name and the target date scale with the cell too, rather than sitting at one
     * size for every widget. They were the thing that suffered when the rows had to be
     * squeezed into a one-row cell — small enough to be hard to read, and for no good
     * reason once the value stopped hoarding the height. The floor is 12sp, which is
     * where they started before any of this.
     *
     * The ceiling is what stops the date outgrowing a one-cell-wide column, where
     * "until Dec 25, 2026" has under 100dp to live in. Past 15sp it would ellipsize
     * there; it still may on the very narrowest, which is what `ellipsize="end"` on
     * both label styles is for.
     */
    private const val LABEL_SHARE = 0.16f
    private const val MIN_LABEL_SP = 12f
    private const val MAX_LABEL_SP = 15f

    /**
     * How much of what is left after padding and labels the value may occupy. The rest
     * is the border that keeps the rows reading as one tile.
     *
     * Lower than it looks like it should be, and the reason is the one thing this
     * approach cannot measure: how tall the value's text turns out. Auto-sizing shrinks
     * it to fit the cell's *width*, which nothing here knows — a band declares a
     * minimum width, not the real one — so the text usually ends up shorter than its
     * box, and that difference is what separates the labels from it. The labels
     * themselves are `wrap_content` and already sit flush against the box, so this
     * fraction, not the padding, is the dial for how close the three rows read.
     *
     * It is a compromise in both directions. Too high and the leftover reappears as the
     * gap this layout exists to remove; too low and the box starts clipping the value
     * before its width does, shrinking the number on cells that had room for it.
     * `WidgetVariantSizeTest` measures gap against border at real cell sizes.
     */
    private const val VALUE_SHARE = 0.64f

    fun build(
        context: Context,
        appWidgetId: Int,
        timer: Timer?,
        nowMillis: Long,
        zone: ZoneId,
    ): RemoteViews {
        if (timer == null) return unconfigured(context, appWidgetId)

        val display = DurationMath.compute(nowMillis, zone, timer.spec)
        // Sampled once so every variant agrees to the millisecond.
        val elapsedRealtime = SystemClock.elapsedRealtime()

        val fontScale = context.resources.configuration.fontScale

        return RemoteViews(
            BANDS.associate { band ->
                SizeF(band.widthDp, band.heightDp) to
                    render(context, appWidgetId, timer, display, band, fontScale, elapsedRealtime)
            },
        )
    }

    private fun render(
        context: Context,
        appWidgetId: Int,
        timer: Timer,
        display: Display,
        band: Band,
        fontScale: Float,
        elapsedRealtime: Long,
    ): RemoteViews {
        val detail = band.detail
        val metrics = metrics(band, fontScale)
        val views = RemoteViews(context.packageName, layoutFor(timer))

        // Applied on every variant rather than left to the XML for the band that
        // happens to match it: a variant whose spacing comes from somewhere else is a
        // variant that moves when that somewhere else does.
        val padding = context.resources.displayMetrics.dpToPx(metrics.paddingDp)
        views.setViewPadding(R.id.widget_root, padding, padding, padding, padding)
        views.setTextViewTextSize(R.id.widget_name, TypedValue.COMPLEX_UNIT_SP, metrics.labelSp)
        views.setTextViewTextSize(R.id.widget_footer, TypedValue.COMPLEX_UNIT_SP, metrics.labelSp)
        // The value's size is not set here — its style auto-sizes it to whatever fits.
        // What is set is the box it fits into, which is how the leftover height ends up
        // outside the rows instead of between them. Both the static value and the
        // chronometer get it; only one of them is ever visible, but the invisible one
        // would otherwise keep a stale box from the layout.
        views.setViewLayoutHeight(
            R.id.widget_value,
            metrics.valueDp,
            TypedValue.COMPLEX_UNIT_DIP,
        )
        views.setViewLayoutHeight(
            R.id.widget_ticker,
            metrics.valueDp,
            TypedValue.COMPLEX_UNIT_DIP,
        )

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

    /**
     * `setViewPadding` is one of the few remotable setters that takes raw pixels rather
     * than a unit and a value, so the conversion has to happen on this side.
     */
    private fun DisplayMetrics.dpToPx(dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, this).toInt()

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
     *
     * One size rather than the ladder: there is no timer, so nothing here changes with
     * the cell, and the prompt has to survive the smallest one. That is also why the
     * value's box is pinned to the smallest band's — `WidgetValue` is `wrap_content`
     * now, and an unbounded auto-sizing line could grow taller than the widget.
     */
    private fun unconfigured(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_timer)
        val metrics = metrics(BANDS.first(), context.resources.configuration.fontScale)
        val padding = context.resources.displayMetrics.dpToPx(metrics.paddingDp)
        views.setViewPadding(R.id.widget_root, padding, padding, padding, padding)
        views.setViewLayoutHeight(
            R.id.widget_value,
            metrics.valueDp,
            TypedValue.COMPLEX_UNIT_DIP,
        )
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
