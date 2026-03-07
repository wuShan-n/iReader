plugins {
    id("com.ireader.android.library")
}

android {
    namespace = "com.ireader.core.reader.api"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
