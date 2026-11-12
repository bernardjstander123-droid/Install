plugins { id("com.android.application") }

android {
    namespace = "com.astra.sevenone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.astra.sevenone"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.3-model-diagnostics"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Bundled, on-device vision models. No camera frames sent to cloud APIs.
dependencies {
  implementation("com.google.mlkit:face-detection:16.1.7")
  implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
}
