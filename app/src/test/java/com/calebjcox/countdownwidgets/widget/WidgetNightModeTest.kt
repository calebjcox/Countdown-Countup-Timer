package com.calebjcox.countdownwidgets.widget

import android.content.Context
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
        val expected = ContextCompat.getColor(context, R.color.widget_on_wallpaper_light_primary)

        RuntimeEnvironment.setQualifiers("+night")
        assertEquals(
            "a light tone chosen for the wallpaper was overridden by the system theme",
            expected,
            apply(views).findViewById<TextView>(R.id.widget_value).currentTextColor,
        )
    }

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
