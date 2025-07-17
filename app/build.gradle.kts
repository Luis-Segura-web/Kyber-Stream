// build.gradle.kts para la app Kyber-Play con Kotlin, KSP y Jetpack Compose

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

// Configuración global del compilador Kotlin
kotlin {
    compilerOptions {
        // Migrado al nuevo compilerOptions DSL
        jvmTarget.set(JvmTarget.fromTarget("1.8"))
    }
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        // Activamos Jetpack Compose
        compose = true
    }

    composeOptions {
        // Asegúrate de que esta versión exista en tu catálogo
        kotlinCompilerExtensionVersion = libs.versions.kotlin.get()
    }

    packaging {
        resources {
            // Excluimos licencias duplicadas
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
    implementation(libs.androidx.compose.foundation) // ¡NUEVO! Añadimos la dependencia de foundation

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

    // --- REPRODUCTOR DE VIDEO (Media3) ---
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
