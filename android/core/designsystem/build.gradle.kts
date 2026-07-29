plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Pulls in ktfmt + ktlint + detekt and configures them per the
    // shared detekt.yml at `config/detekt/detekt.yml`. See
    // `docs/superpowers/specs/2026-07-11-ktfmt-android-lint-design.md`.
    id("livtet.android.library.lint")
}

android {
    namespace = "net.olamaelcu.livtet.core.designsystem"
    compileSdk = 36

    defaultConfig { minSdk = 24 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.03.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime")
}
