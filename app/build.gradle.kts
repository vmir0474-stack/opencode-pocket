plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pocket.opencode"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pocket.opencode"
        minSdk = 26
        // targetSdk задаётся на весь пакет, поэтому у приложения один APK.
        // 28 нужен, чтобы Android разрешал execve() для proot и ELF из
        // app-data: с 29 действует W^X, и Ubuntu внутри приложения не
        // запускается. Android 14 принимает targetSdk >= 23, Android 15 —
        // >= 24, так что 28 устанавливается и на них.
        targetSdk = 28
        versionCode = 21
        versionName = "0.8.1"

        // Helio G85 / TECNO LE7n и остальные целевые телефоны — arm64.
        // Остальные ABI не нужны: встроенные rg/pocket-rs собраны только
        // под arm64, а stub-библиотеки только раздувают APK.
        ndk { abiFilters += "arm64-v8a" }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            // Debug-сборка заметно медленнее на старте и больше по размеру.
            // Подписываем тем же debug-ключом, что и раньше, чтобы обновление
            // ставилось поверх без удаления данных.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    testOptions { unitTests.isReturnDefaultValues = true }

    lint {
        // APK распространяется через GitHub, а не Google Play. targetSdk 28
        // выбран осознанно: он нужен для execve() в proot.
        disable += "ExpiredTargetSdkVersion"
    }

    packaging {
        jniLibs {
            // Спайк: нативный бинарник должен попасть в nativeLibraryDir
            // и быть исполняемым, поэтому упаковываем legacy-способом.
            useLegacyPackaging = true
            // librg.so — это ELF-бинарник ripgrep под именем .so; strip
            // ему не нужен и может повредить образ.
            keepDebugSymbols += "**/libocserver.so"
            keepDebugSymbols += "**/librg.so"
            keepDebugSymbols += "**/libpocket_rs.so"
        }
    }

    sourceSets {
        // ripgrep и libpcre2-8.so пакуются как обычные jniLibs.
        getByName("main").jniLibs.srcDir("src/main/tools")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
