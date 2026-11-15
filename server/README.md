# 브리핑 서버 (기능 4)

앱이 분야(예: `IT`)를 보내면 Claude(웹 검색)로 최근 기사를 찾아 해요체 브리핑 대본을 만들어 돌려준다.
Anthropic API 키는 이 서버에만 둔다. 앱은 `APP_TOKEN` 으로만 부른다.

- 주소: `POST /v1/briefing`, 헤더 `x-haida-token: <APP_TOKEN>`, 본문 `{"category":"IT","avoid":["이미 들려준 제목", …]}`
- 응답: `{"category","generatedAt","subtopics":[{"title","press","publishedAt","lines","term","related"}]}` (앱 `ServerBriefings.kt` 와 같은 모양)
- 모델: `claude-opus-5-5`(웹 검색 최대 5번). 한 번에 40~100초, 약 5만 입력 토큰이다.

## 로컬에서 띄우기 (에뮬레이터 확인용)
```bash
cd server
npm ci
cp .dev.vars.example .dev.vars   # ANTHROPIC_API_KEY, APP_TOKEN 을 채운다(git 제외)
npm run dev                      # http://localhost:8787
```
앱 쪽 저장소 루트 `secrets.properties`(git 제외):
```
BRIEFING_SERVER_URL=http://localhost:8787
BRIEFING_APP_TOKEN=<.dev.vars 의 APP_TOKEN 과 같은 값>
```
`./gradlew --stop && ./gradlew clean assembleDebug` 로 다시 빌드한다. 에뮬레이터가 PC 의 8787 포트를 쓰도록 `adb reverse tcp:8787 tcp:8787` 을 실행한다.
http 는 디버그 빌드에서 localhost · 10.0.2.2 만 허용된다.

## 배포 (Cloudflare Workers, 계정 필요)
```bash
npx wrangler login
npx wrangler secret put ANTHROPIC_API_KEY
npx wrangler secret put APP_TOKEN
npm run deploy                   # https://haida-briefing.<계정>.workers.dev
```
배포하면 `BRIEFING_SERVER_URL` 을 그 https 주소로 바꾼다.

## 검사
`npm test`(응답 정리 · 요청 검사 단위 테스트), `npm run typecheck`. CI 의 `server` 작업이 같은 것을 돌린다.
