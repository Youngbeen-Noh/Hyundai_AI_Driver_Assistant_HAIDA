plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val targetAndroidSdk = 35
val appVersionMajor = 0
val appVersionMinor = 1
val appVersionPatch = 0

android {
    // TODO(공모전 제출 전): 앱 이름이 정해지면 namespace / applicationId 를 바꾼다.
    namespace = "kr.contest.pleosassistant"
    compileSdk = targetAndroidSdk
    // AAOS 표준 Car API(CarPropertyManager). 기능 2 의 변속 단(P단)은 Vehicle SDK 에 없어서, 기능 3 의 와이퍼 · 전조등은 표준 속성이라 이것으로 읽는다.
    useLibrary("android.car")

    defaultConfig {
        applicationId = "kr.contest.pleosassistant"
        // Pleos 가이드: 차량 사이버보안 요건상 minSdk 는 28 이상
        minSdk = 28
        targetSdk = targetAndroidSdk
        // Pleos 샘플 앱과 같은 규칙: targetSdk * 1,000,000 + major * 10,000 + minor * 100 + patch
        versionCode = targetAndroidSdk * 1_000_000 + appVersionMajor * 10_000 + appVersionMinor * 100 + appVersionPatch
        versionName = "$appVersionMajor.$appVersionMinor.$appVersionPatch"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }
}

dependencies {
    // Pleos Connect SDK (신청 없이 사용 가능)
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.vehicle.data)
    implementation(libs.pleos.navihelper)
    // 음성 출력: 차 안(ON_DEVICE) 모드만 써서 앱 등록 없이 쓴다
    implementation(libs.pleos.tts)
    // 준비 중 SDK — 승인 후 주석 해제
    // implementation(libs.pleos.stt)
    // implementation(libs.pleos.fusedlocation)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    // 단위 테스트에서 org.json(안드로이드 스텁은 동작하지 않음)
    testImplementation(libs.json)
}
