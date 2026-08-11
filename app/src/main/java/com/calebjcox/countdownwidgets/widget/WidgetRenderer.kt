package com.calebjcox.countdownwidgets.widget

import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextPaint
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
 * whichever best fits the cell it was given. What that cannot answer is how large to
 * draw the text *inside* a variant, because a breakpoint is only the smallest cell that
 * selects it — see [metricsFor], and [build]'s `cellDp` for where the real one comes
 * from.
 */
object WidgetRenderer {

    /** Which rows to show, chosen by the launcher from the cell size. */
    private enum class Detail { VALUE, VALUE_AND_NAME, EVERYTHING }

    /**
     * How tightly the rows are packed. A separate axis from [Detail] because the two
     * answer different questions: whether there is room for a row at all, and how much
     * of the room left over should go to breathing space rather than to the value.
     *
     * Only the padding now. Each band used to carry a label size as well, which made
     * the band the answer to a question it cannot see — how large a caption has to be
     * to read — and pinned a 2x1 and a 3x1 at 10sp. [metricsFor] measures that instead.
     */
    private enum class Density(@DimenRes val padding: Int) {
        COMPACT(padding = R.dimen.widget_padding_compact),
        ROOMY(padding = R.dimen.widget_padding),
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
     * The heights are derived, not guessed. A label costs its line height — about 17dp
     * at the 13sp floor — the root costs twice its padding, and whatever remains is the
     * box `WidgetValue`'s uniform auto-sizing searches in. 68dp of cell leaves 22dp for
     * the value once a name and a footer are paid for: not much, but a one-row cell
     * that says what it is counting is worth more than a larger number that does not.
     * The previous set asked for 110dp before showing the footer, which is more than a
     * one-row cell has ever offered, so the target date was unreachable at every 1-row
     * size — see issue #11 and the sizes in `DeviceSpec`.
     *
     * The widths track the provider's own `minWidth` of 110dp rather than a guess at
     * how wide two cells are, so width cannot demote a legally-sized widget. `ROOMY` is
     * the exception: a one-cell-wide column has the height for three rows but not the
     * width to spend on padding, so it stays compact and keeps the room for the text.
     *
     * Two entries carry the same variant at different heights. That is not redundancy:
     * each one also sizes the rows to the cell it is for — see [metricsFor] — so the
     * pair is what lets a tall one-row cell spend its extra height on a bigger number
     * rather than on margin.
     */
    private val BREAKPOINTS = listOf(
        SizeF(110f, 40f) to Variant(Detail.VALUE, Density.COMPACT),
        SizeF(110f, 50f) to Variant(Detail.VALUE_AND_NAME, Density.COMPACT),
        SizeF(110f, 68f) to Variant(Detail.EVERYTHING, Density.COMPACT),
        SizeF(110f, 86f) to Variant(Detail.EVERYTHING, Density.COMPACT),
        SizeF(130f, 110f) to Variant(Detail.EVERYTHING, Density.ROOMY),
    )

    /**
     * @param cellDp the content box the launcher is actually drawing this widget in,
     *   when that is known — from `AppWidgetManager`'s options for a live widget, or
     *   from the layout a renderer is compositing into. Null falls back to each
     *   variant's own breakpoint, which is the smallest cell it can be handed and so
     *   sizes everything for the tightest case it will ever face.
     */
    fun build(
        context: Context,
        appWidgetId: Int,
        timer: Timer?,
        nowMillis: Long,
        zone: ZoneId,
        cellDp: SizeF? = null,
    ): RemoteViews {
        if (timer == null) return unconfigured(context, appWidgetId)

        val display = DurationMath.compute(nowMillis, zone, timer.spec)
        // Sampled once so every variant agrees to the millisecond.
        val elapsedRealtime = SystemClock.elapsedRealtime()

        return RemoteViews(
            BREAKPOINTS.associate { (size, variant) ->
                size to render(
                    context, appWidgetId, timer, display,
                    size, cellDp, variant, elapsedRealtime,
                )
            },
        )
    }

