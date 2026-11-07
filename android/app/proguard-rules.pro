# ============================================================================
# Livtet ProGuard / R8 rules
# ============================================================================
# Tested against AGP 8.13.2, Kotlin 2.4.0, Sentry 8.43.0, JNA 5.14.0,
# kotlinx-coroutines 1.11.0, AndroidX 2026.03.00 BOM.
#
# Most third-party libraries ship consumer rules in their AARs; this file
# adds only what's missing. Keep the file SMALL and SURGICAL.
# ============================================================================

# --- Kotlin standard library & metadata -------------------------------
# Kotlin reflection is used by Hilt.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes RuntimeVisibleTypeAnnotations
-dontwarn kotlin.reflect.jvm.internal.**

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

# Native method declarations.
-keepclasseswithmembernames class * {
    native <methods>;
}

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

# --- AndroidX (Compose, Lifecycle, WorkManager, etc.) ---------------
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

# --- Coroutines internal ---------------------------------------------
-keepclassmembers class kotlinx.coroutines.android.AndroidExceptionPreHandler {
    public <init>();
}
-keepclassmembers class kotlinx.coroutines.android.AndroidDispatcherFactory {
    public <init>();
}

# --- Misc safety -----------------------------------------------------
-dontnote **
-dontwarn java.lang.invoke.StringConcatFactory

# Suppress warnings for types in optional dependencies.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
