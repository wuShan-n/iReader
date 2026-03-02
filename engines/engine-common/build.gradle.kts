plugins {
    id("com.ireader.kotlin.library")
}

dependencies {
    implementation(project(":core:model"))
    testImplementation(libs.junit)
}
