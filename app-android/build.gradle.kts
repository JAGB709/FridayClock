import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firma de release: keystore propio leido de variables de entorno o de local.properties (ignorado
// por el control de versiones). Variables: FRIDAY_KEYSTORE, FRIDAY_KEYSTORE_PASSWORD,
// FRIDAY_KEY_ALIAS, FRIDAY_KEY_PASSWORD. Si faltan, el release cae a la clave debug con un aviso.
// Ver docs/firma-release.md.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(name: String): String? =
    (System.getenv(name) ?: localProps.getProperty(name))?.takeIf { it.isNotBlank() }
val releaseKeystore = signingValue("FRIDAY_KEYSTORE")?.let { rootProject.file(it) }?.takeIf { it.isFile }
val releaseSigningReady = releaseKeystore != null &&
    listOf("FRIDAY_KEYSTORE_PASSWORD", "FRIDAY_KEY_ALIAS").all { signingValue(it) != null }

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

    signingConfigs {
        if (releaseSigningReady) {
            create("friday") {
                storeFile = releaseKeystore
                storePassword = signingValue("FRIDAY_KEYSTORE_PASSWORD")
                keyAlias = signingValue("FRIDAY_KEY_ALIAS")
                keyPassword = signingValue("FRIDAY_KEY_PASSWORD") ?: signingValue("FRIDAY_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseSigningReady) {
                signingConfigs.getByName("friday")
            } else {
                logger.warn("AVISO: sin keystore de release (FRIDAY_KEYSTORE...); el release se firma con la clave DEBUG. Ver docs/firma-release.md")
                signingConfigs.getByName("debug")
            }
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
    // El android.jar de los unit tests trae org.json como stub que lanza excepción;
    // añadimos la implementación real para poder testear la serialización JSON.
    testImplementation("org.json:json:20240303")
}
