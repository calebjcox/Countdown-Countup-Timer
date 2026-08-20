package com.calebjcox.countdownwidgets.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetThemeTest {

    @Test
    fun `auto puts light text on a dark wallpaper`() {
        assertEquals(
            TextTone.LIGHT,
            resolveTextTone(TextTheme.AUTO, wallpaperSupportsDarkText = false, systemInDarkMode = false),
        )
    }

    @Test
    fun `auto puts dark text on a light wallpaper`() {
        assertEquals(
            TextTone.DARK,
            resolveTextTone(TextTheme.AUTO, wallpaperSupportsDarkText = true, systemInDarkMode = true),
        )
    }

    @Test
    fun `the wallpaper beats the system theme when they disagree`() {
        // The reported bug: light mode, dark wallpaper. Following the theme gives
        // dark text on a dark photo; following the wallpaper gives a readable widget.
        assertEquals(
            TextTone.LIGHT,
            resolveTextTone(TextTheme.AUTO, wallpaperSupportsDarkText = false, systemInDarkMode = false),
        )
        assertEquals(
            TextTone.DARK,
            resolveTextTone(TextTheme.AUTO, wallpaperSupportsDarkText = true, systemInDarkMode = true),
        )
    }

    @Test
    fun `auto falls back to the system theme when the wallpaper says nothing`() {
        assertEquals(
            TextTone.LIGHT,
            resolveTextTone(TextTheme.AUTO, wallpaperSupportsDarkText = null, systemInDarkMode = true),
        )
        assertEquals(
            TextTone.DARK,
            resolveTextTone(TextTheme.AUTO, wallpaperSupportsDarkText = null, systemInDarkMode = false),
        )
    }

    @Test
    fun `an explicit choice ignores the wallpaper and the system theme`() {
        // Every theme but AUTO, so the two plain colours are held to the same promise as
        // the two tinted ones: chosen is chosen, whatever is behind the widget.
        val chosen = mapOf(
            TextTheme.LIGHT to TextTone.LIGHT,
            TextTheme.DARK to TextTone.DARK,
            TextTheme.WHITE to TextTone.WHITE,
            TextTheme.BLACK to TextTone.BLACK,
        )
        for ((theme, tone) in chosen) {
            for (hint in listOf(true, false, null)) {
                for (dark in listOf(true, false)) {
                    assertEquals(
                        "$theme override, hint=$hint darkMode=$dark",
                        tone,
                        resolveTextTone(theme, hint, dark),
                    )
                }
            }
        }
    }

    @Test
    fun `the plain tones side with the tinted ones they stand in for`() {
        // What a scrim is picked from — see WidgetPalette.scrimTintFor. White has to count as
        // light and black as dark, or a scrim would be drawn the same way as the text it
        // is meant to separate from the wallpaper, which is no separation at all.
        assertTrue(TextTone.WHITE.isLight)
        assertTrue(TextTone.LIGHT.isLight)
        assertFalse(TextTone.BLACK.isLight)
        assertFalse(TextTone.DARK.isLight)
    }

    @Test
    fun `a scrim strength is snapped to a step and held between the ends`() {
        // The slider stops on these by itself. Everything else that can set a strength —
        // a backup a user opened in a text editor, a file from a build whose step was
        // finer — comes through here, so what it guards is a widget drawn at a strength
        // its own editor cannot show or undo.
        assertEquals(ScrimStrength.MIN, ScrimStrength.snap(Int.MIN_VALUE))
        assertEquals(ScrimStrength.MAX, ScrimStrength.snap(Int.MAX_VALUE))
        assertEquals(45, ScrimStrength.snap(43))
        assertEquals(45, ScrimStrength.snap(47))

        for (percent in ScrimStrength.MIN..ScrimStrength.MAX) {
            val snapped = ScrimStrength.snap(percent)
            assertEquals("$percent landed off the step", 0, snapped % ScrimStrength.STEP)
            assertTrue(
                "$percent snapped to $snapped, further than half a step",
                kotlin.math.abs(snapped - percent) <= ScrimStrength.STEP / 2,
            )
        }
    }

    @Test
    fun `the strength the slider starts on is one it can stop on`() {
        // Both ends too: a range whose ends are off the step is a slider that refuses to
        // lay itself out at all.
        for (fixed in listOf(ScrimStrength.MIN, ScrimStrength.DEFAULT, ScrimStrength.MAX)) {
            assertEquals(fixed, ScrimStrength.snap(fixed))
        }
    }
}
