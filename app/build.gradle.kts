plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.parashmani.speechnova"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.parashmani.speechnova"
        minSdk = 26
        targetSdk = 36
        // 7 was rejected for ad content inconsistent with the content rating,
        // 8 for a Families ad-format violation (the rewarded ad), and 10 for
        // more than one ad on a page. A rejected code can never be re-uploaded.
        versionCode = 11
        versionName = "1.6.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 16 KB page alignment support
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

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
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // AdMob. Play's notice asks for a version listed in the Families
    // Self-Certified Ads SDKs programme; that list sets a *minimum* version,
    // so this is kept well ahead of the 23.0.0 the rejected builds shipped.
    // Check the current minimum on the programme page before each release.
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    // MLKit Translation
    implementation("com.google.mlkit:translate:17.0.3")

    // MLKit on-device language identification — works out which language a
    // piece of text is in, with no network call and nothing leaving the phone.
    implementation("com.google.mlkit:language-id:17.0.6")

    // Play In-App Updates: prompts the user inside the app when a newer
    // version is on the Play Store.
    implementation("com.google.android.play:app-update-ktx:2.1.0")

    // MLKit Text Recognition (camera OCR) — one module per script family.
    // Latin covers English/Spanish/French/German/Portuguese/Italian.
    // Devanagari covers Hindi/Marathi. No on-device model exists (yet)
    // for Bengali, Gujarati, Tamil, Telugu, Kannada, Urdu, or Russian —
    // the app checks for this and tells the user honestly instead of
    // silently failing.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")

    // Reads the EXIF orientation of a captured photo so text on a sideways
    // frame is still recognised.
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
