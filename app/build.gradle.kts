plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "xyz.beyoneer.gossipaudio"
    compileSdk = 35

    defaultConfig {
        applicationId = "xyz.beyoneer.gossipaudio"
        minSdk = 24
        targetSdk = 35
        versionCode = 7
        versionName = "1.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("en", "hi")
        // The public APK targets 64-bit ARM; CI builds x86_64 separately for emulator testing.
        ndk { abiFilters += if (project.hasProperty("neuralTestEmulator")) listOf("x86_64") else listOf("arm64-v8a") }
    }

    // Never ship with the repository's historical demo key. Supply a private
    // keystore via environment variables for a properly signed release.
    val releaseStore = System.getenv("GA_STORE_FILE")
    val releaseStorePassword = System.getenv("GA_STORE_PASSWORD")
    val releaseAlias = System.getenv("GA_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("GA_KEY_PASSWORD")
    val hasReleaseKey = listOf(releaseStore, releaseStorePassword, releaseAlias, releaseKeyPassword).all { !it.isNullOrBlank() }
    signingConfigs {
        if (hasReleaseKey) {
            create("releasePrivate") {
                storeFile = file(releaseStore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("releasePrivate")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "DebugProbesKt.bin")
    }
}

dependencies {
    implementation(files("libs/sherpa-onnx.aar"))
    implementation("org.apache.commons:commons-compress:1.26.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    testImplementation("junit:junit:4.13.2")
    // Robolectric boots the real activity + views on the JVM so CI can prove the UI renders.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}
