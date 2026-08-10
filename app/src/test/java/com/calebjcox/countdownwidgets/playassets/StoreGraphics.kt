package com.calebjcox.countdownwidgets.playassets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.calebjcox.countdownwidgets.R

/**
 * The two assets that are drawn rather than screenshotted: the 512x512 launcher icon
 * and the 1024x500 feature graphic.
 *
 * Both bake in fixed colours, which the shipped icon does not have. `ic_launcher.xml`
 * paints itself from `@android:color/system_accent1_100` and `_700` — framework
 * Material You resources, derived per device from the user's wallpaper — so on a phone
 * the icon has no single colour at all. A store listing needs one, and what resolves
 * here is the platform's own baseline palette, which is what a device with an
 * unremarkable wallpaper shows.
 */
object StoreGraphics {

    /** Play's launcher icon size. */
    private const val ICON_PX = 512

    /**
     * An adaptive icon is a 108dp canvas of which only the middle 72dp is ever
     * visible; the launcher masks away the rest. Play applies its own mask to a
     * full-bleed square, so the layers are scaled until that safe zone fills the
     * frame — 512/72 of the way up, which puts the full layer at 768px and its
     * corners 128px outside the frame on every side.
     */
    private const val ICON_LAYER_PX = ICON_PX * 108 / 72
    private const val ICON_LAYER_OFFSET = (ICON_LAYER_PX - ICON_PX) / 2

    private const val FEATURE_WIDTH_PX = 1024
    private const val FEATURE_HEIGHT_PX = 500

    /**
     * Renders the launcher icon full-bleed.
     *
     * The two adaptive layers are drawn directly rather than through
     * [AdaptiveIconDrawable.draw], which would apply the device mask and hand Play a
     * rounded icon to round again. Drawing the shipped layers — rather than
     * re-describing the clock face here — keeps `ic_launcher_foreground.xml` the only
     * place the artwork is defined.
     */
    fun icon(context: Context): Bitmap {
        val adaptive = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
        require(adaptive is AdaptiveIconDrawable) {
            "expected an adaptive icon, got ${adaptive?.javaClass?.name}"
        }

        val bitmap = Capture.bitmap(ICON_PX, ICON_PX, background(context))
        val canvas = Canvas(bitmap)
        canvas.translate(-ICON_LAYER_OFFSET.toFloat(), -ICON_LAYER_OFFSET.toFloat())
        adaptive.foreground?.apply {
            setBounds(0, 0, ICON_LAYER_PX, ICON_LAYER_PX)
            draw(canvas)
        }
        return bitmap
    }

    /** Kept clear on every edge; Play crops this asset in some placements. */
    private const val FEATURE_MARGIN_PX = 64

    /** Between the clock and the words. */
    private const val FEATURE_GAP_PX = 44

    /** The box the clock is drawn into. */
    private const val FEATURE_CLOCK_PX = 340

