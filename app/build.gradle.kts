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
        minSdk = 24
        targetSdk = 34
        versionCode = 6
        versionName = "0.6.0"

        // پلیس‌هولدرهای مانیفست کتابخانه‌ی مایکت — بدون این‌ها merge مانیفست شکست می‌خورد
        manifestPlaceholders["marketApplicationId"] = "ir.mservices.market"
        manifestPlaceholders["marketBindAddress"] = "ir.mservices.market.InAppBillingService.BIND"
        manifestPlaceholders["marketPermission"] = "ir.mservices.market.BILLING"

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=none", "-DANDROID_TOOLCHAIN=clang")
            }
        }
    }

    // ── طعم‌های فروشگاه ─────────────────────────────────────────────
    // هر دو SDK ایرانی (Poolakey و myket-billing-client) کلاس AIDL
    // com.android.vending.billing.IInAppBillingService را داخل خودشان
    // باندل می‌کنند؛ کنار هم duplicate class می‌دهد. راه‌حل استاندارد:
    // هر فروشگاه یک flavor جدا. خروجی:
    //   app-bazaar-*.apk  → آپلود در بازار
    //   app-myket-*.apk   → آپلود در مایکت
    flavorDimensions += "store"
    productFlavors {
        create("bazaar") {
            dimension = "store"
        }
        create("myket") {
            dimension = "store"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        create("release") {
            // کلید داخل ریپو است تا کلون و بیلد بی‌دردسر کار کند.
            // ⚠️ اگر ریپو عمومی است هر کسی می‌تواند با همین کلید امضا کند —
            // قبل از انتشار تجاری، کلید را بچرخانید و مسیرش را محلی کنید.
            storeFile = file("segasim-release.jks")
            storePassword = "segasim123"
            keyAlias = "segasim"
            keyPassword = "segasim123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            // با همان کلید release امضا می‌شود تا روی HyperOS 3 / MIUI هم نصب شود
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // خروجی جدا برای arm64 + نسخه Universal (همه ABIها در یک APK)
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = true
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

    // پرداخت درون‌برنامه‌ای ایرانی — هر SDK فقط در flavor فروشگاه خودش
    // (هر دو کلاس AIDL مشترک را باندل می‌کنند و نباید کنار هم باشند)
    "bazaarImplementation"("com.github.cafebazaar.Poolakey:poolakey:2.2.0")
    "myketImplementation"("com.github.myketstore:myket-billing-client:1.19")
}
