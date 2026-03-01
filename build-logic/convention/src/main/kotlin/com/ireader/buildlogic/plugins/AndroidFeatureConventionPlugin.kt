package com.ireader.buildlogic.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.ireader.android.library")
    }
}
