// Standalone composite build for the Android convention plugins.
//
// This settings file is independent of the consuming Android build
// (mobile/android/settings.gradle.kts) so the convention plugins can
// be compiled against a fixed set of plugin dependencies (ktfmt, ktlint,
// detekt) without inheriting the consuming project's `pluginManagement`
// or `dependencyResolutionManagement` blocks.
//
// `rootProject.name` matches the directory so Gradle's logger
// identifies it as `build-logic` instead of the literal path.

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    versionCatalogs {
        // Re-export the consuming project's version catalog so the
        // convention plugins can resolve versions like
        // `libs.versions.ktlint.get()` instead of hard-coding literals.
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"

include(":convention")
