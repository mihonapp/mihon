import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.KtlintPlugin

apply<KtlintPlugin>()

extensions.configure<KtlintExtension>("ktlint") {
    version.set("0.50.0")
    android.set(true)
    enableExperimentalRules.set(true)

    filter {
        exclude("**/generated/**")

        // For some reason this is needed for Kotlin MPP
        exclude { tree ->
            val path = tree.file.path
            listOf("/generated/").any {
                path.contains(it)
            }
        }
    }
}
