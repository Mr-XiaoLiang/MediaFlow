plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.lollipop.mediaflow"
    compileSdk {
        version = release(36)
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    defaultConfig {
        applicationId = "com.lollipop.mediaflow"
        minSdk = 31
        targetSdk = 36
        versionCode = 1_03_00
        versionName = "1.3.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
//            resValue("string", "app_name", "MediaFlow-Debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = true
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // 基础图标库（包含常用图标如 Menu, Edit, Favorite 等，通常已默认包含）
    implementation(libs.androidx.compose.material.icons.core)
    // 扩展图标库（包含 Google 提供的数千个额外图标，如各种形状的物体、品牌、方向等）
    // 注意：此库体积非常大，编译时会增加内存消耗，建议开启 R8/Proguard
//    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.glide)
    implementation(libs.androidx.window)
    implementation(libs.androidx.swiperefreshlayout)

    implementation(libs.blurview)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.androidx.media3.common)

    implementation("com.davemorrissey.labs:subsampling-scale-image-view-androidx:3.10.0")
}