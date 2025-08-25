plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.android.system"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.android.system"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Конфигурация подписи
    signingConfigs {
        create("release") {
            // Укажите путь к вашему keystore файлу
            storeFile = file("../keystore.jks")
            storePassword = "123456"
            keyAlias = "key0"
            keyPassword = "123456"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Применяем конфигурацию подписи
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Для отладки тоже можно использовать release-подпись, если нужно
            if (rootProject.extra.has("keystoreProperties")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Задача для копирования APK из app-stub в assets app
tasks.register("copyStubApk", Copy::class) {
    val stubProject = project(":app-stub")
    from(stubProject.layout.buildDirectory.file("outputs/apk/release/system_update.apk"))
    into("src/main/assets")
    rename { "system_update.apk" }

    // Зависимость от сборки app-stub
    dependsOn(":app-stub:assembleRelease")
}

// Обеспечиваем выполнение копирования перед сборкой основного APK
afterEvaluate {
    tasks.named("preBuild") {
        dependsOn("copyStubApk")
    }
}

// Очищаем скопированный APK при clean
tasks.register("cleanAssets", Delete::class) {
    delete("src/main/assets/system_update.apk")
}

tasks.named("clean") {
    dependsOn("cleanAssets")
}
