plugins {
    id("com.ireader.android.library")
    id("com.ireader.android.hilt")
}

android {
    namespace = "com.ireader.core.files"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:model"))
    implementation(project(":core:reader:api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.documentfile)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation(project(":core:testing"))
}
