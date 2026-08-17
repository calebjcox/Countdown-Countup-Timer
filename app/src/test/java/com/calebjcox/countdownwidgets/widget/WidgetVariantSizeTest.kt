package com.calebjcox.countdownwidgets.widget

import android.content.Context
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import com.calebjcox.countdownwidgets.R
import com.calebjcox.countdownwidgets.core.Precision
import com.calebjcox.countdownwidgets.core.TimeField
import com.calebjcox.countdownwidgets.core.TimerSpec
import com.calebjcox.countdownwidgets.data.Timer
import com.calebjcox.countdownwidgets.testing.VariantSelection
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins which rows a given cell size renders.
 *
 * This is the test the widget did not have when `BREAKPOINTS` was calibrated against
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
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
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
        // One cell wide but tall: the height is there, the width for a roomy date is
        // not, so it stays in the compact band rather than ellipsizing.
        assertRows(110f, 120f, name = true, footer = true)
    }

    /**
     * One cell. The provider allows a resize down to it, so these are sizes a widget can
     * really be handed rather than hypotheticals — and they are three different answers,
     * because a single cell is not one size.
     */
    @Test
    fun `a one-cell widget shows what its width can carry`() {
        // A phone: about 64dp of content box, which is the number and nothing else. The
        // name would be a dozen characters of ellipsis and the date would not start.
        assertRows(64f, 76f, name = false, footer = false)
        assertRows(64f, 152f, name = false, footer = false)
        // A seven-inch tablet, where one cell is 105dp — wider than a phone's 2x1, and
        // wide enough for the name that a phone has to drop.
        assertRows(105f, 84f, name = true, footer = false)
        // A ten-inch tablet at 130dp keeps all three rows: this is the size the request
        // came from, and it is nothing like a phone's 1x1.
        assertRows(130f, 92f, name = true, footer = true)
    }

    @Test
    fun `a squashed cell drops rows in order`() {
        // Wide but flattened: no room for a third row, still room to say what it counts.
        assertRows(400f, 60f, name = true, footer = false)
        // At the provider's own minimum there is only the value left.
        assertRows(120f, 45f, name = false, footer = false)
    }

    @Test
    fun `a row switched off stays off on a cell with room for it`() {
        // The cell from the first test, which shows all three rows by default. What the
        // toggles say is not "if it fits" — it is that the row is unwanted, so the size
        // that would otherwise draw it is exactly where they have to hold.
        assertRows(140f, 100f, name = false, footer = true, timer = timer.copy(showName = false))
        assertRows(140f, 100f, name = true, footer = false, timer = timer.copy(showTarget = false))
        assertRows(
            140f, 100f, name = false, footer = false,
            timer = timer.copy(showName = false, showTarget = false),
        )
    }

    @Test
    fun `a cell below every breakpoint falls back to the smallest variant`() {
        // Not reachable through minWidth/minHeight, but findBestFitLayout has a
        // documented answer for it and it should be the least demanding variant rather
        // than an arbitrary one.
        assertRows(90f, 30f, name = false, footer = false)
    }

    /**
     * The same answers as the cases above, stated as the rule they come from and checked
     * a dp either side of every threshold there is.
     *
     * The cases above are what the widget does on real cells; this is *why*, and it is the
     * assertion that fails if the rows ever stop being a function of the box. What decides
     * them is `WidgetRenderer.variantFor` — width and height against four sizes — but the
     * launcher reaches it through nearest-fitting-neighbour over the map's keys, which is
     * a different test entirely. The two agree because the keys are the thresholds and the
     * thresholds nest; nothing here knows that, which is the point of checking it from
     * outside at sizes chosen to straddle each one.
     *
     * The rule is written out rather than read from the renderer, because a test that
     * asked the renderer what it thinks would agree with it however wrong it was. Editing
     * a threshold means editing this line, which is the review the numbers deserve.
     */
    @Test
    fun `the rows and the padding follow the thresholds and nothing else`() {
        val views = build(timer)
        for (width in PROBED_WIDTHS) {
            for (height in PROBED_HEIGHTS) {
                val view = select(views, width, height)
                val at = "at ${width}x$height"

                assertEquals(
                    "name visibility $at",
                    width >= 92f && height >= 50f,
                    view.findViewById<View>(R.id.widget_name).visibility == View.VISIBLE,
                )
                assertEquals(
                    "footer visibility $at",
                    width >= 110f && height >= 68f,
                    view.findViewById<View>(R.id.widget_footer).visibility == View.VISIBLE,
                )
                val roomy = width >= 130f && height >= 110f
                assertEquals(
                    "padding $at",
                    context.resources.getDimensionPixelSize(
                        if (roomy) R.dimen.widget_padding else R.dimen.widget_padding_compact,
                    ),
                    view.paddingTop,
                )
            }
        }
    }

    private fun build(timer: Timer): RemoteViews = WidgetRenderer.build(
        context = context,
        appWidgetId = 1,
        timer = timer,
        nowMillis = nowMillis,
        zone = ZoneId.of("America/Denver"),
    )

    /** The variant a launcher would draw this box in, inflated. */
    private fun select(views: RemoteViews, widthDp: Float, heightDp: Float): View =
        VariantSelection.forSize(views, context, SizeF(widthDp, heightDp))
            .apply(context, FrameLayout(context))

    private fun assertRows(
        widthDp: Float,
        heightDp: Float,
        name: Boolean,
        footer: Boolean,
        timer: Timer = this.timer,
    ) {
        val view = select(build(timer), widthDp, heightDp)

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

    private companion object {
        /**
         * A dp either side of each threshold's width, plus the widths real cells come in
         * at and one below anything the provider allows. The boundaries are where a rule
         * and a nearest-neighbour lookup part company, so they are most of the list.
         */
        val PROBED_WIDTHS = listOf(
            30f, 39f, 40f, 41f, 64f, 91f, 92f, 93f, 105f, 109f, 110f, 111f,
            129f, 130f, 131f, 216f, 344f, 600f,
        )

        /** The same for heights. */
        val PROBED_HEIGHTS = listOf(
            30f, 39f, 40f, 41f, 45f, 49f, 50f, 51f, 67f, 68f, 69f, 76f, 84f,
            109f, 110f, 111f, 152f, 300f,
        )
    }
}
