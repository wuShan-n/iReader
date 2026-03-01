plugins {
    id("com.ireader.android.library")
}

android {
    namespace = "com.ireader.engines.txt"
}

dependencies {
    implementation(project(":core:reader:api"))
    implementation(project(":engines:engine-common"))
}
