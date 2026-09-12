plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ir.segasim"
    compileSdk = 34
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "ir.segasim"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.2.0"

        // ABIs از طریق splits.abi مدیریت می‌شوند (abiFilters با splits تداخل دارد)
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=none", "-DANDROID_TOOLCHAIN=clang")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            // کم‌حجم‌سازی: R8 + حذف منابع بلااستفاده (فقط Compose، بدون AppCompat/XML)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // خروجی جدا برای هر معماری → هر APK فقط یک .so حمل می‌کند
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = false
        resValues = false
        viewBinding = false
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources.excludes += setOf(
            "META-INF/*", "META-INF/versions/**", "DebugProbesKt.bin"
        )
    }
    lint {
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // خرید درون‌برنامه‌ای (تنها مسیر مجاز پرداخت مطابق سیاست گوگل‌پلی)
    implementation("com.android.billingclient:billing-ktx:7.0.0")
}
