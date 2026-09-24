plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "shura.vianboard.jektal"
    minSdk = 24
    targetSdk = 36
    versionCode = 4100
    versionName = "4.1-beta1"

    resourceConfigurations += listOf("en", "fr")
    ndk {
      abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
    }

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  val keystorePath = System.getenv("DEBUG_KEYSTORE_PATH")
  if (!keystorePath.isNullOrEmpty() && file(keystorePath).exists()) {
    signingConfigs {
      create("customDebug") {
        storeFile = file(keystorePath)
        storePassword = System.getenv("DEBUG_STORE_PASSWORD") ?: "android"
        keyAlias = System.getenv("DEBUG_KEY_ALIAS") ?: "androiddebugkey"
        keyPassword = System.getenv("DEBUG_KEY_PASSWORD") ?: "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    debug {
      val customDebug = signingConfigs.findByName("customDebug")
      if (customDebug != null) {
        signingConfig = customDebug
      }
    }
  }
  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    viewBinding = false
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

dependencies {
  coreLibraryDesugaring(libs.desugar.jdk.libs)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.recyclerview)
  implementation(libs.androidx.autofill)
  implementation(libs.androidx.viewpager2)
  implementation(libs.kotlinx.serialization.json)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  // implementation(libs.androidx.navigation.compose)
  // implementation(libs.reorderable)
  // implementation(libs.colorpicker.compose)
  testImplementation(libs.junit)
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
