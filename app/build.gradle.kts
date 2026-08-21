plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.navigation.safeargs)
}

android {
    namespace = "com.mvnsh.citizenship"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mvnsh.citizenship"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.contrib)
    androidTestImplementation(libs.espresso.accessibility)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.work.testing)
    // Pinned explicitly: androidx.fragment:fragment-testing publishes STRICT constraints
    // that hold androidx.test:monitor at 1.6.0, but Espresso 3.6.1 needs 1.7.1 for
    // androidx.test.platform.concurrent.DirectExecutor. Nothing here uses
    // FragmentScenario, so the dependency is dropped rather than force-resolved.
    androidTestImplementation(libs.test.core)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.monitor)
    // GrantPermissionRule: pre-grants POST_NOTIFICATIONS so the settings tests
    // never raise a real system dialog, which pauses the activity mid-suite.
    androidTestImplementation(libs.test.rules)
}
