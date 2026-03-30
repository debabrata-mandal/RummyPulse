import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

fun escapeForBuildConfig(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

fun readGroqFromLocalProperties(key: String): String {
    val f = rootProject.file("local.properties")
    if (!f.isFile) return ""
    return runCatching {
        val p = Properties()
        f.reader(Charsets.UTF_8).use { reader -> p.load(reader) }
        p.getProperty(key)?.trim().orEmpty()
    }.getOrDefault("")
}

/**
 * Groq for BuildConfig (first match wins):
 * 1. Gradle property (project `gradle.properties` or `~/.gradle/gradle.properties` or `-P`)
 * 2. OS environment (CI / VS Code task / shell)
 * 3. Root `local.properties` (gitignored; reliable when Android Studio does not pass env to Gradle)
 */
fun groqConfig(propAndEnvName: String, default: String = ""): String {
    val fromProp = project.findProperty(propAndEnvName)?.toString()?.trim()
    if (!fromProp.isNullOrBlank()) return fromProp
    val fromEnv = System.getenv(propAndEnvName)?.trim()
    if (!fromEnv.isNullOrBlank()) return fromEnv
    val fromLocal = readGroqFromLocalProperties(propAndEnvName)
    if (fromLocal.isNotBlank()) return fromLocal
    return default
}

android {
    namespace = "com.example.rummypulse"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.rummypulse"
        minSdk = 28
        targetSdk = 35
        versionCode = 99
        versionName = "5.3.0"

        // Groq: see groqConfig() — properties, env, or local.properties (not committed).
        val groqKey = escapeForBuildConfig(groqConfig("GROQ_API_KEY"))
        val groqModel = escapeForBuildConfig(
            groqConfig("GROQ_MODEL_ID", "llama-3.1-8b-instant"),
        )
        buildConfigField("String", "GROQ_API_KEY", "\"$groqKey\"")
        buildConfigField("String", "GROQ_MODEL_ID", "\"$groqModel\"")
    }

    signingConfigs {
        create("release") {
            // Use release keystore if it exists (CI), otherwise use debug keystore (local dev)
            if (file("release.keystore").exists()) {
                storeFile = file("release.keystore")
                storePassword = "rummypulse123"
                keyAlias = "rummypulse-release"
                keyPassword = "rummypulse123"
            } else {
                storeFile = file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    
    // QR Code generation library
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.2")
    
    // Firebase dependencies
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-config")
    
    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    
    // Glide for image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    
}