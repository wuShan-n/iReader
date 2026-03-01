package com.ireader.buildlogic.plugins

import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.Plugin
import org.gradle.api.Project
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
    }
}
