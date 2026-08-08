plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // The kotlin.android plugin includes compose + serialization
    // Pulls in ktfmt + ktlint + detekt and configures them per the
    // shared detekt.yml at `config/detekt/detekt.yml`. See
    // `docs/superpowers/specs/2026-07-11-ktfmt-android-lint-design.md`.
    id("livtet.android.application.lint")
}

android {
    namespace = "net.olamaelcu.livtet"
    compileSdk = 36
    ndkVersion = "27.1.12297006"

    buildFeatures { buildConfig = true }

    // Builds the literal written into
    // `BuildConfig.LABS_BUILD_TIME_DEFAULTS_JSON`. The Kotlin
    // side parses this on every read (`FeatureFlagsManager.buildTimeDefault`)
    // so the runtime value must be valid JSON, but Gradle
    // writes `buildConfigField` values verbatim into the
    // generated `BuildConfig.java` as a Java string literal —
    // meaning every inner double-quote in the JSON must be
    // backslash-escaped for Java to accept the source, AND
    // the whole literal must already start and end with `"`.
    // Returning the Java-source-ready string directly keeps
    // the call sites simple and avoids a second layer of wrapping.
    fun labsDefaultsJson(flags: Map<String, Boolean>): String =
        "\"{" + flags.entries.joinToString(separator = ",") { (k, v) -> "\\\"$k\\\":$v" } + "}\""

    defaultConfig {
        applicationId = "net.olamaelcu.livtet"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"

        ndk { abiFilters += setOf("arm64-v8a", "x86", "x86_64") }

        // Compose UI tests need the test manifest merged into the
        // main manifest so that `createAndroidComposeRule` can find
        // the activity. Without this, the rule compiles but throws
        // "no activity found" at startup. The manifest entry is in
        // `src/androidTest/AndroidManifest.xml`.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Compile-time API keys. The Google Books plugin reads
        // these via host.get_api_key("googlebooks"). Set the env
        // var at build time to enable authenticated quota; leave
        // unset to fall back to the unauthenticated per-IP path.
        // See ADR 0031 for the threat model and the iOS env var
        // convention (GOOGLE_BOOKS_IOS_API_KEY).
        buildConfigField(
            "String",
            "GOOGLE_BOOKS_API_KEY",
            "\"" + (System.getenv("GOOGLE_BOOKS_ANDROID_API_KEY") ?: "") + "\"",
        )

        // SOPS-wired values sourced from .mise/secrets.env by the calling
        // mise task. GOOGLE_API_KEY is available in all flavors. SENTRY_DSN
        // defaults to empty; the playstore flavor overrides it from the
        // SOPS env var (fdroid and generic get no tracking).
        val googleApiKey: String =
            (project.findProperty("LIVTET_GOOGLE_API_KEY_ANDROID") as String?) ?: ""
        buildConfigField("String", "GOOGLE_API_KEY", "\"$googleApiKey\"")
        buildConfigField("String", "SENTRY_DSN", "\"\"")
        buildConfigField("boolean", "GOOGLE_SIGN_IN_ENABLED", "false")
        buildConfigField("boolean", "APPLE_SIGN_IN_ENABLED", "false")

        // Labs feature-flag build-time gates. Emitted as
        // `BuildConfig.LABS_BUILD_TIME_DEFAULTS_JSON`, a map of
        // `LabsFlag.key` -> boolean. Consumed by
        // `FeatureFlagsManager.buildTimeDefault` on the Kotlin
        // side. Every entry here MUST have a matching
        // `LabsFlag` in `settings/Labs.kt`; the Kotlin resolver
        // falls back to "unlocked" for missing keys so a stale
        // build never silently enables a brand-new flag.
        //
        // Per-flavor overrides live in the `productFlavors`
        // block below — we always emit a `LABS_BUILD_TIME_DEFAULTS_JSON`
        // field there even if the JSON is identical, so flavors
        // can opt into different staged rollouts without
        // touching the default.
        val labsFlagBuildDefaults: Map<String, Boolean> =
            mapOf(
                "show_experimental_duplicates_badge" to false,
                "instant_local_file_import" to false,
            )
        buildConfigField(
            "String",
            "LABS_BUILD_TIME_DEFAULTS_JSON",
            labsDefaultsJson(labsFlagBuildDefaults),
        )
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
            buildConfigField("boolean", "GOOGLE_SIGN_IN_ENABLED", "true")
            buildConfigField("boolean", "APPLE_SIGN_IN_ENABLED", "true")

            // Play Store canary-style rollout: the duplicates-badge
            // Labs flag is unlocked so users (and Test Flight /
            // internal testers) can toggle it on. New flags ship
            // off and get promoted here once the team is ready.
            val playstoreLabs: Map<String, Boolean> =
                mapOf(
                    "show_experimental_duplicates_badge" to true,
                    "instant_local_file_import" to false,
                )
            buildConfigField(
                "String",
                "LABS_BUILD_TIME_DEFAULTS_JSON",
                labsDefaultsJson(playstoreLabs),
            )
        }
        create("fdroid") {
            dimension = "store"
            applicationIdSuffix = ".fdroid"
            // F-Droid policy is no tracking and no
            // experimental surfaces. Lock every Labs flag off
            // at the build-time gate so the toggles render
            // disabled in Settings.
            val fdroidLabs: Map<String, Boolean> =
                mapOf(
                    "show_experimental_duplicates_badge" to false,
                    "instant_local_file_import" to false,
                )
            buildConfigField(
                "String",
                "LABS_BUILD_TIME_DEFAULTS_JSON",
                labsDefaultsJson(fdroidLabs),
            )
        }
        create("generic") {
            dimension = "store"
            applicationIdSuffix = ".generic"
            // Sideload / unsigned-style build. Matches the
            // fdroid posture: no Labs surfaces exposed.
            val genericLabs: Map<String, Boolean> =
                mapOf(
                    "show_experimental_duplicates_badge" to false,
                    "instant_local_file_import" to false,
                )
            buildConfigField(
                "String",
                "LABS_BUILD_TIME_DEFAULTS_JSON",
                labsDefaultsJson(genericLabs),
            )
        }
    }

    compileOptions {
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
    implementation("net.java.dev.jna:jna:5.14.0@aar")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation(libs.timber)
    implementation(libs.sentry.android)
    implementation(libs.ulid.kotlin)
    implementation(libs.androidx.datastore.preferences)
    implementation(project(":core:designsystem"))
    implementation(project(":core:auth"))
    implementation(project(":jigsaw"))

    val composeBom = platform("androidx.compose:compose-bom:2026.03.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.activity:activity-compose")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.compose.material:material-icons-extended")

    // Instrumented test deps. See
    // `mobile/android/app/src/androidTest/java/net/olamaelcu/livtet/AddBookSearchTest.kt`
    // for the test that exercises the Add-Book wizard's search flow.
    // The `ui-test-manifest` is `debugImplementation` only (Compose
    // test convention) so the merged manifest is gated to the
    // `debug` build.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
