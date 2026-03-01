plugins {
    id("com.ireader.android.application")
    id("com.ireader.android.compose")
    id("com.ireader.android.hilt")
}

android {
    namespace = "com.ireader"

    defaultConfig {
        applicationId = "com.ireader"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(project(":core:navigation"))
    implementation(project(":core:reader:runtime"))
    implementation(project(":core:designsystem"))

    implementation(project(":feature:library"))
    implementation(project(":feature:reader"))
    implementation(project(":feature:annotations"))
    implementation(project(":feature:search"))
    implementation(project(":feature:settings"))

    implementation(project(":engines:txt"))
    implementation(project(":engines:epub"))
    implementation(project(":engines:pdf"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
