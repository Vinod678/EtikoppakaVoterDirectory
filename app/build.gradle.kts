import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

ksp {
    // Export Room schema JSON so future database migrations can be checked.
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.example.etikoppakavoterdirectory"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.etikoppakavoterdirectory"
        minSdk = 26
        targetSdk = 37
        versionCode = 7
        versionName = "3.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Proper release signing, ONLY when the developer has created keystore.properties in the project
    // root (never committed; see docs/PROJECT_CLEANUP_AND_FUTURE_IMPORT_GUIDE.md). Without it the
    // release APK stays unsigned. No credentials are stored in this file.
    val keystoreProps = rootProject.file("keystore.properties")
    val releaseSigning = if (keystoreProps.isFile) {
        val p = Properties().apply { keystoreProps.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = rootProject.file(p.getProperty("storeFile"))
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias")
            keyPassword = p.getProperty("keyPassword")
        }
    } else null

    buildTypes {
        release {
            // Whole-app R8 shrinking. (A partial scope limited to androidx/kotlin packages produced
            // an APK that crashed at start-up with IllegalAccessError - found by the clean-install test.)
            optimization {
                enable = true
            }
            if (releaseSigning != null) signingConfig = releaseSigning
        }
        // For sharing with a few trusted testers: exactly the release build (R8-shrunk, not
        // debuggable), signed with this computer's Android debug key - the same identity as every
        // installation made so far, so it installs over them without losing data.
        create("shareable") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        // Lets Robolectric unit tests read the bundled assets (voters_data.json, photos).
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.material.icons.core)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core.ktx)
    // Real org.json for plain JVM tests (android.jar only contains stubs).
    testImplementation(libs.org.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}