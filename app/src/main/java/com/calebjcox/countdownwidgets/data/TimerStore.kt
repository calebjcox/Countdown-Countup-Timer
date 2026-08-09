package com.calebjcox.countdownwidgets.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Persistence for timers and for which widget shows which timer.
 *
 * Deliberately `SharedPreferences` plus `org.json`: both ship with the platform, so
 * storage adds no dependency and nothing here can be broken by a library upgrade.
 * The data set is a handful of small records, so reading and writing it
 * synchronously — including from a broadcast receiver — costs nothing.
 */
class TimerStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ------------------------------------------------------------------ timers

    fun timers(): List<Timer> {
        val raw = prefs.getString(KEY_TIMERS, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                runCatching { array.getJSONObject(i) }.getOrNull()
                    ?.let(Timer::fromJson)
                    ?.let(::add)
            }
        }
    }

    fun timer(id: String?): Timer? = id?.let { wanted -> timers().firstOrNull { it.id == wanted } }

    /** Inserts a new timer or replaces an existing one, keeping list order. */
    fun save(timer: Timer) {
        val existing = timers()
        val index = existing.indexOfFirst { it.id == timer.id }
        val updated = if (index >= 0) {
            existing.toMutableList().apply { set(index, timer) }
        } else {
            existing + timer
        }
        writeTimers(updated)
    }

    /**
     * Removes a timer and unbinds any widgets showing it. Those widgets stay on the
     * home screen and fall back to their "tap to choose a timer" state rather than
     * disappearing or showing a stale count.
     */
    fun delete(id: String) {
        writeTimers(timers().filterNot { it.id == id })
        val editor = prefs.edit()
        for ((key, value) in prefs.all) {
            if (key.startsWith(WIDGET_PREFIX) && value == id) editor.remove(key)
        }
        editor.commit()
    }

    private fun writeTimers(timers: List<Timer>) {
        val array = JSONArray().also { array -> timers.forEach { array.put(it.toJson()) } }
        prefs.edit().putString(KEY_TIMERS, array.toString()).commit()
    }

    // ------------------------------------------------------------- widget links

    fun bindWidget(appWidgetId: Int, timerId: String) {
        prefs.edit().putString(widgetKey(appWidgetId), timerId).commit()
    }

    fun timerIdForWidget(appWidgetId: Int): String? =
        prefs.getString(widgetKey(appWidgetId), null)

    fun timerForWidget(appWidgetId: Int): Timer? = timer(timerIdForWidget(appWidgetId))

    fun unbindWidgets(appWidgetIds: IntArray) {
        val editor = prefs.edit()
        appWidgetIds.forEach { editor.remove(widgetKey(it)) }
        editor.commit()
    }

    /**
     * Reassigns bindings after a backup restore. Widget ids are not stable across
     * devices, so the framework hands over the old-to-new mapping and everything
     * keyed by widget id has to move with it.
     */
    fun remapWidgets(oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val moved = oldWidgetIds.indices.mapNotNull { i ->
            val timerId = timerIdForWidget(oldWidgetIds[i]) ?: return@mapNotNull null
            newWidgetIds.getOrNull(i)?.let { it to timerId }
        }
        val editor = prefs.edit()
        oldWidgetIds.forEach { editor.remove(widgetKey(it)) }
        moved.forEach { (newId, timerId) -> editor.putString(widgetKey(newId), timerId) }
        editor.commit()
    }

    private fun widgetKey(appWidgetId: Int) = "$WIDGET_PREFIX$appWidgetId"

    private companion object {
        const val PREFS_NAME = "timers"
        const val KEY_TIMERS = "timers"
        const val WIDGET_PREFIX = "widget."
    }
}
