package com.calebjcox.countdownwidgets.widget

import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
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
import kotlin.math.ceil
import kotlin.math.floor

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
 * Size handling is declarative. Rather than reacting to resize callbacks, every variant
 * is handed to the platform at once — the [BREAKPOINTS] ladder and a rung for each cell
 * the host reported, see [variantsFor] — and the launcher picks whichever best fits the
 * cell it was given. What that cannot answer is how large to draw the text *inside* a
 * variant, because a breakpoint is only the smallest cell that selects it — see
 * [metricsFor], and [build]'s `cells` for where the real one comes from.
 *
 * Declarative all the way down, which means *per variant*. A launcher reports every
 * cell it might draw this widget in — portrait and landscape both — and then switches
 * between them without asking the app again, because rotating changes nothing about the
 * widget's options. So one cell cannot size the whole `RemoteViews`: each variant is
 * sized for the cell that will select *it*, and the same object is then correct in
 * either orientation. See [sizingFor].
 */
object WidgetRenderer {

    /** Which rows to show, chosen by the launcher from the cell size. */
    private enum class Detail { VALUE, VALUE_AND_NAME, EVERYTHING }

    /**
     * How tightly the rows are packed. A separate axis from [Detail] because the two
     * answer different questions: whether there is room for a row at all, and how much
     * of the room left over should go to breathing space rather than to the value.
     *
     * Only the padding. A label size per band would make the band the answer to a
     * question it cannot see — how large a caption has to be to read — so [metricsFor]
     * measures that against the real cell instead.
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
     * The heights are derived, not guessed. A label costs its line height — about 16dp
     * at the 12sp floor — the root costs twice its padding, and whatever remains is the
     * box the value's own search works in. 68dp of cell leaves 24dp for the value once
     * a name and a footer are paid for: not much, but a one-row cell
     * that says what it is counting is worth more than a larger number that does not.
     * A footer height above what a one-row cell offers makes the target date
     * unreachable at every 1-row size — see issue #11 and the sizes in `DeviceSpec`.
     *
     * The widths say how much room a row needs to be worth drawing, not what the
     * provider's `minWidth` happens to be. The provider resizes down to a single cell,
     * so 110dp is not the narrowest cell this can be handed and a width tracking it
     * would only refuse to answer for the sizes below.
     *
     * A caption is the row width decides. 92dp is where a name reads: it leaves 80dp
     * inside the compact padding, which is a dozen characters at the 12sp floor. The date
     * needs 110dp, being the longest line the widget draws — "until Sep 9, 2026" is most
     * of 100dp before it is worth printing at all. Below 92 there is only the number,
     * which is the one thing a cell that narrow can say legibly. On a tablet none of this
     * bites: a single cell there is 105 to 130dp, wider than a phone's 2x1, so a 1x1
     * keeps its name and often its date.
     *
     * `ROOMY` is the exception to reading the widths as row requirements: a one-cell-wide
     * column has the height for three rows but not the width to spend on padding, so it
     * stays compact and keeps the room for the text.
     *
     * Several entries carry the same variant at different sizes. That is not redundancy:
     * each one also sizes the rows to the cell it is for — see [metricsFor] — so the
     * entry is what lets a tall cell spend its extra height on a bigger number rather
     * than on margin.
     *
     * The five above 130x110 are what a large cell falls back on before the host has
     * reported anything, which is the only time a cell that big is sized by the ladder at
     * all — see [variantsFor], which gives a reported cell a rung of its own. Every cell
     * bigger than the whole of this widget's layout fits every entry, so the nearest one
     * wins, and with nothing above 130x110 a widget the height of the screen would be
     * sized for a rung a fifth of its height until its first redraw. They differ in
     * *shape* rather than only in size, because that is what the cells they stand in for
     * do: 300x130 and 300x220 for a wide, short landscape cell, 300x320 and 300x450 for a
     * tall portrait one, 130x220 for a one-column tower. None of them can be selected by
     * a cell smaller than itself, so nothing below 130x110 moves.
     */
    private val BREAKPOINTS = listOf(
        SizeF(56f, 40f) to Variant(Detail.VALUE, Density.COMPACT),
        SizeF(56f, 72f) to Variant(Detail.VALUE, Density.COMPACT),
        SizeF(92f, 50f) to Variant(Detail.VALUE_AND_NAME, Density.COMPACT),
        SizeF(110f, 40f) to Variant(Detail.VALUE, Density.COMPACT),
        SizeF(110f, 50f) to Variant(Detail.VALUE_AND_NAME, Density.COMPACT),
        SizeF(110f, 68f) to Variant(Detail.EVERYTHING, Density.COMPACT),
        SizeF(110f, 86f) to Variant(Detail.EVERYTHING, Density.COMPACT),
        SizeF(130f, 110f) to Variant(Detail.EVERYTHING, Density.ROOMY),
        SizeF(130f, 220f) to Variant(Detail.EVERYTHING, Density.ROOMY),
        SizeF(300f, 130f) to Variant(Detail.EVERYTHING, Density.ROOMY),
        SizeF(300f, 220f) to Variant(Detail.EVERYTHING, Density.ROOMY),
        SizeF(300f, 320f) to Variant(Detail.EVERYTHING, Density.ROOMY),
        SizeF(300f, 450f) to Variant(Detail.EVERYTHING, Density.ROOMY),
    )

