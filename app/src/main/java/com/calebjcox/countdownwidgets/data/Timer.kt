package com.calebjcox.countdownwidgets.data

import com.calebjcox.countdownwidgets.core.Backdrop
import com.calebjcox.countdownwidgets.core.LabelStyle
import com.calebjcox.countdownwidgets.core.Precision
import com.calebjcox.countdownwidgets.core.RowVisibility
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
    /**
     * Read on every backdrop, and on the two that draw a surface it also decides which
     * surface: that surface goes the opposite way from the text, so a named colour picks
     * the panel or the wash it can be read on. Only [TextTheme.AUTO] is answered
     * differently per backdrop, and there the difference is who resolves it rather than
     * whether this is consulted. See WidgetPalette.
     */
    val textTheme: TextTheme = DEFAULT_TEXT_THEME,
    val backdrop: Backdrop = DEFAULT_BACKDROP,
    /**
     * How much of a say the cell gets over the two rows either side of the value. The
     * renderer drops a row a cell has no room for; these say whether that decision is
     * the user's or the cell's, which is the question a size cannot answer.
     */
    val nameVisibility: RowVisibility = DEFAULT_NAME_VISIBILITY,
    val targetVisibility: RowVisibility = DEFAULT_TARGET_VISIBILITY,
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
        put(KEY_BACKDROP, backdrop.name)
        put(KEY_NAME_VISIBILITY, nameVisibility.name)
        put(KEY_TARGET_VISIBILITY, targetVisibility.name)
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
        val DEFAULT_BACKDROP = Backdrop.NONE

        // Both rows left to the cell, because that is what a stored timer carrying
        // neither field is already doing. These two are the default for absent data as
        // much as for a new timer, so anything else here redraws widgets nobody touched.
        val DEFAULT_NAME_VISIBILITY = RowVisibility.WHEN_ROOM
        val DEFAULT_TARGET_VISIBILITY = RowVisibility.WHEN_ROOM

        const val DEFAULT_WRAP_VALUE = true

        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_TARGET = "target"
        private const val KEY_PRECISION = "precision"
        private const val KEY_FIELDS = "fields"
        private const val KEY_LABEL_STYLE = "labelStyle"
        private const val KEY_TEXT_THEME = "textTheme"
        private const val KEY_BACKDROP = "backdrop"
        private const val KEY_NAME_VISIBILITY = "nameVisibility"
        private const val KEY_TARGET_VISIBILITY = "targetVisibility"
        private const val KEY_WRAP_VALUE = "wrapValue"

        // What these three were called while they were booleans. Read, never written:
        // a file that has the enum has no use for them, and a build old enough to want
        // them falls back to its own defaults, which are these values' own meaning.
        private const val LEGACY_KEY_SHOW_BACKGROUND = "showBackground"
        private const val LEGACY_KEY_SHOW_NAME = "showName"
        private const val LEGACY_KEY_SHOW_TARGET = "showTarget"

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
                // A stored timer with no text theme is one nobody chose a theme
                // for, which is exactly the case AUTO answers.
                textTheme = enumOf<TextTheme>(json.optString(KEY_TEXT_THEME))
                    ?: DEFAULT_TEXT_THEME,
                backdrop = enumOf<Backdrop>(json.optString(KEY_BACKDROP))
                    ?: json.legacyBackdrop(),
                nameVisibility = enumOf<RowVisibility>(json.optString(KEY_NAME_VISIBILITY))
                    ?: json.legacyVisibility(LEGACY_KEY_SHOW_NAME, DEFAULT_NAME_VISIBILITY),
                targetVisibility = enumOf<RowVisibility>(json.optString(KEY_TARGET_VISIBILITY))
                    ?: json.legacyVisibility(LEGACY_KEY_SHOW_TARGET, DEFAULT_TARGET_VISIBILITY),
                wrapValue = json.optBoolean(KEY_WRAP_VALUE, DEFAULT_WRAP_VALUE),
            )
        }.getOrNull()

        private inline fun <reified T : Enum<T>> enumOf(name: String?): T? =
            name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

        /**
         * The backdrop a file written before there were three of them describes.
         *
         * A missing key is a timer nobody chose a backdrop for, which is what the
         * default is; a present one is a deliberate choice between the two ends, and
         * the middle is unreachable from a boolean by construction.
         */
        private fun JSONObject.legacyBackdrop(): Backdrop = when {
            !has(LEGACY_KEY_SHOW_BACKGROUND) -> DEFAULT_BACKDROP
            optBoolean(LEGACY_KEY_SHOW_BACKGROUND) -> Backdrop.PANEL
            else -> Backdrop.NONE
        }

        /**
         * The same for a row toggle, where absent has to keep meaning what the row was
         * doing. `true` was "draw it where the cell has room", so it maps to
         * [RowVisibility.WHEN_ROOM] rather than [RowVisibility.ALWAYS] — reading it as
         * always would print a name onto every 1x1 whose owner never asked for one.
         */
        private fun JSONObject.legacyVisibility(
            key: String,
            absent: RowVisibility,
        ): RowVisibility = when {
            !has(key) -> absent
            optBoolean(key) -> RowVisibility.WHEN_ROOM
            else -> RowVisibility.NEVER
        }
    }
}
