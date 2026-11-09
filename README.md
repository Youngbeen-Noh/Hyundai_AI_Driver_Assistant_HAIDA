# Pleos AI 비서

Pleos Connect(Android Automotive)용 AI 비서 앱입니다. 현대차·기아 차량용 앱 공모전 출품작이고, 앱 이름은 아직 정하지 않았습니다(계획서 추천안: 옆자리).
차량 데이터와 사용자가 맡긴 기억을 엮어 운전 전·중·후에 필요한 말을 먼저 건넵니다. 기준 문서는 [docs/기능계획서.pdf](docs/기능계획서.pdf)입니다.

| # | 기능 | 시점 | 단계 |
|---|---|---|---|
| 1 | 차량 상태 요약 (공기압 · 경고등 · 오일 · 워셔액 · 잔량) | 탑승 직후 | 1단계 |
| 2 | 소지품 · 동승자 기억 (착석 감지, 음성 등록, 도착 알림, 카풀 경유지) | 출발 · 하차 | 2단계 |
| 3 | 노래 자동 추천 (날씨 · 시간대 · 교통 · 취향 → 분위기 → 5곡) | 주행 중 | 2단계 |
| 4 | 최근 이슈 브리핑 (선택) | 주행 5분 뒤 | 선택 |
| 5 | 주행 리포트 · 안전운전 랭킹 | 하차 후 | 1단계 (A등급 항목) |
| 6 | 위험 구간 지정 (근접 알림) | 구간 접근 | 1단계 (내가 지정) |

## 개발 방식

3명이 각자 AI 에이전트로 자기 파트를 만들고, 서로의 PR 을 실행해 보며 리뷰합니다. main 에는 PR 로만 들어갑니다.

- 흐름과 규칙: [CONTRIBUTING.md](CONTRIBUTING.md)
- 에이전트가 따르는 명령: [CLAUDE.md](CLAUDE.md)
- 자동화(GitHub Actions)

  | 파일 | 하는 일 |
  |---|---|
  | `.github/workflows/ci.yml` | **CI**: PR 과 main 마다 빌드 · 단위 테스트 · lint, APK 첨부 |
  | `.github/workflows/review-request.yml` | PR 본문 검사, 리뷰 준비 완료 시 다음 차례 팀원에게 리뷰 요청 |
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
