package com.calebjcox.countdownwidgets.widget

import android.content.Context
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import com.calebjcox.countdownwidgets.R
import com.calebjcox.countdownwidgets.core.Backdrop
import com.calebjcox.countdownwidgets.core.LabelStyle
import com.calebjcox.countdownwidgets.core.Precision
import com.calebjcox.countdownwidgets.core.TimeField
import com.calebjcox.countdownwidgets.core.TimerSpec
import com.calebjcox.countdownwidgets.data.Timer
import com.calebjcox.countdownwidgets.testing.VariantSelection
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins that one `RemoteViews` is sized correctly for every cell the launcher reported,
 * not just for whichever one was current when it was built.
 *
 * The bug: a launcher's options describe the widget in both orientations at once and do
 * not change when the device rotates, so `onAppWidgetOptionsChanged` never fires on a
 * rotation and the app is never asked to redraw. The renderer used to size every variant
 * from the single cell matching the configuration's current orientation, which meant a
 * redraw that happened while any app was in landscape — an alarm tick, the half-hourly
 * `updatePeriodMillis` net — measured every variant against a landscape row. A
 * phone's landscape row is little over half the height of its portrait one, so back on
 * the portrait home screen the number was pinned to the 11sp floor and sat there,
 * indistinguishable from its own captions, until some later update happened to run in
 * portrait or the user edited the timer. Sized per variant, one object is right either
 * way up and no rotation can put it wrong.
 *
 * `@GraphicsMode(NATIVE)` for the same reason as `WidgetSpacingTest`: under LEGACY all
 * text measures zero, auto-sizing has nothing to search, and every size below would
 * agree at zero.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetOrientationTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val zone: ZoneId = ZoneId.of("America/Denver")

    private val nowMillis =
        LocalDateTime.parse("2026-08-16T09:12:00").atZone(zone).toInstant().toEpochMilli()

    /**
     * The widget from the bug report: a short value, so its size is the cell's to set.
     * `8d` is what was reported, so the style is stated rather than inherited from
     * whatever a new timer currently defaults to.
     */
    private val timer = Timer(
        id = "move-out",
        name = "Move Out",
        spec = TimerSpec.of(
            target = LocalDateTime.parse("2026-08-24T00:00"),
            precision = Precision.DATE,
            fields = setOf(TimeField.DAY),
        ),
        labelStyle = LabelStyle.SHORT,
        backdrop = Backdrop.PANEL,
    )

    /**
     * The same widget counting in units spelled out in full, which is what a cell has to
     * be measured against to say anything about filling it. `8d` is two characters: on a
     * cell the size of a tablet's screen it reaches `widget_value_text_max` — a sanity
     * bound, deliberately above what any cell asks for — and stops there with height to
     * spare, so its size says nothing about which cell it was sized for.
     */
    private val spelledOut = timer.copy(
        id = "spelled-out",
        spec = TimerSpec.of(
            target = LocalDateTime.parse("2027-08-10T00:00"),
            precision = Precision.DATE,
            fields = setOf(TimeField.MONTH, TimeField.WEEK, TimeField.DAY),
        ),
        labelStyle = LabelStyle.LONG,
    )

    /**
     * Both, everywhere the question is about which cell a variant was sized for. The two
     * are limited by different things — one by the width of the cell, one by nothing but
     * its height — and a variant sized for the wrong cell can hide behind either.
     */
    private val timers = listOf(timer, spelledOut)

    @Test
    fun `the portrait cell keeps its size when a landscape cell is reported too`() {
        for (timer in timers) {
            for ((portrait, landscape) in PAIRS) {
                val alone = valueSizeSp(listOf(portrait), portrait, timer)
                val both = valueSizeSp(listOf(portrait, landscape), portrait, timer)

                assertEquals(
                    "with '${timer.id}', reporting the " +
                        "${landscape.width}x${landscape.height} landscape cell as well " +
                        "cost the ${portrait.width}x${portrait.height} portrait one " +
                        "${alone - both}sp of its number",
                    alone,
                    both,
                    0.01f,
                )
                // Teeth: without this the test would pass on a widget that collapsed both
                // cells to the floor, which is the failure it exists to catch.
                assertTrue(
                    "a ${portrait.width}x${portrait.height} cell drew '${timer.id}' at " +
                        "${both}sp, which is the size the auto-sizing gives up at",
                    both > VALUE_FLOOR_SP,
                )
            }
        }
    }

    @Test
    fun `the landscape cell keeps its size when a portrait cell is reported too`() {
        for (timer in timers) {
            for ((portrait, landscape) in PAIRS) {
                assertEquals(
                    "with '${timer.id}', at ${landscape.width}x${landscape.height}",
                    valueSizeSp(listOf(landscape), landscape, timer),
                    valueSizeSp(listOf(portrait, landscape), landscape, timer),
                    0.01f,
                )
            }
        }
    }

    /**
     * The complaint itself, which the two equalities above cannot state on their own:
     * they say the second cell costs the first nothing, and would hold just as well if
     * both came out small. A widget dragged out large has to *fill* what it was dragged
     * out to, in whichever orientation it is drawn.
     *
     * Four fifths is a low bar — where the line breaks fall decides the rest, and every
     * cell here clears nine tenths — while a tablet's portrait cell sized for its
     * landscape twin reaches two thirds and leaves the rest as margin.
     */
    @Test
    fun `a large widget fills the cell it is drawn in whichever one that is`() {
        for ((portrait, landscape) in PAIRS) {
            for (cell in listOf(portrait, landscape)) {
                val fill = fillOf(listOf(portrait, landscape), cell, spelledOut)
                assertTrue(
                    "at ${cell.width}x${cell.height} the rows use ${100f * fill}% of the " +
                        "cell, so the rest of the widget is empty space",
                    fill >= 0.8f,
                )
            }
        }
    }

    /**
     * A host is free to report more cells than the size map has room to key one each.
     * `RemoteViews` refuses a map above sixteen entries outright, so the widget has to
     * stay inside that whatever it is handed — a foldable with three screens, or a
     * launcher listing every workspace it has.
     */
    @Test
    fun `a host reporting more cells than the map can hold still builds`() {
        val many = PAIRS.flatMap { listOf(it.first, it.second) } +
            listOf(SizeF(500f, 500f), SizeF(240f, 300f), SizeF(180f, 420f))

        assertTrue(
            "the largest of ${many.size} reported cells came back at the floor",
            valueSizeSp(many, TABLET_PORTRAIT) > VALUE_FLOOR_SP,
        )
    }

    /**
     * Nothing about the widget may depend on which orientation the host happened to list
     * first — that ordering is the host's business and it is not documented.
     */
    @Test
    fun `the order the cells are reported in does not matter`() {
        for ((portrait, landscape) in PAIRS) {
            for (cell in listOf(portrait, landscape)) {
                assertEquals(
                    "at ${cell.width}x${cell.height}",
                    valueSizeSp(listOf(portrait, landscape), cell),
                    valueSizeSp(listOf(landscape, portrait), cell),
                    0.01f,
                )
            }
        }
    }

    /**
     * The renderer has to know which entry a cell will select — [sizingFor] sizes the text
     * for that cell and nothing corrects it afterwards. The rule is the platform's, so
     * this asserts the copy against the original: the same call `AppWidgetHostView` makes,
     * reached through [VariantSelection].
     *
     * Over the sizes the widget really ships rather than a list written out beside them,
     * because a transcription checked against the wrong map is a transcription that agrees
     * with nothing. Both maps a live widget has: the one for a host that has reported its
     * cells and the one for a host that has not.
     *
     * A probe rather than the real widget: each entry is labelled with the size it was
     * filed under, so the selection can be read straight off the inflated view instead of
     * inferred from how the rows came out.
     */
    @Test
    fun `the best-fit rule matches the platform's own selection`() {
        for (reported in listOf(emptyList(), listOf(BIG_PORTRAIT, BIG_LANDSCAPE))) {
            val sizes = WidgetRenderer.variantSizes(reported, context.resources)
            val probe = RemoteViews(
                sizes.associateWith { size ->
                    RemoteViews(context.packageName, R.layout.widget_timer).apply {
                        setTextViewText(R.id.widget_name, label(size))
                    }
                },
            )

            for (cell in PROBED_CELLS + sizes) {
                val view = VariantSelection
                    .forSize(probe, context, cell)
                    .apply(context, FrameLayout(context))
                assertEquals(
                    "with ${reported.size} cells reported, at ${cell.width}x${cell.height}",
                    view.findViewById<TextView>(R.id.widget_name).text.toString(),
                    label(WidgetRenderer.bestFit(cell, sizes)),
                )
            }
        }
    }

    private fun label(size: SizeF): String = "${size.width}x${size.height}"

    /**
     * How much of [cell]'s height the rows occupy when [cells] are reported, as a
     * fraction — the shape of the complaint rather than a size in it, because a number
     * drawn at half the size it could be is a number in the middle of an empty widget.
     */
    private fun fillOf(cells: List<SizeF>, cell: SizeF, timer: Timer): Float {
        val root = render(cells, cell, timer)
        val used = (0 until root.childCount)
            .map { root.getChildAt(it) }
            .filter { it.visibility == View.VISIBLE }
            .sumOf { it.height } + root.paddingTop + root.paddingBottom
        return used / (cell.height * context.resources.displayMetrics.density)
    }

    /**
     * The size the value is drawn at, in sp, when [cells] are reported and the launcher
     * then draws the widget in [cell].
     */
    private fun valueSizeSp(cells: List<SizeF>, cell: SizeF, timer: Timer = this.timer): Float {
        val root = render(cells, cell, timer)
        val ticker = root.findViewById<TextView>(R.id.widget_ticker)
        val value = if (ticker.visibility == View.VISIBLE) {
            ticker
        } else {
            root.findViewById(R.id.widget_value)
        }
        return value.textSize / context.resources.displayMetrics.scaledDensity
    }

    /**
     * The widget a launcher reporting [cells] would draw in [cell], laid out at exactly
     * that size — the variant chosen through the platform's own selection, so nothing
     * here can agree with a wrong expectation about which one that is.
     */
    private fun render(cells: List<SizeF>, cell: SizeF, timer: Timer = this.timer): LinearLayout {
        val views = WidgetRenderer.build(
            context = context,
            appWidgetId = 1,
            timer = timer,
            nowMillis = nowMillis,
            zone = zone,
            cells = cells,
        )
        val root = VariantSelection
            .forSize(views, context, cell)
            .apply(context, FrameLayout(context))

        val density = context.resources.displayMetrics.density
        val widthPx = (cell.width * density).toInt()
        val heightPx = (cell.height * density).toInt()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, widthPx, heightPx)
        return root as LinearLayout
    }

    private companion object {
        /**
         * The two content boxes a phone launcher reports for one 3x1 widget: a home
         * screen three columns wide by one row tall, measured in each orientation. The
         * landscape row is barely half the height of the portrait one, which is the
         * whole reason a widget sized for the wrong one is unreadable rather than merely
         * a little off.
         */
        val PORTRAIT = SizeF(216f, 91f)
        val LANDSCAPE = SizeF(500f, 53f)

        /**
         * And the pair for a widget dragged out to the whole screen, where the two are
         * furthest apart. A 3x1 is small enough that the two cells select different
         * variants on width alone; a full-screen one is bigger than every breakpoint in
         * both orientations, so both are nearest to whichever is largest — and lumped
         * together they are sized for the corner of the two, which is 325 wide and 250
         * tall and is neither of them.
         */
        val BIG_PORTRAIT = SizeF(325f, 549f)
        val BIG_LANDSCAPE = SizeF(700f, 250f)

        /**
         * The same widget on a tablet, and the pair a ladder cannot separate however it
         * is shaped. A phone's two orientations differ enough that rungs of the right
         * shape can catch one each; a tablet's are both wide and both tall, so they land
         * on the same rung whatever rungs there are, and only a rung cut to the cell
         * itself keeps them apart. Sized for the corner of the two — 600 by 520 — a
         * widget the height of the screen draws its countdown at two thirds the size it
         * has room for and leaves the bottom half of itself empty.
         */
        val TABLET_PORTRAIT = SizeF(600f, 800f)
        val TABLET_LANDSCAPE = SizeF(900f, 520f)

        val PAIRS = listOf(
            PORTRAIT to LANDSCAPE,
            BIG_PORTRAIT to BIG_LANDSCAPE,
            TABLET_PORTRAIT to TABLET_LANDSCAPE,
        )

        /** `widget_value_text_min`, in sp: the size a value that fits nowhere comes back at. */
        const val VALUE_FLOOR_SP = 11f

        /**
         * Cells to check the selection rule against, on top of the map's own entries,
         * which the test appends: a dp either side of each threshold, real cells from
         * phones and both tablet classes, and one below everything — which is the branch
         * with no fitting candidate at all, where the platform falls back to the smallest
         * entry rather than to none.
         *
         * The narrow entries are the ones to keep an eye on. A one-cell widget is where
         * the thresholds sit closest together — three of them inside 90dp of width — so
         * it is where a transcription of the platform's rule is likeliest to disagree with
         * it, and it is the only size whose answer differs between a phone and a tablet.
         * Hence a real 1x1 of each: 64x76 on a phone, 105x84 and 130x92 on the two
         * tablets, which are wider than a phone's 2x1.
         */
        val PROBED_CELLS = listOf(
            SizeF(90f, 30f),
            SizeF(56f, 39f),
            SizeF(56f, 40f),
            SizeF(56f, 72f),
            SizeF(64f, 76f),
            SizeF(64f, 152f),
            SizeF(91f, 50f),
            SizeF(92f, 50f),
            SizeF(105f, 84f),
            SizeF(109f, 68f),
            SizeF(130f, 92f),
            SizeF(110f, 40f),
            SizeF(110f, 49f),
            SizeF(110f, 50f),
            SizeF(140f, 74f),
            SizeF(140f, 100f),
            SizeF(168f, 52f),
            PORTRAIT,
            SizeF(216f, 100f),
            SizeF(290f, 120f),
            SizeF(344f, 88f),
            SizeF(344f, 152f),
            SizeF(400f, 60f),
            LANDSCAPE,
            SizeF(129f, 110f),
            SizeF(130f, 110f),
            SizeF(130f, 219f),
            SizeF(130f, 220f),
            SizeF(299f, 130f),
            SizeF(300f, 130f),
            SizeF(300f, 220f),
            SizeF(300f, 320f),
            SizeF(300f, 450f),
            BIG_PORTRAIT,
            BIG_LANDSCAPE,
            SizeF(660f, 210f),
            SizeF(700f, 330f),
            SizeF(900f, 800f),
        )
    }
}
