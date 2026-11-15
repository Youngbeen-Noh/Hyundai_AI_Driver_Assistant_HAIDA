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

## 심사위원 확인 가이드 (약 15분)

아래 순서대로 따라 하면 제출한 APK 의 기능을 모두 볼 수 있습니다. 모든 단계는 이 APK(`apk/AISL_HAIDA_release.apk`)로 Pleos Connect 에뮬레이터 v2.1.0 에서 직접 확인했습니다.

### 0. 실행 방법 (설치 · 준비, 한 번만)
1. Pleos Connect 에뮬레이터를 **Cold boot** 로 켭니다. 소리를 켜 두면 음성 안내를 들을 수 있습니다.
2. APK 를 내려받아 설치하고 실행합니다. 에뮬레이터 화면에 APK 파일을 끌어다 놓아도 설치됩니다.

   ```bash
   adb install -r AISL_HAIDA_release.apk
   adb shell am start --user current -n kr.contest.pleosassistant/.MainActivity
   ```
3. 위치 권한 창(**"HAIDA에서 이 기기의 위치 정보에 …"**)에서 **'앱 사용 중에만 허용'** 을 누릅니다.
4. Virtual Console 을 엽니다. 브라우저 창은 확인이 끝날 때까지 **열어 둡니다**(닫으면 아래 속도 명령이 듣지 않습니다).

   ```bash
   adb forward tcp:8088 tcp:8088
   ```
   → 브라우저에서 `http://127.0.0.1:8088` 을 엽니다.

**테스트 환경과 필요한 설정**
- 앱 왼쪽 메뉴는 6개입니다: **차량 · 동승 · 음악 · 구간 · 리포트 · 브리핑**.
- Virtual Console 에서 쓰는 곳은 두 군데입니다.
  - 화면 오른쪽의 **P R N D** 버튼(기어)
  - 왼쪽 아래 **TEST CONSOLE → Emulation Controls**(배터리 · 좌석 등 신호 목록)
- **속도**는 아래 한 줄로 바꿉니다(Windows PowerShell 은 `curl` 대신 `curl.exe`). Virtual Console 의 ACCEL 버튼은 아주 천천히(약 2km/h) 움직입니다.

  ```bash
  curl -X PUT "http://127.0.0.1:8088/vehicle/controls?speed=30&accelerator=0&brake=0"
  ```
- **위치**는 `adb emu geo fix <경도> <위도>` 로 바꿉니다.
- **D 로 달리는 동안에는 Pleos 가 앱 화면을 "주행 중에는 사용할 수 없습니다"로 가립니다.** HAIDA 는 주행 중에 음성으로만 안내하고, 화면은 정차(P)하면 다시 보입니다. 화면 없이 음성 문장을 보려면 `adb logcat -s PleosSpeaker` 를 켜 두세요(`말함: …` 줄).

### 1. 차량 상태 점검 (기능 1) — 탑승 직후
1. 앱을 열면 **'차량'** 화면에 "오늘 차 상태 좋아요."와 배터리 · 누적 주행거리가 보입니다.
2. Emulation Controls → **Battery SOC** 를 **15** 로 바꿉니다.
   → 다시 열지 않아도 '주의'(노란색)로 바뀌고, "배터리가 15% 남았어요. 가까운 충전소에 들르세요."가 보입니다.
3. **Battery SOC** 를 **8** 로 바꿉니다.
   → '즉시 조치'(빨간색), "배터리가 8% 남았어요. 바로 충전하세요."가 보입니다.
4. **Odometer** 를 지금보다 큰 값(예: 23456)으로 바꾸면 누적 주행거리가 "23,456 km" 로 바뀝니다.
5. 다음 단계를 위해 **Battery SOC** 를 **80** 으로 돌려 둡니다.

### 2. 동승자 · 소지품 기억 (기능 2) — 출발 · 하차
1. 왼쪽 메뉴 **'동승'** 을 엽니다.
2. Emulation Controls → **Seat Occupancy** 에서 **Passenger → Occupied** 로 바꿉니다. 뒷좌석이 Occupied 로 시작했다면 Empty 로 맞춰 두면 보기 쉽습니다.
   → 조수석 이름표가 "누구세요?"로 바뀌고, 오른쪽에 "누가 타셨어요?"와 **손님 · 새 사람** 카드가 뜹니다.
3. **'손님'** 을 누릅니다. → 조수석이 "동승자"가 됩니다.
4. 소지품 예시 **"트렁크에 우산 넣었어"** 를 누르고 **'기억하기'** 를 누릅니다.
   → '기억 중인 소지품'에 "우산 / 트렁크 · 도착 시 알림"이 생기고, 차 그림의 트렁크에 표시가 붙습니다.
5. 왼쪽 메뉴 **'차량'** 으로 옮긴 뒤 기어를 **D → P** 로 바꿉니다.
   → 화면 아래 비서 패널에 **"도착 · 트렁크에 둔 우산 챙기세요."** 와 [다 챙겼어요] [나중에] 가 뜹니다.
