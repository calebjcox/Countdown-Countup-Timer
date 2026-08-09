plugins {
    // No Kotlin plugin here on purpose: AGP 9 compiles Kotlin itself, and applying
    // org.jetbrains.kotlin.android alongside it is now an error. The Kotlin JVM
    // target follows compileOptions below.
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.calebjcox.countdownwidgets"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.calebjcox.countdownwidgets"
        // Android 12. Every widget API this app relies on — responsive layouts,
        // reconfigurable widgets, the Material You system palette and the widget
        // corner-radius dimensions — landed here, so there is no version branching
        // anywhere in the code.
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Left off on purpose. The app is a few hundred kilobytes either way,
            // and not running R8 removes a whole class of "worked in debug" bugs.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
}
