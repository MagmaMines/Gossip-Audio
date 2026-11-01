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
        versionCode = 8
        versionName = "2.0.0"
        resourceConfigurations += listOf("en", "hi")
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/gossipaudio-release.jks")
            storePassword = System.getenv("GA_STORE_PASSWORD") ?: "GossipAudio#2025"
            keyAlias = System.getenv("GA_KEY_ALIAS") ?: "gossipaudio"
            keyPassword = System.getenv("GA_KEY_PASSWORD") ?: "GossipAudio#2025"
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
            signingConfig = signingConfigs.getByName("release")
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
    // Media3 / ExoPlayer (Apache-2.0): the playback engine. It replaces MediaPlayer and
    // the android.media.audiofx effects, which were the source of the booming, clipped
    // sound. ExoPlayer gives us Sonic time-stretching (speed without chipmunk pitch),
    // proper audio focus and gapless queueing.
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")
    testImplementation("junit:junit:4.13.2")
    // Robolectric boots the real activity + views on the JVM so CI can prove the UI renders.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}
