package com.ireader.buildlogic.plugins

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

class DependencyGuardPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        afterEvaluate {
            val ownerPath = project.path
            val rule = ruleFor(ownerPath) ?: return@afterEvaluate

            val violations = mutableListOf<String>()
            configurations
                .sortedBy { it.name }
                .forEach { conf ->
                    conf.dependencies.withType(ProjectDependency::class.java).forEach { dep ->
                        val depPath = dep.path
                        if (!rule.isAllowed(depPath)) {
                            violations += "${conf.name} -> $depPath"
                        }
                    }
                }

            if (violations.isEmpty()) {
                return@afterEvaluate
            }

            throw GradleException(
                buildString {
                    appendLine("Dependency rule violated in $ownerPath")
                    appendLine("Layer: ${rule.layerName}")
                    appendLine("Allowed dependency targets: ${rule.allowedTargetsDescription}")
                    appendLine("Violations:")
                    violations.forEach { appendLine("  - $it") }
                    appendLine("Resolution:")
                    appendLine("  - Move shared code to an allowed core module.")
                    appendLine("  - Keep cross-layer integration tests in feature/app modules.")
                }
            )
        }
    }

    private fun ruleFor(ownerPath: String): Rule? {
        return when {
            ownerPath == APP_PATH -> null
            ownerPath.startsWith(FEATURE_PREFIX) -> Rule(
                layerName = "feature",
                allowedTargetsDescription = "feature/*, core/*",
                isAllowed = { depPath ->
                    depPath.startsWith(FEATURE_PREFIX) || depPath.startsWith(CORE_PREFIX)
                }
            )
            ownerPath.startsWith(CORE_PREFIX) -> Rule(
                layerName = "core",
                allowedTargetsDescription = "core/*",
                isAllowed = { depPath ->
                    depPath.startsWith(CORE_PREFIX)
                }
            )
            ownerPath.startsWith(ENGINES_PREFIX) -> Rule(
                layerName = "engines",
                allowedTargetsDescription = "engines/*, core:reader:api, core:common, core:common-android, core:model, core:files",
                isAllowed = { depPath ->
                    depPath.startsWith(ENGINES_PREFIX) || depPath in ENGINE_ALLOWED_CORE_TARGETS
                }
            )
            else -> null
        }
    }

    private data class Rule(
        val layerName: String,
        val allowedTargetsDescription: String,
        val isAllowed: (String) -> Boolean
    )

    companion object {
        private const val APP_PATH = ":app"
        private const val FEATURE_PREFIX = ":feature:"
        private const val CORE_PREFIX = ":core:"
        private const val ENGINES_PREFIX = ":engines:"

        private val ENGINE_ALLOWED_CORE_TARGETS = setOf(
            ":core:reader:api",
            ":core:common",
            ":core:common-android",
            ":core:model",
            ":core:files"
        )
    }
}
