plugins {
    id("com.android.test")
    kotlin("android")
    id("androidx.baselineprofile")
}

/**
 * Génération du profil de référence (Baseline Profile) : parcours démarrage, Aujourd'hui, fiche d'un objectif et
 * Statistiques sur un appareil géré (`pixel6Api34`, image aosp-atd). Lancement : ./gradlew :app:generateBaselineProfile
 */
android {
    namespace = "fr.pepsgoal.baselineprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        missingDimensionStrategy("store", "github")
    }

    targetProjectPath = ":app"

    testOptions.managedDevices.localDevices {
        create("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "aosp-atd"
        }
    }

    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

baselineProfile {
    managedDevices += "pixel6Api34"
    useConnectedDevices = false
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test:runner:1.6.2")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.4")
}
