plugins {
    id("com.ireader.android.library")
}

android {
    namespace = "com.ireader.engines.pdf"
}

dependencies {
    implementation(project(":core:reader:api"))
    implementation(project(":engines:engine-common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation("io.legere:pdfiumandroid:2.0.0")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
