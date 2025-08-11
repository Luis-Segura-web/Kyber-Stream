import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    id("com.google.protobuf") version "0.9.4"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget("17"))
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

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
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "DEBUG", "false")
            buildConfigField("String", "TMDB_API_KEY", "\"${localProperties.getProperty("tmdb.api.key")}\"")
            buildConfigField("String", "OMDB_API_KEY", "\"${localProperties.getProperty("omdb.api.key")}\"")
        }
        debug {
            buildConfigField("boolean", "DEBUG", "true")
            buildConfigField("String", "TMDB_API_KEY", "\"${localProperties.getProperty("tmdb.api.key")}\"")
            buildConfigField("String", "OMDB_API_KEY", "\"${localProperties.getProperty("omdb.api.key")}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.5"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                register("java") {
                    option("lite")
                }
            }
        }
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
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.moshi.adapters)

    // --- BASE DE DATOS ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // --- REPRODUCTOR DE VIDEO ---
    implementation(libs.libvlc.all)
    
    // --- MEDIA3 (EXOPLAYER) ---
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    implementation("androidx.media3:media3-cast:1.4.1")

    // --- CARGA DE IMÁGENES ---
    implementation(libs.coil.compose)

    // --- DEPENDENCY INJECTION ---
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    
    // --- SECURITY ---
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // --- DATASTORE ---
    implementation("androidx.datastore:datastore:1.1.1")
    implementation("com.google.protobuf:protobuf-javalite:3.25.5")

    // --- TESTING ---
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
