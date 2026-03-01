plugins {
    id("com.ireader.android.feature")
    id("com.ireader.android.compose")
}

android {
    namespace = "com.ireader.feature.annotations"
}

dependencies {
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
}
