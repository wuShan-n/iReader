package com.ireader.buildlogic.plugins

import com.android.build.api.dsl.ApplicationExtension
import com.ireader.buildlogic.configureAndroidApplication
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("com.ireader.quality")

        extensions.configure<ApplicationExtension> {
            configureAndroidApplication(this)
        }
    }
}
