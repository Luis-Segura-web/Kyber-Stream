// build.gradle.kts para la app Kyber-Play con Kotlin, KSP y Jetpack Compose

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

// Configuración global del compilador Kotlin
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget("1.8"))
    }
}

// ¡CAMBIO CLAVE! Leemos el archivo local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.kybers.play"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kybers.play"
        minSdk = 24               // Android 7.0 (Nougat)
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // ¡CAMBIO CLAVE! Hacemos las claves accesibles en el código de la app.
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Hacemos las claves disponibles también en la versión de lanzamiento
            buildConfigField("String", "TMDB_API_KEY", "\"${localProperties.getProperty("tmdb.api.key")}\"")
            buildConfigField("String", "OMDB_API_KEY", "\"${localProperties.getProperty("omdb.api.key")}\"")
        }
        debug {
            // Hacemos las claves disponibles en la versión de depuración
            buildConfigField("String", "TMDB_API_KEY", "\"${localProperties.getProperty("tmdb.api.key")}\"")
            buildConfigField("String", "OMDB_API_KEY", "\"${localProperties.getProperty("omdb.api.key")}\"")
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        compose = true
        // ¡CAMBIO CLAVE! Necesitamos activar buildConfig para que se genere el archivo.
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.kotlin.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    kotlinOptions {
        freeCompilerArgs = listOf("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
    }
}

dependencies {
    // --- CORE & UI ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.accompanist.systemuicontroller)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.gson)

    // --- Compose & Navegación ---
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.foundation)

    // --- ASÍNCRONIA ---
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // --- RED ---
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.moshi.kotlin)

    // --- BASE DE DATOS (Room + KSP) ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp         (libs.androidx.room.compiler)

    // --- REPRODUCTOR DE VIDEO (VLC) ---
    implementation(libs.libvlc.all)

    // --- CARGA DE IMÁGENES ---
    implementation(libs.coil.compose)

    // --- TESTING ---
    testImplementation            (libs.junit)
    androidTestImplementation     (libs.androidx.junit)
    androidTestImplementation     (libs.androidx.espresso.core)
    androidTestImplementation     (platform(libs.compose.bom))
    androidTestImplementation     (libs.androidx.ui.test.junit4)
    debugImplementation           (libs.androidx.ui.tooling)
    debugImplementation           (libs.androidx.ui.test.manifest)
}
