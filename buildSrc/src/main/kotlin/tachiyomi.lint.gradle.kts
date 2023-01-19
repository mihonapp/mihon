import org.jmailen.gradle.kotlinter.KotlinterExtension
import org.jmailen.gradle.kotlinter.KotlinterPlugin

apply<KotlinterPlugin>()

extensions.configure<KotlinterExtension>("kotlinter") {
    experimentalRules = true

    disabledRules = arrayOf(
        "experimental:argument-list-wrapping", // Doesn't play well with Android Studio
        "filename", // Often broken to give a more general name
    )
}

tasks {
    named<DefaultTask>("preBuild").configure {
        if (!System.getenv("CI").toBoolean())
            dependsOn("formatKotlin")
    }
}
