package com.ireader.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

internal fun Project.configureAndroidApplication(ext: ApplicationExtension) {
    ext.apply {
        compileSdk = version("compileSdk").toInt()

        defaultConfig {
            minSdk = version("minSdk").toInt()
            targetSdk = version("targetSdk").toInt()
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        packaging {
            resources.excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES"
            )
        }

        lint {
            abortOnError = true
            warningsAsErrors = false
            checkReleaseBuilds = true
        }
    }
}

internal fun Project.configureAndroidLibrary(ext: LibraryExtension) {
    ext.apply {
        compileSdk = version("compileSdk").toInt()

        defaultConfig {
            minSdk = version("minSdk").toInt()
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            consumerProguardFiles("consumer-rules.pro")
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        packaging {
            resources.excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES"
            )
        }

        lint {
            abortOnError = true
            warningsAsErrors = false
            checkReleaseBuilds = true
        }
    }
}
