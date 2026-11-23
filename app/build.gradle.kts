plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 发布签名：keystore 与密码放在 keystore.properties（已在 .gitignore 里，不会进仓库）
val keystoreProps = java.util.Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String): String? =
    keystoreProps.getProperty(key) ?: providers.gradleProperty(key).orNull

val releaseStoreFile = signingValue("PINDOU_STORE_FILE")
val releaseStorePassword = signingValue("PINDOU_STORE_PASSWORD")
val releaseKeyAlias = signingValue("PINDOU_KEY_ALIAS")
val releaseKeyPassword = signingValue("PINDOU_KEY_PASSWORD")

android {
    namespace = "com.bddhy.pindou"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.bddhy.pindou"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // 调试包也用发布签名：这样 Studio 的 Run 可以直接覆盖手机上的 release 包，
            // 不会因为签名不一致报 INSTALL_FAILED_UPDATE_INCOMPATIBLE
            signingConfig = signingConfigs.findByName("release")
        }
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            // 关掉 R8 / 资源优化：32 位机型、老系统上出现过启动闪退，优先保证兼容性
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