6. **'다 챙겼어요'** 를 누르면 우산이 목록에서 빠집니다.

### 3. 상황 맞춤 노래 추천 (기능 3) — 주행 중
1. 왼쪽 메뉴 **'음악'** 을 엽니다. 처음에는 앱에 든 데모 곡 18곡을 분석하느라 "곡 분위기 분석 중 n/18"이 잠깐 보입니다.
   → 시간대에 맞춰 분위기(예: 아침이면 "상쾌한 출발 · 아침 출발이라 골랐어요")와 지금 곡 · 다음 곡 4개, 곡 분석 결과(BPM · 장조/단조 · 밝기)가 보입니다.
2. 기어를 **D** 로 바꾸면 노래가 저절로 재생됩니다.
3. 위쪽 분위기 칩에서 **'촉촉한 빗길'** 을 누릅니다.
   → 곡이 빗길 곡(느리고 어두운 소리)으로 바뀌고 머리글이 "직접 고른 분위기"가 됩니다. **'자동'** 을 누르면 다시 상황에 맞춰 고릅니다.
4. '다음' · '저장'으로 건너뛰기 · 저장을 하면, 같은 가수 · 분위기의 곡 순서가 앞뒤로 바뀝니다(취향 학습).

> 실제 차에서는 와이퍼 · 전조등 신호로 분위기를 바꿉니다. 에뮬레이터는 이 두 신호를 앱에 읽기 권한을 주지 않아서 여기서는 칩으로 확인합니다.
> 스포티파이 연동은 Client ID 를 넣어 빌드해야 켜집니다. 제출한 APK 는 앱에 든 데모 곡으로 동작합니다.

### 4. 최근 이슈 브리핑 (기능 4) — 주행 5분 뒤
1. 기어를 **P** 로 두고 왼쪽 메뉴 **'브리핑'** 을 엽니다.
   → "목적지 없이 5분 달리면 브리핑을 시작해요", 관심 분야 칩, 준비된 브리핑이 보입니다.
2. 5분을 기다리는 대신 **'지금 듣기'** 를 누릅니다.
   → "IT·테크 브리핑이에요. 소식 3개를 들려 드릴게요."를 말하고, 화면에 소식 1/3 · 제목 · 언론사와 읽는 문장을 따라가는 자막이 보입니다.
3. **'용어 설명'** · **'더 자세히'** · **'다음 브리핑'**(IT·테크 → 여행 → 게임 순)을 눌러 봅니다.
4. 오른쪽 위 **'멈추기'** 로 끝냅니다.

> 제출한 APK 의 브리핑은 앱에 든 시연용 기사로 동작합니다.

### 5. 위험 구간 안내 (기능 6) — 구간 접근
1. 기어 **P** 에서 위치를 정하고 왼쪽 메뉴 **'구간'** 을 엽니다.

   ```bash
   adb emu geo fix 126.9780 37.5665
   ```
2. **'이 위치를 위험 구간으로 저장'** 을 누릅니다. (반경 200m · 알림 시간 '항상'이 기본값입니다)
3. 기어를 **D** 로 바꾸고, 구간 밖으로 갔다가 속도를 30 으로 올린 뒤 구간 안쪽으로 들어옵니다.

   ```bash
   adb emu geo fix 126.9780 37.5755
   curl -X PUT "http://127.0.0.1:8088/vehicle/controls?speed=30&accelerator=0&brake=0"
   adb emu geo fix 126.9780 37.5674
   ```
   → **"200m 앞 위험 구간 1이에요. 보행자 조심하세요."** 라고 말합니다(logcat `PleosSpeaker: 말함: …`).

### 6. 하차 후 주행 리포트 (기능 5) — 하차 후
5번에 이어서 합니다(이미 D · 30km/h 로 달리는 중).
1. 그대로 **30초 넘게** 둡니다. 30초 이상 움직이거나 50m 이상 가야 기록됩니다.
2. 속도를 0 으로 내리고 기어를 **P** 로 바꿉니다.

   ```bash
   curl -X PUT "http://127.0.0.1:8088/vehicle/controls?speed=0&accelerator=0&brake=0"
   ```
3. 왼쪽 메뉴 **'리포트'** 를 엽니다.
   → 100점 만점 점수(안전 · 연비), "가장 크게 깎인 장면"(예: 0 → 30km/h 로 한 번에 올려 **급가속 1회**), 주행 거리 · 움직인 시간 · 정속 주행 비율이 보입니다.

### 처음부터 다시 보려면
앱에 저장된 기억(소지품 · 위험 구간 · 리포트 · 취향)을 모두 지웁니다.

```bash
adb shell pm clear --user current kr.contest.pleosassistant
```

## release APK 다시 만들기 (팀 내부)
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
