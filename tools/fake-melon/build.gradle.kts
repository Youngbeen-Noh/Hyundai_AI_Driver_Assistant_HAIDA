// 테스트용 가짜 '멜론'. 에뮬레이터에 멜론이 없어서, 우리 앱의 음악 앱 연결(기능 3)을 확인하려고 만든 미디어 앱이다.
// 소리는 내지 않고 받은 요청을 logcat(FakeMelon)에 남긴다. 차량에 올리는 앱이 아니다(README 참고).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "kr.contest.pleosassistant.fakemelon"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.contest.pleosassistant.fakemelon"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "test"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // 차량 음악 앱들이 쓰는 MediaBrowserServiceCompat(앱 안 검색 onSearch 가 여기에만 있다)
    implementation(libs.androidx.media)
}
