plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signing key supplied by GitHub Actions secrets (never stored in the repo).
// For Google Play this is the UPLOAD key. Keep a backup of it: losing it means a support request to Google.
val keystorePath: String? = System.getenv("GHOST_KEYSTORE_PATH")
val keystorePassword: String? = System.getenv("GHOST_KEYSTORE_PASSWORD")
fun envOf(vararg names: String): String? = names.map { System.getenv(it) }.firstOrNull { !it.isNullOrBlank() }
// The key alias / key password can be supplied as GHOST_KEY_* or ANDROID_KEY_* secrets (the first one that is set wins)
val keyAliasName: String = envOf("GHOST_KEY_ALIAS", "ANDROID_KEY_ALIAS") ?: "cuteghost"
val keyPasswordValue: String? = envOf("GHOST_KEY_PASSWORD", "ANDROID_KEY_PASSWORD") ?: keystorePassword

fun clean(value: String?): String = (value ?: "").replace("\"", "").replace("\\", "").trim()

// AdMob ids. Release builds use the real ids (they are public identifiers, not secrets) and can be overridden
// with the GitHub secrets ADMOB_APP_ID / ADMOB_REWARDED_ID / ADMOB_BANNER_ID. Debug builds ALWAYS use Google's
// test ids, so testing never produces invalid traffic on the real ads (that can get an AdMob account suspended).
val testAppId = "ca-app-pub-3940256099942544~3347511713"
val testRewardedId = "ca-app-pub-3940256099942544/5224354917"
val testBannerId = "ca-app-pub-3940256099942544/9214589741"
val realAppId = "ca-app-pub-7098954256458559~6991369939"
val realRewardedId = "ca-app-pub-7098954256458559/4911875195"
val realBannerId = "ca-app-pub-7098954256458559/7238781940"
val admobAppId: String = clean(System.getenv("ADMOB_APP_ID") ?: (project.findProperty("ADMOB_APP_ID") as String?))
    .ifEmpty { realAppId }
val admobRewardedId: String = clean(System.getenv("ADMOB_REWARDED_ID") ?: (project.findProperty("ADMOB_REWARDED_ID") as String?))
    .ifEmpty { realRewardedId }
val admobBannerId: String = clean(System.getenv("ADMOB_BANNER_ID") ?: (project.findProperty("ADMOB_BANNER_ID") as String?))
    .ifEmpty { realBannerId }

val hasStableKey = !keystorePath.isNullOrEmpty() &&
    !keystorePassword.isNullOrEmpty() &&
    file(keystorePath!!).exists()

android {
    namespace = "com.cuteghost.app"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.companion.theghost"
        minSdk = 24
        targetSdk = 36
        // Increase both numbers with every update uploaded to Google Play.
        versionCode = 53
        versionName = "6.9"
        buildConfigField("String", "ADMOB_APP_ID", "\"$admobAppId\"")
        buildConfigField("String", "ADMOB_REWARDED_ID", "\"$admobRewardedId\"")
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
        manifestPlaceholders["admobAppId"] = admobAppId
    }

    signingConfigs {
        if (hasStableKey) {
            create("stable") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "ADMOB_APP_ID", "\"$testAppId\"")
            buildConfigField("String", "ADMOB_REWARDED_ID", "\"$testRewardedId\"")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$testBannerId\"")
            manifestPlaceholders["admobAppId"] = testAppId
            if (hasStableKey) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }
        release {
            isMinifyEnabled = false
            if (hasStableKey) {
                signingConfig = signingConfigs.getByName("stable")
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
}

dependencies {
    // Google AdMob (a Google Play Families self-certified ads SDK)
    implementation("com.google.android.gms:play-services-ads:23.6.0")
}
