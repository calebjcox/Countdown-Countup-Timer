import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

// Deliberately a plain Kotlin/JVM library: no Android imports anywhere in this
// module. Everything that decides *what the widget says* lives here, so it can be
// unit tested on a normal JVM with no emulator and no Android SDK.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    // Tests pass explicit zones/clocks; this just guarantees a stable default so a
    // stray `ZoneId.systemDefault()` can never make CI disagree with a laptop.
    systemProperty("user.timezone", "UTC")
    testLogging {
        events("passed", "failed", "skipped")
    }
}