    /**
     * @param cells every content box the launcher might draw this widget in, when that
     *   is known — from `AppWidgetManager`'s options for a live widget, or from the
     *   layout a renderer is compositing into. Usually two: the portrait cell and the
     *   landscape one. A variant no reported cell selects falls back to its own
     *   breakpoint, which is the smallest cell it can be handed and so sizes it for the
     *   tightest case it will ever face.
     */
    fun build(
        context: Context,
        appWidgetId: Int,
        timer: Timer?,
        nowMillis: Long,
        zone: ZoneId,
        cells: List<SizeF> = emptyList(),
    ): RemoteViews {
        if (timer == null) return unconfigured(context, appWidgetId, tightest(cells))

        val display = DurationMath.compute(nowMillis, zone, timer.spec)
        // Sampled once so every variant agrees to the millisecond.
        val elapsedRealtime = SystemClock.elapsedRealtime()
        val variants = variantsFor(cells)
        val sizing = sizingFor(cells, variants.keys)

        return RemoteViews(
            variants.entries.associate { (size, variant) ->
                size to render(
                    context, appWidgetId, timer, display,
                    size, sizing[size], variant, elapsedRealtime,
                )
            },
        )
    }

    /**
     * The sizes the launcher chooses between and the variant filed under each: the
     * [BREAKPOINTS] ladder, plus a rung of its own for every cell the host reported.
     *
     * A rung keyed at exactly a reported cell is one that cell is certain to select —
     * nothing can be nearer than a squared distance of zero — and certain to be the only
     * one selecting it. That is the whole of what it buys, and what it buys is the other
     * half of [sizingFor]: two cells that land on the same rung have to share the text
     * size it was built with, which is the smaller of them on each axis, so a widget
     * drawn the height of the screen in portrait is sized for the height of the landscape
     * row it is *not* being drawn in. The ladder alone cannot keep them apart, because
     * every cell larger than the whole layout fits every rung and the nearest one wins:
     * a full-screen portrait cell and a full-screen landscape cell are both nearest to
     * whatever the largest rung happens to be, and on a tablet, where the two
     * orientations are nearer the same size than a phone's, they land together whatever
     * shapes the ladder carries. A cell keyed as itself cannot land with anything.
     *
     * The rung carries the variant the cell would have selected from the ladder, so which
     * rows are drawn is the ladder's decision as before and only the sizing is exact.
     *
     * Largest first, because the rungs that matter are the ones a cap could cost: the
     * cells that collide are the large ones, and the largest has the most height to lose
     * by being sized for another.
     */
    private fun variantsFor(cells: List<SizeF>): Map<SizeF, Variant> {
        val ladder = BREAKPOINTS.toMap()
        val reported = cells
            .filter { it.width > 0f && it.height > 0f }
            .distinct()
            .filterNot { it in ladder }
            .sortedByDescending { it.width * it.height }
            .take(MAX_VARIANTS - ladder.size)
        return ladder + reported.associateWith { ladder.getValue(bestFitBreakpoint(it)) }
    }

    /**
     * How many entries a size map may hold, which is the platform's number rather than
     * ours: `RemoteViews(Map)` throws above sixteen. It is what limits how many cells
     * [variantsFor] can key exactly — [BREAKPOINTS] takes thirteen of it — and a host
     * reporting more than the rest leaves the remainder to [sizingFor]'s last resort.
     */
    private const val MAX_VARIANTS = 16

