plugins {
    id("com.ireader.android.library")
}

android {
    namespace = "com.ireader.core.common.android"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.core.ktx)
}
