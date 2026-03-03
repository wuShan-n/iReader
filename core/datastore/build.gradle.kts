plugins {
    id("com.ireader.android.library")
    id("com.ireader.android.hilt")
}

android {
    namespace = "com.ireader.core.datastore"
}

dependencies {
    api(project(":core:reader:api"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
