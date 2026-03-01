package com.ireader.buildlogic.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class KotlinLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("com.ireader.quality")
        pluginManager.apply("com.ireader.dependency.guard")

        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(17)
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
                freeCompilerArgs.add("-jvm-default=no-compatibility")
            }
        }
    }
}
