package com.calebjcox.countdownwidgets.widget

import android.app.WallpaperManager
import android.app.WallpaperColors
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.calebjcox.countdownwidgets.R
import com.calebjcox.countdownwidgets.core.TextTheme
import com.calebjcox.countdownwidgets.core.TextTone
import com.calebjcox.countdownwidgets.core.resolveTextTone
import com.calebjcox.countdownwidgets.data.Timer

/**
 * The two colours a widget draws its text in.
 *
 * A widget with its own background is a self-contained card: the panel and the
 * text come from the same light/dark resource pair and always agree. A widget
 * without one is drawing straight onto the wallpaper, and has to make the same
 * decision a launcher makes for its icon labels — which is a question about the
 * *wallpaper*, not about the system theme. Those two signals part company exactly
 * when it matters: a phone in light mode with a dark photo behind the widget.
 */
object WidgetPalette {

    data class Colors(@ColorInt val primary: Int, @ColorInt val secondary: Int)

    fun forTimer(context: Context, timer: Timer): Colors =
        forAppearance(context, timer.showBackground, timer.textTheme)

    /**
     * The same resolution from loose values, so the editor's preview can show what
     * a timer would look like before there is a [Timer] to ask about.
     */
    fun forAppearance(context: Context, showBackground: Boolean, textTheme: TextTheme): Colors {
        if (showBackground) {
            // Sitting on our own panel; the -night resources already pair correctly.
            return Colors(
                primary = color(context, R.color.widget_text_primary),
                secondary = color(context, R.color.widget_text_secondary),
            )
        }

        val tone = resolveTextTone(
            theme = textTheme,
            wallpaperSupportsDarkText = wallpaperSupportsDarkText(context),
            systemInDarkMode = systemInDarkMode(context),
        )

        return when (tone) {
            TextTone.LIGHT -> Colors(
                primary = color(context, R.color.widget_on_wallpaper_light_primary),
                secondary = color(context, R.color.widget_on_wallpaper_light_secondary),
            )
            TextTone.DARK -> Colors(
                primary = color(context, R.color.widget_on_wallpaper_dark_primary),
                secondary = color(context, R.color.widget_on_wallpaper_dark_secondary),
            )
        }
    }

    /**
     * The wallpaper's own opinion on whether dark text reads against it — the same
     * hint a launcher uses. Null when it has none to give, which live wallpapers
     * are entitled to do.
     *
     * Reading it needs no permission, but it is a binder call into another process
     * during a home-screen redraw, so anything it throws is swallowed: a widget
     * that picks a fallback colour is very much better than one that crashes.
     */
    private fun wallpaperSupportsDarkText(context: Context): Boolean? = runCatching {
        val hints = WallpaperManager.getInstance(context)
            ?.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            ?.colorHints
            ?: return null
        hints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
    }.getOrNull()

    private fun systemInDarkMode(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    @ColorInt
    private fun color(context: Context, resId: Int): Int = ContextCompat.getColor(context, resId)
}
