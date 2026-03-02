plugins {
    id("com.ireader.android.library")
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
