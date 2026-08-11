package com.calebjcox.countdownwidgets.playassets

/**
 * One Play Console screenshot slot: the pixel size Google wants, and the Android
 * resource configuration that produces it.
 *
 * The dp figures are not decoration. Robolectric builds its resource table and its
 * display metrics from these qualifiers, so they decide which layout and which
 * `values-*` folder the framework picks — and `dp * density` is chosen to land on the
 * target pixel count exactly, so nothing has to be rescaled afterwards.
 *
 * Play's rules for all three slots: sides between 320 and 3840 px, longer side no
 * more than twice the shorter. 1080x1920 portrait and 16:9 landscape are the sizes
 * Google names for promotion eligibility, so they are what we emit.
 */
data class DeviceSpec(
    /** Output subdirectory, and the Play Console slot it maps to. */
    val slot: String,
    val widthPx: Int,
    val heightPx: Int,
    /**
     * Everything before the night qualifier, in canonical resource order:
     * locale, smallest width, available width and height, orientation.
     */
    private val layout: String,
    /** Density qualifier. Comes last, after night mode. */
    private val density: String,
    /**
     * The widget sizes the home-screen shot lays out, in dp, largest first.
     *
     * Chosen to land one size in each of `WidgetRenderer`'s bands, so the shot shows
     * the real range rather than one layout three times: the largest clears the roomy
     * breakpoint, the middle one is a true 2x1 in the compact band, and the smallest
     * sits just under the height where the target date fits.
     *
     * These used to be picked to *avoid* a band. Every size was pushed above 150x70
     * because anything smaller rendered a bare number with nothing to say what it
     * counted, which read as a rendering fault sitting under two labelled widgets. That
     * was issue #11, and it was a symptom of breakpoints calibrated against the legacy
     * cell formula rather than against real cells — a widget had to be sized up past
     * what a launcher would ever give it before it looked right. With the bands
     * recalibrated the small case carries its name, so the screenshots no longer have
     * to dodge it.
     */
    val widgetSizesDp: List<Pair<Int, Int>>,
) {
    /**
     * A full qualifier string for [android.content.res.Configuration], to hand to
     * `RuntimeEnvironment.setQualifiers`. Spelled out rather than appended to the
     * current config, so a capture can never inherit the previous one's state.
     */
    fun qualifiers(dark: Boolean): String =
        "$layout-${if (dark) "night" else "notnight"}-$density"

    companion object {
        /**
         * 1080x1920, 9:16. 360dp wide is the compact width a great many phones
         * report, and at xxhdpi (density 3.0) 360x640dp is exactly 1080x1920.
         */
        val PHONE = DeviceSpec(
            slot = "phone",
            widthPx = 1080,
            heightPx = 1920,
            layout = "en-rUS-sw360dp-w360dp-h640dp-port",
            density = "xxhdpi",
            widgetSizesDp = listOf(344 to 152, 168 to 76, 168 to 52),
        )

        /** 1920x1080, 16:9 landscape. sw540dp at xhdpi is the 7-inch class. */
        val SEVEN_INCH = DeviceSpec(
            slot = "seven-inch",
            widthPx = 1920,
            heightPx = 1080,
            layout = "en-rUS-sw540dp-w960dp-h540dp-land",
            density = "xhdpi",
            widgetSizesDp = listOf(420 to 168, 200 to 84, 200 to 56),
        )

        /** 2560x1440, 16:9 landscape. sw720dp at xhdpi is the 10-inch class. */
        val TEN_INCH = DeviceSpec(
            slot = "ten-inch",
            widthPx = 2560,
            heightPx = 1440,
            layout = "en-rUS-sw720dp-w1280dp-h720dp-land",
            density = "xhdpi",
            widgetSizesDp = listOf(520 to 184, 230 to 88, 230 to 60),
        )

        val ALL = listOf(PHONE, SEVEN_INCH, TEN_INCH)
    }
}
