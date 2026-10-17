plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.epubreader.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.epubreader.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.readium.kotlin-toolkit:readium-shared:3.1.0")
    implementation("org.readium.kotlin-toolkit:readium-streamer:3.1.0")
    implementation("org.readium.kotlin-toolkit:readium-navigator:3.1.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
