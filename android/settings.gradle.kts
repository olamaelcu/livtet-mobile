pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // `mobile/android/build-logic` is the standalone composite build
    // that publishes convention plugins like
    // `livtet.android.application.lint` and
    // `livtet.android.library.lint`. `includeBuild` makes those plugin
    // ids resolvable by the consuming project's `plugins { id(...) }`
    // blocks.
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

include(":app")
include(":core:designsystem")
include(":core:auth")
include(":jigsaw")

// Composite build that substitutes the branding library so
// `net.olamaelcu:livtet-branding` resolves from the checked-out
// livtet-branding repo instead of a remote repository.
includeBuild("../../branding/android") {
    dependencySubstitution {
        substitute(module("net.olamaelcu:livtet-branding"))
            .using(project(":library"))
    }
}
