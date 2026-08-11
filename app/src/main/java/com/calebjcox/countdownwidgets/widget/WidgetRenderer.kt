package com.calebjcox.countdownwidgets.widget

import android.content.Context
import android.os.SystemClock
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.DimenRes
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
 * variant in [BREAKPOINTS] is handed to the platform at once and the launcher picks
 * whichever best fits the cell it was given.
 */
object WidgetRenderer {

    /** Which rows to show, chosen by the launcher from the cell size. */
    private enum class Detail { VALUE, VALUE_AND_NAME, EVERYTHING }

    /**
     * How tightly the rows are packed. A separate axis from [Detail] because the two
     * answer different questions: whether there is room for a row at all, and how much
     * of the room left over should go to breathing space rather than to the value.
     */
    private enum class Density(
        @DimenRes val padding: Int,
        @DimenRes val nameText: Int,
        @DimenRes val footerText: Int,
    ) {
        COMPACT(
            padding = R.dimen.widget_padding_compact,
            nameText = R.dimen.widget_name_text_compact,
            footerText = R.dimen.widget_footer_text_compact,
        ),
        ROOMY(
            padding = R.dimen.widget_padding,
            nameText = R.dimen.widget_name_text,
            footerText = R.dimen.widget_footer_text,
        ),
    }

    private data class Variant(val detail: Detail, val density: Density)

    /**
     * The cell sizes each variant is built for, in dp of *content box* — what
     * `AppWidgetHostView` measures after the launcher's own inset, not the nominal
     * cell.
     *
     * Three things decide these numbers, and each of them is easy to get wrong.
     *
     * The platform does not pick the largest variant that fits. `findBestFitLayout`
     * takes every entry that fits inside the cell and keeps the one nearest it by
     * squared distance, so a change here has to be checked against real sizes rather
     * than reasoned about as a ladder. The fit table lives in `WidgetVariantSizeTest`,
     * which asserts it against the platform's own selection code.
     *
     * The heights are derived, not guessed. A label costs its line height — about 12dp
     * at 10sp, 15dp at 12sp — the root costs twice its padding, and whatever remains is
     * the box `WidgetValue`'s uniform auto-sizing searches in. 68dp of cell leaves 32dp
     * for the value once a compact name and footer are paid for, which lands the value
     * near 26sp: the smallest size at which three rows still read as a widget rather
     * than as a squeeze. The previous set asked for 110dp before showing the footer,
     * which is more than a one-row cell has ever offered, so the target date was
     * unreachable at every 1-row size — see issue #11 and the sizes in `DeviceSpec`.
     *
     * The widths track the provider's own `minWidth` of 110dp rather than a guess at
     * how wide two cells are, so width cannot demote a legally-sized widget. `ROOMY` is
     * the exception: a one-cell-wide column has the height for three rows but not the
     * width for a 12sp date, so it stays compact and keeps the room for the text.
     */
    private val BREAKPOINTS = listOf(
        SizeF(110f, 40f) to Variant(Detail.VALUE, Density.COMPACT),
        SizeF(110f, 50f) to Variant(Detail.VALUE_AND_NAME, Density.COMPACT),
        SizeF(110f, 68f) to Variant(Detail.EVERYTHING, Density.COMPACT),
        SizeF(130f, 110f) to Variant(Detail.EVERYTHING, Density.ROOMY),
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
        // Sampled once so every variant agrees to the millisecond.
        val elapsedRealtime = SystemClock.elapsedRealtime()

        return RemoteViews(
            BREAKPOINTS.associate { (size, variant) ->
                size to render(context, appWidgetId, timer, display, variant, elapsedRealtime)
            },
        )
    }

    private fun render(
        context: Context,
        appWidgetId: Int,
        timer: Timer,
        display: Display,
        variant: Variant,
        elapsedRealtime: Long,
    ): RemoteViews {
        val (detail, density) = variant
        val views = RemoteViews(context.packageName, layoutFor(timer))

        // The layouts already declare the roomy metrics, so only the compact band has
        // anything to change here — but both are applied explicitly rather than one of
        // them inheriting from the XML, because a variant whose spacing comes from
        // somewhere else is a variant that moves when that somewhere else does.
        //
        // Pixels rather than sp for the text, because the resource is already declared
        // in sp: getDimension has applied the display's density and the user's font
        // scale by the time it is read, so passing it on as sp would apply both twice.
        val resources = context.resources
        val padding = resources.getDimensionPixelSize(density.padding)
        views.setViewPadding(R.id.widget_root, padding, padding, padding, padding)
        // Deliberately not the value or the ticker: an explicit size on those switches
        // off the uniform auto-sizing their weighted slot exists to serve.
        views.setTextViewTextSize(
            R.id.widget_name,
            TypedValue.COMPLEX_UNIT_PX,
            resources.getDimension(density.nameText),
        )
        views.setTextViewTextSize(
            R.id.widget_footer,
            TypedValue.COMPLEX_UNIT_PX,
            resources.getDimension(density.footerText),
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
