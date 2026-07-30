plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("com.android.compose.screenshot")
    jacoco
}

android {
    namespace = "net.mamby.health"
    compileSdk = 37

    defaultConfig {
        applicationId = "net.mamby.health"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "net.mamby.health.testing.HiltTestRunner"
        vectorDrawables.useSupportLibrary = true
    }

    flavorDimensions += "env"

    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            manifestPlaceholders["appLabel"] = "@string/app_name_dev"
            buildConfigField("String", "ENVIRONMENT", "\"dev\"")
        }
        create("beta") {
            dimension = "env"
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            manifestPlaceholders["appLabel"] = "@string/app_name_beta"
            buildConfigField("String", "ENVIRONMENT", "\"beta\"")
        }
        create("stage") {
            dimension = "env"
            applicationIdSuffix = ".stage"
            versionNameSuffix = "-stage"
            manifestPlaceholders["appLabel"] = "@string/app_name_stage"
            buildConfigField("String", "ENVIRONMENT", "\"stage\"")
        }
        create("prod") {
            dimension = "env"
            manifestPlaceholders["appLabel"] = "@string/app_name"
            buildConfigField("String", "ENVIRONMENT", "\"prod\"")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
        }
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
        )
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        fatal += "MissingTranslation"
        fatal += "ExtraTranslation"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}

androidComponents {
    beforeVariants(selector().withBuildType("debug")) { variantBuilder ->
        if (variantBuilder.productFlavors.none { (_, flavor) -> flavor == "dev" }) {
            variantBuilder.enable = false
        }
    }
}

jacoco {
    toolVersion = "0.8.13"
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.05.01")
    val lifecycleVersion = "2.11.0"
    val navigation3Version = "1.1.4"
    val adaptiveVersion = "1.3.0-rc01"
    val workVersion = "2.11.2"

    implementation(composeBom)
    androidTestImplementation(composeBom)
    screenshotTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
    implementation("androidx.compose.material3.adaptive:adaptive:$adaptiveVersion")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:$adaptiveVersion")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation:$adaptiveVersion")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation3:$adaptiveVersion")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.hilt:hilt-work:1.4.0")
    implementation("androidx.lifecycle:lifecycle-process:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:$lifecycleVersion")
    implementation("androidx.navigation3:navigation3-runtime:$navigation3Version")
    implementation("androidx.navigation3:navigation3-ui:$navigation3Version")
    implementation("androidx.work:work-runtime:$workVersion")
    implementation("com.google.dagger:hilt-android:2.59.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    ksp("com.google.dagger:hilt-compiler:2.59.2")
    ksp("androidx.hilt:hilt-compiler:1.4.0")
    kspAndroidTest("com.google.dagger:hilt-compiler:2.59.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("androidx.work:work-testing:$workVersion")

    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.work:work-testing:$workVersion")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.59.2")

    screenshotTestImplementation("com.android.tools.screenshot:screenshot-validation-api:0.0.1-alpha14")
    screenshotTestImplementation("androidx.compose.ui:ui-tooling")
}