    /**
     * Which reported cell each variant should size its text for, keyed by the size it is
     * filed under.
     *
     * A variant is only ever drawn in a cell the launcher picked it for, so the cell to
     * measure against is the one whose selection lands on that variant — which is a
     * question only [bestFit] can answer, because the platform's rule is not "the largest
     * that fits".
     *
     * This is the whole fix for text that comes out tiny after the phone has been in
     * landscape. The options bundle reports both orientations at once and does not
     * change when the device rotates, so `onAppWidgetOptionsChanged` does not fire and
     * the app is never asked to redraw. Sizing every variant from whichever cell was
     * current at build time therefore froze the *other* orientation's text at the wrong
     * size until the next unrelated update — a phone's landscape row is barely half the
     * height of its portrait one, so a widget built while an app was in landscape came
     * back to the home screen with its number at the 11sp floor. Sized per variant, one
     * `RemoteViews` is right in both.
     *
     * Two cells can land on the same variant. Then it takes the smaller of them on each
     * axis, because the text has to fit whichever one the launcher hands it — and the
     * corner of a tall cell and a wide one is a cell neither of them is, so a widget
     * sized there is smaller than both. That is a last resort rather than a design, and
     * it is reached only by a host reporting more cells than [variantsFor] has room to
     * key: every cell that gets a rung of its own is the only cell that can select it.
     *
     * @param entries the sizes the launcher is choosing between, which is [variantsFor]'s
     *   answer rather than [BREAKPOINTS] — a cell has to be matched against the map it
     *   will really be selected from.
     */
    private fun sizingFor(cells: List<SizeF>, entries: Collection<SizeF>): Map<SizeF, SizeF> {
        val sizing = mutableMapOf<SizeF, SizeF>()
        for (cell in cells) {
            if (cell.width <= 0f || cell.height <= 0f) continue
            val entry = bestFit(cell, entries)
            val known = sizing[entry]
            sizing[entry] = if (known == null) {
                cell
            } else {
                SizeF(minOf(known.width, cell.width), minOf(known.height, cell.height))
            }
        }
        return sizing
    }

    /**
     * The smallest cell on each axis of everything the launcher reported, or null if it
     * reported nothing usable.
     *
     * The counterpart to [sizingFor] for the one `RemoteViews` that is not sized per
     * variant: [unconfigured] is a single object serving every orientation, so it cannot
     * pick a cell per breakpoint and has to fit the tightest of them instead. The corners
     * of the two need not be a cell anyone will hand out, which is the point — it is a
     * bound, not a measurement.
     */
    private fun tightest(cells: List<SizeF>): SizeF? = cells
        .filter { it.width > 0f && it.height > 0f }
        .takeIf { it.isNotEmpty() }
        ?.let { usable ->
            SizeF(usable.minOf { it.width }, usable.minOf { it.height })
        }

    /**
     * The ladder rung whose variant a launcher would draw in a cell of this size — which
     * is what [variantsFor] asks to decide what a rung of the cell's own should carry,
     * and what the selection rule is pinned against.
     */
    internal fun bestFitBreakpoint(cell: SizeF): SizeF = bestFit(cell, breakpointSizes)

    /**
     * The entry of [entries] a launcher will draw in a cell of this size.
     *
     * A transcription of `RemoteViews.findBestFitLayout`, down to the ceiling in the fit
     * test and the strict `<` that leaves the earliest of equally distant candidates
     * winning: among the entries that fit, the one nearest the cell by squared distance,
     * and the smallest entry by area when nothing fits at all. Reimplemented rather than
     * reflected into because this runs in the app, and pinned against the platform's own
     * selection by `WidgetOrientationTest` so the copy cannot drift from the original.
     */
    private fun bestFit(cell: SizeF, entries: Collection<SizeF>): SizeF {
        var best: SizeF? = null
        var bestSquareDistance = Float.MAX_VALUE
        for (size in entries) {
            if (!fitsIn(size, cell)) continue
            val squareDistance = squareDistance(size, cell)
            if (best == null || squareDistance < bestSquareDistance) {
                best = size
                bestSquareDistance = squareDistance
            }
        }
        return best ?: entries.minBy { it.width * it.height }
    }

    /** The breakpoint sizes, in declaration order. Shared with the tests that pin them. */
    internal val breakpointSizes: List<SizeF> = BREAKPOINTS.map { it.first }

    private fun fitsIn(size: SizeF, bounds: SizeF): Boolean =
        ceil(size.width.toDouble()) <= ceil(bounds.width.toDouble()) &&
            ceil(size.height.toDouble()) <= ceil(bounds.height.toDouble())

