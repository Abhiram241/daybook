package com.daybook.app.di

import android.content.Context
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Firebase singletons for the v0.5 sync layer (FIREBASE_0.5_PLAN.md §2).
 *
 * Mirrors [DatabaseModule]: `@Module @InstallIn(SingletonComponent) object` with
 * `@Provides @Singleton` factories. `FirebaseApp` itself is initialised by the
 * `google-services` plugin's generated ContentProvider, so [com.daybook.app.DaybookApplication]
 * needs no change; Firebase's provider is separate from the `androidx.startup`
 * InitializationProvider that the manifest strips WorkManager's initializer from.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = Firebase.firestore.apply {
        // Offline persistence is on by default on Android; set it explicitly so a future SDK
        // default flip can't silently break offline launch (R9).
        firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
    }

    @Provides
    @Singleton
    fun provideCredentialManager(@ApplicationContext ctx: Context): CredentialManager =
        CredentialManager.create(ctx)
}
