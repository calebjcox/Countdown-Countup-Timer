package com.calebjcox.countdownwidgets.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.calebjcox.countdownwidgets.R
import com.calebjcox.countdownwidgets.data.AppTheme
import com.calebjcox.countdownwidgets.data.TimerStore
import com.calebjcox.countdownwidgets.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: TimerStore
    private lateinit var adapter: TimerListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = TimerStore(this)
        adapter = TimerListAdapter { startActivity(EditTimerActivity.editIntent(this, it.id)) }

        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.toolbar.inflateMenu(R.menu.menu_main)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_backup -> {
                    startActivity(BackupActivity.intent(this))
                    true
                }

                R.id.action_widget_help -> {
                    showWidgetHelp()
                    true
                }

                R.id.action_app_theme -> {
                    showThemePicker()
                    true
                }

                else -> false
            }
        }

        binding.add.setOnClickListener {
            startActivity(EditTimerActivity.editIntent(this, null))
        }
    }

    override fun onResume() {
        super.onResume()
        // Timers change from the editor and from the widget configuration screen, and
        // the values themselves go stale just by time passing, so the list is simply
        // rebuilt whenever the screen comes forward.
        val timers = store.timers()
        adapter.submit(timers)
        binding.empty.visibility = if (timers.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showWidgetHelp() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.how_to_add_a_widget)
            .setMessage(R.string.how_to_add_a_widget_body)
            .setPositiveButton(R.string.got_it, null)
            .show()
    }

    private fun showThemePicker() {
        val themes = AppTheme.entries.toTypedArray()
        val labels = arrayOf(
            getString(R.string.app_theme_auto),
            getString(R.string.app_theme_light),
            getString(R.string.app_theme_dark),
        )
        val current = themes.indexOf(store.appTheme())

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_theme_dialog_title)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                val theme = themes[which]
                store.setAppTheme(theme)
                AppCompatDelegate.setDefaultNightMode(theme.nightMode)
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
