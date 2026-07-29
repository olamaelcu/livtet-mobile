# ============================================================================
# Livtet ProGuard / R8 rules
# ============================================================================
# Tested against AGP 8.13.2, Kotlin 2.4.0, Readium 3.1.2, Ktor 3.0.3, Sentry
# 8.43.0, ML Kit Barcode 17.3.0, kotlinx-serialization 1.7.3, JNA 5.14.0,
# kotlinx-coroutines 1.11.0, AndroidX 2026.03.00 BOM.
#
# Most third-party libraries ship consumer rules in their AARs; this file
# adds only what's missing. Keep the file SMALL and SURGICAL.
# ============================================================================

# --- Kotlin standard library & metadata -------------------------------
# Kotlin reflection is used by Hilt (Phase 3) and kotlinx-serialization.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes RuntimeVisibleTypeAnnotations
-dontwarn kotlin.reflect.jvm.internal.**

# --- kotlinx-serialization (MANDATORY) --------------------------------
# Without these, @Serializable classes lose their $$serializer companion
# at runtime and throw SerializationException on first decode.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep generated $$serializer classes themselves.
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- UniFFI generated bindings + JNA ----------------------------------
# The generated bindings (net.olamaelcu.livtet.ffi.livtet_ffi.*) load the
# native library via JNA. R8 must not rename the package, class, or any
# method that JNA reflects on, otherwise System.loadLibrary mapping breaks.
-keep class net.olamaelcu.livtet.ffi.** { *; }
-keep class net.olamaelcu.livtet.ffi.livtet_ffi.** { *; }
-keep class livtet.ffi.** { *; }
-keep class * implements com.sun.jna.Library { *; }
-keep class * implements com.sun.jna.Callback { *; }
-keepclassmembers class * extends com.sun.jna.Library {
    *;
}
-keepclassmembers class * extends com.sun.jna.Callback {
    *;
}
-dontwarn com.sun.jna.**

# UniFFI callback interfaces (e.g. for object handles and FfiConverter).
-keep class * implements com.sun.jna.FromNativeConverter { *; }
-keep class * implements com.sun.jna.ToNativeConverter { *; }
-keep class * implements com.sun.jna.TypeMapper { *; }

# Native method declarations (Readium + ML Kit native).
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- Readium 3.1.2 ---------------------------------------------------
# AAR consumer rules cover most cases; these are defensive.
-keep class org.readium.** { *; }
-dontwarn org.readium.**
-keep class org.readium.navigator.** { *; }
-keep class org.readium.r2.** { *; }
-keep class org.readium.adapter.** { *; }

# --- Ktor 3.0.3 (OkHttp engine) --------------------------------------
# AAR consumer rules cover most; defensive dontwarn for slf4j/netty
# if CIO engine is ever swapped in.
-dontwarn org.slf4j.**
-dontwarn io.netty.**
-dontwarn ch.qos.logback.**
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# --- kotlinx-coroutines ----------------------------------------------
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.debug.**

# --- Sentry Android 8.x ----------------------------------------------
# AAR consumer rules cover most; keep our consent / PII redaction
# callback classes so Sentry can find them via the manifest entry.
-keep class io.sentry.** { *; }
-keep class net.olamaelcu.livtet.SentryBeforeSendCallback { *; }
-keep class net.olamaelcu.livtet.SentryConsentManager { *; }
-dontwarn io.sentry.**

# --- ML Kit Barcode ---------------------------------------------------
# AAR consumer rules cover native bindings.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-dontwarn com.google.mlkit.**

# --- AndroidX (Compose, CameraX, Lifecycle, WorkManager, etc.) ------
# Consumer rules are shipped in AARs; defensive keeps for reflection-based
# code paths.
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.**
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep public class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

# --- CameraX ---------------------------------------------------------
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# --- Coroutines internal ---------------------------------------------
-keepclassmembers class kotlinx.coroutines.android.AndroidExceptionPreHandler {
    public <init>();
}
-keepclassmembers class kotlinx.coroutines.android.AndroidDispatcherFactory {
    public <init>();
}

# --- Readium JNI bridge (some versions ship native via JNI) --------
-keep class org.readium.navigator.preferences.** { *; }
-dontwarn org.readium.navigator.preferences.**

# --- Misc safety -----------------------------------------------------
-dontnote **
-dontwarn java.lang.invoke.StringConcatFactory

# Suppress warnings for types in optional dependencies.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
