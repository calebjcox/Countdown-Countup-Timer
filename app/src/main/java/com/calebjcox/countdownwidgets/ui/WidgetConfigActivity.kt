package com.calebjcox.countdownwidgets.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.calebjcox.countdownwidgets.data.TimerStore
import com.calebjcox.countdownwidgets.databinding.ActivityWidgetConfigBinding
import com.calebjcox.countdownwidgets.widget.WidgetUpdater

/**
 * Shown when a widget is dropped on the home screen, and again from the widget's
 * "reconfigure" menu entry. Its only job is to decide which timer a widget id points
 * at.
 */
class WidgetConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetConfigBinding
    private lateinit var store: TimerStore
    private lateinit var adapter: TimerListAdapter

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val createTimer = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val timerId = result.data?.getStringExtra(EditTimerActivity.EXTRA_TIMER_ID)
        if (result.resultCode == RESULT_OK && timerId != null) bind(timerId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Backing out without choosing has to leave the widget unplaced, so the
        // cancelled result is set before anything else can go wrong.
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        binding = ActivityWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = TimerStore(this)
        adapter = TimerListAdapter { bind(it.id) }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.add.setOnClickListener {
            createTimer.launch(EditTimerActivity.editIntent(this, null))
        }
    }

    override fun onResume() {
        super.onResume()
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            adapter.submit(store.timers())
        }
    }

    private fun bind(timerId: String) {
        store.bindWidget(appWidgetId, timerId)

        val manager = AppWidgetManager.getInstance(this)
        if (manager != null) {
            WidgetUpdater.update(this, manager, intArrayOf(appWidgetId))
        }

        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
        finish()
    }

    companion object {
        /** Opens the picker for a widget that has no timer behind it. */
        fun reconfigureIntent(context: Context, appWidgetId: Int): PendingIntent =
            PendingIntent.getActivity(
                context,
                appWidgetId,
                Intent(context, WidgetConfigActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
