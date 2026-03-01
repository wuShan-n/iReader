package com.ireader.buildlogic.plugins

import com.android.build.api.dsl.LibraryExtension
import com.ireader.buildlogic.configureAndroidLibrary
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("com.ireader.quality")
        pluginManager.apply("com.ireader.dependency.guard")

        extensions.configure<LibraryExtension> {
            configureAndroidLibrary(this)
        }
    }
}
