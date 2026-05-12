plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.evdash.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.evdash.app"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-mvp1"

        // 国内网络访问 github.com 经常被 GFW 阻断 (port 443 timeout),
        // 这里统一走 ghproxy.com HTTP 反向代理。前缀单独抽出来,
        // 便于在 MapUpdateApi 里给 manifest 里返回的 packageUrl 也套上同一前缀。
        buildConfigField(
            "String",
            "MAP_MIRROR_PREFIX",
            "\"https://ghproxy.com/\""
        )
        buildConfigField(
            "String",
            "MAP_MANIFEST_URL",
            "\"https://ghproxy.com/https://github.com/GHR-123456/ev-dashboard/releases/latest/download/manifest.json\""
        )
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/INDEX.LIST",
            "META-INF/io.netty.versions.properties"
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.maplibre.android.sdk)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.play.services.location)

    debugImplementation(libs.androidx.ui.tooling)
}
