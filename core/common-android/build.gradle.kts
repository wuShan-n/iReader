plugins {
    id("com.ireader.android.library")
}

android {
    namespace = "com.ireader.core.common.android"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:reader:api"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
}
