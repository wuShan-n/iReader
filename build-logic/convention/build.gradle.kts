plugins {
    `kotlin-dsl`
}

group = "com.ireader.buildlogic"

kotlin {
    jvmToolchain(17)
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.hilt.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)

    testImplementation(gradleTestKit())
    testImplementation("junit:junit:4.13.2")
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "com.ireader.android.application"
            implementationClass = "com.ireader.buildlogic.plugins.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "com.ireader.android.library"
            implementationClass = "com.ireader.buildlogic.plugins.AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "com.ireader.android.feature"
            implementationClass = "com.ireader.buildlogic.plugins.AndroidFeatureConventionPlugin"
        }
        register("androidCompose") {
            id = "com.ireader.android.compose"
            implementationClass = "com.ireader.buildlogic.plugins.AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "com.ireader.android.hilt"
            implementationClass = "com.ireader.buildlogic.plugins.AndroidHiltConventionPlugin"
        }
        register("kotlinLibrary") {
            id = "com.ireader.kotlin.library"
            implementationClass = "com.ireader.buildlogic.plugins.KotlinLibraryConventionPlugin"
        }
        register("quality") {
            id = "com.ireader.quality"
            implementationClass = "com.ireader.buildlogic.plugins.QualityConventionPlugin"
        }
        register("dependencyGuard") {
            id = "com.ireader.dependency.guard"
            implementationClass = "com.ireader.buildlogic.plugins.DependencyGuardPlugin"
        }
    }
}

tasks.test {
    useJUnit()
}
