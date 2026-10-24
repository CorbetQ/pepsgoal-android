plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
}

android {
    namespace = "fr.pepsgoal.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.pepsgoal.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 630
        versionName = "6.3.0"
        // Adresse du serveur PepsGoal (surchargée par -Ppepsgoal.api=… pour un serveur de test)
        resValue("string", "pkg", "fr.pepsgoal.app")
        buildConfigField("String", "API_URL", "\"${project.findProperty("pepsgoal.api") ?: "https://api.pepsgoal.fr"}\"")
    }

    signingConfigs {
        // Signature de publication : fournie par la CI ou Android Studio (variables d'environnement), sinon clé de test
        // du dépôt (stable d'une version à l'autre : les APK des releases GitHub s'installent par-dessus les précédents)
        create("release") {
            val ks = System.getenv("PEPSGOAL_KEYSTORE")
            if (ks != null && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("PEPSGOAL_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PEPSGOAL_KEY_ALIAS")
                keyPassword = System.getenv("PEPSGOAL_KEY_PASSWORD")
            } else {
                storeFile = file("pepsgoal-test.jks"); storePassword = "pepsgoal-test"; keyAlias = "pepsgoal"; keyPassword = "pepsgoal-test"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "pkg", "fr.pepsgoal.app.debug")
            versionNameSuffix = "-debug"
        }
    }

    // Deux distributions : « github » (APK des releases, mise à jour intégrée) et « play » (Play Store : mises à jour
    // et avis par Google Play, permissions conformes à ses règles)
    flavorDimensions += "store"
    productFlavors {
        create("github") { dimension = "store"; buildConfigField("boolean", "PLAY", "false") }
        create("play") { dimension = "store"; buildConfigField("boolean", "PLAY", "true") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Manifestes OSGi en double (JSch et jspecify), inutiles sur Android
        resources.excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-process:2.9.1")
    implementation("androidx.work:work-runtime-ktx:2.10.2")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    // Verre givré : flou de l'arrière-plan derrière les cartes et les barres (backdrop-filter de la version web)
    implementation("dev.chrisbanes.haze:haze:1.6.10")
    // Assistant IA 100 % local : exécution d'un petit modèle de langage sur le téléphone (MediaPipe LLM Inference)
    implementation("com.google.mediapipe:tasks-genai:0.10.24")
    // Health Connect (Santé d'Android) : validation automatique à partir des pas, du sommeil, du sport, de l'eau
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")
    // Rappels par lieu (géorepérage)
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // Sauvegarde automatique dans un dossier choisi (stockage du téléphone, Google Drive…)
    implementation("androidx.documentfile:documentfile:1.1.0")
    // Montre Wear OS : couche de données (journée publiée, validations reçues)
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    // Les services Google tirent une ancienne version de Fragment : version récente imposée (API ActivityResult)
    implementation("androidx.fragment:fragment-ktx:1.8.8")
    // Carte intégrée (OpenStreetMap) pour enregistrer des emplacements, sans clé d’API
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    // Play Store seulement : mises à jour et avis sans quitter l'application
    // Codes QR des objectifs partagés : génération (ZXing) et lecture (scanner Google, sans permission caméra)
    implementation("com.google.zxing:core:3.5.3")
    // Modération › Serveur : mise à jour du serveur en SSH
    implementation("com.github.mwiede:jsch:0.2.20")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    "playImplementation"("com.google.android.play:app-update-ktx:2.1.0")
    "playImplementation"("com.google.android.play:review-ktx:2.0.2")
}
