plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

val releaseStoreFilePath = providers.environmentVariable("RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull

val validateReleaseSigning by tasks.registering {
    group = "verification"
    description = "Fails when production release-signing inputs are unavailable."

    doLast {
        val missingInputs = buildList {
            if (releaseStoreFilePath.isNullOrBlank()) add("RELEASE_STORE_FILE")
            if (releaseStorePassword.isNullOrBlank()) add("RELEASE_STORE_PASSWORD")
            if (releaseKeyAlias.isNullOrBlank()) add("RELEASE_KEY_ALIAS")
            if (releaseKeyPassword.isNullOrBlank()) add("RELEASE_KEY_PASSWORD")
        }

        check(missingInputs.isEmpty()) {
            "Release signing is not configured. Missing environment variables: ${missingInputs.joinToString()}."
        }

        check(rootProject.file(releaseStoreFilePath!!).isFile) {
            "Release keystore file does not exist at RELEASE_STORE_FILE."
        }
    }
}

android {
    namespace = "com.example.rummypulse"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.rummypulse"
        minSdk = 24
        targetSdk = 34
        versionCode = 102
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    signingConfigs {
        create("release") {
            releaseStoreFilePath?.takeIf { it.isNotBlank() }?.let {
                storeFile = rootProject.file(it)
            }
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
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
    testOptions {
        unitTests.all {
            it.jvmArgs("-Dnet.bytebuddy.experimental=true")
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild" || name == "validateSigningRelease") {
        dependsOn(validateReleaseSigning)
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
    implementation(libs.room.runtime)
    annotationProcessor("androidx.room:room-compiler:2.8.4")
    implementation(libs.work.runtime)
    implementation(libs.gson)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    testImplementation(libs.junit)
    testImplementation(libs.room.testing)
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.mockito:mockito-core:5.23.0")
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation(libs.room.testing)
    androidTestImplementation("androidx.test:core:1.7.0")
    
    // QR Code generation library
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.4")
    
    // Firebase dependencies
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-appcheck")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    releaseImplementation("com.google.firebase:firebase-appcheck-playintegrity")
    
    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    
    // Glide for image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    
}
