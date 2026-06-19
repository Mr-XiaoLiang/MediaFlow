plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.lollipop.common"
    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }
    compileSdk {
        version = release(36)
    }
    defaultConfig {
        minSdk = 31
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildTypes {
        // 创建名为 beta 的新构建模式
        create("beta") {
            initWith(getByName("release"))
        }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    api(libs.androidx.activity)
    api(libs.androidx.constraintlayout)
    api(libs.androidx.documentfile)
    api(libs.androidx.lifecycle.runtime.ktx)
    api(libs.androidx.activity.compose)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.compose.material3)
    // 为了LoadingIndicator
    api("androidx.compose.material3:material3:1.5.0-alpha18")
    // 基础图标库（包含常用图标如 Menu, Edit, Favorite 等，通常已默认包含）
    api(libs.androidx.compose.material.icons.core)
    api(libs.glide)
    api(libs.androidx.window)
    api(libs.androidx.swiperefreshlayout)
    api(libs.glide.compose)

    api(libs.blurview)

    api(libs.androidx.media3.exoplayer)
    api(libs.androidx.media3.ui)
    api(libs.androidx.media3.common.ktx)
    api(libs.androidx.media3.common)

    // Source: https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp
    api(libs.okhttp)

    api("com.google.android.flexbox:flexbox:3.0.0")
}