    private fun squareDistance(size: SizeF, bounds: SizeF): Float {
        val dx = size.width - bounds.width
        val dy = size.height - bounds.height
        return dx * dx + dy * dy
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
        views.setViewPadding(android.R.id.background, padding, padding, padding, padding)

        val head = Rendering.formatFields(
            values = display.staticValues,
            style = timer.labelStyle,
            allowEmpty = display.tailMillis != null,
        )
        val footer = targetSummary(context, timer, display)

        // A row the timer has switched off, or a name row on a timer with no name,
        // is not drawn whatever the variant asked for — so everything below is
        // measured against the rows that will actually be drawn, and the value grows
        // into the room a dropped row leaves rather than centring on a gap.
        val showName = detail != Detail.VALUE && timer.showName && timer.name.isNotBlank()
        val showFooter = detail == Detail.EVERYTHING && timer.showTarget
        val text = Text(
            value = valueText(head, display.tailMillis),
            name = if (showName) timer.name else null,
            footer = if (showFooter) footer else null,
        )
        val metrics = metricsFor(
            resources = resources,
            breakpoint = size,
            cellDp = cellDp,
            density = density,
            text = text,
            maxValueLines = if (timer.wrapValue) maxValueLines(text.value) else 1,
        )

        // Pixels rather than sp throughout, because the sizes were read from resources
        // declared in sp: getDimension has applied the display's density and the user's
        // font scale by the time they are used, so passing them on as sp would apply
        // both twice.
        //
        // The value is set and the ticker is not, which is the one asymmetry between the
        // two rows: a `Chronometer` rewrites its own text without this app, so it keeps
        // the auto-sizing that can follow it. See the WidgetValue styles.
        views.setTextViewTextSize(R.id.widget_name, TypedValue.COMPLEX_UNIT_PX, metrics.labelText)
        views.setTextViewTextSize(R.id.widget_footer, TypedValue.COMPLEX_UNIT_PX, metrics.labelText)
        views.setTextViewTextSize(R.id.widget_value, TypedValue.COMPLEX_UNIT_PX, metrics.valueText)
        setValueBox(views, metrics.valueHeight, metrics.valueLines)

        // Only on the wallpaper, where the choice depends on the wallpaper itself and
        // no resource qualifier can express it. On its own panel the layout's -night
        // pair is not merely sufficient, it is the only thing that stays correct: a
        // colour set from here is resolved in this process, at build time, and then
        // frozen into the RemoteViews the launcher keeps, while the panel behind it is
        // resolved from the same resources by the launcher every time it inflates them.
        // Switch the phone to dark mode and the two part company — the panel turns
        // dark, the text keeps the light tone it was built with — and it stays that way
        // until something redraws the widget, which for a day-precision timer is the
        // next midnight. Leaving the pair to the resource system is what keeps it
        // together, and it costs a widget on a panel nothing: the two colours agree by
        // construction because they come from the same qualifier.
        if (!timer.showBackground) {
            val palette = WidgetPalette.forTimer(context, timer)
            views.setTextColor(R.id.widget_name, palette.secondary)
            views.setTextColor(R.id.widget_value, palette.primary)
            views.setTextColor(R.id.widget_ticker, palette.primary)
            views.setTextColor(R.id.widget_footer, palette.secondary)
        }

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
            android.R.id.background,
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

    /**
     * What [render] has to set: the value's box, how many lines it may use inside it and
     * how large to draw it, and the size both labels share.
     */
    private data class Metrics(
        val valueHeight: Int,
        val valueLines: Int,
        val valueText: Float,
        val labelText: Float,
    )

    /**
     * How large to draw each row on a cell of this size.
     *
     * Three decisions, in this order, and the order is the design.
     *
     * **The value grows until the cell stops it, and the cell is the only thing that
     * does.** The sizes in resources are a floor and a sanity bound; between them the
     * answer comes from the width of the cell and from how much of its height is left
     * once the labels have taken their share. That share is [LABEL_SHARE] of whatever
     * size the value is asking for, so the three rows scale together and a widget
     * dragged out to twice the size draws text twice as large rather than the same text
     * in the middle of more space. Reserving a proportion rather than a fixed size is
     * what keeps the caption from being squeezed to its floor by a number that has taken
     * the whole cell — and reserving it from the *count* of labels rather than from
     * their text is what keeps the value's size a property of the value, so a long name
     * cannot take a point off the number.
     *
     * **It takes only as much of the height as its text fills.** That half is why the
     * width has to be known. A `TextView` centres its text in whatever box it is given,
     * and the value is often limited by how *wide* the cell is rather than how tall — a
     * date spelled out in full stops growing at 26sp on a phone-width cell however much
     * height is going spare. A box measured from height alone is therefore taller than
     * the text inside it, and the surplus lands as two bands of empty space: one between
     * the name and the number, one between the number and the date. Sizing the box to
     * what the text settles on leaves the surplus outside the rows, where the root's
     * centre gravity turns it into margin around all three.
     *
     * [valueBox] also decides how many lines to spend the height on — one, unless the
     * timer allows more and more makes the number bigger.
     *
     * **Then the labels take what the value did not.** `widget_label_text_min` is a
     * floor, not a size: it is the smallest a caption may be drawn and still be worth
     * drawing, and it is all a 2x1 or a 3x1 has room for. Where the value came back
     * smaller than its share of the cell — which is most of the time, because width
     * usually stops it first — they grow a point at a time into the difference while
     * three things hold: the height is really there, the label still fits the width
     * without ellipsizing, and it stays under [LABEL_MAX_SHARE] of the value. The last
     * one is what keeps the number the thing you read first.
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
        maxValueLines: Int,
    ): Metrics {
        // Integer pixels throughout, and the same integers the framework will use: the
        // rows now fill the cell exactly, so a fraction of a pixel rounded the wrong way
        // is a fraction of a pixel of clipping.
        val scale = resources.displayMetrics.density
        val padding = resources.getDimensionPixelSize(density.padding)
        val cellHeight = ((cellDp?.height ?: breakpoint.height) * scale).toInt()
        val cellWidth = ((cellDp?.width ?: breakpoint.width) * scale).toInt()
        val widthPx = cellWidth - 2 * padding
        val availablePx = cellHeight - 2 * padding

        val smallest = resources.getDimension(R.dimen.widget_value_text_min)
        val largest = resources.getDimension(R.dimen.widget_value_text_max)
        val floor = resources.getDimension(R.dimen.widget_label_text_min)
        val labelCount = listOfNotNull(text.name, text.footer).size

        // The height the labels hold back while the value is drawn at this size. Their
        // share of it, or the floor — a caption below that is not worth the row, so the
        // room for it is reserved whether the value's size has earned it or not.
        fun reserved(valueSize: Float): Int =
            labelCount * lineHeightPx(maxOf(floor, valueSize * LABEL_SHARE))

        // No text to measure. Nothing below has an answer for that, and neither does a
        // widget: hand back a row at the floor and let a caller with something to draw
        // ask again.
        if (text.value.isEmpty()) {
            val height = (availablePx - labelCount * lineHeightPx(floor))
                .coerceIn(lineHeightPx(smallest, bold = true), lineHeightPx(largest, bold = true))
            return Metrics(height, 1, smallest, floor)
        }

        val step = spStep(resources)
        val box = valueBox(
            resources = resources,
            text = text.value,
            widthPx = widthPx,
            availablePx = availablePx,
            reserved = ::reserved,
            maxLines = maxValueLines,
            smallestPx = smallest,
            largestPx = largest,
            // Per line count rather than once for the most it may use: a value allowed
            // six lines is allowed a size six lines' worth larger, and starting the
            // one-line search from there is a hundred layouts spent ruling that out.
            ceilingFor = { lines ->
                valueCeiling(
                    text = text.value,
                    widthPx = widthPx,
                    availablePx = availablePx,
                    maxLines = lines,
                    labelCount = labelCount,
                    smallestPx = smallest,
                    largestPx = largest,
                    step = step,
                )
            },
        )
        val label = labelSize(
            resources = resources,
            text = text,
            widthPx = widthPx,
            availablePx = availablePx - box.height,
            valueSize = box.textSize,
            floorPx = floor,
            labelCount = labelCount,
        )
        return Metrics(box.height, box.lines, box.textSize, label)
    }

    /**
     * The size both labels are drawn at: up from the floor while the height left over by
     * [valueSize]'s own row is really there, the text still fits [widthPx] without an
     * ellipsis, and the caption stays under [LABEL_MAX_SHARE] of the number beside it.
     *
     * One size for both rows rather than one each, so the two read as a pair — which
     * means the longer of them decides, and a long name holds a short date back to its
     * own size rather than the two disagreeing down the middle of the widget.
     */
    private fun labelSize(
        resources: Resources,
        text: Text,
        widthPx: Int,
        availablePx: Int,
        valueSize: Float,
        floorPx: Float,
        labelCount: Int,
    ): Float {
        if (labelCount == 0) return floorPx
        val step = spStep(resources)
        val ceiling = maxOf(floorPx, valueSize * LABEL_MAX_SHARE)
        val rows = listOfNotNull(text.name, text.footer)

        fun fits(size: Float): Boolean =
            labelCount * lineHeightPx(size) <= availablePx &&
                rows.all { measureTextPx(it, size) <= widthPx }

        var size = floorPx
        while (size + step <= ceiling && fits(size + step)) size += step
        return size
    }

    /**
     * The largest value size [valueBox]'s search need ever start from on this cell.
     *
     * Two bounds, each a necessary condition rather than a fit: the widest the text could
     * be spread over [maxLines] lines, and the tallest one line of it plus the labels'
     * share of the height can be. Both quantities scale with the text size, so one
     * measurement at the top of the range gives the ratio and the bound follows without a
     * search of its own.
     *
     * This buys speed rather than correctness — the answer is at or below it either way.
     * What it avoids is laying out a hundred and fifty sizes that cannot fit,
     * `widget_value_text_max` being a sanity bound rather than a size any cell reaches.
     * Deliberately generous, because an over-estimate costs a few `StaticLayout`s and an
     * under-estimate is a value drawn smaller than it should be.
     *
     * Rounded down onto the candidates the platform's own auto-sizing will offer — the
     * minimum plus whole steps of the granularity — so that the search below starts on
     * that ladder and every size it goes on to consider is one the launcher can actually
     * settle on.
     */
    private fun valueCeiling(
        text: String,
        widthPx: Int,
        availablePx: Int,
        maxLines: Int,
        labelCount: Int,
        smallestPx: Float,
        largestPx: Float,
        step: Float,
    ): Float {
        val paint = valuePaint().apply { textSize = largestPx }
        // Whitespace a line falls on is not drawn and so does not count against the
        // width. Which runs a break could land in depends on the size, so take all of
        // the text's whitespace off rather than work that out: what is left is below
        // what any arrangement of lines has to fit.
        val ink = paint.measureText(text) -
            paint.measureText(text.filter { it.isWhitespace() })
        val byWidth = if (ink <= 0f) largestPx else largestPx * maxLines * widthPx / ink
        // The floor under a label is left out on purpose: without it the reservation is
        // smaller, and a smaller reservation can only make this bound larger.
        val rows = lineHeightPx(largestPx, bold = true) +
            labelCount * lineHeightPx(largestPx * LABEL_SHARE)
        val byHeight = if (rows <= 0) largestPx else largestPx * availablePx / rows
        // Two steps of slack for the rounding in integer font metrics, which is the one
        // place the linear scaling above is not exact.
        val bound = (minOf(largestPx, byWidth, byHeight) + 2 * step).coerceIn(smallestPx, largestPx)
        return smallestPx + floor((bound - smallestPx) / step) * step
    }

    /** A pixel-equivalent of 1sp: the granularity every text search here steps by. */
    private fun spStep(resources: Resources): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, resources.displayMetrics)

