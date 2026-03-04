plugins {
    id("com.ireader.android.library")
}

android {
    namespace = "com.ireader.engines.common.android"
}

dependencies {
    implementation(project(":core:reader:api"))
    implementation(project(":core:common-android"))
    implementation(project(":engines:engine-common"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
