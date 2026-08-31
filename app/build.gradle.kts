plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "moe.notice.filter"
    compileSdk = 37

    defaultConfig {
        applicationId = "moe.notice.filter"
        minSdk = 29
        targetSdk = 37
        // CI 通过 -PversionCode / -PversionName 注入（见 release.yml）：versionName 取 Release 的 tag，
        // versionCode 由 tag 的 x.y.z 计算（x*10000 + y*100 + z），保证随版本单调递增；本地构建用下面的默认值。
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as String?)?.takeIf { it.isNotBlank() } ?: "1.0.0-dev"
    }

    // CI 通过环境变量提供正式签名（见 .github/workflows/release.yml）；本地或未配置时退回 debug 签名。
    val releaseKeystore = System.getenv("RELEASE_KEYSTORE_PATH")
    if (!releaseKeystore.isNullOrBlank()) {
        // 全部 trim：从网页粘贴到 secret 的值常带首尾空白或换行
        val storePass = System.getenv("RELEASE_KEYSTORE_PASSWORD")?.trim()
        signingConfigs.create("release") {
            storeFile = rootProject.file(releaseKeystore.trim()) // 相对路径以仓库根目录为准
            storePassword = storePass
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")?.trim()?.takeIf { it.isNotBlank() } ?: "notice"
            // PKCS12 密钥库的密钥密码必须与库密码一致；未单独提供时沿用库密码
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")?.trim()?.takeIf { it.isNotBlank() } ?: storePass
        }
    }

    buildTypes {
        release {
            // R8 裁剪：material-icons-extended 等依赖整包约 49 MB dex，实际只用到几十个图标
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.5.0-alpha25")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
