package com.calebjcox.countdownwidgets.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.calebjcox.countdownwidgets.data.TimerStore

/**
 * The single widget provider. Every entry point does the same small amount of work
 * — redraw, then re-arm the alarm — because there is no state to keep between
 * callbacks and nothing that benefits from being clever.
 */
class TimerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WidgetUpdater.update(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // The launcher picks a size variant on its own, but a resize is still a good
        // moment to make sure the content behind it is current.
        WidgetUpdater.update(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        TimerStore(context).unbindWidgets(appWidgetIds)
        TimerScheduler.reschedule(context)
    }

    override fun onDisabled(context: Context) {
        // No widgets left anywhere; stop asking to be woken.
        TimerScheduler.cancel(context)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        TimerStore(context).remapWidgets(oldWidgetIds, newWidgetIds)
        WidgetUpdater.updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            TimerScheduler.ACTION_TICK,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> WidgetUpdater.updateAll(context)
        }
    }
}
