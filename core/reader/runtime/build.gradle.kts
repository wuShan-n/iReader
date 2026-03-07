plugins {
    id("com.ireader.android.library")
}

android {
    namespace = "com.ireader.core.reader.runtime"
}

dependencies {
    api(project(":core:reader:api"))
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.robolectric:robolectric:4.16")
}
