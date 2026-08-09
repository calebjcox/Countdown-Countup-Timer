package com.calebjcox.countdownwidgets

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.calebjcox.countdownwidgets.data.TimerStore

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(TimerStore(this).appTheme().nightMode)
        // Takes the app's colors from the wallpaper, matching what the widget already
        // gets for free from the framework's system_* palette.
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
