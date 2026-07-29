// Build script for the `convention` subproject of `build-logic`.
//
// This composite build publishes *binary* Gradle convention plugins
// (written as compiled Kotlin classes under `src/main/kotlin/`), not
// precompiled script plugins. For binary plugins, the standard
// combination is `kotlin("jvm")` + `java-gradle-plugin`:
//
//  - `kotlin("jvm")` brings in the Kotlin compiler + stdlib as a real
//    compile dependency (not Gradle's embedded Kotlin), so the version
//    we use matches what the consuming project uses.
//  - `java-gradle-plugin` adds the `gradlePlugin { plugins { ... } }`
//    DSL that registers our plugin ids.
//
// `kotlin-dsl` is the right choice for *precompiled script* plugins
// (buildSrc-style `.gradle.kts` files). For binary plugins, mixing
// it with another Kotlin version produces "Language version 1.8 is
// no longer supported" errors because `kotlin-dsl` pins language/API
// version to 1.8 by default.
plugins {
    kotlin("jvm") version "2.4.0"
    `java-gradle-plugin`
}

dependencies {
    // `gradleApi()` brings in core Gradle types like `Project`,
    // `Plugin`, `ExtensionContainer`, `Property<T>`, etc.
    // `gradleKotlinDsl()` adds the Kotlin DSL extension functions
    // (`extensions.configure<T> { ... }`, `the<T>()`, etc.).
    // `java-gradle-plugin` also pulls in `gradleApi()` automatically;
    // we redeclare it explicitly so the dependency is obvious in
    // this file.
    implementation(gradleApi())
    implementation(gradleKotlinDsl())

    // The three lint plugins below are referenced directly in our
    // convention plugin bytecode (KtfmtExtension, KtlintExtension,
    // DetektExtension), so Gradle needs them on the runtime classpath
    // when it decorates our plugin class before invoking `apply()`.
    //
    // `api` (rather than `implementation`) exposes them transitively
    // to consumers via `java-gradle-plugin`'s plugin classpath; this
    // is what lets Gradle load `KtlintExtension` etc. when the
    // Android module's plugin manager instantiates us.
    //
    // These are the *actual* plugin jars, not the Gradle "plugin
    // marker" artifacts (which have packaging=pom and can't be used
    // as compile deps). The marker coordinates (e.g.
    // `com.ncorti.ktfmt.gradle:com.ncorti.ktfmt.gradle.gradle.plugin`)
    // only resolve through the `plugins { id(...) version ... }` DSL.
    api("com.ncorti.ktfmt.gradle:plugin:${libs.versions.ktfmt.get()}")
    api("org.jlleitschuh.gradle:ktlint-gradle:${libs.versions.ktlint.get()}")
    api("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:${libs.versions.detekt.get()}")
}

// Match the consuming modules' Java compile target
// (`JavaVersion.VERSION_17`). No toolchain declaration — we let the
// JVM running Gradle (currently Java 20 via Mise) handle compilation.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Register our convention plugins so the consuming project can apply
// them by id (e.g. `id("livtet.android.application.lint")`).
gradlePlugin {
    plugins {
        register("androidApplicationLint") {
            id = "livtet.android.application.lint"
            implementationClass =
                "AndroidApplicationLintConventionPlugin"
        }
        register("androidLibraryLint") {
            id = "livtet.android.library.lint"
            implementationClass =
                "AndroidLibraryLintConventionPlugin"
        }
    }
}
