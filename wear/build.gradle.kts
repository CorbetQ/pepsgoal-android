plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

/** Application PepsGoal pour montre Wear OS : même identifiant que l'application du téléphone (couche de données Wear OS). */
android {
    namespace = "fr.pepsgoal.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.pepsgoal.app"
        minSdk = 30
        targetSdk = 34
        // Même version que l'application du téléphone
        val phone = rootProject.file("app/build.gradle.kts").readText()
        versionCode = Regex("versionCode = (\\d+)").find(phone)!!.groupValues[1].toInt()
        versionName = Regex("versionName = \"([^\"]+)\"").find(phone)!!.groupValues[1]
    }

    signingConfigs {
        create("release") {
            val ks = System.getenv("PEPSGOAL_KEYSTORE")
            if (ks != null && file(ks).exists()) {
                storeFile = file(ks); storePassword = System.getenv("PEPSGOAL_KEYSTORE_PASSWORD"); keyAlias = System.getenv("PEPSGOAL_KEY_ALIAS"); keyPassword = System.getenv("PEPSGOAL_KEY_PASSWORD")
            } else {
                storeFile = rootProject.file("app/pepsgoal-test.jks"); storePassword = "pepsgoal-test"; keyAlias = "pepsgoal"; keyPassword = "pepsgoal-test"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug { applicationIdSuffix = ".debug" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.wear.compose:compose-material:1.4.1")
    implementation("androidx.wear.compose:compose-foundation:1.4.1")
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
