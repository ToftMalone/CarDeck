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

android {
    namespace = "com.cardeck.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cardeck.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
            variant.outputs.forEach { out ->
                out.versionName.set(releaseVersion)
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
    debugImplementation("androidx.compose.ui:ui-tooling")
}
