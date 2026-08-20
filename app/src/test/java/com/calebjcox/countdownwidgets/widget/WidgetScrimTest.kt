package com.calebjcox.countdownwidgets.widget

import android.content.Context
import android.graphics.Color
import android.util.SizeF
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.calebjcox.countdownwidgets.R
import com.calebjcox.countdownwidgets.core.Backdrop
import com.calebjcox.countdownwidgets.core.Precision
import com.calebjcox.countdownwidgets.core.ScrimStrength
import com.calebjcox.countdownwidgets.core.TextTheme
import com.calebjcox.countdownwidgets.core.TimeField
import com.calebjcox.countdownwidgets.core.TimerSpec
import com.calebjcox.countdownwidgets.data.Timer
import androidx.core.content.ContextCompat
import com.calebjcox.countdownwidgets.testing.VariantSelection
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment

/**
 * Pins the middle backdrop: a wash the text can be read on, drawn the other way from the
 * text itself.
 *
 * The report this answers: *"I like that its background is transparent, but none of the
 * text color options show well on my background."* Every colour the widget can draw on
 * the wallpaper is a bet about the photo underneath, and a photo bright at one end of a
 * line and dark at the other wins that bet against all of them. A scrim stops it being a
 * bet — which it only does while it goes the *opposite* way from the text, so that is
 * what these check rather than that a background exists.
 *
 * The direction is the part a refactor can silently invert: both colours are drawn from
 * the same resolved tone, so a single flipped condition in [WidgetPalette.scrimTintFor]
 * gives light text on a light wash, which looks deliberate in the editor and is illegible
 * on a home screen.
 *
 * How much of the wash is drawn is the timer's, and is checked apart from which wash it
 * is. The two are one colour by the time the widget has it — a hue at an alpha — and a
 * test that read them together could pass on a wash going the right way at a strength
 * nobody asked for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetScrimTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val zone: ZoneId = ZoneId.of("America/Denver")

    private val nowMillis =
        LocalDateTime.parse("2026-08-09T13:37:42").atZone(zone).toInstant().toEpochMilli()

    private val timer = Timer(
        id = "christmas",
        name = "Christmas",
        spec = TimerSpec.of(
            target = LocalDateTime.parse("2026-12-25T00:00"),
            precision = Precision.DATE,
            fields = setOf(TimeField.YEAR, TimeField.MONTH, TimeField.DAY),
        ),
    )

    @Test
    fun `a named tone turns the surface the other way, on a scrim and on a panel`() {
        // The rule that makes a named colour worth offering on a panel at all. The surface
        // follows the tone rather than the tone having to survive the surface, so choosing
        // Black is also what makes the card light and there is nothing left to strand the
        // text on. Both backdrops, because one rule covering both is the claim.
        //
        // A panel names its surface with a drawable and a scrim with a tint, so the two
        // are read differently and mean the same thing: which of the two colours this app
        // keeps for the purpose ended up behind the text.
        val panels = mapOf(
            TextTheme.LIGHT to R.drawable.widget_panel_dark,
            TextTheme.WHITE to R.drawable.widget_panel_dark,
            TextTheme.DARK to R.drawable.widget_panel_light,
            TextTheme.BLACK to R.drawable.widget_panel_light,
        )
        for ((theme, panel) in panels) {
            assertEquals(
                "$theme text on a panel got a surface the same way as itself",
                panel,
                backgroundOf(timer.copy(backdrop = Backdrop.PANEL, textTheme = theme)),
            )
        }

        val washes = mapOf(
            TextTheme.LIGHT to R.color.widget_scrim_dark,
            TextTheme.WHITE to R.color.widget_scrim_dark,
            TextTheme.DARK to R.color.widget_scrim_light,
            TextTheme.BLACK to R.color.widget_scrim_light,
        )
        for ((theme, hue) in washes) {
            assertEquals(
                "$theme text on a scrim got a wash the same way as itself",
                ContextCompat.getColor(context, hue),
                hueOf(washOf(timer.copy(backdrop = Backdrop.SCRIM, textTheme = theme))!!),
            )
        }
    }

    @Test
    fun `the strength dial moves the wash's alpha and nothing else`() {
        // What the dial is allowed to be: how much of one of two fixed hues is drawn. A
        // strength that reached the hue would be a second way to choose the tone, running
        // against the tone the text already chose.
        val strengths = listOf(ScrimStrength.MIN, 40, ScrimStrength.DEFAULT, ScrimStrength.MAX)
        val washes = strengths.map { strength ->
            washOf(
                timer.copy(
                    backdrop = Backdrop.SCRIM,
                    textTheme = TextTheme.WHITE,
                    scrimStrength = strength,
                ),
            )!!
        }

        assertEquals(
            "the strength changed which wash it was, not how much of it",
            listOf(ContextCompat.getColor(context, R.color.widget_scrim_dark)),
            washes.map(::hueOf).distinct(),
        )
        assertEquals(
            "a wash at full strength is not solid, which is what full strength means",
            255,
            Color.alpha(washes.last()),
        )
        for ((weaker, stronger) in washes.zipWithNext()) {
            assertTrue(
                "a higher strength did not draw more of the wash",
                Color.alpha(stronger) > Color.alpha(weaker),
            )
        }
    }

    @Test
    fun `the other two backdrops draw no scrim`() {
        // NONE is the wallpaper showing through, and PANEL brings its own background from
        // the layout — neither is a case a scrim has anything to add to. The tint is the
        // half of that a strength could leak through: a panel tinted at 10% is a panel
        // nobody can read.
        assertNull(root(timer.copy(backdrop = Backdrop.NONE)).background)
        assertNotNull(
            "the panel lost the background its layout declares",
            root(timer.copy(backdrop = Backdrop.PANEL)).background,
        )
        for (backdrop in listOf(Backdrop.NONE, Backdrop.PANEL)) {
            assertNull(
                "$backdrop was tinted by a strength that is not its business",
                washOf(timer.copy(backdrop = backdrop, scrimStrength = ScrimStrength.MIN)),
            )
        }
    }

    @Test
    fun `a scrim does not cost the variant its padding`() {
        // Setting a background applies the drawable's own padding to the view, so a
        // background set after the padding would quietly discard it and the rows would
        // run to the edge of the widget. The renderer sets it first; this is what says so.
        val bare = root(timer.copy(backdrop = Backdrop.NONE))
        val scrimmed = root(timer.copy(backdrop = Backdrop.SCRIM))

        assertEquals(
            "a scrim changed the padding the variant chose",
            listOf(
                bare.paddingLeft, bare.paddingTop, bare.paddingRight, bare.paddingBottom,
            ),
            listOf(
                scrimmed.paddingLeft, scrimmed.paddingTop,
                scrimmed.paddingRight, scrimmed.paddingBottom,
            ),
        )
    }

    @Test
    fun `nothing with a background of its own draws a text shadow`() {
        // A shadow separates text from a photo. On a scrim or a panel there is no photo —
        // the text is on a surface this app drew, at a contrast it chose — and the shadow
        // stops being separation: under dark text on the light wash it is a dark halo,
        // which reads as smudging. Both scrim tones, because the wrong one of the two is
        // exactly what a check of a single tone would miss.
        val withBackground = listOf(
            timer.copy(backdrop = Backdrop.SCRIM, textTheme = TextTheme.WHITE),
            timer.copy(backdrop = Backdrop.SCRIM, textTheme = TextTheme.BLACK),
            timer.copy(backdrop = Backdrop.SCRIM, textTheme = TextTheme.LIGHT),
            timer.copy(backdrop = Backdrop.SCRIM, textTheme = TextTheme.DARK),
            timer.copy(backdrop = Backdrop.PANEL),
        )
        for (of in withBackground) {
            for ((id, row) in rows(root(of))) {
                assertEquals(
                    "$id kept a shadow on backdrop=${of.backdrop} text=${of.textTheme}",
                    0f,
                    row.shadowRadius,
                    0f,
                )
            }
        }
    }

    @Test
    fun `the wallpaper keeps its shadow`() {
        // The other half of the pair, and the reason the shadow still exists at all: one
        // photo can be bright at one end of a line of text and dark at the other, so no
        // single colour survives it unaided.
        for ((id, row) in rows(root(timer.copy(backdrop = Backdrop.NONE)))) {
            assertTrue(
                "$id lost the shadow it needs on a photo",
                row.shadowRadius > 0f,
            )
        }
    }

    @Test
    fun `a scrim overrides the colours its shared layout declares`() {
        // What lets a scrim share the panel's layout. That file declares the panel's own
        // -night pair as an attribute on every row, and a scrim states all four itself —
        // so this fails the moment a row stops being recoloured, which is the only way
        // the sharing goes wrong and is invisible from the layout depending on it.
        //
        // Measured against the attribute rather than against another palette call: the
        // declared colour is the thing that would show through, so it is the thing to be
        // different from.
        val of = timer.copy(backdrop = Backdrop.SCRIM, textTheme = TextTheme.WHITE)
        val expected = WidgetPalette.forAppearance(context, Backdrop.SCRIM, TextTheme.WHITE)
        val declared = ContextCompat.getColor(context, R.color.widget_text_primary)

        for ((id, row) in rows(root(of))) {
            val wanted =
                if (id == "value" || id == "ticker") expected.primary else expected.secondary
            assertEquals("$id took the wrong colour", wanted, row.currentTextColor)
        }
        assertNotEquals(
            "white text is indistinguishable from the layout's own attribute",
            declared,
            expected.primary,
        )
    }

    /** The four rows that carry text, by the name their assertion messages use. */
    private fun rows(root: LinearLayout): List<Pair<String, TextView>> = listOf(
        "name" to root.findViewById(R.id.widget_name),
        "value" to root.findViewById(R.id.widget_value),
        "ticker" to root.findViewById<Chronometer>(R.id.widget_ticker),
        "footer" to root.findViewById(R.id.widget_footer),
    )

    /** The drawable resource behind the widget's root, or null where it has no background. */
    private fun backgroundOf(of: Timer): Int? =
        root(of).background?.let { shadowOf(it).createdFromResId }

    /**
     * The colour that background is tinted, or null where nothing tints it. On a scrim
     * this is the wash entire: the shape under it is one file, opaque, for every scrim
     * there is.
     */
    private fun washOf(of: Timer): Int? = root(of).backgroundTintList?.defaultColor

    /** A colour with its alpha put back to solid, so two strengths of one hue compare equal. */
    private fun hueOf(color: Int): Int = color or Color.BLACK

    private fun root(of: Timer): LinearLayout {
        val views = WidgetRenderer.build(
            context = context,
            appWidgetId = 1,
            timer = of,
            nowMillis = nowMillis,
            zone = zone,
            cells = listOf(CELL),
        )
        return VariantSelection.forSize(views, context, CELL)
            .apply(context, FrameLayout(context)) as LinearLayout
    }

    private companion object {
        /** Roomy enough for all three rows, so nothing here turns on a dropped one. */
        val CELL = SizeF(250f, 120f)
    }
}
