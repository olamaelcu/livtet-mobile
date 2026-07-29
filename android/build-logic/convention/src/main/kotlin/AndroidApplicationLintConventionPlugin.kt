import com.ncorti.ktfmt.gradle.KtfmtExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/**
 * Lint convention plugin for modules that apply `com.android.application`.
 *
 * Wires up three complementary lint tools:
 *  - **ktfmt** (Facebook): deterministic formatter; KotlinLang style
 *    keeps the existing 4-space indent (`kotlin.code.style=official`).
 *  - **ktlint** (JLLeitschuh): rule-based linting (import order, naming,
 *    experimental rules); cross-checks what ktfmt doesn't cover.
 *  - **detekt** (Artur Bosch): static analysis (complexity, code smells,
 *    exceptions); uses the shared config at `config/detekt/detekt.yml`.
 *
 * The Android plugin must be applied *before* this convention plugin —
 * ktlint hooks into the Android variant API, which only exists once
 * `com.android.application` has been applied to the project.
 *
 * Id: `livtet.android.application.lint`
 */
class AndroidApplicationLintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.ncorti.ktfmt.gradle")
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")
            pluginManager.apply("io.gitlab.arturbosch.detekt")

            // ktfmt — Facebook's deterministic formatter. KotlinLang
            // style matches the existing 4-space indent declared in
            // `gradle.properties` (`kotlin.code.style=official`).
            extensions.configure<KtfmtExtension> {
                kotlinLangStyle()
                // Skip UniFFI-generated sources under
                // mobile/android/build/generated/source/uniffi/kotlin/.
                srcSetPathExclusionPattern.set(Regex(".*generated.*"))
            }

            // ktlint — rule-based linting. The `filter` extension
            // takes a Java `Action<PatternFilterable>`; passing the
            // SAM type explicitly avoids Kotlin's lambda receiver
            // type inference falling back to `Any`.
            extensions.configure<KtlintExtension> {
                filter(
                    Action<PatternFilterable> { files ->
                        files.exclude("**/generated/**")
                        files.exclude("**/build/**")
                    },
                )
            }

            // detekt — static analysis. Detekt 1.23.8 uses Java Bean
            // accessors (no `Property<T>` wrappers), so configuration
            // is plain Kotlin property assignment.
            extensions.configure<DetektExtension> {
                config = rootProject.files("config/detekt/detekt.yml")
                buildUponDefaultConfig = true
                // autoCorrect defaults to false; the `--fix` flag in
                // `mise run android-lint` passes `--auto-correct` on
                // the gradle CLI, which toggles per-run without
                // rewriting this config.
                autoCorrect = false
            }
        }
    }
}