    /**
     * How many lines the value is drawn on, how tall that leaves its box, and the size
     * it is drawn at — which the labels beside it are then measured against.
     *
     * The height is not the room available but the room *taken*, so that a cell with
     * more height than the text needs turns the difference into margin around all three
     * rows rather than into a gap between them.
     */
    private data class ValueBox(val lines: Int, val height: Int, val textSize: Float)

    /**
     * Spends [availablePx] of height on the value — less whatever [reserved] holds back
     * for the labels at each size considered — on one line, or on up to [maxLines] of
     * them where that draws the number larger.
     *
     * Wrapping is not what a narrow cell does, it is what a narrow cell does *instead of
     * giving up*. One line of `2d 16h 46m` across a single home-screen cell is 11sp — the
     * floor, the size the auto-sizing stops at because there is nothing below it — while
     * three lines of the same string are around 18sp and legible from where a phone sits
     * on a desk. Where a line break buys nothing the search says so on its own: a 2x1
     * has the width for its value and not the height for a second line, so every
     * candidate above one line comes back the same size or smaller and one line wins.
     * That is the whole reason this can be on by default.
     *
     * A line break has to earn its place twice over: an extra line is taken only where it
     * draws the value larger, or where it stops the value being cut off. The second half
     * is not a special case of the first. Once the auto-sizing is at its floor and the
     * text still needs a second line, a `TextView` held to one draws that line and drops
     * the rest — no ellipsis, no sign anything is missing — so a 2x1 with unit names
     * spelled out has been reading `2 years, 0 months, 8` for the whole of a countdown
     * that says `2 years, 0 months, 8 days`. One line and two are both the floor there,
     * and only one of them is the value. Everywhere neither holds, the fewest lines win.
     */
    private fun valueBox(
        resources: Resources,
        text: String,
        widthPx: Int,
        availablePx: Int,
        reserved: (Float) -> Int,
        maxLines: Int,
        smallestPx: Float,
        largestPx: Float,
        ceilingFor: (Int) -> Float,
    ): ValueBox {
        val floor = lineHeightPx(smallestPx, bold = true)

        var best =
            fitted(resources, text, widthPx, availablePx, reserved, 1, smallestPx, ceilingFor(1))
        // Two cases where nothing above one line can win, both worth not measuring: a
        // value already at the resource ceiling has nowhere larger to go and is whole by
        // definition, and a value with no space in it has nowhere to break, so
        // [breaksAtSpaces] would turn down every wrapped candidate in turn.
        if (best.textSize < largestPx && text.any { it.isWhitespace() }) {
            for (lines in 2..maxLines) {
                val candidate = fitted(
                    resources, text, widthPx, availablePx, reserved, lines, smallestPx,
                    ceilingFor(lines),
                )
                val bigger = candidate.textSize > best.textSize
                val rescued =
                    candidate.textSize == best.textSize && candidate.whole && !best.whole
                if (bigger || rescued) best = candidate
            }
        }
        val ceiling = maxOf(floor, availablePx - reserved(best.textSize))
        return ValueBox(best.lines, best.height.coerceIn(floor, ceiling), best.textSize)
    }

