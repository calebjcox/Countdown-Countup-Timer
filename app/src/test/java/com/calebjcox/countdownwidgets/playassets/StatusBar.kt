package com.calebjcox.countdownwidgets.playassets

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The status bar across the top of every screenshot.
 *
 * This is the one piece of furniture in the set that the app does not draw itself, and
 * it is here for two reasons. It is what makes a home-screen composite read as a phone
 * rather than as text on a photograph. And on the app screens it is closer to the truth
 * than leaving it out: every activity layout sets `fitsSystemWindows="true"`, so on a
 * device the content really is inset below a status bar — Robolectric just has no
 * insets to apply, so it lays out from the top edge.
 *
 * Everything is drawn from primitives. No icon fonts, no assets, nothing to keep in
 * sync with a resource folder.
 */
object StatusBar {

    /** Android's own status bar height, and the inset the app screens are given. */
    const val HEIGHT_DP = 28

    private const val SIDE_PADDING_DP = 16

    /** Between the three right-hand icons. */
    private const val ICON_GAP_DP = 7

    fun heightPx(density: Float): Int = (HEIGHT_DP * density).toInt()

    /**
     * Draws the bar into the top [heightPx] of [canvas].
     *
     * @param nowMillis the same instant the widgets were rendered from, so the clock
     *   agrees with the countdown values sharing the frame instead of contradicting them.
     */
    fun draw(
        canvas: Canvas,
        widthPx: Int,
        density: Float,
        dark: Boolean,
        nowMillis: Long,
        zone: ZoneId,
    ) {
        val height = heightPx(density)
        val tint = if (dark) 0xFFF1F2F6.toInt() else 0xFF1B1B1F.toInt()
        val centreY = height / 2f

        // Flat, with no shadow behind it, over a photograph as much as over an app
        // screen. Android's own bar has none — the clock and the icons on a real home
        // screen are unshadowed however busy the wallpaper is — and a bar that carries
        // one is the one piece of the composite that announces it was drawn here.
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint
            textSize = 13f * density
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint }

        // Clock, left, vertically centred on its own metrics rather than on the ascent.
        val clock = DateTimeFormatter.ofPattern("h:mm")
            .format(Instant.ofEpochMilli(nowMillis).atZone(zone))
        val metrics = text.fontMetrics
        val baseline = centreY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(clock, SIDE_PADDING_DP * density, baseline, text)

        // Icons, right to left, so each one only needs to know its own width.
        var right = widthPx - SIDE_PADDING_DP * density
        right -= battery(canvas, right, centreY, density, stroke)
        right -= ICON_GAP_DP * density
        right -= wifi(canvas, right, centreY, density, stroke)
        right -= ICON_GAP_DP * density
        cellular(canvas, right, centreY, density, stroke)
    }

    /** A rounded cell with a fill level and a nub on the right. Returns its width. */
    private fun battery(canvas: Canvas, right: Float, centreY: Float, density: Float, paint: Paint): Float {
        val bodyW = 22f * density
        val bodyH = 11f * density
        val nubW = 2f * density
        val radius = 2.5f * density
        val left = right - bodyW - nubW

        val body = RectF(left, centreY - bodyH / 2, left + bodyW, centreY + bodyH / 2)
        canvas.drawRoundRect(
            body,
            radius,
            radius,
            Paint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.4f * density
            },
        )
        canvas.drawRoundRect(
            RectF(
                body.right,
                centreY - bodyH / 5,
                body.right + nubW,
                centreY + bodyH / 5,
            ),
            nubW / 2,
            nubW / 2,
            Paint(paint).apply { style = Paint.Style.FILL },
        )

        val inset = 2.4f * density
        val fill = RectF(
            body.left + inset,
            body.top + inset,
            body.left + inset + (body.width() - inset * 2) * 0.78f,
            body.bottom - inset,
        )
        canvas.drawRoundRect(
            fill,
            radius / 2,
            radius / 2,
            Paint(paint).apply { style = Paint.Style.FILL },
        )
        return bodyW + nubW
    }

    /** Three arcs over a dot. Returns its width. */
    private fun wifi(canvas: Canvas, right: Float, centreY: Float, density: Float, paint: Paint): Float {
        val size = 15f * density
        val originX = right - size / 2
        val bottomY = centreY + size * 0.34f

        val arc = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * density
            strokeCap = Paint.Cap.ROUND
        }
        for (step in 1..3) {
            val r = size * (0.16f + 0.17f * step)
            canvas.drawArc(
                RectF(originX - r, bottomY - r, originX + r, bottomY + r),
                -145f,
                110f,
                false,
                arc,
            )
        }
        canvas.drawCircle(
            originX,
            bottomY,
            1.7f * density,
            Paint(paint).apply { style = Paint.Style.FILL },
        )
        return size
    }

    /** Four ascending bars. Returns its width. */
    private fun cellular(canvas: Canvas, right: Float, centreY: Float, density: Float, paint: Paint): Float {
        val barW = 2.6f * density
        val gap = 1.6f * density
        val tallest = 12f * density
        val bottomY = centreY + tallest / 2
        val fill = Paint(paint).apply { style = Paint.Style.FILL }

        for (i in 0 until 4) {
            val h = tallest * (0.34f + 0.22f * i)
            val x = right - (barW + gap) * (4 - i) + gap
            canvas.drawRoundRect(
                RectF(x, bottomY - h, x + barW, bottomY),
                barW / 2,
                barW / 2,
                fill,
            )
        }
        return (barW + gap) * 4
    }

    /** Fills the strip behind the bar on an app screen, where there is no wallpaper. */
    fun fill(canvas: Canvas, widthPx: Int, density: Float, colour: Int) {
        canvas.drawRect(
            0f,
            0f,
            widthPx.toFloat(),
            heightPx(density).toFloat(),
            Paint().apply { color = colour },
        )
    }
}
