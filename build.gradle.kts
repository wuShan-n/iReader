import org.gradle.testing.jacoco.tasks.JacocoReport

// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        classpath(libs.android.gradle.plugin)
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.ksp.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    jacoco
}

tasks.register<JacocoReport>("coverageLocalMerged") {
    group = "verification"
    description = "Generates a merged Jacoco report for app/core:data/core:database/core:work."
    dependsOn(
        ":app:jacocoUnitTestReport",
        ":core:data:jacocoUnitTestReport",
        ":core:database:jacocoUnitTestReport",
        ":core:work:jacocoUnitTestReport"
    )

    val modulePaths = listOf("app", "core/data", "core/database", "core/work")
    val excludes = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*"
    )

    classDirectories.setFrom(
        files(
            modulePaths.flatMap { module ->
                listOf(
                    fileTree("$module/build/tmp/kotlin-classes/debug") { exclude(excludes) },
                    fileTree("$module/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes") { exclude(excludes) },
                    fileTree("$module/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes") { exclude(excludes) }
                )
            }
        )
    )
    sourceDirectories.setFrom(
        files(
            modulePaths.flatMap { module ->
                listOf("$module/src/main/java", "$module/src/main/kotlin")
            }
        )
    )
    executionData.setFrom(
        files(
            modulePaths.flatMap { module ->
                listOf(
                    "$module/build/jacoco/testDebugUnitTest.exec",
                    "$module/build/outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"
                )
            }
        )
    )

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/coverageLocal/html"))
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/coverageLocal/coverageLocal.xml"))
    }
}

tasks.register("coverageLocal") {
    group = "verification"
    description = "Runs key module unit tests and generates merged local coverage report."
    dependsOn("coverageLocalMerged")
}
