package com.ireader.buildlogic.plugins

import java.nio.file.Files
import java.nio.file.Path
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityConventionPluginTest {

    @Test
    fun `explicit suffix should isolate test output directories`() {
        val projectDir = createTempProjectDir()
        writeSettings(projectDir)
        writeBuild(projectDir)
        writeSampleTest(projectDir)

        val result = runGradle(
            projectDir,
            "test",
            "-Pireader.testOutputSuffix=facade"
        )

        assertTrue(result.output.contains("test-results/test-facade/binary"))
        assertTrue(result.output.contains("test-results/test-facade"))
        assertTrue(result.output.contains("reports/tests/test-facade"))
    }

    @Test
    fun `default test output directories should stay unchanged`() {
        val projectDir = createTempProjectDir()
        writeSettings(projectDir)
        writeBuild(projectDir)
        writeSampleTest(projectDir)

        val result = runGradle(
            projectDir,
            "test"
        )

        assertTrue(result.output.contains("test-results/test/binary"))
        assertTrue(result.output.contains("test-results/test"))
        assertTrue(result.output.contains("reports/tests/test"))
    }

    private fun runGradle(projectDir: Path, vararg args: String) =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments(*args, "--stacktrace")
            .withPluginClasspath()
            .build()

    private fun createTempProjectDir(): Path =
        Files.createTempDirectory("quality-convention-test-")

    private fun writeSettings(projectDir: Path) {
        writeFile(projectDir, "settings.gradle.kts", "rootProject.name = \"quality-convention-test\"")
        writeFile(projectDir, "build.gradle.kts", "")
    }

    private fun writeBuild(projectDir: Path) {
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            import org.gradle.api.tasks.testing.Test

            plugins {
                java
                id("com.ireader.quality")
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                testImplementation("junit:junit:4.13.2")
            }

            tasks.named<Test>("test") {
                useJUnit()
                doFirst {
                    println("binary=" + binaryResultsDirectory.get().asFile.invariantSeparatorsPath)
                    println("xml=" + reports.junitXml.outputLocation.get().asFile.invariantSeparatorsPath)
                    println("html=" + reports.html.outputLocation.get().asFile.invariantSeparatorsPath)
                }
            }
            """
        )
    }

    private fun writeSampleTest(projectDir: Path) {
        writeFile(
            projectDir,
            "src/test/java/SampleTest.java",
            """
            import org.junit.Test;

            public class SampleTest {
                @Test
                public void works() {
                }
            }
            """
        )
    }

    private fun writeFile(projectDir: Path, relativePath: String, content: String) {
        val file = projectDir.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content.trimIndent() + "\n")
    }
}
