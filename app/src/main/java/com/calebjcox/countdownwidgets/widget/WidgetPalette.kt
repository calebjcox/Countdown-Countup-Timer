package com.calebjcox.countdownwidgets.widget

import android.app.WallpaperManager
import android.app.WallpaperColors
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.calebjcox.countdownwidgets.R
import com.calebjcox.countdownwidgets.core.Backdrop
import com.calebjcox.countdownwidgets.core.TextTheme
import com.calebjcox.countdownwidgets.core.TextTone
import com.calebjcox.countdownwidgets.core.resolveTextTone
import com.calebjcox.countdownwidgets.data.Timer

/**
 * The two colours a widget draws its text in, and the scrim it may draw them on.
 *
 * A widget on its own panel is a self-contained card: the panel and the text come from
 * the same light/dark resource pair and always agree. Anything short of a panel is drawing
 * over the wallpaper, and has to make the same decision a launcher makes for its icon
 * labels — which is a question about the *wallpaper*, not about the system theme. Those
 * two signals part company exactly when it matters: a phone in light mode with a dark
 * photo behind the widget.
 *
 * A scrim does not take the wallpaper out of that question, which is why it is on this
 * side of the split rather than the panel's. It is translucent by design, so the photo
 * still shows through it and still has a say in which tone looks right; what it removes is
 * the photo's ability to make the choice *unreadable*. Hence [scrimFor], which goes the
 * other way from whatever tone was resolved.
 *
 * Which is why only this side is answered here for a real widget. The panel colours below
 * are for views this process draws itself — the editor's preview — where a colour resolved
 * now is a colour drawn now. `WidgetRenderer` leaves the panelled widget's text to its
 * layout instead, because a `RemoteViews` colour outlives the configuration it was
 * resolved in; see the note there.
 */
object WidgetPalette {

    data class Colors(@ColorInt val primary: Int, @ColorInt val secondary: Int)

    fun forTimer(context: Context, timer: Timer): Colors =
        forAppearance(context, timer.backdrop, timer.textTheme)

    /**
     * The same resolution from loose values, so the editor's preview can show what
     * a timer would look like before there is a [Timer] to ask about.
     */
    fun forAppearance(context: Context, backdrop: Backdrop, textTheme: TextTheme): Colors {
        if (backdrop == Backdrop.PANEL) {
            // Sitting on our own panel; the -night resources already pair correctly.
            return Colors(
                primary = color(context, R.color.widget_text_primary),
                secondary = color(context, R.color.widget_text_secondary),
            )
        }

        return when (toneFor(context, textTheme)) {
            TextTone.LIGHT -> Colors(
                primary = color(context, R.color.widget_on_wallpaper_light_primary),
                secondary = color(context, R.color.widget_on_wallpaper_light_secondary),
            )
            TextTone.DARK -> Colors(
                primary = color(context, R.color.widget_on_wallpaper_dark_primary),
                secondary = color(context, R.color.widget_on_wallpaper_dark_secondary),
            )
            TextTone.WHITE -> Colors(
                primary = color(context, R.color.widget_on_wallpaper_white_primary),
                secondary = color(context, R.color.widget_on_wallpaper_white_secondary),
            )
            TextTone.BLACK -> Colors(
                primary = color(context, R.color.widget_on_wallpaper_black_primary),
                secondary = color(context, R.color.widget_on_wallpaper_black_secondary),
            )
        }
    }

    /** What `setBackgroundResource` takes to mean "none": see [backgroundFor]. */
    const val NO_BACKGROUND = 0

    /**
     * The drawable to put behind the text, or [NO_BACKGROUND] where the wallpaper is
     * meant to show through.
     *
     * Total rather than partial, because the caller has to be able to set a background on
     * every backdrop rather than only on the ones that want a picture. A widget whose
     * variant leaves the background alone keeps whichever background the last variant set
     * — the host reuses the view whenever the layout id matches — so "none" has to be a
     * value this can return rather than a reason not to ask.
     *
     * A scrim is always the opposite tone from the text, which is the whole of the
     * mechanism: a wash the same way as the text would move both together and change
     * nothing about whether one can be read on the other. Resolved from the tone rather
     * than from the theme, so the two plain colours pick a scrim on the same terms as the
     * two tinted ones and `AUTO` picks one from the same wallpaper hint the text came
     * from.
     */
    @DrawableRes
    fun backgroundFor(context: Context, backdrop: Backdrop, textTheme: TextTheme): Int =
        when (backdrop) {
            Backdrop.NONE -> NO_BACKGROUND
            Backdrop.PANEL -> R.drawable.widget_background
            Backdrop.SCRIM -> if (toneFor(context, textTheme).isLight) {
                R.drawable.widget_scrim_dark
            } else {
                R.drawable.widget_scrim_light
            }
        }

    /** The colour of that scrim, for the editor's preview, which blends rather than layers. */
    @ColorInt
    fun scrimColor(context: Context, backdrop: Backdrop, textTheme: TextTheme): Int? {
        if (backdrop != Backdrop.SCRIM) return null
        return if (toneFor(context, textTheme).isLight) {
            color(context, R.color.widget_scrim_dark)
        } else {
            color(context, R.color.widget_scrim_light)
        }
    }

    private fun toneFor(context: Context, textTheme: TextTheme): TextTone = resolveTextTone(
        theme = textTheme,
        wallpaperSupportsDarkText = wallpaperSupportsDarkText(context),
        systemInDarkMode = systemInDarkMode(context),
    )

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
