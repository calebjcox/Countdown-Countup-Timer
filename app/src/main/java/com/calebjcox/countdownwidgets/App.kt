package com.calebjcox.countdownwidgets

import android.app.Application
import com.google.android.material.color.DynamicColors

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Takes the app's colors from the wallpaper, matching what the widget already
        // gets for free from the framework's system_* palette.
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
