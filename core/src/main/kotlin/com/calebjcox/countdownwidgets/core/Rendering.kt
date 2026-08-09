package com.calebjcox.countdownwidgets.core

import java.util.Locale

/** How unit names are written out. */
enum class LabelStyle { SHORT, LONG }

object Rendering {

    /**
     * `1y 2mo 5d` or `1 year, 2 months, 5 days`.
     *
     * Leading zeros are dropped — a countdown inside its last week should read
     * `5d`, not `0y 0mo 5d`. Normally the smallest unit survives even at zero so
     * that something is always shown, but when a ticking clock follows the text
     * [allowEmpty] lets an all-zero head disappear entirely rather than leave a
     * stray `0d` in front of it.
     */
    @JvmOverloads
    fun formatFields(
        values: List<FieldValue>,
        style: LabelStyle,
        allowEmpty: Boolean = false,
    ): String {
        if (values.isEmpty()) return ""
        val stripped = values.dropWhile { it.value == 0L }
        val trimmed = when {
            stripped.isNotEmpty() -> stripped
            allowEmpty -> return ""
            else -> values.takeLast(1)
        }
        return when (style) {
            LabelStyle.SHORT -> trimmed.joinToString(" ") { "${it.value}${it.field.shortLabel}" }
            LabelStyle.LONG -> trimmed.joinToString(", ") {
                "${it.value} ${if (it.value == 1L) it.field.singular else it.field.plural}"
            }
        }
    }

    /**
     * Mirrors `android.text.format.DateUtils.formatElapsedTime`, which is what a
     * `Chronometer` uses internally: `MM:SS` under an hour, `H:MM:SS` at or above
     * one, hours unpadded and unbounded. Duplicated here — rather than called
     * through to Android — so the in-app preview shows the widget's exact text and
     * so it stays unit testable off-device.
     */
    fun formatClock(millis: Long): String {
        val totalSeconds = (if (millis < 0) 0 else millis) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * The full value string as the widget will show it. Used for the in-app preview
     * and the timer list; the widget itself builds the same text from the same
     * pieces, but hands the clock portion to a `Chronometer` so it ticks.
     */
    fun formatDisplay(display: Display, style: LabelStyle): String {
        val tail = display.tailMillis?.let { formatClock(it) }
        val head = formatFields(display.staticValues, style, allowEmpty = tail != null)
        return listOfNotNull(head.ifEmpty { null }, tail).joinToString(" ")
    }
}
