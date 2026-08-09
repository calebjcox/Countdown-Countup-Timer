package com.calebjcox.countdownwidgets.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.calebjcox.countdownwidgets.core.DurationMath
import com.calebjcox.countdownwidgets.data.TimerStore
import java.time.ZoneId

/**
 * Wakes the widgets up exactly when their text goes stale, and not otherwise.
 *
 * One alarm serves every widget: the soonest change across all of them wins, and
 * each firing re-arms for the next. A widget showing seconds does not appear here
 * at all between day boundaries — its `Chronometer` ticks on its own.
 *
 * The alarm is inexact ([AlarmManager.setWindow]) and non-waking
 * ([AlarmManager.RTC]). Inexact keeps the app free of `SCHEDULE_EXACT_ALARM`, and
 * non-waking means the update lands the moment the device is next awake, which is
 * the only time anybody can see the widget anyway.
 */
object TimerScheduler {

    const val ACTION_TICK = "com.calebjcox.countdownwidgets.action.TICK"

    private const val REQUEST_CODE = 0
    private const val MIN_DELAY_MILLIS = 1_000L
    private const val MIN_WINDOW_MILLIS = 1_000L
    private const val MAX_WINDOW_MILLIS = 60_000L

    fun reschedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = tickIntent(context)

        val next = earliestChange(context)
        if (next == null) {
            // Every widget is either unconfigured or purely clock-driven; nothing
            // will ever need us again until something else changes.
            alarms.cancel(pendingIntent)
            return
        }

        val now = System.currentTimeMillis()
        val delay = (next - now).coerceAtLeast(MIN_DELAY_MILLIS)
        // A window proportional to the wait: tight near a boundary, relaxed when the
        // next change is hours away, so the system can batch it with other work.
        val window = (delay / 10).coerceIn(MIN_WINDOW_MILLIS, MAX_WINDOW_MILLIS)
        alarms.setWindow(AlarmManager.RTC, now + delay, window, pendingIntent)
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(tickIntent(context))
    }

    private fun earliestChange(context: Context): Long? {
        val store = TimerStore(context)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        return WidgetUpdater.widgetIds(context)
            .asList()
            .mapNotNull { store.timerForWidget(it) }
            .mapNotNull { DurationMath.compute(now, zone, it.spec).nextChangeMillis }
            .minOrNull()
    }

    private fun tickIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, TimerWidgetProvider::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
