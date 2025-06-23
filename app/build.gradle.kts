import java.util.Properties
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.android.libraries.mapsplatform.secrets.gradle.plugin)
}
fun getApiKey(propertyKey: String, projectRoot: File): String {
    val localPropertiesFile = projectRoot.resolve("local.properties")
    if (localPropertiesFile.exists()) {
        val properties = Properties() // Use java.util.Properties
        try {
            localPropertiesFile.inputStream().use {
                properties.load(it)
            }
            return properties.getProperty(propertyKey, "").trim()
        } catch (e: Exception) {
            println("Warning: Could not load properties from local.properties: ${e.message}")
        }
    } else {
        println("Warning: local.properties file not found at ${localPropertiesFile.path}")
    }
    return "" // Return empty string if not found or error
}

android {
    namespace = "com.example.onenighttent"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.onenighttent"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.play.services.maps)
    implementation(libs.androidx.appcompat)
//    implementation(libs.androidx.constraintlayout)
    implementation(libs.play.services.location)
    implementation(libs.material)
//    implementation(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
//    androidTestImplementation(platform(libs.androidx.compose.bom))
//    androidTestImplementation(libs.androidx.ui.test.junit4)
//    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}