plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "id.hyperdesk.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.hyperdesk.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }
}
