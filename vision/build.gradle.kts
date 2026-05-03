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
        versionCode = 2_10_00
        versionName = "2.10.0"
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

    implementation(libs.scaleImage)
}