    private fun render(
        context: Context,
        appWidgetId: Int,
        timer: Timer,
        display: Display,
        size: SizeF,
        cellDp: SizeF?,
        variant: Variant,
        elapsedRealtime: Long,
    ): RemoteViews {
        val (detail, density) = variant
        val views = RemoteViews(context.packageName, layoutFor(timer))

        // Applied explicitly on every variant rather than left to the XML for the band
        // that happens to match it, because a variant whose spacing comes from somewhere
        // else is a variant that moves when that somewhere else does.
        val resources = context.resources
        val padding = resources.getDimensionPixelSize(density.padding)
        views.setViewPadding(R.id.widget_root, padding, padding, padding, padding)

        val head = Rendering.formatFields(
            values = display.staticValues,
            style = timer.labelStyle,
            allowEmpty = display.tailMillis != null,
        )
        val footer = targetSummary(context, timer, display)

        // A timer with no name shows no name row, whatever the variant asked for, so
        // everything below is measured against the rows that will actually be drawn.
        val showName = detail != Detail.VALUE && timer.name.isNotBlank()
        val showFooter = detail == Detail.EVERYTHING
        val text = Text(
            value = valueText(head, display.tailMillis),
            name = if (showName) timer.name else null,
            footer = if (showFooter) footer else null,
        )
        val metrics = metricsFor(resources, size, cellDp, density, text)

        // Pixels rather than sp for the labels, because the sizes were read from
        // resources declared in sp: getDimension has applied the display's density and
        // the user's font scale by the time they are used, so passing them on as sp
        // would apply both twice.
        //
        // Deliberately not the value or the ticker: an explicit text size on those
        // switches off the uniform auto-sizing their bounded slot exists to serve. What
        // they get instead is the slot itself.
        views.setTextViewTextSize(R.id.widget_name, TypedValue.COMPLEX_UNIT_PX, metrics.labelText)
        views.setTextViewTextSize(R.id.widget_footer, TypedValue.COMPLEX_UNIT_PX, metrics.labelText)
        setValueHeight(views, metrics.valueHeight)

        // Resolved rather than left to the layout: on the wallpaper the choice
        // depends on the wallpaper itself, which no resource qualifier can express.
        val palette = WidgetPalette.forTimer(context, timer)
        views.setTextColor(R.id.widget_name, palette.secondary)
        views.setTextColor(R.id.widget_value, palette.primary)
        views.setTextColor(R.id.widget_ticker, palette.primary)
        views.setTextColor(R.id.widget_footer, palette.secondary)

        views.setViewVisibility(R.id.widget_name, if (showName) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.widget_name, timer.name)

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

        views.setViewVisibility(R.id.widget_footer, if (showFooter) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.widget_footer, footer)

        views.setOnClickPendingIntent(
            R.id.widget_root,
            EditTimerActivity.widgetTapIntent(context, appWidgetId, timer.id),
        )
        return views
    }

    /** What the value row will read, exactly as [chronometerFormat] assembles it. */
    private fun valueText(head: String, tailMillis: Long?): String = when {
        tailMillis == null -> head
        head.isEmpty() -> Rendering.formatClock(tailMillis)
        else -> "$head  ${Rendering.formatClock(tailMillis)}"
    }

    /** The three rows' text, with null for a row this variant is not drawing. */
    private data class Text(val value: String, val name: String?, val footer: String?)

    /** What [render] has to set: the value's box, and the size both labels share. */
    private data class Metrics(val valueHeight: Int, val labelText: Float)

    /**
     * How large to draw each row on a cell of this size.
     *
     * Two decisions, in this order, and the order is the design.
     *
     * **The value's box is the smaller of what the cell leaves it and what its own text
     * can fill.** The second half is why the width has to be known. A `TextView` centres
     * its text in whatever box it is given, and the value is almost always limited by
     * how *wide* the cell is rather than how tall — a date spelled out in full stops
     * growing at 26sp on a phone-width cell however much height is going spare. So a box
     * measured from height alone came out taller than the text inside it, and the
     * surplus showed up as two bands of empty space: one between the name and the
     * number, one between the number and the date. Sizing the box to the line the text
     * will actually settle on leaves the surplus outside the rows, where the root's
     * centre gravity turns it into margin around all three. Height still bounds it,
     * because it must — a compact three-row cell has less room than the text would take,
     * and there the value is what gives.
     *
     * **The labels then grow into what is left.** `widget_label_text_min` is a floor,
     * not a size: it is the smallest a caption may be drawn and still be worth drawing,
     * and it is all a 2x1 or a 3x1 has room for. Where there is more, they grow a point
     * at a time while three things hold — the cell has the height, the label still fits
     * the width without ellipsizing, and it stays under [LABEL_SHARE] of the value. The
     * last one is what keeps the number the thing you read first. Growth is checked
     * against the value's *wanted* line, so a label can never take a point off the
     * number to spend on itself.
     *
     * Every figure here is measured rather than assumed, so all of it follows the user's
     * font scale.
     */
    private fun metricsFor(
        resources: Resources,
        breakpoint: SizeF,
        cellDp: SizeF?,
        density: Density,
        text: Text,
    ): Metrics {
        // Integer pixels throughout, and the same integers the framework will use: the
        // rows now fill the cell exactly, so a fraction of a pixel rounded the wrong way
        // is a fraction of a pixel of clipping.
        val scale = resources.displayMetrics.density
        val padding = resources.getDimensionPixelSize(density.padding)
        val cellHeight = ((cellDp?.height ?: breakpoint.height) * scale).toInt()
        val widthPx = cellDp?.let { (it.width * scale).toInt() - 2 * padding }

        val smallest = resources.getDimension(R.dimen.widget_value_text_min)
        val largest = resources.getDimension(R.dimen.widget_value_text_max)
        val wanted = if (widthPx == null) {
            largest
        } else {
            fittingTextSizePx(resources, text.value, widthPx.toFloat(), smallest, largest)
        }
        val wantedLine = lineHeightPx(wanted, bold = true)

        val floor = resources.getDimension(R.dimen.widget_label_text_min)
        val ceiling = maxOf(
            floor,
            minOf(resources.getDimension(R.dimen.widget_label_text_max), wanted * LABEL_SHARE),
        )
        val step = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            1f,
            resources.displayMetrics,
        )

