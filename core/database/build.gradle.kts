plugins {
    id("com.ireader.android.library")
}

android {
    namespace = "com.ireader.core.database"
}

dependencies {
    implementation(project(":core:model"))
}
