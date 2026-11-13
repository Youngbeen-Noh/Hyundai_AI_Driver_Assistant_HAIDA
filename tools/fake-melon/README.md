# 테스트용 가짜 멜론 (`:fake-melon`)

에뮬레이터에는 멜론이 없어서 기능 3의 음악 앱 연결을 확인하려고 만든 미디어 앱이다.

- 앱 이름: "멜론 (테스트)"
- 패키지: `kr.contest.pleosassistant.fakemelon` (진짜 멜론과 다르다)

우리 앱은 패키지 이름이 달라도 이름에 '멜론'이 들어간 미디어 앱을 멜론으로 찾는다. 그래서 이 앱에 붙는다.

- 소리는 내지 않는다.
- 받은 요청과 '지금 곡'만 바꾸고, logcat `FakeMelon` 에 남긴다.
- 차량에 올리는 앱이 아니다. 시연할 때는 지운다.

## 설치 · 동작 방식 바꾸기

```bash
./gradlew :fake-melon:assembleDebug
adb install -r tools/fake-melon/build/outputs/apk/debug/fake-melon-debug.apk
adb shell am broadcast --user 10 -n kr.contest.pleosassistant.fakemelon/.ModeReceiver --es mode search
```

`--es mode` 값에 따라 동작이 달라진다.

| mode | 가짜 멜론의 동작 | 우리 앱이 하는 일 |
|---|---|---|
| `search` (기본) | '검색해서 재생'을 받아 바로 튼다 | `playFromSearch` 한 번으로 재생 |
| `browse` | '검색해서 재생'을 지원하지 않는다고 알린다 | 앱 안 검색 → 제목이 맞는 곡을 `playFromMediaId` |
| `missing` | 앱 안 검색에 찾는 곡이 없다 | "멜론에서 '…' 곡을 찾지 못했어요" 를 보여 주고 엉뚱한 곡은 틀지 않는다 |

## 지우기

```bash
adb uninstall kr.contest.pleosassistant.fakemelon
```
