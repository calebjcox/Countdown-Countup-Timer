package com.calebjcox.countdownwidgets.widget

import android.content.Context
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.calebjcox.countdownwidgets.R
import com.calebjcox.countdownwidgets.core.LabelStyle
import com.calebjcox.countdownwidgets.core.Precision
import com.calebjcox.countdownwidgets.core.TimeField
import com.calebjcox.countdownwidgets.core.TimerSpec
import com.calebjcox.countdownwidgets.data.Timer
import com.calebjcox.countdownwidgets.testing.VariantSelection
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins the spacing, which the row-visibility tests next door cannot see.
 *
 * The bug this guards against was invisible to every assertion the widget had: the
 * right rows were present, the right text was in them, and the thing still looked
 * wrong, because the value sat in a box far taller than the number inside it and a
 * `TextView` centres its text — so the name floated at the top of the cell and the
 * target date at the bottom with two bands of nothing between them. What follows
 * measures the one number that describes that: how much of the value's box is not
 * text. Everything else about the layout follows from the rows being adjacent.
 *
 * `@GraphicsMode(NATIVE)` is required, not stylistic. Robolectric's default is LEGACY,
 * where text measures zero — every gap here would be zero and the test would pass on a
 * widget that renders nothing at all. [assertRowsFit] is the tripwire for that.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetSpacingTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val zone: ZoneId = ZoneId.of("America/Denver")

    private val nowMillis =
        LocalDateTime.parse("2026-08-09T13:37:42").atZone(zone).toInstant().toEpochMilli()

    /**
     * The widest thing the app can put on one line — three units spelled out in full.
     * Its size is decided by the width of the cell, which is what used to leave the
     * height going spare.
     */
    private val spelledOut = Timer(
        id = "filing-date",
        name = "Filing Date",
        spec = TimerSpec.of(
            target = LocalDateTime.parse("2024-08-01T00:00"),
            precision = Precision.DATE,
            fields = setOf(TimeField.YEAR, TimeField.MONTH, TimeField.DAY),
        ),
        labelStyle = LabelStyle.LONG,
        showBackground = true,
    )

    /** Short enough to be limited by the cell's height instead. */
    private val abbreviated = Timer(
        id = "christmas",
        name = "Christmas",
        spec = TimerSpec.of(
            target = LocalDateTime.parse("2026-12-25T00:00"),
            precision = Precision.DATE,
            fields = setOf(TimeField.MONTH, TimeField.DAY),
        ),
        showBackground = true,
    )

    /**
     * A cell taller than the rows need spends the difference on margin around them,
     * never on a gap between them — at 4dp of slack in the value's box, a label cannot
     * come away from the number it belongs to.
     */
    private val allowedSlackDp = 4f

    @Test
    fun `the value's box hugs a spelled-out date at every size`() {
        for ((width, height) in CELLS) assertHugs(spelledOut, width, height)
    }

    @Test
    fun `the value's box hugs an abbreviated one at every size`() {
        for ((width, height) in CELLS) assertHugs(abbreviated, width, height)
    }

    /** The rows are the whole widget: nothing may be pushed out of the cell. */
    @Test
    fun `the rows fit the cell they were built for`() {
        for ((width, height) in CELLS) {
            assertRowsFit(spelledOut, width, height)
            assertRowsFit(abbreviated, width, height)
        }
    }

    /**
     * A label's band size is a floor. On a cell with height going spare it is drawn
     * larger, because 10sp on a widget with 40dp of margin around it is a caption
     * nobody can read — which is the complaint this pins.
     */
    @Test
    fun `the labels grow into a cell that has room for them`() {
        val tight = labelSizes(abbreviated, 140f, 74f)
        val roomy = labelSizes(abbreviated, 344f, 152f)

        assertTrue(
            "a 344x152 cell drew its name at ${roomy.first}sp, no larger than the " +
                "${tight.first}sp a 140x74 cell has room for",
            roomy.first > tight.first && roomy.second > tight.second,
        )
        assertTrue(
            "a 344x152 cell drew its labels at ${roomy.first}sp / ${roomy.second}sp, " +
                "which is still too small to read comfortably",
            minOf(roomy.first, roomy.second) >= 14f,
        )
    }

    /**
     * Growing a label must never cost the number a point of its own size.
     *
     * Two copies of one timer, identical but for the length of the name: the short one
     * leaves room for its labels to grow, the long one is held at the band's size by
     * the width of the cell. The value cannot tell the difference — if it can, growth
     * is being paid for out of the number.
     */
    @Test
    fun `the value keeps its size whatever the labels do`() {
        val short = abbreviated.copy(name = "Trip")
        val long = abbreviated.copy(name = "A name with no room left to grow into")

        for ((width, height) in CELLS) {
            assertEquals(
                "at ${width}x$height the value shrank when the labels beside it grew",
                visibleValue(render(long, width, height)).textSize,
                visibleValue(render(short, width, height)).textSize,
                0.01f,
            )
        }
    }

    /**
     * The width has a say as well as the height. Both timers' labels fit their cells at
     * the band's own size, so an ellipsis on one of them can only have come from growth.
     */
    @Test
    fun `no label is grown until it no longer fits`() {
        for ((width, height) in CELLS) {
            for (timer in listOf(spelledOut, abbreviated)) {
                val root = render(timer, width, height)
                for (id in listOf(R.id.widget_name, R.id.widget_footer)) {
                    val label = root.findViewById<TextView>(id)
                    if (label.visibility != View.VISIBLE) continue
                    assertEquals(
                        "at ${width}x$height '${label.text}' was drawn at " +
                            "${label.textSize}px, which does not fit",
                        0,
                        requireNotNull(label.layout) { "the label never laid out" }
                            .getEllipsisCount(0),
                    )
                }
            }
        }
    }

    /**
     * What wrapping is for. One column is a size the widget can now be dragged to, and
     * `4mo 16d` across 52dp of content box is the auto-sizing's floor — the point at
     * which it has nothing left to give. A line break has plenty left to give.
     */
    @Test
    fun `a one-cell widget wraps its countdown rather than shrinking it`() {
        val wrapped = visibleValue(render(abbreviated, 64f, 152f))
        val oneLine = visibleValue(render(abbreviated.copy(wrapValue = false), 64f, 152f))

        assertTrue(
            "the value stayed on one line at ${wrapped.textSize}px",
            requireNotNull(wrapped.layout) { "the value never laid out" }.lineCount > 1,
        )
        assertTrue(
            "wrapping drew the value at ${wrapped.textSize}px, no larger than the " +
                "${oneLine.textSize}px one line already managed",
            wrapped.textSize > oneLine.textSize,
        )
    }

    /**
     * A days countdown is the value this whole size was asked for, and it is the one
     * with nowhere to break: `25d` is a single word to a line breaker, which will
     * happily leave `25` on one line and `d` on the next if that is what fits the box.
     * A tall single cell is where it would — there is height for three lines and width
     * for none of them at that size — so this is the case that has to come back to one.
     */
    @Test
    fun `a value with nowhere to break is left on one line`() {
        val days = abbreviated.copy(
            spec = TimerSpec.of(
                target = LocalDateTime.parse("2026-12-25T00:00"),
                precision = Precision.DATE,
                fields = setOf(TimeField.DAY),
            ),
        )
        val wrapped = visibleValue(render(days, 64f, 152f))
        val oneLine = visibleValue(render(days.copy(wrapValue = false), 64f, 152f))

        assertEquals(
            "'${wrapped.text}' was split across lines with no space to split at",
            1,
            requireNotNull(wrapped.layout) { "the value never laid out" }.lineCount,
        )
        assertEquals(
            "splitting a unit off its number was allowed to change the size",
            oneLine.textSize,
            wrapped.textSize,
            0.01f,
        )
    }

    /**
     * And what it is not for. A cell with the width for its value gains nothing from a
     * second line and must not take one — this is the assertion that lets the setting
     * default to on, because it says an ordinary 2x1 is untouched by it.
     */
    @Test
    fun `a widget with the width for its value keeps it on one line`() {
        for ((width, height) in CELLS.filter { it.first >= 110f }) {
            val wrapped = visibleValue(render(abbreviated, width, height))
            val oneLine = visibleValue(render(abbreviated.copy(wrapValue = false), width, height))

            assertEquals(
                "at ${width}x$height the value wrapped with no need to",
                1,
                requireNotNull(wrapped.layout) { "the value never laid out" }.lineCount,
            )
            assertEquals(
                "at ${width}x$height allowing a wrap changed the value's size",
                oneLine.textSize,
                wrapped.textSize,
                0.01f,
            )
        }
    }

    /**
     * The other case a line break rescues, and the reason the setting is not described
     * as being about narrow widgets: spelled-out unit names outrun a 2x1 as surely as a
     * short value outruns one cell. What one line does there is worse than shrinking. The
     * auto-sizing runs out at the floor, the text needs a second line anyway, and a
     * `TextView` held to one draws that line and silently discards the rest — so a
     * widget counting `2 years, 0 months, 8 days` has been reading `2 years, 0 months, 8`.
     */
    @Test
    fun `a value too long for its cell wraps instead of being cut off`() {
        val wrapped = visibleValue(render(spelledOut, 140f, 74f))
        val oneLine = visibleValue(render(spelledOut.copy(wrapValue = false), 140f, 74f))

        // Both are already at the smallest size there is, so this is the case where the
        // extra line buys nothing in points and everything in characters.
        assertEquals(
            "the one-line value was not at the floor, so this proves nothing about size",
            oneLine.textSize,
            wrapped.textSize,
            0.01f,
        )
        assertNotEquals(
            "one line fitted the whole of it into a 140x74 cell after all",
            oneLine.text.toString(),
            drawnText(oneLine),
        )
        assertEquals(
            "the wrapped value still does not show all of itself",
            wrapped.text.toString(),
            drawnText(wrapped),
        )
    }

    /**
     * The part of the value a reader can actually see: the lines within `maxLines`, which
     * is all a `TextView` draws and — with no ellipsis on this row — all it admits to.
     */
    private fun drawnText(value: TextView): String {
        val layout = requireNotNull(value.layout) { "the value never laid out" }
        return (0 until minOf(layout.lineCount, value.maxLines)).joinToString(" ") {
            value.text.substring(layout.getLineStart(it), layout.getLineEnd(it)).trim()
        }
    }

    /** The name's and footer's text size in sp, on a cell of this size. */
    private fun labelSizes(timer: Timer, widthDp: Float, heightDp: Float): Pair<Float, Float> {
        val root = render(timer, widthDp, heightDp)
        val scale = context.resources.displayMetrics.scaledDensity
        return root.findViewById<TextView>(R.id.widget_name).textSize / scale to
            root.findViewById<TextView>(R.id.widget_footer).textSize / scale
    }

    private fun assertHugs(timer: Timer, widthDp: Float, heightDp: Float) {
        val root = render(timer, widthDp, heightDp)
        val value = visibleValue(root)
        val slack = value.height - requireNotNull(value.layout) { "the value never laid out" }.height

        assertTrue(
            "at ${widthDp}x$heightDp the value's box is ${px(slack)}dp taller than its " +
                "text, which puts ${px(slack) / 2}dp of empty space between the number " +
                "and each label",
            px(slack) <= allowedSlackDp,
        )
    }

    private fun assertRowsFit(timer: Timer, widthDp: Float, heightDp: Float) {
        val root = render(timer, widthDp, heightDp)
        val rows = (0 until root.childCount)
            .map { root.getChildAt(it) }
            .filter { it.visibility == View.VISIBLE }
        val used = rows.sumOf { it.height } + root.paddingTop + root.paddingBottom

        assertTrue("at ${widthDp}x$heightDp nothing was drawn", rows.isNotEmpty())
        rows.forEach {
            assertTrue("at ${widthDp}x$heightDp a row measured zero-high", it.height > 0)
        }
        assertTrue(
            "at ${widthDp}x$heightDp the rows need ${px(used)}dp of a ${heightDp}dp cell",
            px(used) <= heightDp,
        )
    }

    /** The row holding the value: the `Chronometer` when it ticks, the text when not. */
    private fun visibleValue(root: LinearLayout): TextView {
        val ticker = root.findViewById<TextView>(R.id.widget_ticker)
        val value = root.findViewById<TextView>(R.id.widget_value)
        return if (ticker.visibility == View.VISIBLE) ticker else value
    }

    /**
     * The variant a launcher would pick for this cell, laid out at exactly that size —
     * measurement is the whole point, so the sizes cannot be left to a parent to guess.
     */
    private fun render(timer: Timer, widthDp: Float, heightDp: Float): LinearLayout {
        val views = WidgetRenderer.build(
            context = context,
            appWidgetId = 1,
            timer = timer,
            nowMillis = nowMillis,
            zone = zone,
            cells = listOf(SizeF(widthDp, heightDp)),
        )
        val view = VariantSelection
            .forSize(views, context, SizeF(widthDp, heightDp))
            .apply(context, FrameLayout(context))

        val density = context.resources.displayMetrics.density
        val widthPx = (widthDp * density).toInt()
        val heightPx = (heightDp * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, widthPx, heightPx)
        return view as LinearLayout
    }

    private fun px(value: Int): Float = value / context.resources.displayMetrics.density

    private companion object {
        /**
         * Content-box sizes real launchers hand out, one per band and then some — the
         * tall-and-narrow ones matter most, because a cell with height to spare is
         * exactly where the space used to go.
         *
         * The four below 110dp are single cells: a phone's, short and tall, and the
         * seven- and ten-inch tablets' — where one cell is wider than a phone's 2x1.
         * They are where a value wraps, so they are also where the box has to hug text
         * this file cannot see the line breaks of.
         */
        val CELLS = listOf(
            64f to 76f,
            64f to 152f,
            105f to 84f,
            130f to 92f,
            120f to 45f,
            140f to 74f,
            140f to 100f,
            168f to 52f,
            216f to 74f,
            216f to 100f,
            290f to 120f,
            344f to 88f,
            344f to 152f,
            400f to 60f,
        )
    }
}
