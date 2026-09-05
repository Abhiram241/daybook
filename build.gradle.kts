// Top-level build file. Module configuration lives in app/build.gradle.kts
plugins {
    id("com.android.application") version "8.3.2" apply false
    // Kotlin 2.0.21 (K2) — v0.5.2 §4 option C′. Buys strong skipping + Modifier.animateItem.
    // AGP 8.3.2, compileSdk 34 and every pinned library stay exactly where they are: the premise
    // that Compose 1.7 forces compileSdk 35 is false (that gate is Compose 1.8). kapt stays kapt
    // (K2 kapt runs the Room/Hilt processors in K1 compat mode).
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    // The Compose compiler is its own Gradle plugin as of Kotlin 2.0 — it replaces the
    // composeOptions { kotlinCompilerExtensionVersion } block. Applied in app/build.gradle.kts.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.dagger.hilt.android") version "2.51" apply false
    id("org.jetbrains.kotlin.kapt") version "2.0.21" apply false
    // Firebase: reads app/google-services.json and generates R.string.default_web_client_id
    // (needed by Credential Manager for Google sign-in). 4.4.2 is the known-good pairing with
    // AGP 8.3.2 — do not float this version (see FIREBASE_0.5_PLAN.md R7).
    id("com.google.gms.google-services") version "4.4.2" apply false
    // LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 10 (C-18): Crashlytics reuses the same
    // app/google-services.json already in place for Auth/Firestore — only needs enabling in the
    // Firebase console (if not already) plus this plugin/dependency locally.
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
    // Accent-updates round — Firebase App Distribution (free/Spark-tier in-app-update mechanism
    // for this sideloaded testing-phase app; Play Core's In-App Updates API doesn't apply, this
    // app has no Play listing). 5.3.0 is the latest published release at time of wiring;
    // downgrade here if it turns out to need a newer AGP than the pinned 8.3.2.
    id("com.google.firebase.appdistribution") version "5.3.0" apply false
}
