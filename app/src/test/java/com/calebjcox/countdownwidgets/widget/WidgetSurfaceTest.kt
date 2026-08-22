package com.calebjcox.countdownwidgets.widget

import android.content.Context
import android.util.SizeF
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.calebjcox.countdownwidgets.R
import com.calebjcox.countdownwidgets.core.Backdrop
import com.calebjcox.countdownwidgets.core.Precision
import com.calebjcox.countdownwidgets.core.ScrimStrength
import com.calebjcox.countdownwidgets.core.TextTheme
import com.calebjcox.countdownwidgets.core.TimeField
import com.calebjcox.countdownwidgets.core.TimerSpec
import com.calebjcox.countdownwidgets.data.Timer
import com.calebjcox.countdownwidgets.testing.VariantSelection
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Pins that changing a timer's backdrop changes the widget on the home screen.
 *
 * The bug: switching Tinted to Solid saved the setting and did nothing to the widget,
 * while Solid to Tinted worked. A `RemoteViews` is not a description of a widget, it is a
 * list of calls — and the host does not re-inflate to run them. When the incoming layout
 * id matches the one already on screen it *reapplies*, running the new list over the live
 * view, so every property the new list does not mention keeps the value the old list gave
 * it. Tinted set a background and four text colours; Solid set neither, trusting the
 * layout's own attributes, which only ever applied on a fresh inflate. So Solid inherited
 * the scrim and its text colour and looked exactly like Tinted.
 *
 * It stayed hidden while the two used different layouts, because a changed layout id is
 * the one thing that does force a fresh inflate — the asymmetry was already there, and
 * giving Tinted and Solid the same file is what exposed it. Which is the reason this test
 * is written as it is: not "Solid sets a background" but **a widget redrawn into any
 * backdrop is indistinguishable from one drawn into it from nothing**. Stated that way it
 * covers the property that was missed rather than the two that were noticed, and any
 * future property left to a layout attribute fails it the same way.
 *
 * The same rule the padding and the line count are already set under; see
 * WidgetRenderer.render.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetSurfaceTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val zone: ZoneId = ZoneId.of("America/Denver")

    private val nowMillis =
        LocalDateTime.parse("2026-08-19T13:37:42").atZone(zone).toInstant().toEpochMilli()

    private val timer = Timer(
        id = "christmas",
        name = "Christmas",
        spec = TimerSpec.of(
            target = LocalDateTime.parse("2026-12-25T00:00"),
            precision = Precision.DATE,
            fields = setOf(TimeField.YEAR, TimeField.MONTH, TimeField.DAY),
        ),
    )

    /**
     * Every backdrop reached from every other, including from itself — a redraw that
     * changes nothing is the case a fix could break by writing the wrong value rather
     * than by writing none.
     */
    @Test
    fun `a widget redrawn into a backdrop looks like one drawn into it from nothing`() {
        for (from in appearances()) {
            for (to in appearances()) {
                val redrawn = surfaceOf(redraw(from = from, to = to))
                val fresh = surfaceOf(draw(to))
                assertEquals(
                    "${describe(from)} -> ${describe(to)} kept something of the old backdrop",
                    fresh,
                    redrawn,
                )
            }
        }
    }

    /**
     * And the prompt a widget falls back to when its timer is deleted, which shares the
     * panel's layout and so inherits by the same route.
     */
    @Test
    fun `the unconfigured prompt is not left on the last timer's scrim`() {
        val scrimmed = timer.copy(backdrop = Backdrop.SCRIM, textTheme = TextTheme.WHITE)
        val root = draw(scrimmed)
        variant(null).reapply(context, root)

        assertEquals(
            "the prompt kept the deleted timer's scrim",
            R.drawable.widget_background,
            backgroundOf(root),
        )
        assertNull(
            "the prompt kept the deleted timer's wash over its own panel",
            root.backgroundTintList,
        )
        assertEquals(
            "the prompt kept the deleted timer's text colour",
            ContextCompat.getColor(context, R.color.widget_text_primary),
            root.findViewById<TextView>(R.id.widget_value).currentTextColor,
        )
    }

    /**
     * The backdrops, each with a text colour, since a scrim's surface depends on both —
     * and one scrim at a strength nobody would land on by accident, because the strength
     * is the other half of what a scrim leaves behind.
     */
    private fun appearances(): List<Timer> = listOf(
        timer.copy(backdrop = Backdrop.NONE, textTheme = TextTheme.LIGHT),
        timer.copy(backdrop = Backdrop.SCRIM, textTheme = TextTheme.WHITE),
        timer.copy(backdrop = Backdrop.SCRIM, textTheme = TextTheme.BLACK),
        timer.copy(
            backdrop = Backdrop.SCRIM,
            textTheme = TextTheme.WHITE,
            scrimStrength = ScrimStrength.MIN,
        ),
        timer.copy(backdrop = Backdrop.PANEL),
    )

    private fun describe(of: Timer): String =
        "${of.backdrop}/${of.textTheme}@${of.scrimStrength}"

    /**
     * What the host does with an update: reapply onto the view already on screen when the
     * layout id still matches, and inflate afresh when it does not. Reapplying is the
     * whole of the bug, and inflating is what used to hide it, so the test has to do both
     * on the same terms the host picks between them.
     */
    private fun redraw(from: Timer, to: Timer): LinearLayout {
        val root = draw(from)
        val next = variant(to)
        if (next.layoutId != variant(from).layoutId) return draw(to)
        next.reapply(context, root)
        return root
    }

    private fun draw(of: Timer?): LinearLayout =
        variant(of).apply(context, FrameLayout(context)) as LinearLayout

    private fun variant(of: Timer?): RemoteViews = VariantSelection.forSize(
        WidgetRenderer.build(
            context = context,
            appWidgetId = 1,
            timer = of,
            nowMillis = nowMillis,
            zone = zone,
            cells = listOf(CELL),
        ),
        context,
        CELL,
    )

    /**
     * Everything about the widget that says which backdrop it is on: what is behind the
     * text, and what colour every row of it is drawn in.
     */
    private fun surfaceOf(root: LinearLayout): Map<String, Int?> = mapOf(
        "background" to backgroundOf(root),
        "tint" to root.backgroundTintList?.defaultColor,
        "name" to root.findViewById<TextView>(R.id.widget_name).currentTextColor,
        "value" to root.findViewById<TextView>(R.id.widget_value).currentTextColor,
        "ticker" to root.findViewById<Chronometer>(R.id.widget_ticker).currentTextColor,
        "footer" to root.findViewById<TextView>(R.id.widget_footer).currentTextColor,
    )

    /** The drawable resource behind the root, or null where it has no background at all. */
    private fun backgroundOf(root: LinearLayout): Int? =
        root.background?.let { shadowOf(it).createdFromResId }

    private companion object {
        /** Roomy enough for all three rows, so every colour is on screen. */
        val CELL = SizeF(250f, 120f)
    }
}
