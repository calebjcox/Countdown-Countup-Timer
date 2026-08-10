import com.android.build.api.variant.HostTestBuilder
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.util.Base64

plugins {
    // No Kotlin plugin here on purpose: AGP 9 compiles Kotlin itself, and applying
    // org.jetbrains.kotlin.android alongside it is now an error. The Kotlin JVM
    // target follows compileOptions below.
    alias(libs.plugins.android.application)
}

// The upload key, as three environment variables and nothing else. There is no
// keystore.properties and no fallback to one: a file on disk is a second place the
// key can leak from and a second thing that can silently disagree with CI, and the
// release workflow already has these as repository secrets.
//
// Three, not four, because there is no separate key password to hold. The upload
// keystore is PKCS12 — what keytool has written by default since JDK 9 — and PKCS12
// has no room for a key password that differs from the store password. Ask keytool
// for one with -keypass and it warns that it is ignoring you, then does. So the
// store password unlocks the key as well, and a KEY_PASSWORD variable could only
// ever be a duplicate of KEYSTORE_PASSWORD or a lie.
val keystoreBase64 = providers.environmentVariable("KEYSTORE_B64")
val keystorePassword = providers.environmentVariable("KEYSTORE_PASSWORD")
val uploadKeyAlias = providers.environmentVariable("KEY_ALIAS")

val uploadKeystore = layout.buildDirectory.file("signing/upload.jks")

