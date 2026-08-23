package com.calebjcox.countdownwidgets.ui

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.calebjcox.countdownwidgets.R
import com.calebjcox.countdownwidgets.core.Backdrop
import com.calebjcox.countdownwidgets.core.Precision
import com.calebjcox.countdownwidgets.core.TimeField
import com.calebjcox.countdownwidgets.core.TimerSpec
import com.calebjcox.countdownwidgets.data.Timer
import com.calebjcox.countdownwidgets.data.TimerStore
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins that the editor's explanatory text stays out of the way until it is asked for —
 * and that "the editor's explanatory text" keeps meaning all of it.
 *
 * The activity hides the hints from a list of its own, so a hint added to the layout and
 * left out of that list would simply stay on screen: no assertion anywhere near the
 * change, and nothing to notice it. So these do not read that list. They take every
 * string named `*_hint`, which is the convention the layout already follows, find what is
 * showing it, and require the setting to move it — which makes the seventh hint somebody
 * adds fail here rather than ship.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EditTimerHintsTest {

    /**
     * Tinted, and that is load-bearing rather than incidental.
     *
     * One of the six hints lives inside the group `syncUi` shows only for
     * [Backdrop.SCRIM], and on any other backdrop the two tests below go blind to it in
     * opposite directions: one reads `isShown`, which a hidden parent makes false however
     * the hint itself is set, and the other reads `visibility`, which nothing has touched
     * and so is the layout's own VISIBLE. Both would pass with that hint wired to
     * nothing. Putting the fixture on the backdrop that shows the group makes the two
     * readings mean the same thing for all six.
     */
    private val timer = Timer(
        id = "christmas",
        name = "Christmas",
        spec = TimerSpec.of(
            LocalDateTime.parse("2026-12-25T00:00"),
            Precision.DATE,
            setOf(TimeField.MONTH, TimeField.DAY),
        ),
        backdrop = Backdrop.SCRIM,
    )

    @Test
    fun `every hint is off screen until the reader asks for it`() {
        val activity = editor()

        val shown = hintTexts().filter { hint ->
            viewShowing(activity.window.decorView, hint)?.isShown != false
        }
        assertEquals("hints on screen with the setting off", emptyList<String>(), shown)
    }

    @Test
    fun `turning help on brings all of them back`() {
        store().setShowHints(true)
        val activity = editor()

        val missing = hintTexts().filter { hint ->
            viewShowing(activity.window.decorView, hint)?.visibility != View.VISIBLE
        }
        assertEquals("hints still hidden with the setting on", emptyList<String>(), missing)
    }

    /**
     * The menu item, and the fact that it is remembered. Two timers are opened rather
     * than one, because what makes the setting worth storing is the second one.
     */
    @Test
    fun `the menu item toggles help and the next timer opens the same way`() {
        val first = editor()
        assertFalse("help started on", store().showHints())

        toggleHelp(first)

        assertTrue("the menu item did not turn help on", store().showHints())
        assertEquals(
            View.VISIBLE,
            viewShowing(first.window.decorView, hintTexts().first())?.visibility,
        )

        val second = editor()
        assertEquals(
            "the next timer opened with help off again",
            View.VISIBLE,
            viewShowing(second.window.decorView, hintTexts().first())?.visibility,
        )
        assertTrue(
            "the menu does not show which way the setting is set",
            second.findViewById<Toolbar>(R.id.toolbar).menu
                .findItem(R.id.action_show_hints).isChecked,
        )
    }

    /**
     * The other half of the arrangement, on the backdrop that hides the opacity group.
     *
     * `applyHints` sets the hints without consulting the backdrop, and `syncUi` sets the
     * group without consulting the switch, on the reasoning that a child keeps its own
     * visibility while its parent is gone — so whichever says no wins and neither has to
     * ask what the other decided. This is that reasoning rather than an assumption: with
     * help on and the group hidden, the hint inside it is still VISIBLE in its own right
     * and still off screen.
     */
    @Test
    fun `a hidden group does not un-hide the hint inside it, or the other way round`() {
        store().setShowHints(true)
        store().replaceAll(listOf(timer.copy(backdrop = Backdrop.NONE)))
        val activity = Robolectric.buildActivity(
            EditTimerActivity::class.java,
            EditTimerActivity.editIntent(RuntimeEnvironment.getApplication(), "christmas"),
        ).setup().get()

        val hint = activity.findViewById<View>(R.id.scrim_opacity_hint)
        assertEquals("help on did not reach the hint itself", View.VISIBLE, hint.visibility)
        assertFalse("a hint in a hidden group was on screen", hint.isShown)
    }

    /** Delete needs a timer to delete; the help switch does not, and both share a menu. */
    @Test
    fun `a timer that has never been saved still has the help switch`() {
        val activity = Robolectric.buildActivity(
            EditTimerActivity::class.java,
            EditTimerActivity.editIntent(RuntimeEnvironment.getApplication(), null),
        ).setup().get()

        val menu = activity.findViewById<Toolbar>(R.id.toolbar).menu
        assertTrue("no help switch on a new timer", menu.findItem(R.id.action_show_hints).isVisible)
        assertFalse("a new timer offered to delete itself", menu.findItem(R.id.action_delete).isVisible)
    }

    private fun toggleHelp(activity: EditTimerActivity) {
        activity.findViewById<Toolbar>(R.id.toolbar).menu
            .performIdentifierAction(R.id.action_show_hints, 0)
    }

    /** Every string the layout calls a hint, resolved to the words it puts on screen. */
    private fun hintTexts(): List<String> {
        val context = RuntimeEnvironment.getApplication()
        val ids = R.string::class.java.fields
            .filter { it.name.endsWith("_hint") }
            .map { it.name to it.getInt(null) }
        assertTrue("no hint strings found at all", ids.isNotEmpty())
        return ids.map { (_, id) -> context.getString(id) }
    }

    /** The `TextView` showing exactly this text, or null if nothing does. */
    private fun viewShowing(root: View, text: String): View? = when {
        root is TextView && root.text?.toString() == text -> root
        root is ViewGroup -> (0 until root.childCount)
            .asSequence()
            .mapNotNull { viewShowing(root.getChildAt(it), text) }
            .firstOrNull()

        else -> null
    }

    private fun store() = TimerStore(RuntimeEnvironment.getApplication())

    private fun editor(): EditTimerActivity {
        store().replaceAll(listOf(timer))
        return Robolectric.buildActivity(
            EditTimerActivity::class.java,
            EditTimerActivity.editIntent(RuntimeEnvironment.getApplication(), "christmas"),
        ).setup().get()
    }
}
