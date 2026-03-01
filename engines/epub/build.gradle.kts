plugins {
    id("com.ireader.android.library")
}

android {
    namespace = "com.ireader.engines.epub"
}

dependencies {
    implementation(project(":core:reader:api"))
    implementation(project(":engines:engine-common"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
