plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.poofacescan1"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.poofacescan1"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    // 1. CameraX (Para manejo estable de la cámara frontal)
    val cameraxVersion = "1.6.2"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // 2. MediaPipe (Para detección de rostro y prueba de vida/Liveness)
    implementation("com.google.mediapipe:tasks-vision:1.2.2")

    // 3. TensorFlow Lite (Versiones estables comprobadas)
    implementation("org.tensorflow:tensorflow-lite:2.16.1")

    // 4. AndroidX Security (Para resguardo encriptado TEE)
    implementation("androidx.security:security-crypto:1.1.0")

    // 5. OpenCV
    implementation(project(":opencv"))
}