    /**
     * 1024x500, which Play requires before a listing can be published.
     *
     * The icon's own two colours, a wash between them, the clock face, and the app's
     * existing one-line description. Every text size is measured and fitted rather
     * than guessed: the tagline is a translatable string, so hard-coding a size that
     * happens to fit today is how it ends up running off the edge tomorrow.
     */
    fun featureGraphic(context: Context): Bitmap {
        val background = background(context)
        val foreground = foreground(context)

        val bitmap = Capture.bitmap(FEATURE_WIDTH_PX, FEATURE_HEIGHT_PX, background)
        val canvas = Canvas(bitmap)

        canvas.drawRect(
            0f,
            0f,
            FEATURE_WIDTH_PX.toFloat(),
            FEATURE_HEIGHT_PX.toFloat(),
            Paint().apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    FEATURE_WIDTH_PX.toFloat(),
                    FEATURE_HEIGHT_PX.toFloat(),
                    background,
                    ColorUtils.blendARGB(background, foreground, 0.28f),
                    Shader.TileMode.CLAMP,
                )
            },
        )

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = foreground
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val tagline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(foreground, 0xDD)
            typeface = Typeface.SANS_SERIF
            textSize = 36f
        }

        val textWidth = FEATURE_WIDTH_PX - 2 * FEATURE_MARGIN_PX - FEATURE_CLOCK_PX - FEATURE_GAP_PX
        val titleText = context.getString(R.string.app_name)
        title.textSize = fitTextSize(title, titleText, textWidth.toFloat(), from = 92f)
        val taglineLines = wrap(tagline, context.getString(R.string.widget_description), textWidth.toFloat())

        // Centre the clock and the words together, so the composition does not lean
        // whichever way the strings happen to measure.
        val widest = maxOf(
            title.measureText(titleText),
            taglineLines.maxOfOrNull { tagline.measureText(it) } ?: 0f,
        )
        val groupWidth = FEATURE_CLOCK_PX + FEATURE_GAP_PX + widest
        val groupLeft = (FEATURE_WIDTH_PX - groupWidth) / 2f

        val clockTop = (FEATURE_HEIGHT_PX - FEATURE_CLOCK_PX) / 2
        ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)?.apply {
            // The vector's own 108dp viewport, drawn whole: outside an adaptive icon
            // there is no mask to leave room for.
            setBounds(
                groupLeft.toInt(),
                clockTop,
                groupLeft.toInt() + FEATURE_CLOCK_PX,
                clockTop + FEATURE_CLOCK_PX,
            )
            draw(canvas)
        }

        val textLeft = groupLeft + FEATURE_CLOCK_PX + FEATURE_GAP_PX
        val lineHeight = tagline.textSize * 1.35f
        val blockHeight = title.textSize + 26f + lineHeight * taglineLines.size
        var y = (FEATURE_HEIGHT_PX - blockHeight) / 2f + title.textSize

        canvas.drawText(titleText, textLeft, y, title)
        y += 26f
        for (line in taglineLines) {
            y += lineHeight
            canvas.drawText(line, textLeft, y, tagline)
        }

        return bitmap
    }

    /** The largest size at or below [from] that keeps [text] inside [maxWidth]. */
    private fun fitTextSize(paint: Paint, text: String, maxWidth: Float, from: Float): Float {
        var size = from
        while (size > 12f) {
            paint.textSize = size
            if (paint.measureText(text) <= maxWidth) break
            size -= 1f
        }
        return size
    }

    /** Greedy word wrap at the paint's current size. */
    private fun wrap(paint: Paint, text: String, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var line = StringBuilder()
        for (word in text.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= maxWidth || line.isEmpty()) {
                line = StringBuilder(candidate)
            } else {
                lines += line.toString()
                line = StringBuilder(word)
            }
        }
        if (line.isNotEmpty()) lines += line.toString()
        return lines
    }

    /**
     * The icon's background, and the tripwire for the whole baked-colour idea.
     *
     * These come out of the platform's baseline Material You palette. If a Robolectric
     * upgrade ever moves that palette somewhere unusable — transparent, or so close to
     * the foreground that the clock face disappears — this is where it should be
     * noticed, not in a rejected store listing.
     */
    private fun background(context: Context): Int =
        ContextCompat.getColor(context, R.color.ic_launcher_background).also {
            check(Color.alpha(it) == 255) { "icon background is not opaque: ${hex(it)}" }
            check(contrast(it, foreground(context)) >= 3.0) {
                "icon foreground ${hex(foreground(context))} has too little contrast " +
                    "against background ${hex(it)}"
            }
        }

    /** The stroke colour `ic_launcher_foreground.xml` draws the clock face in. */
    private fun foreground(context: Context): Int =
        ContextCompat.getColor(context, android.R.color.system_accent1_700)

    private fun contrast(a: Int, b: Int): Double = ColorUtils.calculateContrast(b, a)

    private fun hex(color: Int): String = "#%08X".format(color)
}
