import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.tvmediaapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.showhub.tv"
        minSdk = 21
        targetSdk = 34
        versionCode = 161
        versionName = "2.8.102"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    flavorDimensions += "platform"
    productFlavors {
        create("tv") {
            dimension = "platform"
            applicationId = "com.showhub.tv"
            manifestPlaceholders["leanbackRequired"] = "true"
            manifestPlaceholders["screenOrientation"] = "landscape"
            buildConfigField("String", "PLATFORM_TYPE", "\"tv\"")
        }
        create("mobile") {
            dimension = "platform"
            applicationId = "com.showhub.mobile"
            manifestPlaceholders["leanbackRequired"] = "false"
            manifestPlaceholders["screenOrientation"] = "sensorLandscape"
            buildConfigField("String", "PLATFORM_TYPE", "\"mobile\"")
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols.add("**/libtorrserver.so")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose Core
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)

    // Jetpack Compose for Android TV
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Media3 (ExoPlayer)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.session)

    // Coil for Images
    implementation(libs.coil.compose)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
}

tasks.register("ensureTorrServerLibs") {
    doLast {
        val baseDir = file("src/main/jniLibs")
        val libs = mapOf(
            "arm64-v8a" to "https://github.com/YouROK/TorrServer/releases/download/MatriX.145.2/TorrServer-android-arm64",
            "armeabi-v7a" to "https://github.com/YouROK/TorrServer/releases/download/MatriX.145.2/TorrServer-android-arm7"
        )
        libs.forEach { (abi, urlStr) ->
            val targetDir = File(baseDir, abi)
            targetDir.mkdirs()
            val targetFile = File(targetDir, "libtorrserver.so")
            if (!targetFile.exists() || targetFile.length() < 10_000_000L) {
                println("Downloading $abi TorrServer binary...")
                val url = URL(urlStr)
                val inStream: InputStream = url.openStream()
                inStream.use { input ->
                    val outStream = FileOutputStream(targetFile)
                    outStream.use { output ->
                        input.copyTo(output)
                    }
                }
                println("Downloaded $abi successfully!")
            }
        }
    }
}

tasks.matching { it.name.startsWith("pre") && it.name.endsWith("Build") }.configureEach {
    dependsOn("ensureTorrServerLibs")
}

