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
     * Chosen to straddle `WidgetRenderer`'s three breakpoints — 100x40, 150x70 and
     * 200x110 dp — so one screenshot shows all three detail levels the launcher can
     * ask for: everything, value with name, and value alone. A launcher picks the
     * largest variant that fits inside the cell, so the third size is deliberately
     * shorter than 70dp and the second shorter than 110dp.
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
            widgetSizesDp = listOf(344 to 152, 344 to 88, 152 to 64),
        )

        /** 1920x1080, 16:9 landscape. sw540dp at xhdpi is the 7-inch class. */
        val SEVEN_INCH = DeviceSpec(
            slot = "seven-inch",
            widthPx = 1920,
            heightPx = 1080,
            layout = "en-rUS-sw540dp-w960dp-h540dp-land",
            density = "xhdpi",
            widgetSizesDp = listOf(420 to 168, 420 to 100, 190 to 68),
        )

        /** 2560x1440, 16:9 landscape. sw720dp at xhdpi is the 10-inch class. */
        val TEN_INCH = DeviceSpec(
            slot = "ten-inch",
            widthPx = 2560,
            heightPx = 1440,
            layout = "en-rUS-sw720dp-w1280dp-h720dp-land",
            density = "xhdpi",
            widgetSizesDp = listOf(520 to 184, 520 to 108, 230 to 68),
        )

        val ALL = listOf(PHONE, SEVEN_INCH, TEN_INCH)
    }
}
