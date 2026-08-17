package com.calebjcox.countdownwidgets.data

import com.calebjcox.countdownwidgets.core.LabelStyle
import com.calebjcox.countdownwidgets.core.Precision
import com.calebjcox.countdownwidgets.core.TextTheme
import com.calebjcox.countdownwidgets.core.TimeField
import com.calebjcox.countdownwidgets.core.TimerSpec
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * A saved timer. Timers exist independently of widgets — one can drive several
 * widgets, and deleting a widget does not delete the timer behind it.
 */
data class Timer(
    val id: String,
    val name: String,
    val spec: TimerSpec,
    val labelStyle: LabelStyle = DEFAULT_LABEL_STYLE,
    /** Only consulted when [showBackground] is false; see WidgetPalette. */
    val textTheme: TextTheme = DEFAULT_TEXT_THEME,
    val showBackground: Boolean = DEFAULT_SHOW_BACKGROUND,
    /**
     * Whether the two rows either side of the value are wanted at all. A cell too
     * small for a row drops it regardless — these say the row is unwanted even where
     * there is room, which is a different question and the only one the user answers.
     */
    val showName: Boolean = DEFAULT_SHOW_NAME,
    val showTarget: Boolean = DEFAULT_SHOW_TARGET,
    /**
     * Whether the value may run onto a second and third line where that makes it
     * bigger. Permission, not instruction: see WidgetRenderer.valueBox, which wraps
     * only when a line break buys the number a larger size than one line can.
     */
    val wrapValue: Boolean = DEFAULT_WRAP_VALUE,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_NAME, name)
        put(KEY_TARGET, spec.target.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        put(KEY_PRECISION, spec.precision.name)
        put(KEY_FIELDS, JSONArray().also { array -> spec.orderedFields.forEach { array.put(it.name) } })
        put(KEY_LABEL_STYLE, labelStyle.name)
        put(KEY_TEXT_THEME, textTheme.name)
        put(KEY_SHOW_BACKGROUND, showBackground)
        put(KEY_SHOW_NAME, showName)
        put(KEY_SHOW_TARGET, showTarget)
        put(KEY_WRAP_VALUE, wrapValue)
    }

    companion object {
        // The single source of every appearance default: the constructor, anything
        // read back from storage without them, and the editor all take them from
        // here. A default written down anywhere else is one that can disagree with
        // this one — see EditTimerActivity, which holds appearance in fields rather
        // than letting a layout attribute stand in for a default.

        // Spelled out rather than abbreviated: "3 days, 4 hours" reads as itself,
        // where "3d 4h" has to be decoded. Anyone who wants the compact form is one
        // switch away, and the renderer shrinks the text to fit either way.
        val DEFAULT_LABEL_STYLE = LabelStyle.LONG
        val DEFAULT_TEXT_THEME = TextTheme.AUTO
        const val DEFAULT_SHOW_BACKGROUND = false

        // Both rows on by default, which is also what a timer saved before these
        // existed was showing — so an upgrade changes nothing anyone can see.
        const val DEFAULT_SHOW_NAME = true
        const val DEFAULT_SHOW_TARGET = true

        const val DEFAULT_WRAP_VALUE = true

        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_TARGET = "target"
        private const val KEY_PRECISION = "precision"
        private const val KEY_FIELDS = "fields"
        private const val KEY_LABEL_STYLE = "labelStyle"
        private const val KEY_TEXT_THEME = "textTheme"
        private const val KEY_SHOW_BACKGROUND = "showBackground"
        private const val KEY_SHOW_NAME = "showName"
        private const val KEY_SHOW_TARGET = "showTarget"
        private const val KEY_WRAP_VALUE = "wrapValue"

        fun newId(): String = UUID.randomUUID().toString()

        /**
         * Reads one stored timer, or null if the entry is unusable. Stored data is
         * parsed defensively so that a single bad record — from a hand-edited
         * backup, or a field this version does not know — cannot take down every
         * widget on the home screen.
         */
        fun fromJson(json: JSONObject): Timer? = runCatching {
            val target = LocalDateTime.parse(
                json.getString(KEY_TARGET),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            )
            val precision = enumOf<Precision>(json.getString(KEY_PRECISION)) ?: return null

            val storedFields = json.getJSONArray(KEY_FIELDS)
            val fields = buildSet {
                for (i in 0 until storedFields.length()) {
                    enumOf<TimeField>(storedFields.getString(i))?.let { add(it) }
                }
            }

            Timer(
                id = json.getString(KEY_ID),
                name = json.optString(KEY_NAME, ""),
                spec = TimerSpec.of(target, precision, fields),
                labelStyle = enumOf<LabelStyle>(json.optString(KEY_LABEL_STYLE))
                    ?: DEFAULT_LABEL_STYLE,
                // Absent from anything saved before the setting existed, which is
                // exactly the case AUTO is the right answer for.
                textTheme = enumOf<TextTheme>(json.optString(KEY_TEXT_THEME))
                    ?: DEFAULT_TEXT_THEME,
                showBackground = json.optBoolean(KEY_SHOW_BACKGROUND, DEFAULT_SHOW_BACKGROUND),
                showName = json.optBoolean(KEY_SHOW_NAME, DEFAULT_SHOW_NAME),
                showTarget = json.optBoolean(KEY_SHOW_TARGET, DEFAULT_SHOW_TARGET),
                wrapValue = json.optBoolean(KEY_WRAP_VALUE, DEFAULT_WRAP_VALUE),
            )
        }.getOrNull()

        private inline fun <reified T : Enum<T>> enumOf(name: String?): T? =
            name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
    }
}
