import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.time.Duration
import java.util.Base64

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.google.services)
}

val appId = "com.usman.musicplayer"
val googleServicesFile = file("google-services.json")

if (!googleServicesFile.exists()) {
  val base64Secret = System.getenv("GOOGLE_SERVICES_JSON_BASE64")?.trim().orEmpty()
  val rawSecret = System.getenv("GOOGLE_SERVICES_JSON")?.trim().orEmpty()
  if (base64Secret.isNotEmpty()) {
    try {
      val decodedBytes = Base64.getMimeDecoder().decode(base64Secret)
      googleServicesFile.writeBytes(decodedBytes)
      logger.lifecycle("Firebase: Restored app/google-services.json from GOOGLE_SERVICES_JSON_BASE64 environment variable.")
    } catch (e: IllegalArgumentException) {
      throw GradleException(
        "Invalid Base64 content in GOOGLE_SERVICES_JSON_BASE64 environment variable: ${e.message}",
        e
      )
    }
  } else if (rawSecret.isNotEmpty()) {
    googleServicesFile.writeText(rawSecret)
    logger.lifecycle("Firebase: Restored app/google-services.json from GOOGLE_SERVICES_JSON environment variable.")
  }
}

val googleServicesContent = if (googleServicesFile.exists()) googleServicesFile.readText() else ""
val hasMatchingPackageName =
  Regex("\"package_name\"\\s*:\\s*\"${Regex.escape(appId)}\"").containsMatchIn(googleServicesContent)
val hasWebClientIdInGoogleServices =
  hasMatchingPackageName && Regex("\"client_type\"\\s*:\\s*3").containsMatchIn(googleServicesContent)
val requireGoogleServices =
  providers.gradleProperty("requireGoogleServices").map { it.equals("true", ignoreCase = true) }.getOrElse(false)

if (!googleServicesFile.exists()) {
  val missingMsg =
    """
    Firebase configuration file 'app/google-services.json' is missing.
    - For GitHub Actions: Add a repository secret named 'GOOGLE_SERVICES_JSON_BASE64' containing the Base64-encoded contents of your 'app/google-services.json' (`base64 -w 0 app/google-services.json`).
    - For local builds: Place a valid 'google-services.json' for package '$appId' (with Google Sign-In enabled so 'oauth_client' includes 'client_type: 3') at 'app/google-services.json'.
    """.trimIndent()
  if (requireGoogleServices) {
    throw GradleException(missingMsg)
  } else {
    logger.warn(missingMsg)
  }
} else {
  if (!hasMatchingPackageName) {
    val pkgMismatchMsg =
      "Firebase configuration error in 'app/google-services.json': No Android client found with package_name '$appId' matching applicationId."
    if (requireGoogleServices) {
      throw GradleException(pkgMismatchMsg)
    } else {
      logger.warn(pkgMismatchMsg)
    }
  }
  if (!hasWebClientIdInGoogleServices) {
    val missingWebClientMsg =
      "Firebase configuration warning in 'app/google-services.json': No OAuth 2.0 Web Client ID ('client_type: 3') found for '$appId'. Enable Google Sign-In in Firebase Console -> Authentication -> Sign-in method -> Google and re-download 'google-services.json' so 'R.string.default_web_client_id' is generated."
    if (requireGoogleServices) {
      throw GradleException(missingWebClientMsg)
    } else {
      logger.warn(missingWebClientMsg)
    }
  }
}

android {
  namespace = appId
  compileSdk = 35
  buildToolsVersion = "35.0.0"

  defaultConfig {
    applicationId = appId
    minSdk = 24
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // When app/google-services.json is present and contains a client_type: 3 Web Client ID,
    // the Google Services Gradle plugin automatically generates R.string.default_web_client_id.
    // When app/google-services.json is absent or lacks client_type: 3, provide an empty fallback
    // resource so GoogleAuthHelper.kt always compiles cleanly and handles the missing ID at runtime.
    if (!hasWebClientIdInGoogleServices) {
      resValue("string", "default_web_client_id", "")
    }
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Use custom debugConfig if keystore exists (e.g. in AI Studio environment),
      // otherwise fall back to default debug signing (e.g. in GitHub Actions).
      if (file("${rootDir}/debug.keystore").exists()) {
        signingConfig = signingConfigs.getByName("debugConfig")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlin {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.media3.ui)
  implementation(libs.compose.cloudy)
  implementation(libs.haze)
  implementation(libs.androidx.palette.ktx)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Firestore:
  implementation(libs.firebase.firestore)

  // Firebase Auth and Google Sign-In via Credential Manager:
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockwebserver)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

tasks.withType<Test> {
  failFast = true
  timeout.set(Duration.ofMinutes(2))
  testLogging {
    events("passed", "failed", "skipped")
  }
}
