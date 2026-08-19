package com.calebjcox.countdownwidgets.widget

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.SizeF
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.calebjcox.countdownwidgets.R
import com.calebjcox.countdownwidgets.core.Backdrop
import com.calebjcox.countdownwidgets.core.Precision
import com.calebjcox.countdownwidgets.core.TextTheme
import com.calebjcox.countdownwidgets.core.TimeField
import com.calebjcox.countdownwidgets.core.TimerSpec
import com.calebjcox.countdownwidgets.data.Timer
import com.calebjcox.countdownwidgets.testing.VariantSelection
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins the widget's text to the configuration it is *drawn* in rather than the one it
 * was built in.
 *
 * The bug: a widget on its own panel had its text colours resolved in this process and
 * written into the `RemoteViews`, where they froze. The panel behind them did not — it
 * is a resource reference the launcher resolves each time it inflates the layout — so
 * switching the phone to dark mode turned the card dark and left the text the tone it
 * had been built with, dark on dark, until something happened to redraw the widget. A
 * timer counting in days redraws at midnight, so it stayed that way all evening.
 *
 * Building under one qualifier and applying under another is the whole test: it is what
 * a launcher does to a cached `RemoteViews` when the phone changes theme.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetNightModeTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val zone: ZoneId = ZoneId.of("America/Denver")

    private val nowMillis =
        LocalDateTime.parse("2026-08-09T13:37:42").atZone(zone).toInstant().toEpochMilli()

    private val timer = Timer(
        id = "filing-date",
        name = "Filing Date",
        spec = TimerSpec.of(
            target = LocalDateTime.parse("2024-08-01T00:00"),
            precision = Precision.DATE,
            fields = setOf(TimeField.YEAR, TimeField.MONTH, TimeField.DAY),
        ),
        backdrop = Backdrop.PANEL,
    )

    /** The two tones have to differ, or nothing below could tell them apart. */
    @Test
    fun `the panel's text has a night tone of its own`() {
        RuntimeEnvironment.setQualifiers("+notnight")
        val day = panelColors()
        RuntimeEnvironment.setQualifiers("+night")
        val night = panelColors()

        assertNotEquals(
            "widget_text_primary is the same colour in both themes",
            day.first,
            night.first,
        )
        assertNotEquals(
            "widget_text_secondary is the same colour in both themes",
            day.second,
            night.second,
        )
    }

    @Test
    fun `a panelled widget built in day mode draws night text after the switch`() {
        RuntimeEnvironment.setQualifiers("+notnight")
        val views = build()

        RuntimeEnvironment.setQualifiers("+night")
        val root = apply(views)
        val (primary, secondary) = panelColors()

        assertEquals(
            "the value kept its light-mode colour on a dark panel",
            primary,
            root.findViewById<TextView>(R.id.widget_value).currentTextColor,
        )
        assertEquals(
            "the name kept its light-mode colour on a dark panel",
            secondary,
            root.findViewById<TextView>(R.id.widget_name).currentTextColor,
        )
        assertEquals(
            "the footer kept its light-mode colour on a dark panel",
            secondary,
            root.findViewById<TextView>(R.id.widget_footer).currentTextColor,
        )
    }

    /** And the other way round, for a widget that was built in the dark. */
    @Test
    fun `a panelled widget built in night mode draws day text after the switch`() {
        RuntimeEnvironment.setQualifiers("+night")
        val views = build()

        RuntimeEnvironment.setQualifiers("+notnight")
        val root = apply(views)

        assertEquals(
            "the value kept its dark-mode colour on a light panel",
            panelColors().first,
            root.findViewById<TextView>(R.id.widget_value).currentTextColor,
        )
    }

    /**
     * The wallpaper variant is the case that must keep resolving in code: no qualifier
     * can answer "is the photo behind this widget light or dark", so the answer is
     * carried in the `RemoteViews` and a change of theme must not touch it.
     */
    @Test
    fun `a widget on the wallpaper keeps the tone it was given`() {
        RuntimeEnvironment.setQualifiers("+notnight")
        val onWallpaper = timer.copy(backdrop = Backdrop.NONE, textTheme = TextTheme.LIGHT)
        val views = build(onWallpaper)
        val expected = ContextCompat.getColor(context, R.color.widget_tone_light_primary)

        RuntimeEnvironment.setQualifiers("+night")
        assertEquals(
            "a light tone chosen for the wallpaper was overridden by the system theme",
            expected,
            apply(views).findViewById<TextView>(R.id.widget_value).currentTextColor,
        )
    }

    /**
     * The reported bug: Tinted with Auto stayed dark whatever the phone was set to.
     *
     * Auto used to be one question — what can this wallpaper carry — asked for every
     * backdrop. On a scrim that is the wrong question and its answer never moves: a phone
     * switched to dark mode has not changed its wallpaper, so the hint says what it always
     * said and the widget kept the tone it was built with forever.
     *
     * Stated as a change across a configuration rather than as a colour, because that is
     * what makes it independent of what the hint happens to return here: a tone frozen
     * from anything at all cannot vary between two applies of one RemoteViews, so the old
     * behaviour fails this whatever the wallpaper does.
     */
    @Test
    fun `a tinted widget follows a change of system theme`() {
        val tinted = timer.copy(backdrop = Backdrop.SCRIM, textTheme = TextTheme.AUTO)

        RuntimeEnvironment.setQualifiers("+notnight")
        val views = build(tinted)
        val byDay = surfaceOf(apply(views))

        RuntimeEnvironment.setQualifiers("+night")
        val byNight = surfaceOf(apply(views))

        assertNotEquals(
            "the tinted backdrop drew the same wash in both themes",
            byDay.first,
            byNight.first,
        )
        assertNotEquals(
            "the text on it kept one tone through both themes",
            byDay.second,
            byNight.second,
        )
        // And the right way round, so it cannot pass by following the theme backwards.
        assertEquals(
            "dark mode did not get the light tone",
            ContextCompat.getColor(context, R.color.widget_tone_light_primary),
            byNight.second,
        )
    }

    /**
     * The same for a panel, which is what Auto there has always done — kept here so the
     * two backdrops that draw their own surface are pinned to one rule rather than to two
     * that happen to agree.
     */
    @Test
    fun `a tinted widget and a panel answer Auto the same way`() {
        for (backdrop in listOf(Backdrop.SCRIM, Backdrop.PANEL)) {
            RuntimeEnvironment.setQualifiers("+night")
            val views = build(timer.copy(backdrop = backdrop, textTheme = TextTheme.AUTO))
            val darkTone = surfaceOf(apply(views)).second

            RuntimeEnvironment.setQualifiers("+notnight")
            val lightTone = surfaceOf(apply(views)).second

            assertNotEquals("$backdrop ignored the theme", darkTone, lightTone)
        }
    }

    /**
     * What the widget is drawn on, and what its value row is drawn in.
     *
     * The wash is read as a colour rather than as the resource it came from, and that is
     * the point of how Auto works: the drawable is one file either way, and it is the
     * colour *inside* it that carries the qualifier. A test that compared resource ids
     * would see no difference and would be checking the mechanism instead of the result.
     */
    private fun surfaceOf(root: LinearLayout): Pair<Int?, Int> =
        (root.background as? GradientDrawable)?.color?.defaultColor to
            root.findViewById<TextView>(R.id.widget_value).currentTextColor

    /** The panel's primary and secondary text colours in the current configuration. */
    private fun panelColors(): Pair<Int, Int> =
        ContextCompat.getColor(context, R.color.widget_text_primary) to
            ContextCompat.getColor(context, R.color.widget_text_secondary)

    private fun build(of: Timer = timer) = WidgetRenderer.build(
        context = context,
        appWidgetId = 1,
        timer = of,
        nowMillis = nowMillis,
        zone = zone,
        cells = listOf(CELL),
    )

    /** The variant a launcher would pick for [CELL], inflated in whatever theme is set. */
    private fun apply(views: android.widget.RemoteViews): LinearLayout =
        VariantSelection.forSize(views, context, CELL)
            .apply(context, FrameLayout(context)) as LinearLayout

    private companion object {
        /** Roomy enough to show all three rows, so every colour is on screen. */
        val CELL = SizeF(250f, 120f)
    }
}
