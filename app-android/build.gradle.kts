plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jarvis.clock"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jarvis.clock"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Firma con la clave debug para poder instalar el release sin keystore propio.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Sin dependencias de runtime a propósito: nada de AppCompat/Compose/OkHttp.
// Solo el framework de Android + la stdlib de Kotlin, para que la app ocupe poco
// en los 1 GB de RAM del MT8167. JUnit es solo para tests, no entra en el APK.
dependencies {
    testImplementation("junit:junit:4.13.2")
}
