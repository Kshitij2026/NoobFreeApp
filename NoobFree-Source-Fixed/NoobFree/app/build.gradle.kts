import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
}

// Resolve signing parameters from keystore.properties OR environment variables (GitHub Actions secrets)
val resolvedStoreFile: String? = keystoreProperties.getProperty("storeFile")
    ?: System.getenv("NOOBFREE_KEYSTORE_PATH")
val resolvedStorePassword: String? = keystoreProperties.getProperty("storePassword")
    ?: System.getenv("NOOBFREE_KEYSTORE_PASSWORD")
val resolvedKeyAlias: String? = keystoreProperties.getProperty("keyAlias")
    ?: System.getenv("NOOBFREE_KEY_ALIAS")
val resolvedKeyPassword: String? = keystoreProperties.getProperty("keyPassword")
    ?: System.getenv("NOOBFREE_KEY_PASSWORD")

val hasExplicitSigning = !resolvedStoreFile.isNullOrBlank() &&
    rootProject.file(resolvedStoreFile).exists() &&
    !resolvedStorePassword.isNullOrBlank() &&
    !resolvedKeyAlias.isNullOrBlank() &&
    !resolvedKeyPassword.isNullOrBlank()

android {
    namespace = "com.noobfree.android"
    compileSdk = 35

    defaultConfig {
        // Stable applicationId across all builds to prevent INSTALL_FAILED_UPDATE_INCOMPATIBLE / package conflicts
        applicationId = "com.noobfree.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasExplicitSigning) {
            create("release") {
                storeFile = rootProject.file(resolvedStoreFile!!)
                storePassword = resolvedStorePassword
                keyAlias = resolvedKeyAlias
                keyPassword = resolvedKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // Keep stable applicationId so debug and release builds upgrade cleanly when signed with the same persistent key
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            if (hasExplicitSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use explicit release signing when provided; fall back to debug signing so CI always outputs an installable APK
            signingConfig = if (hasExplicitSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = true
        warningsAsErrors = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    val shizukuVersion = "13.1.5"
    implementation("dev.rikka.shizuku:api:$shizukuVersion")
    implementation("dev.rikka.shizuku:provider:$shizukuVersion")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("app.cash.turbine:turbine:1.2.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
