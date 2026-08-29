import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Signature de release : keystore.properties (hors dépôt) à la racine du projet.
// Le keystore est l'objet le plus irréversible du projet (docs/PROJET.md §14) :
// à sauvegarder hors du dépôt ET hors de la machine. Sans lui, le build release
// est signé en debug — installable pour tester, mais à ne jamais distribuer.
val keystoreProperties: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.isFile }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

android {
    namespace = "com.monselio.fidcards"
    // Le BOM Compose 2026.08.00 (Compose 1.12) exige une compilation contre l'API 37.
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.monselio.fidcards"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (keystoreProperties != null) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("keystore.properties absent : release signée en DEBUG, ne pas distribuer.")
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    // camera-view tire camera-video -> media3-common, dont le manifeste injecte
    // ACCESS_NETWORK_STATE dans l'APK fusionné : une permission réseau visible
    // sur la fiche Play Store, dans une app qui n'a pas le droit d'en avoir
    // (CLAUDE.md). Seul PreviewView est utilisé ici, jamais l'enregistrement
    // vidéo : l'exclusion retire aussi media3-muxer et Guava au passage.
    implementation(libs.androidx.camera.view) {
        exclude(group = "androidx.camera", module = "camera-video")
    }

    implementation(libs.zxing.cpp.android)
    // Génération des codes : zxing-cpp 3.1.1 n'expose aucune API d'écriture sur Android
    // (vérifié dans l'AAR : seul BarcodeReader est présent). zxing core est du Java pur,
    // sans Play Services, et rend le round-trip testable en JVM (docs/PROJET.md §14).
    implementation(libs.zxing.core)

    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.room.testing)
}
