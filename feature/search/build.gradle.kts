plugins {
    id("com.ireader.android.feature")
    id("com.ireader.android.compose")
}

android {
    namespace = "com.ireader.feature.search"
}

dependencies {
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
}
