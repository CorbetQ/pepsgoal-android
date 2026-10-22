pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.11.1"
        kotlin("android") version "2.2.0"
        kotlin("jvm") version "2.2.0"
        kotlin("plugin.serialization") version "2.2.0"
        kotlin("plugin.compose") version "2.2.0"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "PepsGoal"

// Logique métier, chiffrement, synchronisation et client de l'API : Kotlin pur, testable sans SDK Android
include(":core")
// Application Android : incluse dès qu'un SDK Android est disponible (Android Studio, CI)
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null || file("local.properties").exists()
if (hasAndroidSdk) { include(":app"); include(":wear") }
