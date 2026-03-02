plugins {
    id("com.ireader.android.library")
    id("com.ireader.android.hilt")
}

android {
    namespace = "com.ireader.core.data"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:database"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.room.runtime)
}
