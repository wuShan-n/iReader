package com.ireader.buildlogic.plugins

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.assertTrue
import org.junit.Test

class DependencyGuardPluginTest {

    @Test
    fun `feature test dependency on engines should fail`() {
        val projectDir = createTempProjectDir()
        writeSettings(
            projectDir,
            ":feature:sample",
            ":engines:txt"
        )
        writeBuild(projectDir, ":engines:txt")
        writeBuild(
            projectDir,
            ":feature:sample",
            """
            plugins {
                id("com.ireader.dependency.guard")
            }

            configurations.create("testImplementation")

            dependencies {
                add("testImplementation", project(":engines:txt"))
            }
            """
        )

        val result = runGradleAndFail(projectDir, ":feature:sample:help")
        assertTrue(result.output.contains("testImplementation -> :engines:txt"))
    }

    @Test
    fun `engines dependencies to allowed targets should pass`() {
        val projectDir = createTempProjectDir()
        writeSettings(
            projectDir,
            ":engines:txt",
            ":engines:engine-common",
            ":core:reader:api"
        )
        writeBuild(projectDir, ":engines:engine-common")
        writeBuild(projectDir, ":core:reader:api")
        writeBuild(
            projectDir,
            ":engines:txt",
            """
            plugins {
                id("com.ireader.dependency.guard")
            }

            configurations.create("implementation")

            dependencies {
                add("implementation", project(":engines:engine-common"))
                add("implementation", project(":core:reader:api"))
            }
            """
        )

        val result = runGradle(projectDir, ":engines:txt:help")
        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `core debug dependency on feature should fail`() {
        val projectDir = createTempProjectDir()
        writeSettings(
            projectDir,
            ":core:data",
            ":feature:reader"
        )
        writeBuild(projectDir, ":feature:reader")
        writeBuild(
            projectDir,
            ":core:data",
            """
            plugins {
                id("com.ireader.dependency.guard")
            }

            configurations.create("debugImplementation")

            dependencies {
                add("debugImplementation", project(":feature:reader"))
            }
            """
        )

        val result = runGradleAndFail(projectDir, ":core:data:help")
        assertTrue(result.output.contains("debugImplementation -> :feature:reader"))
    }

    @Test
    fun `app may depend on core feature and engines`() {
        val projectDir = createTempProjectDir()
        writeSettings(
            projectDir,
            ":app",
            ":core:model",
            ":feature:reader",
            ":engines:txt"
        )
        writeBuild(projectDir, ":core:model")
        writeBuild(projectDir, ":feature:reader")
        writeBuild(projectDir, ":engines:txt")
        writeBuild(
            projectDir,
            ":app",
            """
            plugins {
                id("com.ireader.dependency.guard")
            }

            configurations.create("implementation")

            dependencies {
                add("implementation", project(":core:model"))
                add("implementation", project(":feature:reader"))
                add("implementation", project(":engines:txt"))
            }
            """
        )

        val result = runGradle(projectDir, ":app:help")
        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `engines test dependency on core runtime should fail`() {
        val projectDir = createTempProjectDir()
        writeSettings(
            projectDir,
            ":engines:pdf",
            ":core:reader:runtime"
        )
        writeBuild(projectDir, ":core:reader:runtime")
        writeBuild(
            projectDir,
            ":engines:pdf",
            """
            plugins {
                id("com.ireader.dependency.guard")
            }

            configurations.create("testImplementation")

            dependencies {
                add("testImplementation", project(":core:reader:runtime"))
            }
            """
        )

        val result = runGradleAndFail(projectDir, ":engines:pdf:help")
        assertTrue(result.output.contains("testImplementation -> :core:reader:runtime"))
    }

    @Test
    fun `engines test dependency on core reader testkit should pass`() {
        val projectDir = createTempProjectDir()
        writeSettings(
            projectDir,
            ":engines:pdf",
            ":core:reader:testkit"
        )
        writeBuild(projectDir, ":core:reader:testkit")
        writeBuild(
            projectDir,
            ":engines:pdf",
            """
            plugins {
                id("com.ireader.dependency.guard")
            }

            configurations.create("testImplementation")

            dependencies {
                add("testImplementation", project(":core:reader:testkit"))
            }
            """
        )

        val result = runGradle(projectDir, ":engines:pdf:help")
        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    }

    private fun runGradle(projectDir: Path, vararg args: String) =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments(*args, "--stacktrace")
            .withPluginClasspath()
            .build()

    private fun runGradleAndFail(projectDir: Path, vararg args: String) =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments(*args, "--stacktrace")
            .withPluginClasspath()
            .buildAndFail()

    private fun createTempProjectDir(): Path =
        Files.createTempDirectory("dependency-guard-test-")

    private fun writeSettings(projectDir: Path, vararg projectPaths: String) {
        val settings = buildString {
            appendLine("rootProject.name = \"dependency-guard-test\"")
            projectPaths.forEach { appendLine("include(\"$it\")") }
        }
        writeFile(projectDir, "settings.gradle.kts", settings)
        writeFile(projectDir, "build.gradle.kts", "")
    }

    private fun writeBuild(projectDir: Path, projectPath: String, buildScript: String = "") {
        val moduleDir = projectPath.removePrefix(":").replace(':', File.separatorChar)
        writeFile(projectDir, "$moduleDir/build.gradle.kts", buildScript.trimIndent())
    }

    private fun writeFile(projectDir: Path, relativePath: String, content: String) {
        val file = projectDir.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content + "\n")
    }
}
