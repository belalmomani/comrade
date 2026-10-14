plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Stable signing key supplied by GitHub Actions secrets (never stored in the repo).
// Without it the build falls back to a random debug key and cannot update an installed app.
val keystorePath: String? = System.getenv("GHOST_KEYSTORE_PATH")
val keystorePassword: String? = System.getenv("GHOST_KEYSTORE_PASSWORD")
val hasStableKey = !keystorePath.isNullOrEmpty() &&
    !keystorePassword.isNullOrEmpty() &&
    file(keystorePath!!).exists()

android {
    namespace = "com.cuteghost.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cuteghost.app"
        minSdk = 24
        targetSdk = 34
        // Increase both numbers with every update so Android installs it as an update.
        versionCode = 4
        versionName = "1.3"
    }

    signingConfigs {
        if (hasStableKey) {
            create("stable") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = "cuteghost"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (hasStableKey) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
