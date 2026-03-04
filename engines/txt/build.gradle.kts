import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id("com.ireader.android.library")
    id("com.ireader.android.hilt")
}

android {
    namespace = "com.ireader.engines.txt"
}

dependencies {
    implementation(project(":core:reader:api"))
    implementation(project(":engines:engine-common"))
    implementation(project(":engines:engine-common-android"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.16")
}

tasks.register<Detekt>("detektTxtHotspots") {
    group = "verification"
    description = "Runs detekt on TXT hotspot files only."
    buildUponDefaultConfig = true
    autoCorrect = false
    jvmTarget = "17"
    setSource(
        files(
            "src/main/kotlin/com/ireader/engines/txt/internal/open/TxtOpener.kt",
            "src/main/kotlin/com/ireader/engines/txt/internal/render/TxtController.kt",
            "src/main/kotlin/com/ireader/engines/txt/internal/provider/TxtSearchProviderPro.kt",
            "src/main/kotlin/com/ireader/engines/txt/internal/softbreak/SoftBreakIndexBuilder.kt",
        ),
    )
    include("**/*.kt")
}
