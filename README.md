# HAIDA (Hyundai AI Driver Assistant)

**차 상태 · 동승자 기억 · 위험 구간 · 주행 점수를 운전 전 · 중 · 후에 먼저 말해 주는 Pleos Connect 용 AI 비서**

Pleos Connect(Android Automotive)용 AI 비서 앱입니다. 현대자동차그룹 차량용 앱 개발 공모전 출품작입니다(팀 AISL).
차량 데이터와 사용자가 맡긴 기억을 엮어 운전 전·중·후에 필요한 말을 먼저 건넵니다. 기준 문서는 [docs/기능계획서.pdf](docs/기능계획서.pdf)입니다.

| # | 기능 | 시점 | 단계 |
|---|---|---|---|
| 1 | 차량 상태 요약 (공기압 · 경고등 · 오일 · 워셔액 · 잔량) | 탑승 직후 | 1단계 |
| 2 | 소지품 · 동승자 기억 (착석 감지, 음성 등록, 도착 알림, 카풀 경유지) | 출발 · 하차 | 2단계 |
| 3 | 노래 자동 추천 (날씨 · 시간대 · 교통 · 취향 → 분위기 → 5곡) | 주행 중 | 2단계 |
| 4 | 최근 이슈 브리핑 (선택) | 주행 5분 뒤 | 선택 |
| 5 | 주행 리포트 · 안전운전 랭킹 | 하차 후 | 1단계 (A등급 항목) |
| 6 | 위험 구간 지정 (근접 알림) | 구간 접근 | 1단계 (내가 지정) |

## 공모전 제출 정보

| 항목 | 내용 |
|---|---|
| 앱 이름 | HAIDA (팀 AISL) |
| APK | [`apk/AISL_HAIDA_release.apk`](apk/AISL_HAIDA_release.apk) — release 빌드, 서명됨, 패키지 `kr.contest.pleosassistant`, 버전 0.1.0 |
| 에뮬레이터 | Pleos Connect Emulator **v2.1.0** (시스템 이미지 `release_connect_v2.1.0`, Android 14 · API 34, x86_64) |
| 화면 규격 | **2560 × 1440, 160dpi** (가로). 앱은 오른쪽 앱 영역(약 1682 × 1210)에 뜬다 |
| 빌드 환경 | JDK 17 · AGP 8.8.2 · Gradle 8.10.2 · Kotlin 2.0.0 · minSdk 28 · target/compile SDK 35 |

### 실행 방법
1. Pleos Connect 에뮬레이터를 **Cold boot** 로 켠다(`tools/start-emulator.ps1`).
2. APK 를 설치하고 실행한다.

   ```bash
   adb install -r apk/AISL_HAIDA_release.apk
   adb shell am start --user current -n kr.contest.pleosassistant/.MainActivity
   ```
3. 처음 실행하면 나오는 **위치 · 차속 권한을 허용**한다(위험 구간 · 날씨 · 주행 리포트에 필요).
4. 차량 신호는 Virtual Console(`tools/open-virtual-console.ps1`)에서 바꾼다. 위치는 `adb emu geo fix <경도> <위도>`.

### 테스트 환경과 필요한 설정
- 앱 화면은 왼쪽 탭 6개다: 차량 · 동승 · 음악 · 구간 · 리포트 · 브리핑.
- **위험 구간(기능 6):** P 에서 '구간' 탭 → '이 위치를 위험 구간으로 저장' → D 로 바꾸고 구간 밖에서 안으로 위치를 옮기면 화면 경고와 음성 안내가 나온다.
  - 확인한 위치: 저장 `126.9780 37.5665`, 밖 `37.5755`, 안 `37.5674`
