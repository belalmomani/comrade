plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Stable signing key supplied by GitHub Actions secrets (never stored in the repo).
// Without it the build falls back to a random debug key and cannot update an installed app.
val keystorePath: String? = System.getenv("GHOST_KEYSTORE_PATH")
val keystorePassword: String? = System.getenv("GHOST_KEYSTORE_PASSWORD")
// AI keys come from the GitHub secret GHOST_AI_KEYS (comma separated); empty when not set.
val aiKeys: String = (System.getenv("GHOST_AI_KEYS") ?: "")
    .replace("\"", "")
    .replace("\\", "")
    .trim()

// Repository that publishes the releases used by the in-app updater (set automatically by GitHub Actions).
val updateRepo: String = (System.getenv("GITHUB_REPOSITORY") ?: "")
    .replace("\"", "")
    .replace("\\", "")
    .trim()

val hasStableKey = !keystorePath.isNullOrEmpty() &&
    !keystorePassword.isNullOrEmpty() &&
    file(keystorePath!!).exists()

android {
    namespace = "com.cuteghost.app"
    compileSdk = 34

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.companion.theghost"
        minSdk = 24
        targetSdk = 34
        // Increase both numbers with every update so Android installs it as an update.
        versionCode = 28
        versionName = "4.3"
        buildConfigField("String", "AI_KEYS", "\"$aiKeys\"")
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
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
