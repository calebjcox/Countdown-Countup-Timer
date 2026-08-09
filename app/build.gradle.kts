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
        versionCode = System.getenv("APP_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("APP_VERSION_NAME") ?: "1.0"
    }

    signingConfigs {
        // Overrides AGP's auto-generated ~/.android/debug.keystore, which is created
        // fresh on every machine — and on every CI runner, since nothing caches it.
        // A downloaded debug APK could then never update over the last one, because
        // Android refuses an update whose signing certificate has changed.
        //
        // The keystore is committed, and holds the public androiddebugkey/android
        // credentials every Android debug key uses. It is not a secret and is not
        // meant to be one: paired with the applicationIdSuffix below it can only
        // ever sign .debug, never the production package.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            // Debug installs as a separate app, so it can sit next to a production
            // install rather than replacing it: its own launcher entry, its own
            // widgets, its own timers. Without this the two compete for one package
            // slot, and since their signing keys differ they cannot even replace one
            // another — every switch would mean an uninstall, which takes the user's
            // timers with it.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // AGP's default already, said out loud so the config above is visibly
            // connected to the build type that uses it.
            signingConfig = signingConfigs.getByName("debug")
        }

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

    testImplementation(libs.junit)
    // Shadows the org.json stub in android.jar, whose methods all throw under unit
    // tests. Only the backup format needs it, and only on the test classpath — the
    // app itself still uses the platform's org.json on a device.
    testImplementation(libs.json)
}
