import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Version affichée par l'application.
//  - Debug   : toujours 0.1
//  - Release : INTERDITE sans accord explicite -> il faut passer -PappVersion=X.Y
val isReleaseBuild = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
val releaseVersion = providers.gradleProperty("appVersion").orNull
if (isReleaseBuild && releaseVersion == null) {
    throw GradleException(
        "Build release refusé : aucune version fournie. " +
            "Une release nécessite l'accord explicite du propriétaire et un numéro de version (-PappVersion=X.Y)."
    )
}

// Signature de la release : fichier de clé et mots de passe fournis hors dépôt (-P… ou variables d'environnement).
fun signingProp(name: String): String? = providers.gradleProperty(name).orNull ?: System.getenv(name)

// Clé des sauvegardes chiffrées : dérivée d'un secret fourni hors dépôt (BACKUP_KEY), seule sa dérivée est intégrée à l'APK.
// Sans ce secret (versions de développement), les sauvegardes utilisent une clé de test ; une release l'exige.
val backupSecret = signingProp("BACKUP_KEY")?.takeIf { it.isNotBlank() }
if (isReleaseBuild && backupSecret == null) {
    throw GradleException("Build release refusé : secret BACKUP_KEY manquant (clé des sauvegardes chiffrées).")
}
val backupKeyHex = backupSecret?.let { secret ->
    MessageDigest.getInstance("SHA-256").digest("CarDeck sauvegarde v1\u0000$secret".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
} ?: ""

android {
    namespace = "com.cardeck.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cardeck.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        buildConfigField("String", "BACKUP_KEY", "\"$backupKeyHex\"")
    }

    signingConfigs {
        val file = signingProp("KEYSTORE_FILE")
        if (file != null) {
            create("release") {
                storeFile = file(file)
                storePassword = signingProp("KEYSTORE_PASSWORD")
                keyAlias = signingProp("KEY_ALIAS")
                keyPassword = signingProp("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // La version de développement s'installe à côté de l'officielle : identifiant et nom différents.
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "CarDeck Debug")
            // Essai du code obfusqué sur un téléphone, à côté de la version installée : ./gradlew assembleDebug -PminifyDebug
            if (providers.gradleProperty("minifyDebug").isPresent) {
                isMinifyEnabled = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            }
        }
        release {
            resValue("string", "app_name", "CarDeck")
            // R8 : code réduit, optimisé et obfusqué ; ressources inutilisées retirées. Correspondance dans build/outputs/mapping.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

androidComponents {
    onVariants { variant ->
        if (variant.buildType == "release" && releaseVersion != null) {
            // Code de version croissant, tiré du numéro : 0.2 → 200, 1.4.3 → 10403 (Android refuse une mise à jour de code inférieur).
            val parts = releaseVersion.split('.').map { it.toIntOrNull() ?: throw GradleException("Numéro de version invalide : $releaseVersion") }
            if (parts.size !in 1..3 || parts.drop(1).any { it > 99 }) throw GradleException("Numéro de version invalide : $releaseVersion (X.Y ou X.Y.Z)")
            val code = parts.getOrElse(0) { 0 } * 10_000 + parts.getOrElse(1) { 0 } * 100 + parts.getOrElse(2) { 0 }
            variant.outputs.forEach { out ->
                out.versionName.set(releaseVersion)
                out.versionCode.set(code)
            }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    // Vrai org.json pour les tests (celui d'Android n'existe pas hors appareil).
    testImplementation("org.json:json:20240303")
}
