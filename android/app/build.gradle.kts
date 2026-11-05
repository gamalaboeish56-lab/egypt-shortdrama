import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

fun prop(name: String): String = (project.findProperty(name) as String?) ?: ""

// keystore.properties (مش بيترفع على GitHub) - فيه بيانات توقيع نسخة الـ release
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "egypt.shortdrama.top"
    compileSdk = 36

    defaultConfig {
        applicationId = "egypt.shortdrama.top"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        manifestPlaceholders["admobAppId"] = prop("ADMOB_APP_ID")
        buildConfigField("String", "SITE_URL", "\"${prop("SITE_URL")}\"")
        buildConfigField("String", "INSTAPAY_ADDRESS", "\"${prop("INSTAPAY_ADDRESS")}\"")
        buildConfigField("String", "VODAFONE_CASH", "\"${prop("VODAFONE_CASH")}\"")
        buildConfigField("boolean", "PAYMOB_ENABLED", prop("PAYMOB_ENABLED").ifEmpty { "false" })
        buildConfigField("String", "PAYMOB_BACKEND_URL", "\"${prop("PAYMOB_BACKEND_URL")}\"")
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
            // نسخة الديباج بتستخدم إعلانات جوجل التجريبية (ممنوع تضغط على إعلاناتك الحقيقية)
            buildConfigField("String", "BANNER_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
            buildConfigField("String", "INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "BANNER_ID", "\"${prop("ADMOB_BANNER_ID")}\"")
            buildConfigField("String", "INTERSTITIAL_ID", "\"${prop("ADMOB_INTERSTITIAL_ID")}\"")
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-analytics")

    // Google Sign-In (Credential Manager)
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // AdMob
    implementation("com.google.android.gms:play-services-ads:24.6.0")

    // QR لعنوان InstaPay
    implementation("com.google.zxing:core:3.5.3")
}