        fun labels(size: Float): Int =
            (text.name?.let { lineHeightPx(size) } ?: 0) +
                (text.footer?.let { lineHeightPx(size) } ?: 0)

        fun fits(size: Float): Boolean {
            if (2 * padding + wantedLine + labels(size) > cellHeight) return false
            if (widthPx == null) return false
            val name = text.name?.let { measureTextPx(it, size) } ?: 0f
            val footer = text.footer?.let { measureTextPx(it, size) } ?: 0f
            return maxOf(name, footer) <= widthPx
        }

        var label = floor
        while (label + step <= ceiling && fits(label + step)) label += step

        val box = (cellHeight - 2 * padding - labels(label))
            .coerceIn(lineHeightPx(smallest, bold = true), wantedLine)
        return Metrics(box, label)
    }

    /**
     * How much of the value's size a label may reach. Two thirds is far enough to read
     * comfortably and near enough that the number is still what the eye lands on; above
     * about three quarters the three rows start to read as one paragraph.
     */
    private const val LABEL_SHARE = 0.66f

    /**
     * The size uniform auto-sizing will settle on for one line of [text] in [widthPx],
     * arrived at the same way `TextView` does: candidates a pixel-equivalent of 1sp
     * apart, largest first, and the first that fits wins.
     */
    private fun fittingTextSizePx(
        resources: Resources,
        text: String,
        widthPx: Float,
        smallestPx: Float,
        largestPx: Float,
    ): Float {
        if (text.isEmpty() || widthPx <= 0f) return largestPx
        val step = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            1f,
            resources.displayMetrics,
        )
        val paint = valuePaint()
        var candidate = largestPx
        while (candidate > smallestPx) {
            paint.textSize = candidate
            if (paint.measureText(text) <= widthPx) return candidate
            candidate -= step
        }
        return smallestPx
    }

    /** How wide one line of [text] is at [textSizePx], in a label's own typeface. */
    private fun measureTextPx(text: String, textSizePx: Float): Float =
        TextPaint().apply { this.textSize = textSizePx }.measureText(text)

    /**
     * The height a single line of text at [textSizePx] occupies, font padding included
     * — which is what a `wrap_content` `TextView` measures to, and what the auto-sizing
     * search compares its own candidates against.
     *
     * From the integer metrics rather than the float ones, because those are what
     * `StaticLayout` measures a line with: the fractional pair rounds the friendly way
     * and would leave the rows a pixel taller than this function promised.
     */
    private fun lineHeightPx(textSizePx: Float, bold: Boolean = false): Int {
        val paint = if (bold) valuePaint() else TextPaint()
        paint.textSize = textSizePx
        val metrics = paint.fontMetricsInt
        return metrics.bottom - metrics.top
    }

    /** Matched to `WidgetValue`: the same weight and the same tabular figures. */
    private fun valuePaint() = TextPaint().apply {
        typeface = Typeface.DEFAULT_BOLD
        fontFeatureSettings = "tnum"
    }

    /** Both rows that can hold the value, so whichever one is showing is sized. */
    private fun setValueHeight(views: RemoteViews, heightPx: Int) {
        views.setViewLayoutHeight(R.id.widget_value, heightPx.toFloat(), TypedValue.COMPLEX_UNIT_PX)
        views.setViewLayoutHeight(R.id.widget_ticker, heightPx.toFloat(), TypedValue.COMPLEX_UNIT_PX)
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
     *
     * One size rather than the whole [BREAKPOINTS] ladder, because there is nothing to
     * vary: one row of text, the same at every size. It is built to the smallest entry
     * so the prompt fits a cell at the provider's own minimum, which is the one place
     * an unconfigured widget is most likely to be sitting.
     */
    private fun unconfigured(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_timer)
        val (size, variant) = BREAKPOINTS.first()
        val resources = context.resources
        val padding = resources.getDimensionPixelSize(variant.density.padding)
        views.setViewPadding(R.id.widget_root, padding, padding, padding, padding)
        val metrics = metricsFor(
            resources = resources,
            breakpoint = size,
            cellDp = null,
            density = variant.density,
            text = Text(value = "", name = null, footer = null),
        )
        setValueHeight(views, metrics.valueHeight)
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
