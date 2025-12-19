plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("io.objectbox") // Apply ObjectBox plugin
}

android {
    namespace = "com.example.notes"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.notes"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            // MediaPipe tasks only support ARM architectures.
            // This ensures that on x86 emulators (with ARM translation),
            // the ARM native libraries are used.
            abiFilters.addAll(listOf("arm64-v8a"))
        }

        // CMake build disabled - using prebuilt paddleocr4android AAR instead
        // externalNativeBuild {
        //     cmake {
        //         arguments("-DANDROID_STL=c++_shared", "-DANDROID_PLATFORM=android-23")
        //         cppFlags("-std=c++11")
        //     }
        // }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // Helps with loading native libraries on some devices/emulators
            useLegacyPackaging = true
            // Pick the first occurrence of libc++_shared.so (from paddleocr4android)
            pickFirsts.add("lib/arm64-v8a/libc++_shared.so")
            pickFirsts.add("lib/armeabi-v7a/libc++_shared.so")
        }
    }

    // CMake build disabled - using prebuilt paddleocr4android AAR instead
    // externalNativeBuild {
    //     cmake {
    //         path = file("src/main/cpp/CMakeLists.txt")
    //     }
    // }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // ML Kit & MediaPipe
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu-api:2.16.1")

    // TensorFlow Lite (kept for potential future use or other ML tasks)
    // Note: MediaPipe tasks-text handles text embeddings now

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.6")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // ObjectBox dependencies are automatically added by the plugin
    // But we can enable debug browser if needed (debugImplementation "io.objectbox:objectbox-android-objectbrowser:$objectboxVersion")
    // Explicitly adding processor just in case
    kapt("io.objectbox:objectbox-processor:5.0.1")

    // Paddle OCR for Android - Built from source (forked from equationl/paddleocr4android)
    // Includes Paddle Lite 2.14-rc with PP-OCRv4 support
    implementation(files("libs/PaddleOCR4Android-release.aar"))
    
    // OpenCV for image preprocessing
    implementation("com.quickbirdstudios:opencv:4.5.3.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}