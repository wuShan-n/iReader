plugins {
    id("com.ireader.android.library")
    id("com.ireader.android.hilt")
}

android {
    namespace = "com.ireader.engines.pdf"
}

dependencies {
    implementation(project(":core:reader:api"))
    implementation(project(":engines:engine-common"))
    implementation(project(":engines:engine-common-android"))
    implementation(libs.kotlinx.coroutines.core)
    implementation("io.legere:pdfiumandroid:2.0.0")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":feature:reader"))
    testImplementation(project(":core:reader:runtime"))
    testImplementation(project(":core:datastore"))
    testImplementation("org.robolectric:robolectric:4.16")
}
