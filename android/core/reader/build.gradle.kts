plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    // Pulls in ktfmt + ktlint + detekt and configures them per the
    // shared detekt.yml at `config/detekt/detekt.yml`.
    id("livtet.android.library.lint")
}

android {
    namespace = "net.olamaelcu.livtet.core.reader"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        // Readium uses java.time and friends; the toolkit's own docs require
        // desugaring in every module that links it, not just the app.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    // readium-shared (Publication / Locator models), readium-streamer (opens a
    // file into a Publication), readium-navigator (the EPUB fragment host).
    // No OPDS module: catalog search is XRPC, not OPDS. No LCP module: nothing
    // this app receives is DRM-wrapped.
    api(libs.bundles.readium)
    implementation(libs.kotlinx.coroutines.core)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