    /**
     * How far [text] may wrap when the timer allows it: a line for each thing it can be
     * broken into, which is one per run of non-space characters.
     *
     * Not a policy so much as the arithmetic of [breaksAtSpaces]. Every line has to end
     * at a space, so a value can never occupy more lines than it has words, and any
     * smaller bound is a cell being told to stop short of what its height could carry.
     * `2d 16h 46m` gets three, one per unit; `11 months, 3 weeks, 4 days` gets six,
     * because the spelled-out form is two words per unit and a number above its own unit
     * name is a fine place for a small widget to break; `25d` gets one and never
     * consults any of this.
     *
     * Height is what really decides, and on all but the tallest cells it decides long
     * before this does — see [valueBox], which takes the extra line only where the extra
     * line draws the number larger.
     */
    private fun maxValueLines(text: String): Int =
        text.split(WHITESPACE).count { it.isNotEmpty() }.coerceAtLeast(1)

    /** Any run of it, so the two spaces before a ticking clock count once. */
    private val WHITESPACE = Regex("\\s+")

    /**
     * One candidate for the value row: the size it settles on, what that occupies, and
     * whether all of the text is still there — [whole] is false where even the floor
     * needs more lines than it is allowed, which is the state a `TextView` with no
     * ellipsis expresses by drawing the lines it may and discarding the rest in
     * silence.
     *
     * [lines] is the bound the candidate was searched under rather than the count it
     * came back with, because the bound is what the view has to be told: hand
     * `setMaxLines` anything smaller and the same search inside the launcher would
     * settle somewhere else.
     */
    private data class Fit(
        val lines: Int,
        val textSize: Float,
        val height: Int,
        val whole: Boolean,
    )

