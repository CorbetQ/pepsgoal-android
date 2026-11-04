plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    // Profil de référence (Baseline Profile) généré par le module :baselineprofile
    id("androidx.baselineprofile")
}

android {
    namespace = "fr.pepsgoal.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.pepsgoal.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 901
        versionName = "9.0.1"
        // Adresse du serveur PepsGoal (surchargée par -Ppepsgoal.api=… pour un serveur de test)
        resValue("string", "pkg", "fr.pepsgoal.app")
        buildConfigField("boolean", "BENCH", "false")
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
        // Mesures de performance (module :benchmark) : comme la publication, signée avec la clé de débogage, profilable
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            buildConfigField("boolean", "BENCH", "true")
            proguardFiles("benchmark-rules.pro")
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

// Profil de référence : fusionné dans src/main (baseline-prof.txt versionné), généré sur l'appareil géré du module :baselineprofile
baselineProfile {
    mergeIntoMain = true
    saveInSrc = true
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    // Installation du profil de référence au premier lancement (démarrage et défilement plus fluides)
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    baselineProfile(project(":baselineprofile"))
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

// Background authoring lives entirely in assets/backgrounds. Layer sources are compiled, never evaluated at runtime.
val backgroundAssetsDirectory = layout.projectDirectory.dir("src/main/assets/backgrounds")
val generatedBackgroundDirectory = layout.buildDirectory.dir("generated/backgrounds/kotlin")
val generateBackgroundAssets by tasks.registering {
    group = "build"
    description = "Validate background assets and compile their vector layers and animation properties"
    inputs.dir(backgroundAssetsDirectory)
    outputs.dir(generatedBackgroundDirectory)
    doLast {
        val root = backgroundAssetsDirectory.asFile
        val parser = groovy.json.JsonSlurper()
        fun json(file: java.io.File) = parser.parse(file) as Map<*, *>
        val catalog = json(root.resolve("catalog.json"))
        require(catalog["formatVersion"] == 1) { "Unsupported background catalog format" }
        val ids = (catalog["backgrounds"] as List<*>).map { it as String }
        require(ids.size == ids.toSet().size) { "Duplicate background identifiers" }
        val source = StringBuilder("// GENERATED from assets/backgrounds; edit those files instead.\npackage fr.pepsgoal.app.ui\n\n")
        source.append(root.resolve("_shared/imports.txt").readText()).append("\n")
        val artworkIds = ids.filter { root.resolve("$it/art/artwork.png").isFile }
        source.append("private val BACKGROUND_ARTWORK_IDS: Set<String> = setOf(")
            .append(artworkIds.joinToString(", ") { "\"$it\"" }).append(")\n")
        source.append("internal fun hasSceneArtwork(key: String): Boolean = key.removePrefix(\"s:\") in BACKGROUND_ARTWORK_IDS\n")
        source.append("private val BACKGROUND_STYLES: Map<String, BackgroundStyle> = mapOf(\n")
        for (id in ids) {
            require(id.matches(Regex("[a-z][a-z0-9-]*"))) { "Invalid background identifier: $id" }
            val folder = root.resolve(id)
            val props = json(folder.resolve("properties.json"))
            val animation = json(folder.resolve("animations.json"))
            require(props["id"] == id) { "Background directory/id mismatch: $id" }
            val palette = props["palette"] as Map<*, *>
            val lighting = props["lighting"] as Map<*, *>
            val texture = props["texture"] as Map<*, *>
            val framing = props["framing"] as Map<*, *>
            fun number(map: Map<*, *>, key: String, range: ClosedFloatingPointRange<Double>): String {
                val value = (map[key] as? Number)?.toDouble() ?: error("$id: missing numeric $key")
                require(value.isFinite() && value in range) { "$id: invalid $key" }
                return "${value}f"
            }
            fun color(key: String): String {
                val value = lighting[key] as? String ?: error("$id: missing $key")
                require(value.matches(Regex("#[0-9a-fA-F]{6}"))) { "$id: invalid $key" }
                return "\"$value\""
            }
            val material = texture["material"] as String
            require(material in setOf("paper", "water", "stone", "sand", "wood", "starlight")) { "$id: unknown texture" }
            val cycle = (animation["cycleMs"] as Number).toInt()
            val fps = (animation["maxFps"] as Number).toInt()
            val overlay = animation["overlay"] as? String ?: "none"
            val overlayCount = (animation["overlayCount"] as? Number)?.toInt() ?: 0
            require(overlay in setOf("none","stellar","bubbles","embers","glyphs","aurora","glass","lanterns","snow","fireworks","hearts","petals","stadium","scoreboard")) { "$id: unknown artwork overlay" }
            require(overlayCount in 0..40) { "$id: invalid artwork particle budget" }
            require(cycle in 6000..180000 && fps in 1..24) { "$id: invalid animation budget" }
            require(animation["pauseWhenHidden"] == true && animation["respectReducedMotion"] == true && animation["respectPowerSave"] == true) {
                "$id: animation must preserve lifecycle, accessibility and energy settings"
            }
            val capability = when (props["compatibility"]) {
                "outdoor" -> "OUTDOOR"
                "window" -> "WINDOW"
                "artistic" -> "ARTISTIC"
                else -> error("$id: invalid compatibility")
            }
            source.append("    \"$id\" to BackgroundStyle(\"$id\", ")
                .append(number(palette, "saturation", .5..1.8)).append(", ")
                .append(number(palette, "contrast", .8..1.4)).append(", ")
                .append(number(lighting, "strength", 0.0..2.0)).append(", ")
                .append(number(lighting, "shadowStrength", 0.0..2.0)).append(", ")
                .append(color("lightColor")).append(", ").append(color("shadowColor")).append(", ")
                .append("\"$material\", ").append(number(texture, "strength", 0.0..1.5))
                .append(", $cycle, $fps, ").append(number(animation, "phaseAtRest", 0.0..1.0)).append(", ").append(number(framing, "focusY", 0.0..1.0))
                .append(", fr.pepsgoal.core.SceneCapability.$capability, ")
                .append(number(framing + mapOf("focusX" to (framing["focusX"] ?: .5)), "focusX", 0.0..1.0))
                .append(", \"$overlay\", $overlayCount),\n")
            val layers = folder.resolve("layers")
            require(layers.isDirectory && layers.listFiles()?.isNotEmpty() == true) { "$id: missing layers" }
        }
        val surface = json(root.resolve("plain/layers/surface.json"))["source"] as String
        require(surface == "theme.bg" || surface.matches(Regex("#[0-9a-fA-F]{6}"))) { "Invalid plain background source" }
        source.append(")\ninternal val PLAIN_BACKGROUND_SOURCE = \"$surface\"\ninternal fun backgroundStyle(key: String): BackgroundStyle = BACKGROUND_STYLES[key.removePrefix(\"s:\")] ?: BACKGROUND_STYLES.getValue(\"pop\")\n")
        root.resolve("_shared").listFiles()!!.filter { it.name.endsWith(".layer.kts") }.sortedBy { it.name }.forEach {
            source.append("\n// Shared asset: ${it.name}\n").append(it.readText()).append("\n")
        }
        for (id in ids) {
            root.resolve("$id/layers").listFiles()!!.filter { it.name.endsWith(".layer.kts") }.sortedBy { it.name }.forEach {
                source.append("\n// Source: assets/backgrounds/$id/layers/${it.name}\n").append(it.readText()).append("\n")
            }
        }
        val destination = generatedBackgroundDirectory.get().file("fr/pepsgoal/app/ui/BackgroundAssets.generated.kt").asFile
        destination.parentFile.mkdirs()
        destination.writeText(source.toString())
    }
}
android.sourceSets.getByName("main").java.srcDir(generatedBackgroundDirectory)
tasks.matching { (it.name.startsWith("compile") && it.name.endsWith("Kotlin")) || it.name.startsWith("lint") }.configureEach {
    dependsOn(generateBackgroundAssets)
}
