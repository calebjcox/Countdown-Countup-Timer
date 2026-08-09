package com.calebjcox.countdownwidgets.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The file format behind "export timers" and "import timers".
 *
 * JSON rather than a copy of the app's own `timers.xml`, even though that file is
 * what storage actually writes. `TimerStore` keeps the whole list as one
 * `JSONArray` string inside a single preference, so `timers.xml` is escaped JSON
 * wrapped in SharedPreferences' `<map>` container — exporting JSON is *less*
 * transformation, not more. A prefs file also cannot be imported (dropping one
 * into `shared_prefs/` while the app runs just loses to the in-memory copy), it
 * carries `widget.<id>` keys that are meaningless on another device, and it has
 * nowhere to put the format marker and version below.
 *
 * Reading is deliberately forgiving, in the same spirit as [Timer.fromJson]: a
 * backup is something a user may have kept for years and may well have opened in
 * a text editor.
 */
object TimerBackup {

    const val MIME_TYPE = "application/json"

    /**
     * MIME types offered to the file picker on import. Document providers disagree
     * about what a `.json` file is — plenty report `text/plain` or
     * `application/octet-stream` — and a filter that is too narrow greys out the
     * user's own backup in the picker.
     */
    val IMPORT_MIME_TYPES = arrayOf(MIME_TYPE, "text/plain", "application/octet-stream")

    /** What [decode] made of the file the user picked. */
    sealed interface Result {
        /** Timers read from the file; may be empty if nothing in it was usable. */
        data class Ok(val timers: List<Timer>) : Result

        /** Not a Countdowns backup, or not JSON at all. */
        data object NotABackup : Result

        /** Written by a version of the app that knows a format this one does not. */
        data class TooNew(val version: Int) : Result
    }

    fun encode(timers: List<Timer>, exportedAt: LocalDateTime = LocalDateTime.now()): String =
        JSONObject().apply {
            put(KEY_FORMAT, FORMAT)
            put(KEY_VERSION, VERSION)
            put(KEY_EXPORTED_AT, exportedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            put(KEY_TIMERS, JSONArray().also { array -> timers.forEach { array.put(it.toJson()) } })
        }
            // Indented so that someone who opens the file can read it, and so that a
            // hand-edit is a reasonable thing to attempt.
            .toString(2)

    fun decode(text: String): Result {
        val trimmed = text.trim()
        val array = when (trimmed.firstOrNull()) {
            '[' -> runCatching { JSONArray(trimmed) }.getOrNull() ?: return Result.NotABackup

            '{' -> {
                val root = runCatching { JSONObject(trimmed) }.getOrNull()
                    ?: return Result.NotABackup
                if (root.optString(KEY_FORMAT) != FORMAT) return Result.NotABackup
                val version = root.optInt(KEY_VERSION, VERSION)
                if (version > VERSION) return Result.TooNew(version)
                root.optJSONArray(KEY_TIMERS) ?: return Result.NotABackup
            }

            // A bare array is accepted above because it is what the app stores
            // internally, so a file assembled by hand from that is worth honouring.
            // Anything else is not JSON we can use.
            else -> return Result.NotABackup
        }
        return Result.Ok(readTimers(array))
    }

    fun defaultFileName(today: LocalDate): String =
        "countdowns-backup-${today.format(DateTimeFormatter.ISO_LOCAL_DATE)}.json"

    /**
     * Reads every entry it can, skipping the rest. One unreadable record — a
     * truncated file, a date someone mistyped — must not cost the user the timers
     * either side of it.
     */
    private fun readTimers(array: JSONArray): List<Timer> {
        // Keyed by id so a file that repeats one cannot put two timers with the
        // same id into the store, where every lookup is a `firstOrNull { it.id == }`
        // and would quietly disagree with the list the user is looking at.
        val byId = LinkedHashMap<String, Timer>()
        for (i in 0 until array.length()) {
            val timer = runCatching { array.getJSONObject(i) }.getOrNull()?.let(Timer::fromJson)
            if (timer != null && timer.id.isNotBlank()) byId[timer.id] = timer
        }
        return byId.values.toList()
    }

    private const val FORMAT = "countdowns-backup"
    private const val VERSION = 1

    private const val KEY_FORMAT = "format"
    private const val KEY_VERSION = "version"
    private const val KEY_EXPORTED_AT = "exportedAt"
    private const val KEY_TIMERS = "timers"
}
