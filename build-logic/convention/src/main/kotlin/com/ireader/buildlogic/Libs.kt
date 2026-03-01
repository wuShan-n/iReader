package com.ireader.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.library(alias: String): Provider<MinimalExternalModuleDependency> =
    libs.findLibrary(alias).orElseThrow { IllegalStateException("Missing library alias: $alias") }

internal fun Project.version(alias: String): String =
    libs.findVersion(alias).orElseThrow { IllegalStateException("Missing version alias: $alias") }.requiredVersion

internal fun String.safeNamespaceSegment(): String =
    lowercase()
        .replace("-", "_")
        .replace(".", "_")
        .replace(Regex("[^a-z0-9_]"), "_")
        .trim('_')
