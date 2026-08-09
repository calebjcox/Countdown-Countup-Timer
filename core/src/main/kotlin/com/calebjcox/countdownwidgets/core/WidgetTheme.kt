package com.calebjcox.countdownwidgets.core

/** What the user asked for when the widget has no background of its own. */
enum class TextTheme { AUTO, LIGHT, DARK }

/** The colour the text is actually drawn in. */
enum class TextTone { LIGHT, DARK }

/**
 * Decides whether text sitting directly on the wallpaper should be light or dark.
 *
 * A widget with no background of its own has the same problem a launcher has with
 * its icon labels, and the platform exposes the same answer: the wallpaper reports
 * whether dark text is legible on it. Following the system's light/dark *theme*
 * instead is the obvious-looking mistake — a phone in light mode with a dark
 * photo wallpaper wants light text, and the two signals disagree exactly when it
 * matters.
 *
 * @param wallpaperSupportsDarkText the wallpaper's own hint, or null when it
 *   publishes none — some live wallpapers do not.
 * @param systemInDarkMode only consulted as a fallback, when there is no hint.
 */
fun resolveTextTone(
    theme: TextTheme,
    wallpaperSupportsDarkText: Boolean?,
    systemInDarkMode: Boolean,
): TextTone = when (theme) {
    TextTheme.LIGHT -> TextTone.LIGHT
    TextTheme.DARK -> TextTone.DARK
    TextTheme.AUTO -> when (wallpaperSupportsDarkText) {
        // A wallpaper that can carry dark text is a light wallpaper.
        true -> TextTone.DARK
        false -> TextTone.LIGHT
        null -> if (systemInDarkMode) TextTone.LIGHT else TextTone.DARK
    }
}
