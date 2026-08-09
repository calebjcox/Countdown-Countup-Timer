package com.calebjcox.countdownwidgets.data

import androidx.appcompat.app.AppCompatDelegate

/** The user's manual override for the app's own light/dark appearance. */
enum class AppTheme {
    AUTO,
    LIGHT,
    DARK;

    val nightMode: Int
        get() = when (this) {
            AUTO -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
}
