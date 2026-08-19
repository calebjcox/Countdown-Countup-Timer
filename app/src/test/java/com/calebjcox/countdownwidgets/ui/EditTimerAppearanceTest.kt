package com.calebjcox.countdownwidgets.ui

import android.view.View
import android.widget.TextView
import com.calebjcox.countdownwidgets.R
import com.calebjcox.countdownwidgets.core.Backdrop
import com.calebjcox.countdownwidgets.core.Precision
import com.calebjcox.countdownwidgets.core.RowVisibility
import com.calebjcox.countdownwidgets.core.TextTheme
import com.calebjcox.countdownwidgets.core.TimeField
import com.calebjcox.countdownwidgets.core.TimerSpec
import com.calebjcox.countdownwidgets.data.Timer
import com.calebjcox.countdownwidgets.data.TimerStore
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.ChipGroup
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins the editor's appearance controls to the fields they stand for, in both
 * directions: what a saved timer puts on screen, and what the screen saves back.
 *
 * The round trip is the whole test, because the two halves are written separately —
 * `syncUi` maps a field to a checked id, each listener maps a checked id back to a field
 * — and nothing but this notices when they stop agreeing. A group whose listener reads
 * the wrong button is a setting that silently resets itself the next time the editor is
 * opened, which is the kind of bug a user reports as "it didn't save".
 *
 * It also stands in for a compile check the layout cannot give. `text_theme_group` is a
 * `ChipGroup` rather than the `MaterialButtonToggleGroup` the choices beside it use —
 * five weighted buttons do not fit a phone — and the two have different selection APIs.
 * View binding types the field, so a mismatch is caught, but the *behaviour* difference
 * is not: a `ChipGroup` reports an empty selection during its own bookkeeping, and a
 * listener that reads that as "the user chose nothing" would wipe the setting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EditTimerAppearanceTest {

    private val saved = Timer(
        id = "christmas",
        name = "Christmas",
        spec = TimerSpec.of(
            LocalDateTime.parse("2026-12-25T00:00"),
            Precision.DATE,
            setOf(TimeField.MONTH, TimeField.DAY),
        ),
        backdrop = Backdrop.SCRIM,
        textTheme = TextTheme.BLACK,
        nameVisibility = RowVisibility.ALWAYS,
        targetVisibility = RowVisibility.NEVER,
    )

    @Test
    fun `the editor shows a saved timer's appearance and saves it back`() {
        // Every appearance field set away from its default, so a control left wired to
        // the default would show as a difference rather than as a coincidence.
        TimerStore(RuntimeEnvironment.getApplication()).replaceAll(listOf(saved))

        val intent = EditTimerActivity.editIntent(RuntimeEnvironment.getApplication(), "christmas")
        val activity = Robolectric.buildActivity(EditTimerActivity::class.java, intent)
            .setup().get()

        assertEquals(
            "backdrop group",
            R.id.backdrop_scrim,
            activity.findViewById<MaterialButtonToggleGroup>(R.id.backdrop_group).checkedButtonId,
        )
        assertEquals(
            "name group",
            R.id.name_always,
            activity.findViewById<MaterialButtonToggleGroup>(R.id.name_visibility_group)
                .checkedButtonId,
        )
        assertEquals(
            "target group",
            R.id.target_never,
            activity.findViewById<MaterialButtonToggleGroup>(R.id.target_visibility_group)
                .checkedButtonId,
        )
        assertEquals(
            "text theme chips",
            R.id.text_theme_black,
            activity.findViewById<ChipGroup>(R.id.text_theme_group).checkedChipId,
        )
        assertEquals(
            "preview footer",
            View.GONE,
            activity.findViewById<TextView>(R.id.preview_footer).visibility,
        )

        activity.findViewById<MaterialButtonToggleGroup>(R.id.backdrop_group)
            .check(R.id.backdrop_panel)
        activity.findViewById<MaterialButtonToggleGroup>(R.id.name_visibility_group)
            .check(R.id.name_when_room)
        activity.findViewById<MaterialButtonToggleGroup>(R.id.target_visibility_group)
            .check(R.id.target_always)
        activity.findViewById<ChipGroup>(R.id.text_theme_group).check(R.id.text_theme_white)

        // The text colour control stays offered on a solid panel: the panel follows the
        // tone, so a named colour cannot strand the text there.
        assertEquals(
            "text theme label on a panel",
            View.VISIBLE,
            activity.findViewById<View>(R.id.text_theme_label).visibility,
        )

        activity.findViewById<View>(R.id.save).performClick()

        val stored = TimerStore(RuntimeEnvironment.getApplication()).timer("christmas")!!
        assertEquals(Backdrop.PANEL, stored.backdrop)
        assertEquals(RowVisibility.WHEN_ROOM, stored.nameVisibility)
        assertEquals(RowVisibility.ALWAYS, stored.targetVisibility)
        assertEquals(TextTheme.WHITE, stored.textTheme)
    }
}
