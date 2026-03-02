plugins {
    id("com.ireader.android.library")
}

android {
    namespace = "com.ireader.engines.epub"
}

dependencies {
    implementation(project(":core:reader:api"))
    implementation(project(":engines:engine-common"))
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)
    testImplementation(libs.junit)
}
