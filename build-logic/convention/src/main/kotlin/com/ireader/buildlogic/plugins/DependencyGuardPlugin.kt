package com.ireader.buildlogic.plugins

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

class DependencyGuardPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        afterEvaluate {
            val path = project.path

            val forbiddenPrefixes: List<String> = when {
                path.startsWith(":feature:") -> listOf(":engines:")
                path.startsWith(":core:") -> listOf(":feature:")
                else -> emptyList()
            }
            if (forbiddenPrefixes.isEmpty()) return@afterEvaluate

            val checkConfigurations = listOf(
                "api",
                "implementation",
                "compileOnly",
                "runtimeOnly",
                "testImplementation",
                "androidTestImplementation",
                "ksp",
                "kapt"
            )

            val violations = mutableListOf<String>()
            checkConfigurations.forEach { confName ->
                val conf = configurations.findByName(confName) ?: return@forEach
                conf.dependencies.withType(ProjectDependency::class.java).forEach { dep ->
                    val depPath = dep.path
                    if (forbiddenPrefixes.any { depPath.startsWith(it) }) {
                        violations += "$confName -> $depPath"
                    }
                }
            }

            if (violations.isNotEmpty()) {
                throw GradleException(
                    buildString {
                        appendLine("Dependency rule violated in $path")
                        appendLine("Forbidden prefixes: $forbiddenPrefixes")
                        appendLine("Violations:")
                        violations.forEach { appendLine("  - $it") }
                        appendLine("Rules:")
                        appendLine("  - feature/* cannot depend on engines/*")
                        appendLine("  - core/* cannot depend on feature/*")
                    }
                )
            }
        }
    }
}
