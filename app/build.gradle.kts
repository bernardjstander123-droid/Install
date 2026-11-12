plugins { id("com.android.application") }

android {
    namespace = "com.astra.sevenone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.astra.sevenone"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1-avatar"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
