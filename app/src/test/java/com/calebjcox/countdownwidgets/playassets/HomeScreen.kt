package com.calebjcox.countdownwidgets.playassets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import com.calebjcox.countdownwidgets.data.Timer
import com.calebjcox.countdownwidgets.testing.VariantSelection
import com.calebjcox.countdownwidgets.widget.WidgetRenderer
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.time.ZoneId
import javax.imageio.ImageIO

/**
 * The home-screen shots: real widgets over a wallpaper drawn in code.
 *
 * There is no launcher to photograph — this project has no emulator available — so the
 * home screen around the widgets is a backdrop. The widgets themselves are not: they
 * are the `RemoteViews` `WidgetRenderer` ships, inflated and drawn by the framework
 * exactly as a launcher would, at the pixel sizes a launcher would give them.
 *
 * Deliberately no fake dock and no other apps' icons: apart from the status bar and the
 * wallpaper, everything in the frame is this app's own output.
 */
object HomeScreen {

    /**
     * The photograph the widgets sit on.
     *
     * **Not MIT licensed**, unlike almost everything else here: it and the screenshots
     * composited on top of it are all rights reserved. See IMAGE-LICENSE before copying
     * either anywhere.
     *
     * A test resource, so it stays on the unit-test classpath and never reaches the APK.
     * Stored at 2560x3413 because that is the smallest 3:4 frame from which every crop
     * below is a downsample rather than an upscale.
     */
    private const val PHOTO = "/wallpaper/kalalau-valley.jpg"

    /**
     * How far into the photo each crop starts, as a fraction of the slack left over
     * after the target aspect is taken out. Portrait crops horizontally, landscape
     * vertically; both were picked by looking at the result.
     */
    private const val PORTRAIT_CROP_X = 0.62f

    /**
     * 0.55 rather than centred: higher framings are almost all sky, which is bright but
     * has nothing in it, and lower ones drop to luminance 0.44 where light theme's dark
     * text starts to struggle. This keeps the ridge and the rainbow's landing in frame
     * at a 0.70 mid-band — the same tone that reads well on the phone shots.
     */
    private const val LANDSCAPE_CROP_Y = 0.55f

    /**
     * How much black goes over the photo in dark theme.
     *
     * Necessary rather than stylistic: the sky the widgets sit on is luminance 0.9, and
     * dark theme is exactly when `resolveTextTone` picks *light* text. Dimming is also
     * what Android 12+ does to the wallpaper in dark theme, so the light and dark shots
     * still read as one home screen rather than two unrelated ones.
     */
    private const val DARK_DIM = 0.72f

    /**
     * The wallpaper, cropped to fill [widthPx] x [heightPx] and dimmed in dark theme.
     *
     * Decoded and resampled through `ImageIO` rather than `BitmapFactory`: `javax.*` is
     * not instrumented by Robolectric so it works untouched, it does not depend on
     * Robolectric's JPEG decoder being present, and `Graphics2D` under bicubic hints
     * resamples better than a bitmap scale would.
     */
    fun wallpaper(widthPx: Int, heightPx: Int, dark: Boolean): Bitmap {
        val source = requireNotNull(HomeScreen::class.java.getResourceAsStream(PHOTO)) {
            "$PHOTO is missing from the test resources"
        }
        val photo = source.use { requireNotNull(ImageIO.read(it)) { "$PHOTO would not decode" } }

        // Crop to the target aspect first, so the scale that follows cannot distort.
        val wanted = widthPx.toDouble() / heightPx
        var cropW = photo.width
        var cropH = (photo.width / wanted).toInt()
        if (cropH > photo.height) {
            cropH = photo.height
            cropW = (photo.height * wanted).toInt()
        }
        val offsetX = ((photo.width - cropW) * PORTRAIT_CROP_X).toInt()
        val offsetY = ((photo.height - cropH) * LANDSCAPE_CROP_Y).toInt()
        val cropped = photo.getSubimage(offsetX, offsetY, cropW, cropH)

        val scaled = BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_RGB)
        scaled.createGraphics().apply {
            setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC,
            )
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            drawImage(cropped, 0, 0, widthPx, heightPx, null)
            dispose()
        }

        val pixels = IntArray(widthPx * heightPx)
        scaled.getRGB(0, 0, widthPx, heightPx, pixels, 0, widthPx)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, widthPx, 0, 0, widthPx, heightPx)

        if (dark) {
            Canvas(bitmap).drawColor(
                Color.argb((DARK_DIM * 255).toInt(), 0, 0, 0),
                PorterDuff.Mode.SRC_OVER,
            )
        }
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
        nowMillis: Long,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val bitmap = wallpaper(spec.widthPx, spec.heightPx, dark)

        val requested = spec.widgetSizesDp.map { (w, h) -> SizeF(w.toFloat(), h.toFloat()) }
        val sizes = spec.widgetSizesDp.map { (w, h) -> (w * density).toInt() to (h * density).toInt() }
        val gapPx = (22 * density).toInt()

        val places = if (spec.widthPx > spec.heightPx) {
            twoColumn(spec, sizes, gapPx)
        } else {
            oneColumn(spec, sizes, gapPx, StatusBar.heightPx(density))
        }

        for ((index, size) in sizes.withIndex()) {
            val (widthPx, heightPx) = size
            val (x, y) = places[index]
            val timer = timers[index % timers.size].copy(showBackground = showBackground)
            val view = widgetView(context, timer, requested[index], index)
            Capture.draw(view, widthPx, heightPx, bitmap, atX = x, atY = y)
        }

        StatusBar.draw(
            canvas = Canvas(bitmap),
            widthPx = spec.widthPx,
            density = density,
            dark = dark,
            nowMillis = nowMillis,
            zone = ZoneId.systemDefault(),
        )
        return bitmap
    }

    /**
     * Portrait: a vertical stack under the status bar, largest at the top.
     *
     * High rather than centred, for two reasons that happen to agree. It is where
     * widgets actually sit on a home screen, with the app icons below them. And it puts
     * the text over the photograph's sky, which is luminance 0.9 — far kinder to the
     * dark text light theme picks than the 0.70 of the frame's middle.
     */
    private fun oneColumn(
        spec: DeviceSpec,
        sizes: List<Pair<Int, Int>>,
        gapPx: Int,
        statusBarPx: Int,
    ): List<Pair<Int, Int>> {
        var y = statusBarPx + gapPx
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
            // What a launcher reports through AppWidgetManager's options, and the
            // reason these widgets are spaced here as they are on a device.
            cells = listOf(requestedDp),
        )
        val view = forSize(remoteViews, context, requestedDp).apply(context, FrameLayout(context))
        Capture.repaintChronometers(view)
        return view
    }

    /**
     * Picks the variant a launcher would pick for a cell of this size.
     *
     * Shared with `WidgetVariantSizeTest`, which asserts the same selection against the
     * breakpoint table — so the screenshots and the regression test cannot disagree
     * about what a given cell renders.
     */
    private fun forSize(views: RemoteViews, context: Context, size: SizeF): RemoteViews =
        VariantSelection.forSize(views, context, size)
}
