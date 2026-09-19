import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.yt4.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yt4.app"
        minSdk = 26          // Media3 session + WebView auth flows work cleanly from 26.
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // Ship only the ABIs real devices use; keeps the APK tiny.
        resourceConfigurations += setOf("en")
    }

    buildTypes {
        release {
            // R8 full mode (enabled globally via android.enableR8.fullMode).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), // -optimize passes on
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // No BuildConfig generation → fewer classes for R8 to scan.
        buildConfig = false
    }

    packaging {
        resources {
            // Strip metadata/junk that libraries drag in.
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module",
                "kotlin/**",
                "DebugProbesKt.bin",
            )
        }
    }
}

kotlin {
    // Baseline profile: the profile artifacts are consumed by R8 and the
    // profileinstaller at runtime; nothing extra needed here, but keep
    // jvmTarget pinned so the generated code is stable across machines.
    jvmToolchain(17)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        // Zero-cost assertions in release, full intrinsics checks kept on
        // (they are already stripped by R8 in full mode).
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-opt-in=kotlin.RequiresOptIn",
            "-Xlambdas=indy", // invokedynamic lambdas → smaller dex, no synthetic classes
        )
    }
}

// Compose compiler stability reports — run with: ./gradlew assembleRelease -PcomposeMetrics
// Outputs class/function stability + restartable/skippable tables so we can
// verify "zero unnecessary recompositions" on every change.
composeCompiler {
    if (project.hasProperty("composeMetrics")) {
        metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
        reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
    }
}

dependencies {
    // Kotlin
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    // Installs the baseline profile on sideloaded installs (no Play needed).
    implementation(libs.androidx.profileinstaller)

    // Compose — everything pinned by the BOM.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Media3 (ExoPlayer) — player engine, session service, OkHttp data source.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.common)

    // Network + extraction
    implementation(libs.okhttp)
    implementation(libs.newpipe.extractor) {
        // NPE pulls Rhino for JS de-obfuscation; keep it, but drop its
        // duplicated kotlin-stdlib transitive to avoid version clashes.
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    }

    // Images
    implementation(libs.coil.compose)

    // Persistence
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)
}
