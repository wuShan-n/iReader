plugins {
    id("com.ireader.android.library")
}

android {
    namespace = "com.ireader.core.reader.api"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:files"))
    implementation(libs.kotlinx.coroutines.core)
}