    /**
     * The size the value settles on for [text] given [maxLines] lines of a [widthPx] box
     * inside [availablePx] of height, and the height the result occupies.
     *
     * Deliberately the same search `TextView.autoSizeText` runs, laying each candidate
     * out with the same unbounded `StaticLayout` and rejecting it on the same two counts
     * — too many lines, or too tall — with [breaksAtSpaces] added as a third of our own.
     * That third rule is why the search is here rather than left to the view: it is the
     * one a box cannot express, so a `TextView` searching the same range would answer
     * differently and answer wrong. Running it here also settles the box, which has to
     * be the height of the text that will be in it for the rows to stay together.
     *
     * The height a candidate is measured against is the cell's less what [reserved]
     * holds back for the labels at *that* size, because their size follows the value's:
     * a taller number is one with taller captions either side of it, and both have to
     * come out of the same cell.
     */
    private fun fitted(
        resources: Resources,
        text: String,
        widthPx: Int,
        availablePx: Int,
        reserved: (Float) -> Int,
        maxLines: Int,
        smallestPx: Float,
        largestPx: Float,
    ): Fit {
        val step = spStep(resources)
        var candidate = largestPx
        while (candidate > smallestPx) {
            val layout = valueLayout(text, candidate, widthPx)
            if (layout.lineCount <= maxLines &&
                layout.height + reserved(candidate) <= availablePx &&
                breaksAtSpaces(layout, text)
            ) {
                return Fit(maxLines, candidate, layout.height, whole = true)
            }
            candidate -= step
        }
        // Nothing fits, so the floor is what the launcher will draw — it is where its
        // auto-sizing stops too. The height is only of the lines that will be *drawn*:
        // the layout runs on past [maxLines] and the view shows the first of them, so
        // measuring the whole of it would leave the box tall enough for text nobody can
        // see, which is the gap between the rows this file exists to keep shut.
        val layout = valueLayout(text, smallestPx, widthPx)
        val drawn = minOf(layout.lineCount, maxLines)
        return Fit(
            lines = maxLines,
            textSize = smallestPx,
            height = layout.getLineTop(drawn),
            whole = layout.lineCount <= maxLines,
        )
    }

    /**
     * Whether every line of [layout] ends where a reader would end it.
     *
     * A third condition on a candidate, and the one that is about the value rather than
     * about the box. `StaticLayout` breaks between words where it can and *inside* one
     * where it must, which is right for prose and wrong for this: `25d` on a tall
     * one-cell widget has no space to break at, so the largest size that "fits" three
     * lines is one that fits `25` on the first and `d` on the second. Rejecting the size
     * instead of the wrap is what sends the search back down to where `25d` fits a line
     * whole.
     *
     * A space is a break either way, so this says nothing about a number and a unit
     * *name*: `3` above `weeks,` is a line ending where a reader would end it, and on a
     * cell narrow enough to be asking the question it is the break that keeps the text
     * large. Only the abbreviated form makes the two one word, and that is the font's
     * doing rather than a rule here.
     */
    private fun breaksAtSpaces(layout: StaticLayout, text: String): Boolean =
        (0 until layout.lineCount - 1).all { text[layout.getLineEnd(it) - 1].isWhitespace() }

