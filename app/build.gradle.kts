plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
}

android {
    namespace = "io.livekit.android.example.voiceassistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.livekit.android.example.voiceassistant"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "3.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"https://medbot-backend.fly.dev\"")
            buildConfigField("String", "LIVEKIT_WS_URL", "\"wss://clinical-chatbot-1dewlazs.livekit.cloud\"")
            buildConfigField("String", "PRIVACY_POLICY_URL", "\"https://sites.google.com/view/first-aid-pp/home\"")
            buildConfigField("String", "TERM_URL", "\"https://sites.google.com/view/first-aid-terms-of-service/home\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"https://medbot-backend.fly.dev\"")
            buildConfigField("String", "LIVEKIT_WS_URL", "\"wss://clinical-chatbot-1dewlazs.livekit.cloud\"")
            buildConfigField("String", "PRIVACY_POLICY_URL", "\"https://sites.google.com/view/first-aid-pp/home\"")
            buildConfigField("String", "TERM_URL", "\"https://sites.google.com/view/first-aid-terms-of-service/home\"")
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
        buildConfig = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    // For local development with the LiveKit Compose SDK only.
    // implementation("io.livekit:livekit-compose-components")

    implementation(libs.livekit.lib)
    implementation(libs.livekit.components)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.timberkt)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.logging.interceptor)
}