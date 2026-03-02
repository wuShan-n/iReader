plugins {
    id("com.ireader.android.feature")
    id("com.ireader.android.compose")
    id("com.ireader.android.hilt")
}

android {
    namespace = "com.ireader.feature.library"
}

dependencies {
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:files"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.core)
}
