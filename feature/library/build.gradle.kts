plugins {
    id("com.ireader.android.feature")
    id("com.ireader.android.compose")
}

android {
    namespace = "com.ireader.feature.library"
}

dependencies {
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
}
