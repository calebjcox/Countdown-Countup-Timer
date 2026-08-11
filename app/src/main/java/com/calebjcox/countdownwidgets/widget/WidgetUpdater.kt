package com.calebjcox.countdownwidgets.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.calebjcox.countdownwidgets.data.TimerStore
import java.time.ZoneId

/**
 * Redraws widgets. Every path into this object is synchronous and cheap — read a
 * few hundred bytes of preferences, do some calendar arithmetic, hand a
 * `RemoteViews` to the system — so it is safe to call straight from a broadcast
 * receiver with no coroutine, no `goAsync`, and nothing that can outlive the call.
 */
object WidgetUpdater {

    fun widgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context)
            ?.getAppWidgetIds(ComponentName(context, TimerWidgetProvider::class.java))
            ?: IntArray(0)

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        update(context, manager, widgetIds(context))
    }

    /** Redraws every widget bound to [timerId], for use after an edit. */
    fun updateForTimer(context: Context, timerId: String) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val store = TimerStore(context)
        val affected = widgetIds(context).filter { store.timerIdForWidget(it) == timerId }
        update(context, manager, affected.toIntArray())
    }

    fun update(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val store = TimerStore(context)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()

        for (appWidgetId in appWidgetIds) {
            val views = WidgetRenderer.build(
                context = context,
                appWidgetId = appWidgetId,
                timer = store.timerForWidget(appWidgetId),
                nowMillis = now,
                zone = zone,
                cellWidthDp = cellWidthDp(manager, appWidgetId),
            )
            manager.updateAppWidget(appWidgetId, views)
        }

        // Redrawing changes when the next boundary is, so the alarm is always
        // re-armed from the freshest state rather than left where it was.
        TimerScheduler.reschedule(context)
    }

    /**
     * How wide the launcher is drawing this widget, in dp, or null if it has not said.
     *
     * The narrow end of the range rather than the wide one. The two bracket the same
     * widget across orientations — portrait is the narrower — and the renderer spends
     * the number on how large the value's text can be, where guessing narrow costs a
     * point of text size and guessing wide costs the spacing the number was read for.
     *
     * Null is normal, not a failure: the bundle is empty until the launcher has
     * measured the widget once. The first draw is sized from height alone and
     * `onAppWidgetOptionsChanged` brings the real width moments later.
     */
    private fun cellWidthDp(manager: AppWidgetManager, appWidgetId: Int): Float? =
        manager.getAppWidgetOptions(appWidgetId)
            ?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            ?.takeIf { it > 0 }
            ?.toFloat()
}
