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
     * The widget sizes the home-screen shot lays out, in dp, largest first: a 4x2, a
     * 3x1 and a 2x1 of this device's own cell.
     *
     * Whole cells, always. A launcher hands out a content box of *n* columns by *m*
     * rows and nothing in between, so a screenshot at any other size is advertising a
     * layout nobody can drop. That is what the smallest slot used to do: at 52dp it
     * fell short of the 68dp where the target date appears, so the store listing showed
     * a widget with no date on a size no launcher offers — every real one-row cell
     * clears that breakpoint. So each device below declares one row height and the
     * content widths its columns come to, and every size here is a whole number of
     * each. Two of the three end up one row tall, because that is what a 3x1 and a 2x1
     * are; the shot varies by width instead, which is the axis a user actually sees.
     *
     * They used to be picked to *avoid* a band. Every size was pushed above 150x70
     * because anything smaller rendered a bare number with nothing to say what it
     * counted, which read as a rendering fault sitting under two labelled widgets. That
     * was issue #11, and it was a symptom of breakpoints calibrated against the legacy
     * cell formula rather than against real cells — a widget had to be sized up past
     * what a launcher would ever give it before it looked right. With the bands
     * recalibrated, sizing the shots honestly costs nothing: all three carry their name
     * and their date.
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
            // Rows of 76dp, and 140 / 216 / 344 for two, three and four columns —
            // not a clean multiple of anything, because a content box carries the gaps
            // between its cells. They are the figures WidgetVariantSizeTest pins as
            // what real launchers report at this width.
            widgetSizesDp = listOf(344 to 152, 216 to 76, 140 to 76),
        )

        /** 1920x1080, 16:9 landscape. sw540dp at xhdpi is the 7-inch class. */
        val SEVEN_INCH = DeviceSpec(
            slot = "seven-inch",
            widthPx = 1920,
            heightPx = 1080,
            layout = "en-rUS-sw540dp-w960dp-h540dp-land",
            density = "xhdpi",
            // Rows of 84dp and columns of roughly 105. Cells on a tablet are not much
            // bigger than a phone's — there are simply more of them — so a 4x2 here is
            // the same size class as the phone's, just roomier.
            widgetSizesDp = listOf(420 to 168, 315 to 84, 210 to 84),
        )

        /** 2560x1440, 16:9 landscape. sw720dp at xhdpi is the 10-inch class. */
        val TEN_INCH = DeviceSpec(
            slot = "ten-inch",
            widthPx = 2560,
            heightPx = 1440,
            layout = "en-rUS-sw720dp-w1280dp-h720dp-land",
            density = "xhdpi",
            // Rows of 92dp and columns of roughly 130: the largest cell of the three.
            widgetSizesDp = listOf(520 to 184, 390 to 92, 260 to 92),
        )

        val ALL = listOf(PHONE, SEVEN_INCH, TEN_INCH)
    }
}