// Decoding happens in a task rather than while the build script is evaluated, for
// two reasons. Configuration is skipped entirely on a configuration-cache hit — and
// the cache is on in gradle.properties — so a file written from the script body
// would never come back after a clean. And configuration runs for every build,
// including :app:assembleDebug and :core:test, which have no business demanding a
// release key. A task with a declared output is regenerated whenever the file is
// missing, and only runs when something downstream of it is asked for.
val prepareUploadKeystore = tasks.register("prepareUploadKeystore") {
    description = "Decodes and verifies the upload keystore held in KEYSTORE_B64."

    outputs.file(uploadKeystore)
    // A private key must not be written into a shared build-cache entry, and
    // verification costs a millisecond, so it is never worth skipping.
    outputs.cacheIf { false }
    outputs.upToDateWhen { false }

    // Read through locals so the action never touches `project`, which the
    // configuration cache forbids at execution time.
    val encoded = keystoreBase64
    val storePassword = keystorePassword
    val alias = uploadKeyAlias
    val target = uploadKeystore

    doLast {
        // Declared inside the action on purpose: a helper at the top level of a
        // .kts file is a method on the script object, and referring to it from a
        // task action would drag that object into the configuration cache, which
        // cannot serialize it.
        fun loadKeyStore(type: String, bytes: ByteArray, password: String): KeyStore =
            KeyStore.getInstance(type).apply {
                ByteArrayInputStream(bytes).use { load(it, password.toCharArray()) }
            }

        val missing = listOf(
            "KEYSTORE_B64" to encoded,
            "KEYSTORE_PASSWORD" to storePassword,
            "KEY_ALIAS" to alias,
        ).filter { (_, value) -> value.orNull.isNullOrBlank() }.map { (name, _) -> name }

        if (missing.isNotEmpty()) {
            throw GradleException(
                "Release builds are signed with the upload key, and these environment " +
                    "variables are unset or empty: ${missing.joinToString(", ")}. " +
                    "See \"Releasing\" in the README. Debug builds need none of this — " +
                    "try :app:assembleDebug instead.",
            )
        }

        // The MIME decoder, not the plain one: a base64 blob that has been through a
        // terminal or a secrets field usually carries line breaks, and the strict
        // decoder rejects those outright. The trade is that it discards anything it
        // does not recognise instead of complaining, so a value that is not base64
        // at all arrives here as an empty array rather than an exception — hence the
        // emptiness check, which would otherwise surface as a confusing "not a
        // keystore" further down.
        val bytes = try {
            Base64.getMimeDecoder().decode(encoded.get().trim())
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "KEYSTORE_B64 is not valid base64: ${e.message}. It should be the " +
                    "output of `base64 -w0 upload.jks`.",
                e,
            )
        }

        if (bytes.isEmpty()) {
            throw GradleException(
                "KEYSTORE_B64 decoded to nothing, so it holds no base64 data at all. " +
                    "It should be the output of `base64 -w0 upload.jks`.",
            )
        }

        // "JKS" rather than the default type: the SUN provider reads both JKS and
        // PKCS12 under it, and keytool has written PKCS12 by default since JDK 9, so
        // a keystore made this year and one made in 2016 both load. The explicit
        // PKCS12 retry covers a JDK with that compatibility mode switched off.
        val store = try {
            loadKeyStore("JKS", bytes, storePassword.get())
        } catch (e: Exception) {
            try {
                loadKeyStore("PKCS12", bytes, storePassword.get())
            } catch (_: Exception) {
                throw GradleException(
                    "The keystore in KEYSTORE_B64 could not be opened: " +
                        "${e.message ?: e::class.simpleName}. Either the bytes are not " +
                        "a keystore or KEYSTORE_PASSWORD is wrong.",
                    e,
                )
            }
        }

        if (!store.containsAlias(alias.get())) {
            throw GradleException(
                "The keystore has nothing aliased \"${alias.get()}\". It contains: " +
                    store.aliases().toList().joinToString(", ").ifEmpty { "(nothing)" } +
                    ". Fix KEY_ALIAS.",
            )
        }

        // Present is not the same as usable: an alias can name a trusted certificate
        // with no private key behind it, which satisfies containsAlias and signs
        // nothing.
        if (!store.isKeyEntry(alias.get())) {
            throw GradleException(
                "\"${alias.get()}\" is in the keystore, but it is a certificate rather " +
                    "than a signing key — there is no private key under that alias to " +
                    "sign with.",
            )
        }

        // Proves the key actually comes out, rather than letting the signing task
        // discover otherwise after a full release compile. The store password is the
        // key password too — see the note where these variables are declared.
        try {
            store.getKey(alias.get(), storePassword.get().toCharArray())
        } catch (e: Exception) {
            throw GradleException(
                "The key \"${alias.get()}\" would not open with KEYSTORE_PASSWORD: " +
                    "${e.message?.trimEnd('.')}. This build assumes a PKCS12 keystore, " +
                    "where the key password and the store password are necessarily the " +
                    "same. A keystore created with -storetype JKS can hold a genuinely " +
                    "separate key password, and if this one does it needs a KEY_PASSWORD " +
                    "variable adding back.",
                e,
            )
        }

        val file = target.get().asFile
        file.parentFile.mkdirs()
        file.writeBytes(bytes)
        // Owner-only, so the key is not readable by other accounts on a shared
        // machine for the rest of the build.
        file.setReadable(false, false)
        file.setReadable(true, true)
    }
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

        // Created unconditionally, even when the environment variables are absent.
        // That is the point: attaching it to the release build type means a release
        // with no key *fails*, where the alternative — configuring it only when the
        // secrets happen to be present — would quietly produce an unsigned build
        // that looks like a successful one.
        //
        // Empty strings rather than nulls for the same reason. A signing config with
        // a null password is "not ready" as far as the build tools are concerned,
        // and something not ready is skipped rather than rejected; empty means
        // present-but-wrong, which nothing will sign with and nothing will ignore.
        // prepareUploadKeystore fails long before either matters, so this is only
        // the second lock — but an unsigned release reaching Play is exactly the
        // failure worth holding two keys against.
        create("release") {
            storeFile = uploadKeystore.get().asFile
            storePassword = keystorePassword.getOrElse("")
            keyAlias = uploadKeyAlias.getOrElse("")
            // The same password twice, and not a mistake: PKCS12 keeps no separate
            // one for the key.
            keyPassword = keystorePassword.getOrElse("")
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
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }

    testOptions {
        unitTests {
            // Robolectric runs the real framework against the real resource table,
            // and cannot see either without this. The Play Store asset generator
            // under src/test/.../playassets depends on it entirely: it renders the
            // shipping layouts, themes and drawables to PNG.
            isIncludeAndroidResources = true
        }
    }
}

