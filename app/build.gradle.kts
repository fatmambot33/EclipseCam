plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ciVersionCode = providers.environmentVariable("ECLIPSE_CAM_VERSION_CODE")
    .orElse("1")
    .map(String::toInt)

val ciVersionName = providers.environmentVariable("ECLIPSE_CAM_VERSION_NAME")
    .orElse("0.0.1")

android {
    namespace = "com.fatmambo33.eclipsecam"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fatmambo33.eclipsecam"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode.get()
        versionName = ciVersionName.get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = providers.environmentVariable("ECLIPSE_CAM_STORE_FILE").orNull
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = providers.environmentVariable("ECLIPSE_CAM_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("ECLIPSE_CAM_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("ECLIPSE_CAM_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (providers.environmentVariable("ECLIPSE_CAM_STORE_FILE").isPresent) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.maplibre.gl:android-sdk:13.0.2")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
