package com.calebjcox.countdownwidgets.playassets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.TextView
import com.calebjcox.countdownwidgets.R
import com.calebjcox.countdownwidgets.data.Timer
import com.calebjcox.countdownwidgets.ui.EditTimerActivity
import com.calebjcox.countdownwidgets.ui.MainActivity
import com.calebjcox.countdownwidgets.ui.WidgetConfigActivity
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every Google Play listing asset.
 *
 * Run with `./gradlew :app:testPlayAssetsUnitTest -PplayAssets`; without the flag the
 * whole package is excluded from the build, so CI never pays for it. Its own build type
 * rather than debug, because `src/debug/res` renames the app to "Countdowns (debug)"
 * and `MainActivity` puts that straight in its app bar — see app/build.gradle.kts.
 *
 * `@GraphicsMode(NATIVE)` is not optional. Robolectric's default is LEGACY, where every
 * `Canvas` call is a no-op and text measures zero — the tests would pass and write two
 * dozen blank PNGs. `Capture.assertNotBlank` is the tripwire for that.
 *
 * SDK 36 rather than the app's targetSdk of 37 because 36 is the newest platform the
 * last stable Robolectric ships. Every API this app uses landed in 31.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayAssetsTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    /**
     * Read once per capture, and shared by the seed targets, the widgets and the status
     * bar clock — so the time in the corner agrees with the countdown values beside it
     * instead of contradicting them.
     */
    private var nowMillis: Long = 0L

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun onlyWhenAsked() {
        assumeTrue("not generating assets; run with -PplayAssets", Capture.outputDir != null)
        // Catches a run from the debug variant, whose src/debug/res would letter every
        // screenshot "Countdowns (debug)".
        assertEquals(
            "store assets must not be generated from the debug variant",
            "Countdowns",
            context.getString(R.string.app_name),
        )
    }

    // ------------------------------------------------------------------ graphics

    @Test
    fun `launcher icon and feature graphic`() {
        Capture.configure(DeviceSpec.PHONE, dark = false)
        Capture.writeIcon(StoreGraphics.icon(context), "icon-512.png")
        Capture.writeScreenshot(
            StoreGraphics.featureGraphic(context),
            "feature-graphic-1024x500.png",
        )
    }

    // --------------------------------------------------------------- screenshots

    /** 01 / 02 — the list, with count-down and count-up timers side by side. */
    @Test
    fun `timer list`() {
        forEachDevice { spec, dark ->
            seed()
            Robolectric.buildActivity(MainActivity::class.java).use { controller ->
                val activity = controller.setup().get()
                assertEquals(
                    "the app bar lost its title",
                    "Countdowns",
                    activity.findViewById<View>(R.id.toolbar).let {
                        (it as com.google.android.material.appbar.MaterialToolbar).title
                    },
                )
                write(spec, dark, 1, "timer-list", Capture.activity(activity, spec, dark, nowMillis))
            }
        }
    }

    /**
     * 03 / 04 — the editor at the top of the form: the name, the date and time it
     * counts to, and all seven unit chips. Scrolling on to Appearance and the live
     * preview makes a second shot, which goes to `extras/` — the form is taller than
     * any of these screens, so one capture cannot hold both halves.
     *
     * Opened on an existing timer on purpose: the new-timer path defaults its target
     * from `LocalDate.now()`, a `java.time` call Robolectric does not instrument, so it
     * would disagree with the clock everything else here reads.
     */
    @Test
    fun `timer editor`() {
        forEachDevice { spec, dark ->
            seed()
            val intent = EditTimerActivity.editIntent(context, SeedTimers.EDITOR_TIMER_ID)

            Robolectric.buildActivity(EditTimerActivity::class.java, intent).use { controller ->
                val activity = controller.setup().get()
                val scroll = requireNotNull(Capture.findScrollView(activity.window.decorView)) {
                    "the editor's ScrollView could not be found"
                }
                // A scrollbar is drawn because the capture scrolls programmatically and
                // nothing fades it back out. On a device it would not be on screen, so
                // leaving it in would be the less faithful choice.
                scroll.isVerticalScrollBarEnabled = false

                write(spec, dark, 2, "timer-editor", Capture.activity(activity, spec, dark, nowMillis))

                val preview = activity.findViewById<TextView>(R.id.preview_value)
                assertTrue("the editor's live preview is empty", preview.text.isNotEmpty())

                scroll.scrollTo(0, scroll.getChildAt(0).height - scroll.height)
                write(
                    spec, dark, 0, "timer-editor-appearance",
                    Capture.activity(activity, spec, dark, nowMillis), extra = true,
                )
            }
        }
    }

    /** 05 / 06 and 07 / 08 — real widgets on a wallpaper, panelled and transparent. */
    @Test
    fun `home screen widgets`() {
        forEachDevice { spec, dark ->
            val timers = onHomeScreen()
            write(
                spec, dark, 3, "widgets-panel",
                HomeScreen.compose(context, spec, dark, true, timers, nowMillis),
            )
            write(
                spec, dark, 4, "widgets-wallpaper",
                HomeScreen.compose(context, spec, dark, false, timers, nowMillis),
            )
        }
    }

    /** Extras: the screen the launcher shows when a widget is dropped. */
    @Test
    fun `widget picker`() {
        forEachDevice { spec, dark ->
            seed()
            val intent = Intent(context, WidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 1)

            Robolectric.buildActivity(WidgetConfigActivity::class.java, intent).use { controller ->
                val activity = controller.setup().get()
                write(spec, dark, 0, "widget-picker", Capture.activity(activity, spec, dark, nowMillis), extra = true)
            }
        }
    }

    // ------------------------------------------------------------------- helpers

    private fun seed(): List<Timer> {
        nowMillis = System.currentTimeMillis()
        return SeedTimers.install(context, zone, nowMillis)
    }

    /**
     * The three timers the home-screen shots show, largest widget first, so the biggest
     * cell gets the timer with the most to say.
     */
    private fun onHomeScreen(): List<Timer> {
        val byId = seed().associateBy { it.id }
        return listOf("product-launch", "vacation", "japan").map { requireNotNull(byId[it]) }
    }

    /** Runs [body] once per screenshot slot, in light and then dark. */
    private fun forEachDevice(body: (DeviceSpec, Boolean) -> Unit) {
        for (spec in DeviceSpec.ALL) {
            for (dark in listOf(false, true)) {
                Capture.configure(spec, dark)
                body(spec, dark)
            }
        }
    }

    /**
     * `phone/01-timer-list-light.png`. Light and dark of one subject sit next to each
     * other so Play Console's upload order matches the numbering.
     *
     * Play accepts at most eight screenshots per slot and the requested set is exactly
     * eight, so anything else goes to `extras/` for the operator to swap in.
     */
    private fun write(
        spec: DeviceSpec,
        dark: Boolean,
        subject: Int,
        name: String,
        bitmap: android.graphics.Bitmap,
        extra: Boolean = false,
    ) {
        val theme = if (dark) "dark" else "light"
        val path = if (extra) {
            "extras/${spec.slot}/$name-$theme.png"
        } else {
            val ordinal = if (dark) subject * 2 else subject * 2 - 1
            "${spec.slot}/%02d-%s-%s.png".format(ordinal, name, theme)
        }
        Capture.writeScreenshot(bitmap, path)
    }
}
