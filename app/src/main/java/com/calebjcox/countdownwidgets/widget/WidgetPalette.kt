package com.calebjcox.countdownwidgets.widget

import android.app.WallpaperManager
import android.app.WallpaperColors
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.calebjcox.countdownwidgets.R
import com.calebjcox.countdownwidgets.core.Backdrop
import com.calebjcox.countdownwidgets.core.TextTheme
import com.calebjcox.countdownwidgets.core.TextTone
import com.calebjcox.countdownwidgets.core.chosenTone
import com.calebjcox.countdownwidgets.core.drawsOnWallpaper
import com.calebjcox.countdownwidgets.core.resolveTextTone
import com.calebjcox.countdownwidgets.data.Timer

/**
 * What the widget's text is drawn in, and what it is drawn on.
 *
 * One rule covers all three backdrops: **the surface goes the opposite way from the
 * text.** Light text gets the dark scrim or the dark panel, dark text the light ones, and
 * a widget with no surface at all gets whatever the wallpaper can carry. That is what
 * makes a named colour worth offering on every backdrop — choosing Black cannot strand the
 * text on a dark card, because choosing Black is also what makes the card light.
 *
 * What differs between the backdrops is only who answers [TextTheme.AUTO], and
 * `Backdrop.drawsOnWallpaper` is the whole of that question. On [Backdrop.NONE] the text
 * is on the photo, so the photo is asked, through the same
 * `WallpaperColors.HINT_SUPPORTS_DARK_TEXT` a launcher uses for its icon labels — a
 * question no resource qualifier asks, and one the system theme answers wrongly exactly
 * when it matters, on a light-mode phone with a dark photo. On a scrim or a panel the text
 * is on a surface this app draws, that surface follows the system theme, and so the theme
 * decides and the wallpaper has no say.
 *
 * Which is why [TextColors] has two shapes rather than one. A tone somebody named is the
 * same tone in every configuration and can be frozen the moment it is read. Auto on a
 * surface this app draws cannot be frozen at all: a colour resolved here is resolved in
 * this process, at build time, and then kept by the launcher, while the surface behind it
 * is resolved from the same resources at every inflate. Freeze one and not the other and
 * a phone switched to dark mode shows the old tone on the new surface until something
 * redraws the widget, which for a day-precision timer is the next midnight. So Auto is
 * handed over as a resource id and the launcher resolves both together.
 */
object WidgetPalette {

    data class Colors(@ColorInt val primary: Int, @ColorInt val secondary: Int)

    /**
     * How a variant states its text colour.
     *
     * [Fixed] is a colour decided here and frozen; [Themed] is a pair of resource ids for
     * the host to resolve every time it draws, which is the only way a widget follows a
     * change of system theme without being redrawn. See the note above for which is which
     * and why it is not a free choice.
     */
    sealed interface TextColors {
        data class Fixed(val colors: Colors) : TextColors

        data class Themed(@ColorRes val primary: Int, @ColorRes val secondary: Int) : TextColors
    }

    /** What `setBackgroundResource` takes to mean "none": see [backgroundFor]. */
    const val NO_BACKGROUND = 0

    /**
     * The text colours, and how the caller has to state them.
     *
     * The one place the rule lives. Everything else here and in `WidgetRenderer` reads its
     * answer rather than working the question out again.
     */
    fun textColorsFor(
        context: Context,
        backdrop: Backdrop,
        textTheme: TextTheme,
    ): TextColors {
        val named = textTheme.chosenTone
        if (named != null) return TextColors.Fixed(pairFor(context, named))

        // Auto. On the wallpaper the answer is the wallpaper's and has to be taken now;
        // anywhere else it is the theme's and has to be left to the host.
        return if (backdrop.drawsOnWallpaper) {
            TextColors.Fixed(pairFor(context, toneFromWallpaper(context)))
        } else {
            when (backdrop) {
                Backdrop.SCRIM -> TextColors.Themed(
                    primary = R.color.widget_on_scrim_primary,
                    secondary = R.color.widget_on_scrim_secondary,
                )
                // The panel and its text have always come from one qualifier, which is
                // what keeps them a matched pair; Auto here is that pair, unchanged.
                else -> TextColors.Themed(
                    primary = R.color.widget_text_primary,
                    secondary = R.color.widget_text_secondary,
                )
            }
        }
    }

