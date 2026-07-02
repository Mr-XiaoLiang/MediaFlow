plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.lollipop.mediaflow"
    compileSdk {
        version = release(37)
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    defaultConfig {
        applicationId = "com.lollipop.mediaflow"
        minSdk = 26
        targetSdk = 37
        versionCode = 2_17_00
        versionName = "2.17.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            versionNameSuffix = ".debug"
//            resValue("string", "app_name", "MediaFlow-Debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // 创建名为 beta 的新构建模式
        create("beta") {
            // 继承 release 的配置（包括签名配置等）
            initWith(getByName("release"))

            // 增加包名后缀，这样可以和正式版同时安装在同一台手机上
            applicationIdSuffix = ".beta"

            // 增加版本名后缀，方便在 App 内查看版本
            versionNameSuffix = ".beta.${System.currentTimeMillis().toString(16).uppercase()}"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // 扩展图标库（包含 Google 提供的数千个额外图标，如各种形状的物体、品牌、方向等）
    // 注意：此库体积非常大，编译时会增加内存消耗，建议开启 R8/Proguard
    api(libs.androidx.compose.material.icons.extended)

    implementation(libs.scaleImage)
    // Source: https://mvnrepository.com/artifact/io.github.anilbeesetti/nextlib-media3ext
    implementation(libs.nextlib.media3ext)
}