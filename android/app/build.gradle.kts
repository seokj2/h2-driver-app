plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "kr.bighero.h2.driver"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.bighero.h2.driver"
        minSdk = 28          // 플레오스 Connect 최소 API 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        // 지도 엔진(MapLibre) 네이티브 라이브러리: 에뮬레이터(x86_64)와 실제 차량·휴대폰(arm64)만 넣어 APK 크기를 줄임
        ndk { abiFilters += listOf("x86_64", "arm64-v8a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")   // 시연용. 앱 마켓 제출 때는 별도 서명 키
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests { isIncludeAndroidResources = true; all { it.systemProperty("roborazzi.test.record", "true") } } }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    // 실제 도로 지도(키 없이 쓰는 오픈 지도 엔진). 타일: OpenFreeMap(OpenStreetMap), 경로: assets/routes.json(OSRM)
    implementation("org.maplibre.gl:android-sdk:11.13.5")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // 화면 확인용(에뮬레이터 없이 JVM에서 화면을 그려 PNG로 저장): ./gradlew :app:testDebugUnitTest
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.43.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.43.0")
    implementation("androidx.compose.ui:ui-tooling-preview")
}