/**
 * The android-all platform jar Robolectric runs the framework from, resolved through
 * Gradle rather than downloaded by Robolectric's own Maven client at test runtime.
 * The version is pinned in the catalogue like everything else, so a generator run
 * years from now uses the same platform bytes as today's.
 */
val robolectricPlatform = configurations.create("robolectricPlatform") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val stageRobolectricPlatform = tasks.register<Sync>("stageRobolectricPlatform") {
    description = "Stages the android-all jar where Robolectric's offline mode looks for it."
    from(robolectricPlatform)
    into(layout.buildDirectory.dir("robolectric-platform"))
}

/**
 * The asset generator is a set of unit tests, but it is not part of the test suite:
 * it is slow, it writes a couple of dozen PNGs, and its output is judged by eye
 * rather than asserted. So it is excluded unless `-PplayAssets` asks for it, and CI
 * goes on running `testDebugUnitTest` without ever touching it.
 */
val generatingPlayAssets = providers.gradleProperty("playAssets").isPresent

/**
 * A build type that exists only while assets are being generated, because neither of
 * the other two will do.
 *
 * Not `debug`: `src/debug/res` renames the app to "Countdowns (debug)", which
 * `MainActivity` puts straight in its app bar, so every screenshot would carry the
 * wrong name. Not `release` either: `preReleaseBuild` depends on
 * `prepareUploadKeystore` above, and compiling the release variant is exactly what a
 * release unit test does — so generating screenshots would demand the upload key. That
 * guard is worth keeping intact rather than working around, and rendering marketing
 * images has no business needing the key that signs shipped builds.
 *
 * Source sets are per build type and there is no `src/playAssets/`, so `app_name`
 * resolves from `src/main`: "Countdowns". The generator asserts that.
 */
val playAssetsBuildType = "playAssets"

android.buildTypes {
    if (generatingPlayAssets) {
        create(playAssetsBuildType) {
            // Never installed anywhere — it exists to be rendered, not to run — but a
            // variant with no signing config at all fails to assemble.
            isDebuggable = true
            signingConfig = android.signingConfigs.getByName("debug")
        }
    }
}

// AGP only builds unit tests for the debug variant, so the one variant that needs them
// has to ask.
androidComponents {
    beforeVariants(selector().withBuildType(playAssetsBuildType)) { variant ->
        variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
}

tasks.withType<Test>().configureEach {
    // Rendered text includes formatted dates, so the locale and zone have to be
    // fixed or the screenshots would differ between a laptop and CI. Mirrors what
    // core/build.gradle.kts already does for the pure-JVM tests.
    systemProperty("user.timezone", "America/Denver")
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")

    if (!generatingPlayAssets) {
        exclude("**/playassets/**")
        return@configureEach
    }

    dependsOn(stageRobolectricPlatform)
    systemProperty("robolectric.offline", "true")
    systemProperty(
        "robolectric.dependency.dir",
        layout.buildDirectory.dir("robolectric-platform").get().asFile.path,
    )
    // Rendering a 2560x1440 screenshot means several 15 MB bitmaps live at once, on
    // top of the framework Robolectric instruments into the same JVM.
    maxHeapSize = "4g"

    // Only the playAssets variant is told where to write, so a debug or release run
    // cannot produce assets even by accident — it skips the package for want of this.
    if (name == "testPlayAssetsUnitTest") {
        systemProperty(
            "playAssets.outputDir",
            rootProject.layout.projectDirectory.dir("play-assets").asFile.path,
        )
    }
}

// preReleaseBuild is upstream of everything in the release variant, so bundleRelease,
// assembleRelease and lintRelease all stop here — before any compilation — when the
// key is missing or wrong. Nothing in the debug variant passes through it, which is
// what keeps :app:assembleDebug working with no secrets at all.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(prepareUploadKeystore)
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
    // Used only by the Play Store asset generator. Test-only, so none of this
    // reaches the APK.
    testImplementation(libs.robolectric)
    robolectricPlatform(libs.android.all.instrumented)
}
