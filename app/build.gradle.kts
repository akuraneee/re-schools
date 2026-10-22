import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Версия приложения — rs.version из gradle.properties. В CI последнее число — номер сборки,
// чтобы новая версия ставилась поверх старой.
val appVersion: String = run {
    val nums = providers.gradleProperty("rs.version").get().split('.').map { it.toInt() }.toMutableList()
    providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.let { nums[2] = it.toInt() }
    nums.joinToString(".")
}
val appVersionCode: Int = appVersion.split('.').map { it.toInt() }.let { it[0] * 1_000_000 + it[1] * 10_000 + it[2] }

// Подпись: постоянный ключ из переменных окружения, иначе — отладочный ключ.
val keystore: String? = providers.environmentVariable("ANDROID_KEYSTORE_FILE").orNull

android {
    namespace = "io.github.akuraneee.reschools"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.akuraneee.reschools"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }

    signingConfigs {
        create("release") {
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystore != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
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

    testOptions {
        unitTests {
            // Compose-тесты под Robolectric: рендерят экраны в PNG (app/build/screenshots)
            isIncludeAndroidResources = true
        }
    }

    lint {
        // Сборка APK не должна падать из-за предупреждений линтера о версиях бета-библиотек.
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    // BOM тянет material3 1.4.0, в котором нет API Material 3 Expressive, — версия задана явно
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.navigation.suite)
    implementation(libs.compose.material3.adaptive)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    compileOnly(libs.jspecify)

    testImplementation(testFixtures(project(":core")))
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    debugImplementation(libs.compose.ui.test.manifest)
}
