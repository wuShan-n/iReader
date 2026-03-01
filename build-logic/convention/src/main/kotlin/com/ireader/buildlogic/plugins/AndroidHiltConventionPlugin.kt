package com.ireader.buildlogic.plugins

import com.ireader.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.dagger.hilt.android")
        pluginManager.apply("com.google.devtools.ksp")

        dependencies {
            add("implementation", library("hilt-android"))
            add("ksp", library("hilt-compiler"))
        }
    }
}
