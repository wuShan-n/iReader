plugins {
    id("com.ireader.android.library")
}

android {
    namespace = "com.ireader.core.files"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