    /**
     * One candidate laid out the way the value's own `TextView` will lay it out.
     *
     * The two line-breaking settings are `TextView`'s defaults rather than
     * `StaticLayout`'s, which differ. They matter only where a single word is wider than
     * the cell, which for a countdown means a spelled-out unit name on a one-cell widget;
     * everywhere else the text breaks at its spaces and the settings cannot change the
     * count.
     */
    private fun valueLayout(text: String, textSizePx: Float, widthPx: Int): StaticLayout =
        StaticLayout.Builder
            .obtain(text, 0, text.length, valuePaint().apply { textSize = textSizePx }, widthPx)
            .setIncludePad(true)
            .setLineSpacing(0f, 1f)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()

    /**
     * The proportion of the value's size a label is drawn at when height is what limits
     * the widget — which is to say, the shape the whole thing grows in. Two fifths is
     * what a caption under a display number wants to be: unmistakably secondary, still
     * comfortably readable.
     *
     * Held back from the value's own budget rather than taken out of what is left. Taken
     * out of what is left, a cell with height going spare spends all of it on the number
     * and leaves the captions either side at their floor.
     */
    private const val LABEL_SHARE = 0.4f

    /**
     * How far past [LABEL_SHARE] a label may be pushed by height the value could not use
     * — usually because the width of the cell stopped the number growing first.
     *
     * Two thirds is far enough to read comfortably and near enough that the number is
     * still what the eye lands on; above about three quarters the three rows start to
     * read as one paragraph.
     */
    private const val LABEL_MAX_SHARE = 0.66f

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

    /**
     * Both rows that can hold the value, so whichever one is showing is sized.
     *
     * The line count is set even when it is the 1 the layouts already declare, for the
     * same reason the padding is: a variant whose spacing comes from somewhere else is a
     * variant that moves when that somewhere else does.
     *
     * `setMaxLines` reaches the view by name because `RemoteViews` has no wrapper for
     * it, which works only because `TextView` marks it `@RemotableViewMethod` — the
     * dispatcher refuses anything that is not. `Chronometer` inherits it, so a ticking
     * value wraps on the same terms as a static one.
     */
    private fun setValueBox(views: RemoteViews, heightPx: Int, lines: Int) {
        views.setViewLayoutHeight(R.id.widget_value, heightPx.toFloat(), TypedValue.COMPLEX_UNIT_PX)
        views.setViewLayoutHeight(R.id.widget_ticker, heightPx.toFloat(), TypedValue.COMPLEX_UNIT_PX)
        views.setInt(R.id.widget_value, "setMaxLines", lines)
        views.setInt(R.id.widget_ticker, "setMaxLines", lines)
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
     *
     * That makes it the exception to sizing per variant, and the reason [cellDp] here is
     * [tightest] rather than a cell of its own. One object with no size map behind it is
     * the object the launcher draws in every orientation, so the only cell it can safely
     * measure against is the smallest of the ones reported.
     *
     * Wrapping is not optional here, because there is no timer whose option to read and
     * because the prompt is a sentence rather than a number. "Tap to choose a timer" is
     * most of a two-cell row at the smallest size it can be drawn, so on a single cell
     * one line would be a line nobody can read.
     */
    private fun unconfigured(
        context: Context,
        appWidgetId: Int,
        cellDp: SizeF?,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_timer)
        val (size, variant) = BREAKPOINTS.first()
        val resources = context.resources
        val padding = resources.getDimensionPixelSize(variant.density.padding)
        views.setViewPadding(android.R.id.background, padding, padding, padding, padding)
        val prompt = context.getString(R.string.widget_unconfigured)
        val metrics = metricsFor(
            resources = resources,
            breakpoint = size,
            cellDp = cellDp,
            density = variant.density,
            text = Text(value = prompt, name = null, footer = null),
            maxValueLines = maxValueLines(prompt),
        )
        views.setTextViewTextSize(R.id.widget_value, TypedValue.COMPLEX_UNIT_PX, metrics.valueText)
        setValueBox(views, metrics.valueHeight, metrics.valueLines)
        views.setViewVisibility(R.id.widget_name, View.GONE)
        views.setViewVisibility(R.id.widget_ticker, View.GONE)
        views.setViewVisibility(R.id.widget_footer, View.GONE)
        views.setViewVisibility(R.id.widget_value, View.VISIBLE)
        views.setTextViewText(R.id.widget_value, prompt)
        views.setOnClickPendingIntent(
            android.R.id.background,
            WidgetConfigActivity.reconfigureIntent(context, appWidgetId),
        )
        return views
    }
}