    fun forTimer(context: Context, timer: Timer): Colors =
        forAppearance(context, timer.backdrop, timer.textTheme)

    /**
     * The same answer resolved to colours, for the editor's preview — where a colour
     * resolved now really is a colour drawn now, because the activity is redrawn on a
     * configuration change and the widget is not.
     */
    fun forAppearance(context: Context, backdrop: Backdrop, textTheme: TextTheme): Colors =
        when (val colors = textColorsFor(context, backdrop, textTheme)) {
            is TextColors.Fixed -> colors.colors
            is TextColors.Themed -> Colors(
                primary = color(context, colors.primary),
                secondary = color(context, colors.secondary),
            )
        }

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
     * Always a resource id rather than a colour, which is what lets the two Auto entries
     * be qualified drawables and follow the theme on their own.
     */
    @DrawableRes
    fun backgroundFor(context: Context, backdrop: Backdrop, textTheme: TextTheme): Int {
        if (backdrop == Backdrop.NONE) return NO_BACKGROUND
        val named = textTheme.chosenTone
            ?: return when (backdrop) {
                Backdrop.SCRIM -> R.drawable.widget_scrim_auto
                else -> R.drawable.widget_background
            }
        return when (backdrop) {
            Backdrop.SCRIM -> if (named.isLight) {
                R.drawable.widget_scrim_dark
            } else {
                R.drawable.widget_scrim_light
            }
            else -> if (named.isLight) {
                R.drawable.widget_panel_dark
            } else {
                R.drawable.widget_panel_light
            }
        }
    }

    /**
     * The colour of that surface, for the editor's preview, which blends rather than
     * layers. Null where there is nothing between the text and the wallpaper.
     *
     * A panel's colour is opaque and a scrim's is not, which the preview needs no special
     * case for: compositing an opaque colour over the stand-in gives the colour back.
     */
    @ColorInt
    fun surfaceColorFor(context: Context, backdrop: Backdrop, textTheme: TextTheme): Int? {
        val background = backgroundFor(context, backdrop, textTheme)
        if (background == NO_BACKGROUND) return null
        return color(context, surfaceColorRes(backdrop, textTheme))
    }

    /** The colour each background drawable is filled with; see [surfaceColorFor]. */
    @ColorRes
    private fun surfaceColorRes(backdrop: Backdrop, textTheme: TextTheme): Int {
        val named = textTheme.chosenTone
            ?: return when (backdrop) {
                Backdrop.SCRIM -> R.color.widget_scrim_auto
                else -> R.color.widget_background
            }
        return when (backdrop) {
            Backdrop.SCRIM -> if (named.isLight) R.color.widget_scrim_dark else R.color.widget_scrim_light
            else -> if (named.isLight) R.color.widget_panel_dark else R.color.widget_panel_light
        }
    }

    /** What each tone is drawn in. The same four pairs on every backdrop. */
    private fun pairFor(context: Context, tone: TextTone): Colors = when (tone) {
        TextTone.LIGHT -> Colors(
            primary = color(context, R.color.widget_tone_light_primary),
            secondary = color(context, R.color.widget_tone_light_secondary),
        )
        TextTone.DARK -> Colors(
            primary = color(context, R.color.widget_tone_dark_primary),
            secondary = color(context, R.color.widget_tone_dark_secondary),
        )
        TextTone.WHITE -> Colors(
            primary = color(context, R.color.widget_tone_white_primary),
            secondary = color(context, R.color.widget_tone_white_secondary),
        )
        TextTone.BLACK -> Colors(
            primary = color(context, R.color.widget_tone_black_primary),
            secondary = color(context, R.color.widget_tone_black_secondary),
        )
    }

    /** Auto on the wallpaper: the photo's own answer, with the theme as a fallback. */
    private fun toneFromWallpaper(context: Context): TextTone = resolveTextTone(
        theme = TextTheme.AUTO,
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
