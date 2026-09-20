import java.util.Properties
import com.google.firebase.appdistribution.gradle.firebaseAppDistribution

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Kotlin 2.0 Compose compiler plugin (v0.5.2 §4 C′) — owns the compiler version now, so the
    // old `composeOptions { kotlinCompilerExtensionVersion = "1.5.10" }` block is gone.
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")   // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 10 (C-18)
    id("com.google.firebase.appdistribution") // Accent-updates round — in-app update checks
}

// Release signing: credentials live in keystore.properties at the repo root (gitignored).
// If that file is absent the release build falls back to the debug key so it still assembles.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseSigning = keystorePropsFile.exists()

android {
    namespace = "com.daybook.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.daybook.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 53          // v0.8.0 build 53 (2026-09-20) — Beast Mode Settings
                                   // reorganized into categorized sub-screens with clearer
                                   // explanations. Data source priority is now a ranked list per
                                   // data point (falls back to the next app if the top one has no
                                   // data that day), and changing it now re-applies to past days
                                   // too. Hydration moved out of Beast Mode into Intake — turn it
                                   // on in Settings, log it from Intake/Today with a configurable
                                   // reminder (default 9pm), still synced to Health Connect.
                                   // Workout session photos now back up to the cloud. Fixed
                                   // several spacing/alignment glitches (New habit form, workout
                                   // detail/session screens, History's filter row).
                                   // v0.8.0 build 52 (2026-09-20) — fixed filter chips (muscle
                                   // filter on Workout detail, and History's Muscle Group/Exercise/
                                   // Date filters) rendering with no visible label text. Health
                                   // Connect "Data source priority" now has a separate setting per
                                   // data point (Steps, Distance, Calories, Heart rate, SpO2,
                                   // Hydration, Weight, Nutrition) instead of one combined switch,
                                   // and the app picker for each now discovers every app actually
                                   // syncing into Health Connect (fixes Mi Fitness not appearing).
                                   // v0.8.0 build 51 (2026-09-20) — Workout detail: muscle-group
                                   // filter chips + muscle pill on each exercise card + tighter
                                   // card spacing. Equipment thumbnails now always sit on a white
                                   // backing (fixes the dark icon in History). Health Connect: when
                                   // two apps report the same metric, the app now picks one source
                                   // instead of summing both (new "Data source priority" setting in
                                   // Beast Mode Settings) and old health tiles now show which app a
                                   // value came from, not just today's.
                                   // v0.8.0 build 50 (2026-09-20) — Health tab: aggregated
                                   // "Walking"-style session tiles (grouped by exercise type +
                                   // day, tap-to-split) instead of one raw tile per Health Connect
                                   // segment, plus a data-source line on every tile; fixed a
                                   // build-49 regression where Home and Health's month-eviction
                                   // pins shared one set (R-1); rest-timer alert now plays on the
                                   // media volume stream with a user-choosable tone; Routines'
                                   // settings gear now matches History/Health's tinted style.
                                   // v0.8.0 build 49 (2026-09-20) — health-sync battery fixes
                                   // (capped automatic resync window, delta-pull scope limited to
                                   // the daily worker, no-op writes skipped), import data-loss
                                   // fixes (evicted months rehydrate for the Health tab, null
                                   // reads no longer overwrite stored metrics), and a settings
                                   // gear added to Beast Mode's History and Health tabs.
                                   // v0.8.0 build 48 — filter bar single-row + elapsed-time-on-edit
                                   // fix + Detail screen redesign parity with History.
                                   // v0.8.0 build 47 — the Beast Mode round: all 4 Critical +
                                   // both in-scope High audit findings fixed (sync data-loss,
                                   // downgrade wipe, Hevy import race, sleep-dedup continuation
                                   // bug, Health Connect scope/backfill), battery B-1/B-2/B-3,
                                   // exercise picker performance, Hevy review queue + missing-
                                   // details prompt, rest-timer sound, workout session photos,
                                   // History tab muscle-group filter/grouping, and more — see
                                   // major-fixes-17-spet/PROGRESS.md for the full round.
        versionName = "0.8.0"     // v0.8.0 — the Beast Mode "major fixes" round (see PROGRESS.md)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // For Room database
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }

    // Ship the exported schema JSONs as androidTest assets so MigrationTestHelper can load them.
    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Accent-updates round (Phase 6) — lets `./gradlew assembleRelease
            // appDistributionUploadRelease` upload straight to App Distribution's "testers"
            // group. Optional alternative to the plain `firebase appdistribution:distribute`
            // CLI command (both work; the CLI needs no Gradle DSL at all and is documented as
            // the primary path — see ACCENT_UPDATES_PLAN.md Phase 6 / the regression doc).
            firebaseAppDistribution {
                releaseNotes = "Beast Mode Settings is reorganized into Appearance / Workout " +
                    "defaults / Health Connect sync / Data source priority / Import & backup, " +
                    "with clearer explanations. Data source priority is now a ranked list per " +
                    "data point (Steps, Distance, Calories, Heart rate, SpO2, Hydration, Weight, " +
                    "Nutrition) — set an order of apps to trust, and if your top choice has no " +
                    "data for a day it falls back to the next one; changing the order now also " +
                    "re-applies to your past days, not just new syncs. Hydration has moved out " +
                    "of Beast Mode into Intake — turn it on in Settings, then log water and set " +
                    "its reminder time (default 9pm) from Intake or Today, same as other intake " +
                    "items; it still logs to Health Connect. Workout session photos now back up " +
                    "to the cloud. Fixed spacing/alignment issues on the New habit form, workout " +
                    "detail and session screens, and made History's 3 filter chips equal width."
                groups = "testers"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME is stamped into the backup file's `meta.appVersionName` (L4).
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    
    // Room database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Hilt for dependency injection
    implementation("com.google.dagger:hilt-android:2.51")
    kapt("com.google.dagger:hilt-compiler:2.51")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // WorkManager: daily background refresh of the rolling reminder window so reminders don't
    // stop after WINDOW_DAYS when the app is never opened (REV-06). hilt-work lets the worker
    // inject OccurrenceScheduler.
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Serialization for JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Firebase — Auth + Firestore for cross-device sync (v0.5). BoM 33.1.2 is the last line
    // that builds against compileSdk 34; bump compileSdk before the BoM (FIREBASE_0.5_PLAN.md R7).
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    // Workout session photos → Firebase Storage backup round (2026-09-20). Version comes from the
    // firebase-bom platform above, same as firebase-auth/firebase-firestore.
    implementation("com.google.firebase:firebase-storage")
    // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 10 (C-18): version comes from the BoM above.
    implementation("com.google.firebase:firebase-crashlytics")
    // Accent-updates round (Phase 6) — in-app update checks via updateIfNewReleaseAvailable().
    // NOT managed by firebase-bom (this artifact isn't in its version catalog) — pin explicitly.
    // 16.0.0-beta14 is the last release that depends on firebase-common:21.0.0, exactly what
    // firebase-bom:33.1.2 already pins; anything newer (beta15+) bumps firebase-common to 22.0.1,
    // which is incompatible with the still-21.0.0-pinned firebase-common-ktx transitively pulled
    // in elsewhere (FirebaseModule.kt's `Firebase` accessor becomes unresolvable) — do not float
    // this past beta14 without also re-checking every other Firebase artifact's ktx compatibility.
    implementation("com.google.firebase:firebase-appdistribution:16.0.0-beta14")

    // Credential Manager — the supported replacement for the deprecated GoogleSignIn client.
    // 1.3.0 is the last that builds against compileSdk 34.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // App lock (v0.5.1 §K). Pinned, not floated — see V051_PLAN.md R3.
    // biometric 1.1.0 is the last stable that builds against compileSdk 34 (1.2.0-alpha* pulls
    // androidx that wants 35). It brings FragmentActivity as a hard requirement: BiometricPrompt's
    // constructor takes one, which is why MainActivity extends FragmentActivity now.
    // biometric-ktx is still alpha and not needed — the Java API is enough.
    implementation("androidx.biometric:biometric:1.1.0")
    // EncryptedSharedPreferences for the PIN hash + per-install salt. 1.1.0-alpha06 is the last
    // release compatible with compileSdk 34; alpha07+ requires 35.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Round B (Health Connect, HEALTH_AND_WORKOUT_PLAN.md §5.1) — pinned to alpha08 deliberately:
    // it's the last connect-client release that builds against compileSdk 34 / AGP 8.3.2 / Gradle
    // 8.6 (1.1.0-alpha09+ requires compileSdk 35, 1.1.0 stable requires compileSdk 36 + AGP 8.9.1).
    // Moving off this pin is its own later toolchain round (§5.1) — do not float this version.
    implementation("androidx.health.connect:connect-client:1.1.0-alpha08")

    // Image loading — renders the profile photo copied into filesDir (L5).
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Reads EXIF orientation off picked photos so the saved avatar isn't sideways (Section 11).
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // DAILY_REPORT_PLAN.md §3.3 — the app's first direct HTTP client dependency, used only by
    // data/ai/'s seven AI-provider adapters (Firebase's own SDKs handle Firestore's networking
    // internally). One small, well-known, actively-maintained library beats hand-rolling
    // HttpURLConnection across three distinct request shapes.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Round 2 (AI Summary markdown fix) — the AI-generated summary/chat text comes back as
    // markdown (`**bold**`, `- bullets`); it was previously dumped into a plain `Text`, so the
    // literal asterisks/dashes showed. No markdown renderer existed anywhere in the app already
    // (checked before adding this). A small, actively-maintained Compose-native renderer beats
    // hand-rolling a parser; the Material3 flavour picks up MaterialTheme (bridged to
    // DaybookColors/DaybookText at the call site) instead of its own default look.
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.26.0")

    // Testing — JUnit 4 only. junit-jupiter (JUnit 5) was declared but ran nothing: no Jupiter
    // platform runner is configured and AGP's unit-test task is JUnit 4 (REV-43).
    testImplementation("junit:junit:4.13.2")
    // androidTest = MigrationTest + NavIconInflateTest only — both plain instrumented JUnit4, no
    // Compose UI test, no Espresso, so the compose-bom platform / ui-test-* / espresso deps that
    // used to sit here were dead weight (v0.5.2 §6.1).
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    // ui-tooling powers the Android Studio @Preview panel; debug-only, zero release cost — kept.
    debugImplementation("androidx.compose.ui:ui-tooling")
}
