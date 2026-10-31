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
        versionCode = 1
        versionName = "1.0.0"
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
    testImplementation("junit:junit:4.13.2")
}
