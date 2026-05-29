import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val generatedVersionCode = providers.gradleProperty("VERSION_CODE")
    .orNull
    ?.toIntOrNull()
    ?: ((System.currentTimeMillis() / 1000L).toInt())

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

gradle.taskGraph.whenReady {
    val releaseTasksRequested = allTasks.any { task ->
        task.name.contains("Release", ignoreCase = true)
    }

    if (releaseTasksRequested && !keystorePropertiesFile.exists()) {
        throw IllegalStateException(
            "Missing keystore.properties at the project root. Create it before building release bundles."
        )
    }
}

android {
    namespace = "com.storrs.homeweatherhub"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.storrs.homeweatherhub"
        minSdk = 30
        targetSdk = 36
        versionCode = generatedVersionCode
        versionName = "0.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.create("release") {
                    keyAlias = keystoreProperties.getProperty("keyAlias")
                        ?: error("Missing keyAlias in keystore.properties")
                    keyPassword = keystoreProperties.getProperty("keyPassword")
                        ?: error("Missing keyPassword in keystore.properties")
                    storeFile = rootProject.file(
                        keystoreProperties.getProperty("storeFile")
                            ?: error("Missing storeFile in keystore.properties")
                    )
                    storePassword = keystoreProperties.getProperty("storePassword")
                        ?: error("Missing storePassword in keystore.properties")
                }
            }

            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}