plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dgy.menusuggestion"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dgy.menusuggestion"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                // ── 최적화 플래그 ──────────────────────────────────────────
                // -O3          : 최대 최적화 (기존 "" → Debug -O0 대비 ~100x 향상)
                // -march=...   : ARM NEON + dotprod + i8mm 활성화 (Q8_0 행렬곱 가속)
                // -ffast-math  : 부동소수점 연산 최적화
                // -DNDEBUG     : assert/디버그 코드 제거 (Debug 빌드에서도 강제 적용)
                cppFlags("-O3 -march=armv8.4-a+dotprod+i8mm -fno-finite-math-only -DNDEBUG")
                cFlags  ("-O3 -march=armv8.4-a+dotprod+i8mm -fno-finite-math-only -DNDEBUG")
                arguments(
                    "-DANDROID_STL=c++_shared",
                    "-DCMAKE_BUILD_TYPE=Release"   // 항상 Release로 강제
                )
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a")   // x86_64 제외 → 빌드 시간 단축
        }
    }

    buildTypes {
        debug {
            // Debug 빌드도 네이티브는 Release 최적화로 실행
            externalNativeBuild {
                cmake {
                    cppFlags("-O3 -march=armv8.4-a+dotprod+i8mm -fno-finite-math-only -DNDEBUG")
                    cFlags  ("-O3 -march=armv8.4-a+dotprod+i8mm -fno-finite-math-only -DNDEBUG")
                    arguments(
                        "-DANDROID_STL=c++_shared",
                        "-DCMAKE_BUILD_TYPE=Release"
                    )
                }
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Prevent compressing the model file to speed up build and runtime access
    // aaptOptions {
    //    noCompress += "gguf"
    // }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
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
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}