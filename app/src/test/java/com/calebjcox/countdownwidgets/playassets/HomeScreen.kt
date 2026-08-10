package com.calebjcox.countdownwidgets.playassets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import com.calebjcox.countdownwidgets.data.Timer
import com.calebjcox.countdownwidgets.widget.WidgetRenderer
import java.time.ZoneId
import org.junit.Assert.assertTrue

/**
 * The home-screen shots: real widgets over a wallpaper drawn in code.
 *
 * There is no launcher to photograph — this project has no emulator available — so the
 * home screen around the widgets is a backdrop. The widgets themselves are not: they
 * are the `RemoteViews` `WidgetRenderer` ships, inflated and drawn by the framework
 * exactly as a launcher would, at the pixel sizes a launcher would give them.
 *
 * Deliberately no fake status bar, no fake dock and no other apps' icons: the parts of
 * the picture that are not this app's own output are a wallpaper and nothing else.
 */
object HomeScreen {

    /**
     * A wallpaper. Deterministic, and drawn rather than committed, so no binary asset
     * enters the repository.
     *
     * The brightness is load-bearing. `WidgetPalette` asks the wallpaper whether dark
     * text reads against it; off-device nothing answers, so `resolveTextTone` falls
     * back to the night qualifier — light mode picks dark text, dark mode picks light.
     * Pairing a pale wallpaper with light mode and a deep one with dark mode is what
     * makes that fallback the right answer instead of an accident.
     */
    fun wallpaper(widthPx: Int, heightPx: Int, dark: Boolean): Bitmap {
        val top = if (dark) 0xFF1A1F33.toInt() else 0xFFF3F1FC.toInt()
        val bottom = if (dark) 0xFF070A12.toInt() else 0xFFC6D7F2.toInt()
        val blooms = if (dark) {
            listOf(0xAA2E4B8F.toInt(), 0x99512F7A.toInt(), 0x881F5F63.toInt())
        } else {
            listOf(0xAAB4C9F4.toInt(), 0x99E9CEE8.toInt(), 0x88C6E9DC.toInt())
        }

        val bitmap = Capture.bitmap(widthPx, heightPx, top)
        val canvas = Canvas(bitmap)

        canvas.drawRect(
            0f,
            0f,
            widthPx.toFloat(),
            heightPx.toFloat(),
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, widthPx * 0.35f, heightPx.toFloat(),
                    top, bottom, Shader.TileMode.CLAMP,
                )
            },
        )

        // Three soft blooms at fixed fractions of the frame, so every size gets the
        // same picture rather than the same pixels.
        val spots = listOf(
            Triple(0.18f, 0.16f, 0.55f),
            Triple(0.86f, 0.42f, 0.48f),
            Triple(0.45f, 0.92f, 0.60f),
        )
        val longest = maxOf(widthPx, heightPx).toFloat()
        for ((spot, colour) in spots.zip(blooms)) {
            val (fx, fy, fr) = spot
            val radius = longest * fr
            canvas.drawCircle(
                widthPx * fx,
                heightPx * fy,
                radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        widthPx * fx, heightPx * fy, radius,
                        colour, Color.TRANSPARENT, Shader.TileMode.CLAMP,
                    )
                },
            )
        }

        // A vignette, which is what stops the result reading as a flat CSS gradient.
        canvas.drawRect(
            0f,
            0f,
            widthPx.toFloat(),
            heightPx.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    widthPx / 2f, heightPx * 0.42f, longest * 0.72f,
                    Color.TRANSPARENT, if (dark) 0x99000000.toInt() else 0x33203040,
                    Shader.TileMode.CLAMP,
                )
            },
        )
        return bitmap
    }

    /**
     * Composites [timers] onto a wallpaper at [spec]'s widget sizes, largest first.
     *
     * @param showBackground whether the widgets sit on their own panel or straight on
     *   the wallpaper — the per-timer "Show widget background" setting.
     */
    fun compose(
        context: Context,
        spec: DeviceSpec,
        dark: Boolean,
        showBackground: Boolean,
        timers: List<Timer>,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val bitmap = wallpaper(spec.widthPx, spec.heightPx, dark)

        val requested = spec.widgetSizesDp.map { (w, h) -> SizeF(w.toFloat(), h.toFloat()) }
        val sizes = spec.widgetSizesDp.map { (w, h) -> (w * density).toInt() to (h * density).toInt() }
        val gapPx = (22 * density).toInt()

        val places = if (spec.widthPx > spec.heightPx) {
            twoColumn(spec, sizes, gapPx)
        } else {
            oneColumn(spec, sizes, gapPx)
        }

        for ((index, size) in sizes.withIndex()) {
            val (widthPx, heightPx) = size
            val (x, y) = places[index]
            val timer = timers[index % timers.size].copy(showBackground = showBackground)
            val view = widgetView(context, timer, requested[index], index)
            Capture.draw(view, widthPx, heightPx, bitmap, atX = x, atY = y)
        }
        return bitmap
    }

    /** Portrait: a centred vertical stack, largest at the top. */
    private fun oneColumn(
        spec: DeviceSpec,
        sizes: List<Pair<Int, Int>>,
        gapPx: Int,
    ): List<Pair<Int, Int>> {
        val total = sizes.sumOf { it.second } + gapPx * (sizes.size - 1)
        var y = ((spec.heightPx - total) / 2).coerceAtLeast(gapPx)
        return sizes.map { (widthPx, heightPx) ->
            val place = (spec.widthPx - widthPx) / 2 to y
            y += heightPx + gapPx
            place
        }
    }

    /**
     * Landscape: the big widget on the left, the other two stacked on its right.
     *
     * A landscape tablet is nearly twice as wide as it is tall, and a single centred
     * column leaves most of the frame empty — which is a fair description of a home
     * screen, but a poor screenshot.
     */
    private fun twoColumn(
        spec: DeviceSpec,
        sizes: List<Pair<Int, Int>>,
        gapPx: Int,
    ): List<Pair<Int, Int>> {
        val (largeW, largeH) = sizes[0]
        val rightWidth = maxOf(sizes[1].first, sizes[2].first)
        val rightHeight = sizes[1].second + gapPx + sizes[2].second

        val groupWidth = largeW + gapPx + rightWidth
        val groupHeight = maxOf(largeH, rightHeight)
        val left = (spec.widthPx - groupWidth) / 2
        val top = (spec.heightPx - groupHeight) / 2

        val rightLeft = left + largeW + gapPx
        val rightTop = top + (groupHeight - rightHeight) / 2

        return listOf(
            left to top + (groupHeight - largeH) / 2,
            rightLeft + (rightWidth - sizes[1].first) / 2 to rightTop,
            rightLeft + (rightWidth - sizes[2].first) / 2 to rightTop + sizes[1].second + gapPx,
        )
    }

    /**
     * Inflates one widget at the size a launcher would ask for.
     *
     * Two things here are not obvious. `RemoteViews.apply` on its own returns the
     * *smallest* of the three variants — quietly, not as an error — so the variant has
     * to be selected first by asking for the one that fits the cell, which is the same
     * call `AppWidgetHostView` makes. And a `Chronometer` arrives showing the wrong
     * text until it is repainted; see [Capture.repaintChronometers].
     */
    private fun widgetView(
        context: Context,
        timer: Timer,
        requestedDp: SizeF,
        appWidgetId: Int,
    ): View {
        val remoteViews = WidgetRenderer.build(
            context = context,
            appWidgetId = appWidgetId,
            timer = timer,
            nowMillis = System.currentTimeMillis(),
            zone = ZoneId.systemDefault(),
        )
        val view = forSize(remoteViews, context, requestedDp).apply(context, FrameLayout(context))
        Capture.repaintChronometers(view)
        return view
    }

    /**
     * Picks the variant a launcher would pick for a cell of this size.
     *
     * `getRemoteViewsToApply(Context, SizeF)` is public on `RemoteViews` and has been
     * since API 31, but it is not in the SDK's public surface, so it is reached by
     * reflection. There is no hidden-API enforcement off-device. If it ever disappears
     * the fallback is the plain `apply` path, which loses the detail level rather than
     * the screenshot — so the caller asserts what it got.
     */
    private fun forSize(views: RemoteViews, context: Context, size: SizeF): RemoteViews =
        runCatching {
            RemoteViews::class.java
                .getMethod("getRemoteViewsToApply", Context::class.java, SizeF::class.java)
                .invoke(views, context, size) as RemoteViews
        }.getOrElse {
            assertTrue("could not select a RemoteViews variant for $size: $it", false)
            views
        }
}
