package com.ireader.buildlogic.plugins

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.ireader.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        plugins.withId("com.android.application") {
            extensions.configure<ApplicationExtension> {
                buildFeatures.compose = true
            }
        }

        plugins.withId("com.android.library") {
            extensions.configure<LibraryExtension> {
                buildFeatures.compose = true
            }
        }

        dependencies {
            add("implementation", platform(library("androidx-compose-bom")))
            add("implementation", library("androidx-compose-ui"))
            add("implementation", library("androidx-compose-ui-graphics"))
            add("implementation", library("androidx-compose-ui-tooling-preview"))
            add("implementation", library("androidx-compose-material3"))
            add("implementation", library("androidx-lifecycle-runtime-compose"))
            add("debugImplementation", library("androidx-compose-ui-tooling"))
        }
    }
}
