plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // Pulls in ktfmt + ktlint + detekt and configures them per the
    // shared detekt.yml at `config/detekt/detekt.yml`.
    id("livtet.android.application.lint")
}

// Hilt 2.58 bundles kotlin-metadata-jvm 2.3.x which cannot read
// metadata format 2.4.0 emitted by Kotlin 2.4.0. Force the newer
// version so the Hilt Java annotation processor can parse classes.
configurations.all { resolutionStrategy.force("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.0") }

android {
    namespace = "net.olamaelcu.livtet"
    compileSdk = 36
    ndkVersion = "27.1.12297006"

    buildFeatures { buildConfig = true }

    defaultConfig {
        applicationId = "net.olamaelcu.livtet"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"

        ndk { abiFilters += setOf("arm64-v8a", "x86", "x86_64") }

        // Instrumented test runner for FfiGoldenScenarioTest.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // SENTRY_DSN is the only build-time secret the launcher reads
        // (LivtetApp). Default is empty; the playstore flavor overrides it.
        buildConfigField("String", "SENTRY_DSN", "\"\"")
    }

    signingConfigs {
        create("release") {
            storeFile =
                file(
                    project.findProperty("LIVTET_STORE_FILE") as String?
                        ?: "keystore/livtet-release.jks"
                )
            storePassword = project.findProperty("LIVTET_STORE_PASSWORD") as String?
            keyAlias = project.findProperty("LIVTET_KEY_ALIAS") as String?
            keyPassword = project.findProperty("LIVTET_KEY_PASSWORD") as String?
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    flavorDimensions += "store"

    productFlavors {
        create("playstore") {
            dimension = "store"
            val sentryDsn: String =
                (project.findProperty("LIVTET_SENTRY_DSN_MOBILE") as String?) ?: ""
            buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
        }
        create("fdroid") {
            dimension = "store"
            applicationIdSuffix = ".fdroid"
        }
        create("generic") {
            dimension = "store"
            applicationIdSuffix = ".generic"
        }
    }

    compileOptions {
        // Readium uses java.time and other post-API-24 types.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets["main"]
        .kotlin
        .srcDir(layout.buildDirectory.dir("../../build/generated/source/uniffi/kotlin"))
    sourceSets["androidTest"].kotlin.srcDirs("src/androidTest/java")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    // UniFFI Kotlin bindings load the native library via JNA.
    implementation("net.java.dev.jna:jna:5.14.0@aar")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation(libs.timber)
    implementation(libs.sentry.android)
    implementation(project(":core:designsystem"))
    implementation(project(":core:auth"))
    // Opening a received EPUB: Readium is linked here through the reader module,
    // so the app also has to desugar (see the compileOptions block above).
    implementation(project(":core:reader"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    // Hosts Readium's EpubNavigatorFragment; the toolkit ships it as a Fragment.
    implementation(libs.androidx.fragment.ktx)
    // Feed covers are URLs from the platform's catalog, not files beside the source, so the feed
    // needs a network image loader; the library's own covers are decoded from the EPUB.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // The background poll of paired devices: an offer waits for a pull, so something has to pull
    // while the reader is not on the offers screen.
    implementation(libs.androidx.work.runtime.ktx)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    val composeBom = platform("androidx.compose:compose-bom:2026.03.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime")
    // Not covered by the Compose BOM, so the version has to be explicit: the
    // instrumentation source set has no other node supplying one.
    implementation("androidx.activity:activity-compose:1.8.2")
    // Lifecycle-aware effects in Compose: the home screen re-reads the library when
    // it comes back to the foreground, so a book deleted from the library screen
    // stops being on the shelf.
    implementation(libs.androidx.lifecycle.runtime.compose)

    // FFI golden-scenario instrumented test.
    androidTestImplementation(libs.junit)
    // The offer-inbox presentation rule is plain Kotlin and tested on the JVM.
    testImplementation(libs.junit)
    // The catalog cache round-trips JSON; the Android artifact stubs org.json for unit tests.
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
