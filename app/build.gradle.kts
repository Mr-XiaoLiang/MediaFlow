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
        versionCode = 3_00_00
        versionName = "3.0.0-alpha"
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.window)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.flexbox)
    implementation(libs.okhttp)
    implementation(libs.androidx.biometric)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // 为了 LoadingIndicator（版本高于 BOM）
    //noinspection UseTomlInstead
    implementation("androidx.compose.material3:material3:1.5.0-alpha22")
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.activity.compose)

    // 扩展图标库（包含 Google 提供的数千个额外图标，如各种形状的物体、品牌、方向等）
    // 注意：此库体积非常大，编译时会增加内存消耗，建议开启 R8/Proguard
    implementation(libs.androidx.compose.material.icons.extended)

    // Media3（播放 / 播放器表面 / 字幕）
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.common.ktx)
    // Compose 表现层的播放器表面（PlayerSurface），版本跟随 media3
    implementation(libs.androidx.media3.ui.compose)

    // 图片加载：迁移期 Glide（旧 View 体系）与 Coil 3（Compose 体系）并存，
    // 里程碑 10 完成首页 Coil 化后清理 Glide 与 blurview。
    implementation(libs.glide)
    implementation(libs.glide.compose)
    implementation(libs.blurview)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.coil.network.okhttp)

    implementation(libs.scaleImage)
    // Source: https://mvnrepository.com/artifact/io.github.anilbeesetti/nextlib-media3ext
    implementation(libs.nextlib.media3ext)
}
