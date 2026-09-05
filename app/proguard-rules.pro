# ---- Keep crash-report readability; skip obfuscation (sideloaded beta, no Play mapping upload) ----
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,*Annotation*,InnerClasses,Signature,EnclosingMethod
-renamesourcefileattribute SourceFile

# ---- kotlinx.serialization (R8 full mode) ----
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **$*
-keepclassmembers class <1>$<2> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.daybook.app.**$$serializer { *; }
-keep @kotlinx.serialization.Serializable class com.daybook.app.data.model.** { *; }
# v2 backup wire model — field names ARE the file format, so nothing here may be shrunk (L4).
-keep @kotlinx.serialization.Serializable class com.daybook.app.data.backup.** { *; }
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.**

# ---- Enums used by name (Room stores enums by name; ColorTag.fromNameOrAuto / DayOfWeek.valueOf) ----
-keepclassmembers enum com.daybook.app.data.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static ** fromNameOrAuto(java.lang.String);
}
-keepclassmembers class com.daybook.app.ui.theme.AccentColor {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- Room (generated impls are automatic; keep entities to be safe) ----
-keep class com.daybook.app.data.model.** { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# ---- Hilt / Dagger (plugin ships consumer rules; belt-and-braces) ----
-dontwarn dagger.hilt.**
-keep class dagger.hilt.internal.** { *; }
-keep class hilt_aggregated_deps.** { *; }

# ---- Coroutines ----
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
-dontwarn kotlinx.coroutines.**

# ---- Compose: no rules needed (compiler + library consumer rules handle it) ----

# ---- App lock: security-crypto / Tink (v0.5.1 §K, plan R5) ----
# Tink resolves key managers and protobuf message types reflectively. R8 can shrink them without
# any build-time complaint, and the failure then shows up only at runtime, as a throw from the
# first EncryptedSharedPreferences.create() on the RELEASE build. Test the release APK's app-lock
# enable flow on a device, not just the debug one.
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite { <fields>; }
-dontwarn com.google.crypto.tink.**

# ---- App lock: PIN hashing enum stored by name in EncryptedSharedPreferences ----
-keepclassmembers enum com.daybook.app.data.lock.LockTimeout {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- androidx.biometric ----
-dontwarn androidx.biometric.**
