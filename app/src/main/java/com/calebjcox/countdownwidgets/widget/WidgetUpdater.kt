package com.calebjcox.countdownwidgets.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.util.SizeF
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
                cellDp = cellDp(context, manager, appWidgetId),
            )
            manager.updateAppWidget(appWidgetId, views)
        }

        // Redrawing changes when the next boundary is, so the alarm is always
        // re-armed from the freshest state rather than left where it was.
        TimerScheduler.reschedule(context)
    }

    /**
     * The content box the launcher is drawing this widget in, or null if it has not
     * said. Without it the renderer sizes each variant for the smallest cell that can
     * select it, which is right but leaves text smaller than it needs to be.
     *
     * The bundle holds a *range*, not a size: the four values bracket the same widget
     * across both orientations, narrow-and-tall in portrait and wide-and-short in
     * landscape. Which pair is current is not in the bundle, so it comes from the
     * configuration.
     *
     * Null is normal rather than a failure — the bundle is empty until the host has
     * measured the widget once. It does not stay empty: `AppWidgetHostView` writes
     * these values during layout whenever they change, which is what makes
     * `onAppWidgetOptionsChanged` fire on a resize, a rotation or a grid change, and
     * what makes it safe to size text for the cell that is really there.
     */
    private fun cellDp(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
    ): SizeF? {
        val options = manager.getAppWidgetOptions(appWidgetId) ?: return null
        val portrait = context.resources.configuration.orientation !=
            Configuration.ORIENTATION_LANDSCAPE
        val width = options.getInt(
            if (portrait) {
                AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
            } else {
                AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
            },
        )
        val height = options.getInt(
            if (portrait) {
                AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
            } else {
                AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
            },
        )
        return if (width > 0 && height > 0) SizeF(width.toFloat(), height.toFloat()) else null
    }
}
