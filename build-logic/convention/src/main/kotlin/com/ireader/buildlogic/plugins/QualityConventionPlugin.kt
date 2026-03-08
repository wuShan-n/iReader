package com.ireader.buildlogic.plugins

import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.gitlab.arturbosch.detekt")
        pluginManager.apply("org.jlleitschuh.gradle.ktlint")

        tasks.withType<Detekt>().configureEach {
            buildUponDefaultConfig = true
            autoCorrect = false
            jvmTarget = "17"
        }

        tasks.withType<Test>().configureEach {
            val outputSuffix = providers.gradleProperty(TEST_OUTPUT_SUFFIX_PROPERTY)
                .orElse("")

            binaryResultsDirectory.set(
                layout.buildDirectory.dir(
                    outputSuffix.map { suffix ->
                        if (suffix.isBlank()) {
                            "test-results/$name/binary"
                        } else {
                            "test-results/$name-$suffix/binary"
                        }
                    }
                )
            )
            reports.junitXml.outputLocation.set(
                layout.buildDirectory.dir(
                    outputSuffix.map { suffix ->
                        if (suffix.isBlank()) {
                            "test-results/$name"
                        } else {
                            "test-results/$name-$suffix"
                        }
                    }
                )
            )
            reports.html.outputLocation.set(
                layout.buildDirectory.dir(
                    outputSuffix.map { suffix ->
                        if (suffix.isBlank()) {
                            "reports/tests/$name"
                        } else {
                            "reports/tests/$name-$suffix"
                        }
                    }
                )
            )
        }
    }

    private companion object {
        private const val TEST_OUTPUT_SUFFIX_PROPERTY = "ireader.testOutputSuffix"
    }
}
