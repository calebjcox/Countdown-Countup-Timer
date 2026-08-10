package com.calebjcox.countdownwidgets.playassets

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatDelegate
import java.awt.image.BufferedImage
import java.io.File
import java.time.ZoneId
import javax.imageio.ImageIO
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment

/**
 * Bitmap plumbing shared by every generator: put the framework into a device
 * configuration, lay a view out at an exact pixel size, draw it, and write a PNG that
 * Play Console will accept.
 *
 * Laying out at an exact size rather than letting the configuration decide matters
 * twice over. It guarantees the output is the pixel size Google asked for, and it is
 * the only way the widget's `autoSizeTextType="uniform"` value text sizes correctly —
 * uniform auto-sizing searches for a size that fits a box, so the box has to be real.
 */
object Capture {

    /** Play's ceiling for a screenshot. */
    private const val SCREENSHOT_MAX_BYTES = 8L * 1024 * 1024

    /** Play's ceiling for the launcher icon. */
    private const val ICON_MAX_BYTES = 1024L * 1024

    /**
     * Where assets land, passed in by Gradle so the generator does not have to guess
     * how far up the tree the repository root is. Null when the generator was not
     * asked for, which is what makes the tests skip during a normal `test` run.
     */
    val outputDir: File? by lazy {
        System.getProperty("playAssets.outputDir")?.let { File(it).apply { mkdirs() } }
    }

    /**
     * Puts the framework into [spec]'s configuration, then checks it took.
     *
     * The assertion is the point: a qualifier string the parser did not understand,
     * or a density that rounds, would otherwise show up as a screenshot Play rejects
     * days later rather than as a failure here.
     */
    fun configure(spec: DeviceSpec, dark: Boolean) {
        RuntimeEnvironment.setQualifiers(spec.qualifiers(dark))

        // The night qualifier is what DayNight and values-night resolve from, but
        // AppCompat caches its own idea of night mode from App.onCreate, which ran
        // before this. Say it again so the two cannot disagree.
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO,
        )

