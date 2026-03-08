plugins {
    id("com.ireader.android.library")
}

android {
    namespace = "com.ireader.core.reader.testkit"
}

dependencies {
    api(project(":core:reader:api"))
    api(project(":core:reader:runtime"))
    implementation(project(":core:model"))
    implementation(project(":core:testing"))

    implementation(libs.junit)
    implementation(libs.kotlinx.coroutines.test)
}
