package com.calebjcox.countdownwidgets.widget

import android.content.Context
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.calebjcox.countdownwidgets.R
import com.calebjcox.countdownwidgets.core.Precision
import com.calebjcox.countdownwidgets.core.RowVisibility
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
 * Pins what happens when `RowVisibility.ALWAYS` asks for more rows than the box holds.
 *
 * This exists because the comment that used to carry it was wrong three times running,
 * and CI was green for all three, because nothing measured it. The claims were "the
 * overrun lands on the last row", then "every row loses a little, the value included"
 * — from a run in Robolectric's LEGACY graphics mode, where text metrics are stubbed and
 * a 12sp label reports 28px tall — then the right mechanism with the wrong row named at
 * the boundary. Prose could be wrong about this indefinitely; a measurement cannot.
 *
 * The numbers below are derived from `widget_label_text_min`, `widget_value_text_min`,
 * `widget_padding_compact`, `LABEL_SHARE` and the font's own metrics. Change any of those
 * and this fails rather than going quietly stale, which is the whole point of it being
 * here rather than in a paragraph.
 *
 * `@GraphicsMode(NATIVE)` is not optional, for the reason the second wrong version
 * existed: under the default LEGACY mode every measurement here is fiction.
 *
 * Two of the three facts are `LinearLayout` behaviour and do not depend on the font — the
 * rows giving way under a remaining-height `AT_MOST` spec, and the value holding an
 * `EXACTLY` box. Only [CLEAN_FLOOR_DP] is a measured pixel boundary, and Robolectric's
 * font is not a device's, so treat that one as indicative of the shape rather than exact.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetOverrunTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val zone: ZoneId = ZoneId.of("America/Denver")

    private val nowMillis =
        LocalDateTime.parse("2026-08-19T13:37:42").atZone(zone).toInstant().toEpochMilli()

    /** Both rows forced on, which is the only way to ask a small box for three rows. */
    private val timer = Timer(
        id = "christmas",
        name = "Christmas",
        spec = TimerSpec.of(
            target = LocalDateTime.parse("2026-12-25T00:00"),
            precision = Precision.DATE,
            fields = setOf(TimeField.MONTH, TimeField.DAY),
        ),
        nameVisibility = RowVisibility.ALWAYS,
        targetVisibility = RowVisibility.ALWAYS,
    )

    /**
     * The value keeps its box; the labels are what give way.
     *
     * `LinearLayout.measureVertical` passes `mTotalLength` as the height already used, so
     * each child is measured against what its predecessors left. The footer is last and
     * `wrap_content`, so it is squeezed to nothing; the name follows once the box is
     * tighter still. The value is immune because [WidgetRenderer] sets an exact height on
     * it, so its spec is `EXACTLY` — and that is the line a reader could undo. Swap it for
     * `wrap_content` or a weighted slot and the number starts giving way with the rest.
     */
    @Test
    fun `the labels give way and the value does not`() {
        val heights = (44 downTo 24).map { it.toFloat() }
        val values = heights.map { rows(it).value.measuredHeight }
        val names = heights.map { rows(it).name.measuredHeight }
        val footers = heights.map { rows(it).footer.measuredHeight }

        assertEquals(
            "the value's box changed with the cell, so it is no longer an EXACTLY spec",
            listOf(values.first()),
            values.distinct(),
        )
        assertEquals("the footer took height it was not left", listOf(0), footers.distinct())
        assertTrue(
            "the name grew as the box shrank: $names",
            names.zipWithNext().all { (taller, shorter) -> shorter <= taller },
        )
    }

    /**
     * Down to [CLEAN_FLOOR_DP] the overrun is spent out of the padding rather than off an
     * edge: the rows come to more than the content box, and the root's centre gravity
     * absorbs the difference by moving the first child up into the padding it was given.
     */
    @Test
    fun `nothing is clipped down to the measured floor`() {
        val floor = (44 downTo 20).map { it.toFloat() }.last { heightDp -> clipped(heightDp).isEmpty() }

        assertEquals(
            "the size at which the rows stop fitting moved; re-measure and update the " +
                "comment in WidgetRenderer.render that points here",
            CLEAN_FLOOR_DP,
            floor,
        )
        assertEquals(
            "something is clipped at the floor itself",
            emptyList<String>(),
            clipped(CLEAN_FLOOR_DP),
        )
    }

    /**
     * And the row that goes first is the value's *box*, not the name's top.
     *
     * The fact the comment got wrong twice, and the reason it is worth an assertion of its
     * own: the value's height is fixed, so it is the one thing that cannot shrink to fit,
     * and the stack it sits in overflows the bottom before the centre gravity has pushed
     * the first child off the top. Nothing legible is lost either way — the glyphs are
     * centred in the box — so only a measurement tells them apart.
     */
    @Test
    fun `below the floor the value's box overhangs before the name's top is cut`() {
        val justBelow = rows(CLEAN_FLOOR_DP - 1f)
        assertTrue(
            "the value's box did not overhang first",
            justBelow.value.bottom > justBelow.root.height,
        )
        assertTrue(
            "the name was cut at the same size the value first overhung",
            justBelow.name.top >= 0,
        )

        val lower = rows(CLEAN_FLOOR_DP - 2f)
        assertTrue("the name's top never goes negative", lower.name.top < 0)
    }

    /** Which visible rows fall outside the root, named for the failure message. */
    private fun clipped(heightDp: Float): List<String> {
        val laid = rows(heightDp)
        return listOf("name" to laid.name, "value" to laid.value, "footer" to laid.footer)
            .filter { (_, row) -> row.visibility == View.VISIBLE }
            .filter { (_, row) -> row.top < 0 || row.bottom > laid.root.height }
            .map { (id, row) -> "$id ${row.top}..${row.bottom} outside 0..${laid.root.height}" }
    }

    private class Rows(val root: LinearLayout) {
        val name: View = root.findViewById(R.id.widget_name)
        val value: View = root.findViewById(R.id.widget_value)
        val footer: View = root.findViewById(R.id.widget_footer)
    }

    /**
     * The variant a launcher would pick for this box, inflated and laid out at it — the
     * same route `WidgetRoundingTest` takes, because a layout fact has to come from a real
     * measure and layout pass rather than from the metrics that fed it.
     */
    private fun rows(heightDp: Float): Rows {
        val cell = SizeF(WIDTH_DP, heightDp)
        val views = WidgetRenderer.build(
            context = context,
            appWidgetId = 1,
            timer = timer,
            nowMillis = nowMillis,
            zone = zone,
            cells = listOf(cell),
        )
        val root = VariantSelection.forSize(views, context, cell)
            .apply(context, FrameLayout(context)) as LinearLayout
        val density = context.resources.displayMetrics.density
        val widthPx = (WIDTH_DP * density).toInt()
        val heightPx = (heightDp * density).toInt()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, widthPx, heightPx)
        return Rows(root)
    }

    private companion object {
        /** Narrow enough that the labels are at their floor, so height is the only variable. */
        const val WIDTH_DP = 40f

        /**
         * The smallest box that still draws all three rows without clipping any of them.
         *
         * Measured, not chosen. It sits under the 40dp the provider advertises as its
         * minimum, but the two are not directly comparable — `minResizeHeight` is a nominal
         * cell and this is the content box `AppWidgetHostView` measures after the launcher's
         * own inset. The gap between them is the headroom a host that insets gets to spend,
         * and stating it as a number here is what makes it checkable rather than a claim
         * about every launcher there will ever be.
         */
        const val CLEAN_FLOOR_DP = 32f
    }
}
