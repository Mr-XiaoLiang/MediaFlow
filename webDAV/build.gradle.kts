plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.lollipop.webdav"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

dependencies {
    implementation(libs.androidx.core.ktx)
    api("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("org.simpleframework:simple-xml:2.7.1") {
        exclude(module= "stax")
        exclude(module = "stax-api")
        exclude(module = "xpp3")
    }
}