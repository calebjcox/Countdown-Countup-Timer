package com.calebjcox.countdownwidgets.widget

import android.content.Context
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import com.calebjcox.countdownwidgets.R
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
 * `updatePeriodMillis` net — measured all five variants against a landscape row. A
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

    /** The widget from the bug report: a short value, so its size is the cell's to set. */
    private val timer = Timer(
        id = "divorce",
        name = "Divorce",
        spec = TimerSpec.of(
            target = LocalDateTime.parse("2026-08-24T00:00"),
            precision = Precision.DATE,
            fields = setOf(TimeField.DAY),
        ),
        showBackground = true,
    )

    @Test
    fun `the portrait cell keeps its size when a landscape cell is reported too`() {
        val alone = valueSizeSp(listOf(PORTRAIT), PORTRAIT)
        val both = valueSizeSp(listOf(PORTRAIT, LANDSCAPE), PORTRAIT)

        assertEquals(
            "reporting the landscape cell as well cost the portrait one " +
                "${alone - both}sp of its number",
            alone,
            both,
            0.01f,
        )
        // Teeth: without this the test would pass on a widget that collapsed both cells
        // to the floor, which is the failure it exists to catch.
        assertTrue(
            "a ${PORTRAIT.width}x${PORTRAIT.height} cell drew its number at ${both}sp, " +
                "no bigger than the captions beside it",
            both >= 2 * LABEL_FLOOR_SP,
        )
    }

    @Test
    fun `the landscape cell keeps its size when a portrait cell is reported too`() {
        assertEquals(
            valueSizeSp(listOf(LANDSCAPE), LANDSCAPE),
            valueSizeSp(listOf(PORTRAIT, LANDSCAPE), LANDSCAPE),
            0.01f,
        )
    }

    /**
     * Nothing about the widget may depend on which orientation the host happened to list
     * first — that ordering is the host's business and it is not documented.
     */
    @Test
    fun `the order the cells are reported in does not matter`() {
        for (cell in listOf(PORTRAIT, LANDSCAPE)) {
            assertEquals(
                "at ${cell.width}x${cell.height}",
                valueSizeSp(listOf(PORTRAIT, LANDSCAPE), cell),
                valueSizeSp(listOf(LANDSCAPE, PORTRAIT), cell),
                0.01f,
            )
        }
    }

    /**
     * The renderer has to know which variant a cell will select, and it now works that
     * out itself rather than being told one cell for all five. The rule is the
     * platform's, so this asserts the copy against the original — the same call
     * `AppWidgetHostView` makes, reached through [VariantSelection].
     *
     * A probe rather than the real widget: each variant is labelled with the breakpoint
     * it was filed under, so the selection can be read straight off the inflated view
     * instead of inferred from how the rows came out.
     */
    @Test
    fun `the best-fit rule matches the platform's own selection`() {
        val probe = RemoteViews(
            WidgetRenderer.breakpointSizes.associateWith { size ->
                RemoteViews(context.packageName, R.layout.widget_timer).apply {
                    setTextViewText(R.id.widget_name, label(size))
                }
            },
        )

        for (cell in PROBED_CELLS) {
            val view = VariantSelection
                .forSize(probe, context, cell)
                .apply(context, FrameLayout(context))
            assertEquals(
                "at ${cell.width}x${cell.height}",
                view.findViewById<TextView>(R.id.widget_name).text.toString(),
                label(WidgetRenderer.bestFitBreakpoint(cell)),
            )
        }
    }

    private fun label(size: SizeF): String = "${size.width}x${size.height}"

    /**
     * The size the value is drawn at, in sp, when [cells] are reported and the launcher
     * then draws the widget in [cell].
     */
    private fun valueSizeSp(cells: List<SizeF>, cell: SizeF): Float {
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

        val ticker = root.findViewById<TextView>(R.id.widget_ticker)
        val value = if (ticker.visibility == View.VISIBLE) {
            ticker
        } else {
            root.findViewById(R.id.widget_value)
        }
        return value.textSize / context.resources.displayMetrics.scaledDensity
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

        /** `widget_label_text_min`, in sp. */
        const val LABEL_FLOOR_SP = 12f

        /**
         * Cells to check the selection rule against: every breakpoint exactly, a little
         * either side of each, both reported cells, and one below everything — which is
         * the branch with no fitting candidate at all, where the platform falls back to
         * the smallest variant rather than to none.
         */
        val PROBED_CELLS = listOf(
            SizeF(90f, 30f),
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
        )
    }
}
