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
    implementation(project(":core:files"))
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
