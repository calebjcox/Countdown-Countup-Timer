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
            )
            manager.updateAppWidget(appWidgetId, views)
        }

        // Redrawing changes when the next boundary is, so the alarm is always
        // re-armed from the freshest state rather than left where it was.
        TimerScheduler.reschedule(context)
    }
}
