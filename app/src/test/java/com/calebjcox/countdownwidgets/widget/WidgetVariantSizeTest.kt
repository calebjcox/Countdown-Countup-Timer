package com.calebjcox.countdownwidgets.widget

import android.content.Context
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
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
 * Pins which rows a given cell size renders.
 *
 * This is the test the widget did not have when the size ladder was calibrated against
 * the legacy `70n - 30` cell formula instead of against what launchers report. The
 * numbers looked like a sensible ladder and were wrong by a whole size class: a 2x1
 * cell — the app's own advertised default — fell short of the 150dp width the name
 * needed, and no one-row cell has ever reached the 110dp height the target date
 * needed, so a 3x1 widget could not show it at all. See issue #11.
 *
 * The sizes below are dp of *content box*: what `AppWidgetHostView` measures after the
 * launcher's inset, which is smaller than the nominal cell. The one that matters most
 * is the 2x1 pair — 140x74 and 140x100 bracket what real launchers hand a
 * `targetCellWidth=2 / targetCellHeight=1` widget, and both must show all three rows,
 * because that is what the picker preview promises before the widget is dropped.
 *
 * SDK 36 rather than the app's targetSdk of 37 because 36 is the newest platform the
 * last stable Robolectric ships, and the same one the asset generator runs on — one
 * pinned `android-all` jar serves both. Every API this exercises landed in 31, so a
 * lower SDK would do, but it would mean staging a second platform jar to save nothing.
 * Robolectric will not build a sandbox for 36 on a JDK older than 21, which is why
 * both workflows ask for Java 21.
 *
 * `@GraphicsMode(NATIVE)` is not decoration either. Robolectric's default is LEGACY,
 * where text measures zero — the spacing assertions below would compare one zero
 * against another and pass on any layout at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetVariantSizeTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** A plain year/month/day countdown: static text, so no `Chronometer` involved. */
    private val timer = Timer(
        id = "christmas",
        name = "Christmas",
        spec = TimerSpec.of(
            target = LocalDateTime.parse("2026-12-25T00:00"),
            precision = Precision.DATE,
            fields = setOf(TimeField.YEAR, TimeField.MONTH, TimeField.DAY),
        ),
        showBackground = true,
    )

    private val nowMillis =
        LocalDateTime.parse("2026-08-09T13:37:42")
            .atZone(ZoneId.of("America/Denver"))
            .toInstant()
            .toEpochMilli()

    @Test
    fun `a two-by-one cell shows the name and the target date`() {
        assertRows(140f, 74f, name = true, footer = true)
        assertRows(140f, 100f, name = true, footer = true)
    }

    @Test
    fun `a three-by-one cell shows the target date`() {
        // The size from the bug report: it used to clear the name's breakpoint and miss
        // the footer's by ten dp, which is how a widget with room to spare for a date
        // ended up refusing to print one.
        assertRows(216f, 100f, name = true, footer = true)
    }

    @Test
    fun `taller and wider cells keep everything`() {
        assertRows(168f, 80f, name = true, footer = true)
        assertRows(344f, 88f, name = true, footer = true)
        assertRows(344f, 152f, name = true, footer = true)
        assertRows(216f, 120f, name = true, footer = true)
        assertRows(140f, 210f, name = true, footer = true)
        // One cell wide but tall. Every band asks for the same 110dp, so a column this
        // narrow is judged on height alone and keeps all three rows.
        assertRows(110f, 120f, name = true, footer = true)
    }

    @Test
    fun `a squashed cell drops rows in order`() {
        // Wide but flattened: no room for a third row, still room to say what it counts.
        assertRows(400f, 60f, name = true, footer = false)
        // At the provider's own minimum there is only the value left.
        assertRows(120f, 45f, name = false, footer = false)
    }

    @Test
    fun `a cell below every breakpoint falls back to the smallest variant`() {
        // Not reachable through minWidth/minHeight, but findBestFitLayout has a
        // documented answer for it and it should be the least demanding variant rather
        // than an arbitrary one.
        assertRows(90f, 30f, name = false, footer = false)
    }

    /**
     * The complaint this layout was rebuilt to answer: the rows were further from each
     * other than from the widget's own edges, which reads as three stacked things
     * rather than one tile. So measure both and compare them.
     *
     * The name and the footer are `wrap_content`, so their boxes are their text and the
     * distance from the first row's box to the top edge *is* the border. The value is
     * not: it gets a box sized by `WidgetRenderer.metrics`, its text is centred inside,
     * and the leftover shows up as half a gap above and half below. That leftover is
     * the whole quantity at issue, and `layout.height` is the rendered text against
     * which to measure it.
     *
     * Real cell sizes rather than exhaustive ones. How far the value's text shrinks
     * depends on the cell's width and on the string, so this is evidence about the
     * shapes people actually use, not a proof for every conceivable cell.
     */
    @Test
    fun `rows sit closer to each other than to the widget edge`() {
        for ((widthDp, heightDp) in listOf(
            140f to 68f, 140f to 74f, 140f to 100f, 216f to 100f,
            168f to 80f, 344f to 88f, 344f to 152f, 216f to 120f, 140f to 210f,
        )) {
            val view = laidOutAt(widthDp, heightDp)
            val name = view.findViewById<View>(R.id.widget_name)
            val value = view.findViewById<TextView>(R.id.widget_value)
            val footer = view.findViewById<View>(R.id.widget_footer)

            val border = minOf(name.top, view.height - footer.bottom)
            // Half above the text and half below, so half is what lands between rows.
            val betweenRows = (value.height - (value.layout?.height ?: 0)) / 2

            assertTrue(
                "at ${widthDp}x$heightDp the gap between rows is ${betweenRows}px but " +
                    "the border is only ${border}px — the tile reads as separate rows",
                betweenRows <= border,
            )
            assertTrue("no border at ${widthDp}x$heightDp", border > 0)
        }
    }

    private fun Float.toPx(): Int =
        (this * context.resources.displayMetrics.density).toInt()

    /** Inflates the variant a cell of this size gets, then measures and lays it out. */
    private fun laidOutAt(widthDp: Float, heightDp: Float): View {
        val views = WidgetRenderer.build(
            context = context,
            appWidgetId = 1,
            timer = timer,
            nowMillis = nowMillis,
            zone = ZoneId.of("America/Denver"),
        )
        val view = VariantSelection.forSize(views, context, SizeF(widthDp, heightDp))
            .apply(context, FrameLayout(context))

        val exactly = View.MeasureSpec.EXACTLY
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthDp.toPx(), exactly),
            View.MeasureSpec.makeMeasureSpec(heightDp.toPx(), exactly),
        )
        view.layout(0, 0, widthDp.toPx(), heightDp.toPx())
        return view
    }

    private fun assertRows(widthDp: Float, heightDp: Float, name: Boolean, footer: Boolean) {
        val views = WidgetRenderer.build(
            context = context,
            appWidgetId = 1,
            timer = timer,
            nowMillis = nowMillis,
            zone = ZoneId.of("America/Denver"),
        )
        val view = VariantSelection.forSize(views, context, SizeF(widthDp, heightDp))
            .apply(context, FrameLayout(context))

        assertEquals(
            "name visibility at ${widthDp}x$heightDp",
            name,
            view.findViewById<View>(R.id.widget_name).visibility == View.VISIBLE,
        )
        assertEquals(
            "footer visibility at ${widthDp}x$heightDp",
            footer,
            view.findViewById<View>(R.id.widget_footer).visibility == View.VISIBLE,
        )
        // The value is the one row that is never dropped; if it ever were, the
        // visibility assertions above would still pass on an empty widget.
        assertEquals(
            "value visibility at ${widthDp}x$heightDp",
            View.VISIBLE,
            view.findViewById<View>(R.id.widget_value).visibility,
        )
    }
}