- **주행 리포트(기능 5):** D 에서 Virtual Console 로 속도를 올렸다가 0 으로 내리고 P 로 바꾸면 '리포트' 탭에 점수가 뜬다(50m 이상 또는 30초 이상 움직여야 기록).
- **음악(기능 3):** 스포티파이 연동은 빌드할 때 `secrets.properties` 의 `SPOTIFY_CLIENT_ID` 가 있어야 한다. 없으면 앱에 든 데모 곡(`assets/music`)으로 분위기 추천 · 재생을 보여 준다.
- 인터넷 · 위치가 있으면 날씨(Open-Meteo)도 분위기에 쓰고, 없으면 날씨 없이 나머지 신호로 고른다.

### release APK 다시 만들기
`./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk` 를 `apk/AISL_HAIDA_release.apk` 로 복사한다.
제출용 서명 키는 저장소 밖에 두고 `secrets.properties`(git 제외)에 경로와 비밀번호를 적는다: `RELEASE_STORE_FILE` · `RELEASE_STORE_PASSWORD` · `RELEASE_KEY_ALIAS` · `RELEASE_KEY_PASSWORD`.
키가 없으면 debug 키로 서명되어 설치 · 실행은 된다(같은 패키지의 다른 서명 APK 가 깔려 있으면 먼저 지운다).

## 개발 방식

3명이 각자 AI 에이전트로 자기 파트를 만들고, 서로의 PR 을 실행해 보며 리뷰합니다. main 에는 PR 로만 들어갑니다.

- 흐름과 규칙: [CONTRIBUTING.md](CONTRIBUTING.md)
- 에이전트가 따르는 명령: [CLAUDE.md](CLAUDE.md)
- 자동화(GitHub Actions)

  | 파일 | 하는 일 |
  |---|---|
  | `.github/workflows/ci.yml` | **CI**: PR 과 main 마다 빌드 · 단위 테스트 · lint, APK 첨부 |
  | `.github/workflows/review-request.yml` | PR 본문 검사, 리뷰 준비 완료 시 덜 밀린 팀원에게 리뷰 요청(같으면 번갈아) |
  | `.github/workflows/cd.yml` | **CD**: main 이 바뀌면 `latest-main` 릴리스에 APK 갱신 |

## 개발 환경 준비 (팀원 각자 한 번)

1. **JDK 17**. Android Studio 기본 JDK(JBR)는 버전이 달라 빌드가 안 되므로 JDK 17 을 따로 설치하고 `JAVA_HOME` 을 지정합니다.
2. **Android Studio + Android SDK**. `ANDROID_HOME` 을 설정하거나 `local.properties` 에 `sdk.dir` 을 적습니다(이 파일은 git 에 올리지 않습니다).
3. **Pleos Connect 에뮬레이터**. Pleos Playground 개발자 문서의 안내대로 시스템 이미지를 받아 AVD 를 만듭니다(42dot 라이선스 동의 필요).
   - 이미지 위치: `Sdk/system-images/android-34/pleos_car_emulator/x86_64`
   - 외장 GPU 가 권장됩니다. 내장 GPU PC 는 부팅이 수 분 걸리고 불안정할 수 있습니다.
   - 실행은 반드시 Cold boot: `tools/start-emulator.ps1 -Avd <AVD 이름>`
   - 차량 신호 바꾸기(Virtual Console): `tools/open-virtual-console.ps1`
4. **GitHub CLI**: `winget install --id GitHub.cli` → 본인 계정으로 `gh auth login`
5. **저장소 받기와 보호 훅 켜기**

   ```bash
   gh repo clone <owner>/<repo>
   cd <repo>
   git config core.hooksPath .githooks
   ```
6. 저장소 루트에 `CLAUDE.local.md` 를 만들어 내 GitHub 아이디와 자동 승인 여부를 적습니다(CONTRIBUTING.md 의 예시 참고).

## 빌드 · 실행

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

툴체인은 Pleos 공식 샘플 앱과 맞췄습니다: AGP 8.8.2 · Gradle 8.10.2 · Kotlin 2.0.0 · compile/target SDK 35 · minSdk 28.
Pleos SDK: Vehicle 2.3.2, NaviHelper 3.0.5 (TTS · STT · FusedLocation 은 Playground 에서 "준비 중").
