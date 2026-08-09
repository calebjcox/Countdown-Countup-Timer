package com.calebjcox.countdownwidgets.core

import org.junit.Assert.assertEquals
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
        for (hint in listOf(true, false, null)) {
            for (dark in listOf(true, false)) {
                assertEquals(
                    "LIGHT override, hint=$hint darkMode=$dark",
                    TextTone.LIGHT,
                    resolveTextTone(TextTheme.LIGHT, hint, dark),
                )
                assertEquals(
                    "DARK override, hint=$hint darkMode=$dark",
                    TextTone.DARK,
                    resolveTextTone(TextTheme.DARK, hint, dark),
                )
            }
        }
    }
}
