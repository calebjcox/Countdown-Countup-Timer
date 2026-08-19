package com.calebjcox.countdownwidgets.core

/**
 * What sits behind the widget's text.
 *
 * [SCRIM] is the middle answer to a problem neither end solves: a widget drawn straight
 * onto the wallpaper is at the mercy of the photo behind it, and one on a solid panel has
 * stopped showing the wallpaper at all. A scrim is a translucent wash the opposite tone
 * from the text, so the wallpaper still reads through it and the contrast no longer
 * depends on what the wallpaper is doing.
 */
enum class Backdrop { NONE, SCRIM, PANEL }

/** What the user asked for when the widget has no panel of its own. */
enum class TextTheme { AUTO, LIGHT, DARK, WHITE, BLACK }

/**
 * The colour the text is actually drawn in.
 *
 * Two pairs rather than two values. [LIGHT] and [DARK] are tinted from the device's
 * Material You palette, which is what makes a widget look like it belongs on the home
 * screen — and is also how they fail: a wallpaper with a strong accent tints them towards
 * itself, and text a few steps from the colour behind it is text nobody can read. [WHITE]
 * and [BLACK] are plain, so they give that up and cannot fail that way.
 *
 * @property isLight whether this tone reads as light. It is what decides which way a
 *   scrim goes — see Backdrop.SCRIM — and the two pairs answer it the same way, which is
 *   the point of asking the tone rather than the theme.
 */
enum class TextTone(val isLight: Boolean) {
    LIGHT(isLight = true),
    DARK(isLight = false),
    WHITE(isLight = true),
    BLACK(isLight = false),
}

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
 * Only [TextTheme.AUTO] asks any of that. The four named themes are the user saying they
 * have looked at the wallpaper themselves, so they pass through whatever the hint says.
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
    TextTheme.WHITE -> TextTone.WHITE
    TextTheme.BLACK -> TextTone.BLACK
    TextTheme.AUTO -> when (wallpaperSupportsDarkText) {
        // A wallpaper that can carry dark text is a light wallpaper.
        true -> TextTone.DARK
        false -> TextTone.LIGHT
        null -> if (systemInDarkMode) TextTone.LIGHT else TextTone.DARK
    }
}
