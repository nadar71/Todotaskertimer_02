plugins {
    id("com.android.application")
    id("androidx.baselineprofile")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("androidx.room")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("plugin.serialization")
}

android {
    compileSdk = 36

    defaultConfig {
        applicationId = "com.indiewalkabout.nowdothis"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    val signingValues = listOf(
        "NOWDOTHIS_STORE_FILE",
        "NOWDOTHIS_STORE_PASSWORD",
        "NOWDOTHIS_KEY_ALIAS",
        "NOWDOTHIS_KEY_PASSWORD"
    ).associateWith { providers.environmentVariable(it).orNull }
    val releaseEnvironmentSigning = if (signingValues.values.all { !it.isNullOrBlank() }) {
        signingConfigs.create("releaseEnvironment") {
            storeFile = file(requireNotNull(signingValues["NOWDOTHIS_STORE_FILE"]))
            storePassword = signingValues["NOWDOTHIS_STORE_PASSWORD"]
            keyAlias = signingValues["NOWDOTHIS_KEY_ALIAS"]
            keyPassword = signingValues["NOWDOTHIS_KEY_PASSWORD"]
        }
    } else {
        null
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            releaseEnvironmentSigning?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        listOf("benchmarkRelease", "nonMinifiedRelease").forEach { variantName ->
            maybeCreate(variantName).apply {
                java.srcDir("src/debug/java/com/indiewalkabout/nowdothis/benchmark")
            }
        }
    }

    namespace = "com.indiewalkabout.nowdothis"
}

androidComponents {
    onVariants { variant ->
        if (variant.name in setOf("benchmarkRelease", "nonMinifiedRelease")) {
            variant.sources.manifests.addStaticManifestFile(
                "src/debug/benchmark/AndroidManifest.xml"
            )
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.compose.ui:ui:1.7.2")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:2.10.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.hilt:hilt-lifecycle-viewmodel-compose:1.3.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.2")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.2")

    // Compose Navigation 3
    implementation("androidx.navigation3:navigation3-runtime:1.1.3")
    implementation("androidx.navigation3:navigation3-ui:1.1.3")

    // Room components
    implementation("androidx.room:room-runtime:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")

    // DataStore Preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Dagger - Hilt
    implementation("com.google.dagger:hilt-android:2.57.1")
    ksp("com.google.dagger:hilt-compiler:2.57.1")

    // Splash API
    implementation("androidx.core:core-splashscreen:1.0.1")

    // KotlinX Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
    baselineProfile(project(":benchmark"))
}
