plugins {
    id("com.ireader.android.feature")
    id("com.ireader.android.compose")
    id("com.ireader.android.hilt")
}

android {
    namespace = "com.ireader.feature.reader"
}

dependencies {
    implementation(project(":core:common-android"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:files"))
    implementation(project(":core:reader:runtime"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
