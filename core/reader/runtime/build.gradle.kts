plugins {
    id("com.ireader.android.library")
}

android {
    namespace = "com.ireader.core.reader.runtime"
}

dependencies {
    api(project(":core:reader:api"))
    implementation(project(":core:model"))
    implementation(project(":core:files"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
