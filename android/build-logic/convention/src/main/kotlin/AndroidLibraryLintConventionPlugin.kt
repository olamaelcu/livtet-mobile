import com.ncorti.ktfmt.gradle.KtfmtExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/**
 * Lint convention plugin for modules that apply `com.android.library`.
 *
 * Mirrors `AndroidApplicationLintConventionPlugin` for library modules
 * (`:core:*`, `:feature:*`, `:sync:*`, `:benchmarks`). Same three lint
 * tools, same shared config. Kept as a separate plugin (rather than a
 * shared base class) so the Android plugin id each consumer applies is
 * explicit at the call site — the ktlint plugin needs to know whether
 * it's hooking into `com.android.application` or `com.android.library`
 * to register the right variant tasks.
 *
 * Id: `livtet.android.library.lint`
 */
class AndroidLibraryLintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.ncorti.ktfmt.gradle")
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")
            pluginManager.apply("io.gitlab.arturbosch.detekt")

            extensions.configure<KtfmtExtension> {
                kotlinLangStyle()
                srcSetPathExclusionPattern.set(Regex(".*generated.*"))
            }

            extensions.configure<KtlintExtension> {
                ignoreFailures.set(true)
                filter(
                    Action<PatternFilterable> { files ->
                        files.exclude("**/generated/**")
                        files.exclude("**/build/**")
                    },
                )
            }

            extensions.configure<DetektExtension> {
                config = rootProject.files("config/detekt/detekt.yml")
                buildUponDefaultConfig = true
                autoCorrect = false
            }
        }
    }
}
