plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.s1nn3rv2.kartka"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.s1nn3rv2.kartka"
        minSdk = 31
        targetSdk = 37
        // Releases take their version from the git tag (v1.2.3 -> versionName 1.2.3, versionCode 10203), so app
        // stores like Obtainium see the installed version match the release. Local builds stay 1.0.
        val tag = System.getenv("KARTKA_VERSION")?.removePrefix("v")?.takeIf { Regex("""\d+(\.\d+){0,2}""").matches(it) }
        versionName = tag ?: "1.0"
        versionCode = tag?.split('.')?.map(String::toInt)?.let { p -> p.getOrElse(0) { 0 } * 10000 + p.getOrElse(1) { 0 } * 100 + p.getOrElse(2) { 0 } } ?: 1

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing key from environment variables (GitHub Actions secrets, see .github/workflows/build.yml).
    // Without them, release builds fall back to this computer's debug key, like before.
    val releaseKeystore = System.getenv("KARTKA_KEYSTORE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("KARTKA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KARTKA_KEY_ALIAS")
                keyPassword = System.getenv("KARTKA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            optimization {
                enable = true
            }
            proguardFiles("proguard-rules.pro")
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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.commonmark.ext.gfm.strikethrough)
    implementation(libs.commonmark.ext.task.list.items)
    implementation(libs.commonmark.ext.autolink)
    implementation(libs.commonmark.ext.footnotes)
    implementation(libs.jlatexmath.android)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}