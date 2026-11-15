import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val targetAndroidSdk = 35
val appVersionMajor = 0
val appVersionMinor = 1
val appVersionPatch = 0

// 비밀 · 개인 값은 저장소 루트 secrets.properties(git 제외)에 둔다. 없으면 빈 값으로 빌드된다(CI 포함)
val secrets = Properties().apply {
    rootProject.file("secrets.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    // 앱 이름은 HAIDA 로 정했다(2026-11-15). 패키지명은 adb 명령 · 디버그 수신기 · 스포티파이 리디렉트가 모두 기대고 있어 그대로 둔다.
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

        // 기능 4: 브리핑 서버(server/) 주소와 앱 토큰. 비어 있으면 시연용 브리핑을 쓴다
        buildConfigField("String", "BRIEFING_SERVER_URL", "\"${secrets.getProperty("BRIEFING_SERVER_URL", "")}\"")
        buildConfigField("String", "BRIEFING_APP_TOKEN", "\"${secrets.getProperty("BRIEFING_APP_TOKEN", "")}\"")
        // 기능 3: 스포티파이 개발자 앱 Client ID(PKCE 라 비밀 값은 아니지만 개인 앱이라 저장소에 두지 않는다)
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"${secrets.getProperty("SPOTIFY_CLIENT_ID", "")}\"")
    }

    // 제출용 release 서명. 키 정보는 secrets.properties 에만 둔다(RELEASE_STORE_FILE · RELEASE_STORE_PASSWORD · RELEASE_KEY_ALIAS · RELEASE_KEY_PASSWORD).
    // 없으면(CI · 다른 팀원) debug 키로 서명해 release 도 설치 · 실행은 되게 한다.
    val releaseStore = secrets.getProperty("RELEASE_STORE_FILE")?.let { rootProject.file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = releaseStore
                storePassword = secrets.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = secrets.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = secrets.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (releaseStore != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
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
        buildConfig = true
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