        val metrics = RuntimeEnvironment.getApplication().resources.displayMetrics
        assertTrue(
            "qualifiers '${spec.qualifiers(dark)}' gave a " +
                "${metrics.widthPixels}x${metrics.heightPixels} display, wanted " +
                "${spec.widthPx}x${spec.heightPx}",
            metrics.widthPixels == spec.widthPx && metrics.heightPixels == spec.heightPx,
        )
    }

    /** Measures and lays [view] out at exactly this size, then draws it onto [onto]. */
    fun draw(view: View, widthPx: Int, heightPx: Int, onto: Bitmap, atX: Int = 0, atY: Int = 0) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, widthPx, heightPx)
        val canvas = Canvas(onto)
        canvas.save()
        canvas.translate(atX.toFloat(), atY.toFloat())
        view.draw(canvas)
        canvas.restore()
    }

    /** An opaque canvas of exactly this size. */
    fun bitmap(widthPx: Int, heightPx: Int, background: Int = Color.BLACK): Bitmap =
        Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            .also { it.eraseColor(background) }

    /**
     * A whole activity, rendered at [spec]'s pixel size with a status bar above it.
     *
     * The activity is laid out into the height that is left over, rather than the full
     * screen, which is what actually happens on a device: every layout here sets
     * `fitsSystemWindows="true"`, and the framework insets the content below the status
     * bar. Robolectric has no insets to apply, so without this the app bar would sit
     * under the clock.
     *
     * The bitmap starts filled with the theme's window background because a themed
     * decor background can leave pixels untouched, and a screenshot with holes in it is
     * a screenshot Play rejects.
     */
    fun activity(activity: Activity, spec: DeviceSpec, dark: Boolean, nowMillis: Long): Bitmap {
        val density = activity.resources.displayMetrics.density
        val barPx = StatusBar.heightPx(density)

        val bitmap = bitmap(spec.widthPx, spec.heightPx, windowBackground(activity))
        draw(
            activity.window.decorView,
            spec.widthPx,
            spec.heightPx - barPx,
            bitmap,
            atY = barPx,
        )

        // Sampled from the row the activity just drew rather than resolved from a theme
        // attribute: whatever the app bar actually painted is what the bar has to match,
        // and reading it back is the only way to be certain there is no seam.
        val seam = bitmap.getPixel(spec.widthPx / 2, barPx + 1)

        val canvas = Canvas(bitmap)
        StatusBar.fill(canvas, spec.widthPx, density, seam)
        StatusBar.draw(
            canvas = canvas,
            widthPx = spec.widthPx,
            density = density,
            dark = dark,
            nowMillis = nowMillis,
            zone = ZoneId.systemDefault(),
            onWallpaper = false,
        )
        return bitmap
    }

    /** The theme's own background colour, or black if it is not a flat colour. */
    fun windowBackground(context: Context): Int {
        val value = TypedValue()
        val resolved = context.theme.resolveAttribute(android.R.attr.colorBackground, value, true)
        return if (resolved && value.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
            value.type <= TypedValue.TYPE_LAST_COLOR_INT
        ) {
            value.data
        } else {
            Color.BLACK
        }
    }

    /**
     * The editor's `ScrollView` has no `android:id`, so it can only be reached by
     * walking the tree. Used to capture the bottom of a form taller than the screen.
     */
    fun findScrollView(root: View): ScrollView? = when {
        root is ScrollView -> root
        root is ViewGroup -> (0 until root.childCount)
            .asSequence()
            .mapNotNull { findScrollView(root.getChildAt(it)) }
            .firstOrNull()

        else -> null
    }

    /**
     * Repaints a `Chronometer` that `RemoteViews` left showing the wrong thing.
     *
     * `RemoteViews.setChronometer` dispatches `setBase`, then `setFormat`, then
     * `setStarted`. Only `setBase` repaints, and it runs while the format is still
     * null — so the calendar head the app embeds in the format string ("12d  %s") is
     * missing from the text. Assigning the same base back re-runs the repaint with
     * the format in place. `setStarted` cannot help: it needs `isShown()`, which is
     * false for a hierarchy that was never attached to a window.
     */
    fun repaintChronometers(root: View) {
        if (root is Chronometer) {
            root.base = root.base
            return
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) repaintChronometers(root.getChildAt(i))
        }
    }

    /**
     * Fails if a bitmap came out flat, which is what a capture looks like when
     * Robolectric is in its default LEGACY graphics mode: every Canvas call is a
     * no-op and every PNG is a single colour. Worth a cheap check on every asset,
     * because the failure is otherwise completely silent.
     */
    fun assertNotBlank(bitmap: Bitmap, label: String) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val distinct = pixels.asSequence().distinct().take(2).count()
        assertTrue("$label rendered as a single flat colour", distinct > 1)
    }

    /**
     * Writes a screenshot or the feature graphic: 24-bit PNG, no alpha channel.
     *
     * Encoding goes through `ImageIO` rather than `Bitmap.compress` so the result is
     * RGB *by construction* — `TYPE_INT_RGB` has nowhere to put an alpha channel —
     * instead of depending on Skia's encoder inferring it from the bitmap's alpha
     * type. Play rejects screenshots that carry alpha.
     */
    fun writeScreenshot(bitmap: Bitmap, relativePath: String): File {
        assertNotBlank(bitmap, relativePath)
        return write(bitmap, relativePath, BufferedImage.TYPE_INT_RGB, SCREENSHOT_MAX_BYTES, 3)
    }

    /** Writes the launcher icon, which Play wants as 32-bit PNG *with* alpha. */
    fun writeIcon(bitmap: Bitmap, relativePath: String): File {
        assertNotBlank(bitmap, relativePath)
        return write(bitmap, relativePath, BufferedImage.TYPE_INT_ARGB, ICON_MAX_BYTES, 4)
    }

    private fun write(
        bitmap: Bitmap,
        relativePath: String,
        imageType: Int,
        maxBytes: Long,
        expectedComponents: Int,
    ): File {
        val dir = requireNotNull(outputDir) { "playAssets.outputDir is unset" }
        val file = File(dir, relativePath)
        file.parentFile?.mkdirs()

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val image = BufferedImage(bitmap.width, bitmap.height, imageType)
        image.setRGB(0, 0, bitmap.width, bitmap.height, pixels, 0, bitmap.width)

        ImageIO.setUseCache(false)
        check(ImageIO.write(image, "png", file)) { "no PNG writer produced $relativePath" }

        // Read the file back rather than trusting what we meant to write. Play checks
        // exactly these things on upload; better to fail here.
        val written = requireNotNull(ImageIO.read(file)) { "$relativePath is not readable as PNG" }
        check(written.width == bitmap.width && written.height == bitmap.height) {
            "$relativePath is ${written.width}x${written.height}, expected " +
                "${bitmap.width}x${bitmap.height}"
        }
        check(written.colorModel.numComponents == expectedComponents) {
            "$relativePath has ${written.colorModel.numComponents} colour components, " +
                "expected $expectedComponents"
        }
        check(file.length() in 1..maxBytes) {
            "$relativePath is ${file.length()} bytes, outside Play's limit of $maxBytes"
        }
        return file
    }
}
