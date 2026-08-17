package com.calebjcox.countdownwidgets.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
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
                cells = cellSizes(manager, appWidgetId),
            )
            manager.updateAppWidget(appWidgetId, views)
        }

        // Redrawing changes when the next boundary is, so the alarm is always
        // re-armed from the freshest state rather than left where it was.
        TimerScheduler.reschedule(context)
    }

    /**
     * Every content box the launcher might draw this widget in, or empty if it has not
     * said. Without them the renderer sizes each variant for the smallest cell that can
     * select it, which is right but leaves text smaller than it needs to be.
     *
     * All of them, not the current one, and that is the point. The bundle describes the
     * widget in *both* orientations at once, and it does not change when the device
     * rotates — the same four numbers already covered the new orientation — so
     * `onAppWidgetOptionsChanged` does not fire and nothing asks this app to redraw.
     * Picking the orientation that happened to be current at build time therefore left
     * the other one sized wrong for as long as the `RemoteViews` lived: a widget built
     * while some app was in landscape came back to the portrait home screen with its
     * number shrunk to the floor, and stayed that way until the next tick or edit
     * rebuilt it. Handing the renderer the whole set lets it size each variant for the
     * cell that selects it, so one object is correct either way up.
     *
     * `OPTION_APPWIDGET_SIZES` is the exact answer and the one hosts have written since
     * API 31. The min/max quadruple is the fallback for a host that only writes the
     * legacy keys: it is the same information flattened, so it unflattens back into the
     * conventional pair — portrait is narrow and tall, landscape wide and short.
     *
     * Empty is normal rather than a failure — the bundle holds nothing until the host
     * has measured the widget once. It does not stay empty: `AppWidgetHostView` writes
     * these values during layout whenever they change, which is what makes
     * `onAppWidgetOptionsChanged` fire on a resize or a grid change, and what makes it
     * safe to size text for the cells that are really there.
     */
    private fun cellSizes(manager: AppWidgetManager, appWidgetId: Int): List<SizeF> {
        val options = manager.getAppWidgetOptions(appWidgetId) ?: return emptyList()

        // The untyped overload, deprecated since API 33, because the typed one landed
        // there and this app's minSdk is 31. It is the only reader that works on every
        // version the app runs on, and there is nothing here worth a version branch.
        @Suppress("DEPRECATION")
        val reported = options
            .getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            .orEmpty()
            .filter { it.width > 0f && it.height > 0f }
        if (reported.isNotEmpty()) return reported.distinct()

        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return listOf(minWidth to maxHeight, maxWidth to minHeight)
            .filter { (width, height) -> width > 0 && height > 0 }
            .map { (width, height) -> SizeF(width.toFloat(), height.toFloat()) }
            .distinct()
    }
}
