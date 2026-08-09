package com.calebjcox.countdownwidgets.data

import android.app.backup.BackupManager
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

    private val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
        dropWidgetBindings { it == id }
    }

    /**
     * Folds imported timers into the ones already here, matching on id: a timer
     * that is already present is brought up to date in place, keeping its position
     * in the list, and anything new lands at the end. Nothing is ever lost, so
     * importing the same backup twice does nothing the second time.
     */
    fun merge(imported: List<Timer>): MergeResult {
        val byId = LinkedHashMap<String, Timer>()
        timers().forEach { byId[it.id] = it }

        var added = 0
        var updated = 0
        for (timer in imported) {
            if (byId.put(timer.id, timer) == null) added++ else updated++
        }

        writeTimers(byId.values.toList())
        return MergeResult(added = added, updated = updated)
    }

    /**
     * Makes [timers] the entire list, discarding anything not in it. Widgets left
     * pointing at a timer that is gone fall back to "tap to choose a timer", the
     * same as after a delete.
     */
    fun replaceAll(timers: List<Timer>) {
        writeTimers(timers)
        val kept = timers.mapTo(HashSet<String>()) { it.id }
        dropWidgetBindings { it !in kept }
    }

    /** What [merge] did, so the caller can tell the user. */
    data class MergeResult(val added: Int, val updated: Int)

    private fun writeTimers(timers: List<Timer>) {
        val array = JSONArray().also { array -> timers.forEach { array.put(it.toJson()) } }
        commit(prefs.edit().putString(KEY_TIMERS, array.toString()))
    }

    // ------------------------------------------------------------- widget links

    fun bindWidget(appWidgetId: Int, timerId: String) {
        commit(prefs.edit().putString(widgetKey(appWidgetId), timerId))
    }

    fun timerIdForWidget(appWidgetId: Int): String? =
        prefs.getString(widgetKey(appWidgetId), null)

    fun timerForWidget(appWidgetId: Int): Timer? = timer(timerIdForWidget(appWidgetId))

    fun unbindWidgets(appWidgetIds: IntArray) {
        val editor = prefs.edit()
        appWidgetIds.forEach { editor.remove(widgetKey(it)) }
        commit(editor)
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
        commit(editor)
    }

    /** Forgets every widget binding whose timer id [isStale]. */
    private fun dropWidgetBindings(isStale: (String) -> Boolean) {
        val editor = prefs.edit()
        var changed = false
        for ((key, value) in prefs.all) {
            if (key.startsWith(WIDGET_PREFIX) && value is String && isStale(value)) {
                editor.remove(key)
                changed = true
            }
        }
        if (changed) commit(editor)
    }

    private fun widgetKey(appWidgetId: Int) = "$WIDGET_PREFIX$appWidgetId"

    /**
     * The single write path. Committing synchronously is what makes writing from a
     * broadcast receiver safe, and the nudge afterwards tells Android's backup
     * transport that `timers.xml` has changed. Without it a cloud backup carries
     * whatever the file happened to hold the last time the system felt like
     * looking; with it, a timer the user just created is a candidate for the next
     * backup run.
     */
    private fun commit(editor: SharedPreferences.Editor) {
        editor.commit()
        BackupManager(appContext).dataChanged()
    }

    // ------------------------------------------------------------------ app theme

    /** The manual light/dark override, or [AppTheme.AUTO] when unset or unrecognized. */
    fun appTheme(): AppTheme =
        prefs.getString(KEY_APP_THEME, null)?.let { name ->
            runCatching { enumValueOf<AppTheme>(name) }.getOrNull()
        } ?: AppTheme.AUTO

    fun setAppTheme(theme: AppTheme) {
        commit(prefs.edit().putString(KEY_APP_THEME, theme.name))
    }

    private companion object {
        const val PREFS_NAME = "timers"
        const val KEY_TIMERS = "timers"
        const val WIDGET_PREFIX = "widget."
        const val KEY_APP_THEME = "app_theme"
    }
}
