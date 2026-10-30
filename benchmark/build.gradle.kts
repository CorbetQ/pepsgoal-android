plugins {
    id("com.android.test")
    kotlin("android")
}

/**
 * Mesures de performance (Macrobenchmark) : démarrage à froid, défilement d'Aujourd'hui (fond Partout puis Nulle
 * part), validations, cap « appui = +1 », ouverture de la fiche, Statistiques et conversation. Cible la variante
 * « benchmark » de l'application (publication signée avec la clé de débogage, profilable).
 */
android {
    namespace = "fr.pepsgoal.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // L'application a deux distributions : on mesure celle des releases GitHub
        missingDimensionStrategy("store", "github")
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test:runner:1.6.2")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.4")
}

// Seule la variante « benchmark » a un sens (la cible doit être profilable et non débogable)
androidComponents { beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" } }
