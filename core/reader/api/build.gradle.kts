plugins {
    id("com.ireader.android.library")
}

android {
    namespace = "com.ireader.core.reader.api"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:files"))
    api(libs.androidx.fragment.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
