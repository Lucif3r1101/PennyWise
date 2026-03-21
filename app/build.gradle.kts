import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun localProperty(name: String): String = localProperties.getProperty(name, "").trim()

android {
    namespace = "com.rishav.pennywise"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.rishav.pennywise"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "GMAIL_CONFIGURED", localProperty("GMAIL_CLIENT_ID").isNotBlank().toString())
        buildConfigField("boolean", "OUTLOOK_CONFIGURED", localProperty("OUTLOOK_CLIENT_ID").isNotBlank().toString())
        buildConfigField("String", "GMAIL_CLIENT_ID", "\"${localProperty("GMAIL_CLIENT_ID")}\"")
        buildConfigField("String", "GMAIL_REDIRECT_SCHEME", "\"${localProperty("GMAIL_REDIRECT_SCHEME")}\"")
        buildConfigField("String", "GMAIL_REDIRECT_HOST", "\"${localProperty("GMAIL_REDIRECT_HOST")}\"")
        buildConfigField("String", "OUTLOOK_CLIENT_ID", "\"${localProperty("OUTLOOK_CLIENT_ID")}\"")
        buildConfigField("String", "OUTLOOK_TENANT_ID", "\"${localProperty("OUTLOOK_TENANT_ID")}\"")
        buildConfigField("String", "OUTLOOK_REDIRECT_SCHEME", "\"${localProperty("OUTLOOK_REDIRECT_SCHEME")}\"")
        buildConfigField("String", "OUTLOOK_REDIRECT_HOST", "\"${localProperty("OUTLOOK_REDIRECT_HOST")}\"")
        manifestPlaceholders["appAuthRedirectScheme"] = localProperty("GMAIL_REDIRECT_SCHEME").ifBlank { "com.rishav.pennywise" }
        manifestPlaceholders["gmailRedirectScheme"] = localProperty("GMAIL_REDIRECT_SCHEME").ifBlank { "com.rishav.pennywise" }
        manifestPlaceholders["gmailRedirectHost"] = localProperty("GMAIL_REDIRECT_HOST").ifBlank { "oauth2redirect" }
        manifestPlaceholders["outlookRedirectScheme"] = localProperty("OUTLOOK_REDIRECT_SCHEME").ifBlank { "com.rishav.pennywise" }
        manifestPlaceholders["outlookRedirectHost"] = localProperty("OUTLOOK_REDIRECT_HOST").ifBlank { "auth" }
    }

    buildTypes {
        release {
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
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.appauth)
    implementation(libs.play.services.auth)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
