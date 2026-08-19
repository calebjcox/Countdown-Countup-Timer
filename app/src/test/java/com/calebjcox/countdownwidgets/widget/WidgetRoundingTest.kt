package com.calebjcox.countdownwidgets.widget

import android.content.Context
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.calebjcox.countdownwidgets.core.Backdrop
import com.calebjcox.countdownwidgets.core.Precision
import com.calebjcox.countdownwidgets.core.RowVisibility
import com.calebjcox.countdownwidgets.core.TimeField
import com.calebjcox.countdownwidgets.core.TimerSpec
import com.calebjcox.countdownwidgets.data.Timer
import com.calebjcox.countdownwidgets.testing.VariantSelection
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins that a launcher rounding this widget's corners rounds *the widget*.
 *
 * The bug: a 1x1 with no background of its own came out clipped to a lozenge, with the
 * ends of the countdown cut off — and the same widget with a background was untouched.
 * The launcher decides which view its corner radius belongs to, and when nothing says so
 * it guesses: Launcher3 walks down from the root through views that draw nothing, stops
 * at the first that draws something, and clips the whole widget to *that* view's
 * rectangle. On the wallpaper layout no view has a background, and a variant showing one
 * row has exactly one visible child at every level — so the guess ran all the way down to
 * the row of text and clipped the widget to a rounded box one line high. The layout with
 * a background stopped the walk at its own root, which is why only one of the two showed
 * it.
 *
 * [findBackground] is a transcription of the rule, from
 * `packages/apps/Launcher3/src/com/android/launcher3/widget/RoundedCornerEnforcement.java`.
 * Nothing on a unit-test classpath implements it, so unlike the best-fit rule in
 * `WidgetOrientationTest` this cannot be asserted against the original — which is the
 * reason it is written out in full here rather than summarised: a reader comparing the
 * two has the whole of ours in front of them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetRoundingTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val zone: ZoneId = ZoneId.of("America/Denver")

    private val nowMillis =
        LocalDateTime.parse("2026-08-16T09:51:00").atZone(zone).toInstant().toEpochMilli()

    private val timer = Timer(
        id = "trip",
        name = "Trip",
        spec = TimerSpec.of(
            target = LocalDateTime.parse("2027-08-01T00:00"),
            precision = Precision.DATE,
            fields = setOf(TimeField.MONTH, TimeField.WEEK, TimeField.DAY),
        ),
    )

    @Test
    fun `a launcher rounds the whole widget, on the wallpaper and on a panel`() {
        for (backdrop in Backdrop.entries) {
            for ((width, height) in CELLS) {
                assertRoundsTheRoot(timer.copy(backdrop = backdrop), width, height)
            }
        }
    }

    /**
     * The single-row sizes, which are where the guess had nowhere to stop. Both the rows
     * a cell is too small for and the rows a timer has switched off leave one behind, and
     * the second kind reaches every size — a timer showing neither its name nor its date
     * is one row on a 4x2 as surely as on a 1x1.
     */
    @Test
    fun `a widget down to its last row is still rounded as a whole`() {
        val bare = timer.copy(
            nameVisibility = RowVisibility.NEVER,
            targetVisibility = RowVisibility.NEVER,
        )
        for (backdrop in Backdrop.entries) {
            for ((width, height) in CELLS) {
                assertRoundsTheRoot(bare.copy(backdrop = backdrop), width, height)
            }
        }
    }

    private fun assertRoundsTheRoot(timer: Timer, widthDp: Float, heightDp: Float) {
        val host = render(timer, widthDp, heightDp)
        val root = host.getChildAt(0)

        assertSame(
            "at ${widthDp}x$heightDp with backdrop=${timer.backdrop} the " +
                "launcher would round a ${describe(findBackground(host))} rather than " +
                "the widget",
            root,
            findBackground(host),
        )
    }

    private fun describe(view: View): String =
        "${view.javaClass.simpleName}(${view.width}x${view.height})"

    /**
     * `RoundedCornerEnforcement.findBackground`: the view a launcher takes to be the
     * widget's background, and so the view whose corners it rounds — clipping the host to
     * that view's rectangle. The id is the only thing an app can say about it; everything
     * below the id is the guess.
     */
    private fun findBackground(appWidget: View): View {
        val backgrounds = mutableListOf<View>()
        accumulateViewsWithId(appWidget, android.R.id.background, backgrounds)
        if (backgrounds.size == 1) return backgrounds.single()
        if (appWidget is ViewGroup && appWidget.childCount > 0) {
            findUndefinedBackground(appWidget.getChildAt(0))?.let { return it }
        }
        return appWidget
    }

    /**
     * The guess. Down through the tree while each view draws nothing and has exactly one
     * visible child; the first view that draws something wins, and a view with two
     * visible children stops the descent at itself.
     */
    private fun findUndefinedBackground(current: View): View? {
        if (current.visibility != View.VISIBLE) return null
        if (isViewVisible(current)) return current
        var lastVisibleView: View? = null
        if (current is ViewGroup) {
            for (i in 0 until current.childCount) {
                val visibleView = findUndefinedBackground(current.getChildAt(i)) ?: continue
                if (lastVisibleView != null) return current
                lastVisibleView = visibleView
            }
        }
        return lastVisibleView
    }

    /** Draws something: a `TextView` always does, a bare `LinearLayout` never does. */
    private fun isViewVisible(view: View): Boolean =
        view.visibility == View.VISIBLE &&
            (!view.willNotDraw() || view.foreground != null || view.background != null)

    private fun accumulateViewsWithId(view: View, id: Int, into: MutableList<View>) {
        if (view.id == id) into.add(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) accumulateViewsWithId(view.getChildAt(i), id, into)
        }
    }

    /**
     * The variant a launcher would pick for this cell, inside a stand-in for the host
     * view it would be inflated into — the tree the rule above walks starts there, not at
     * the widget's own root.
     */
    private fun render(timer: Timer, widthDp: Float, heightDp: Float): ViewGroup {
        val views = WidgetRenderer.build(
            context = context,
            appWidgetId = 1,
            timer = timer,
            nowMillis = nowMillis,
            zone = zone,
            cells = listOf(SizeF(widthDp, heightDp)),
        )
        val host = FrameLayout(context)
        VariantSelection.forSize(views, context, SizeF(widthDp, heightDp)).apply(context, host)
            .also { host.addView(it) }

        val density = context.resources.displayMetrics.density
        val widthPx = (widthDp * density).toInt()
        val heightPx = (heightDp * density).toInt()
        host.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, widthPx, heightPx)
        return host
    }

    private companion object {
        /** One cell up to a 4x2, so every variant is covered. */
        val CELLS = listOf(
            64f to 76f,
            64f to 152f,
            105f to 84f,
            120f to 45f,
            130f to 92f,
            140f to 74f,
            216f to 100f,
            344f to 152f,
            400f to 60f,
        )
    }
}
