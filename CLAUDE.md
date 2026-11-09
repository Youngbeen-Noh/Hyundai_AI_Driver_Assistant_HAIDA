# 에이전트 작업 규칙

이 저장소는 3명이 각자 AI 에이전트로 개발하고 서로의 PR 을 실행해 보며 리뷰한다.
사람용 전체 설명: @CONTRIBUTING.md  ·  요구사항 기준: `docs/기능계획서.pdf`
개인 설정은 `CLAUDE.local.md` (GitHub 아이디, 자동 전송 · 자동 제출). 파일이 없거나 값이 없으면 둘 다 "끄기"로 본다.

## 공통
- 빌드에는 JDK 17 이 필요하다. 명령: `./gradlew assembleDebug testDebugUnitTest lintDebug`
- 에뮬레이터: `tools/start-emulator.ps1` (Cold boot), Virtual Console: `tools/open-virtual-console.ps1`
- 절대 하지 않는다: main 에 커밋 · push / 남의 PR 브랜치에 push / `--no-verify` / 리뷰가 시작된 브랜치에 force push /
  Pleos SDK AAR 디컴파일(javap 포함, 라이선스 3.4) / 비밀 값 커밋 / 승인과 CI 통과 없이 merge
- 사람에게 확인받는 지점(자동 설정이 꺼져 있을 때): 작성자 4번 · 7번, 리뷰어 6번. 그 밖에는 멈추지 않고 진행한다.

## 작성자 모드 — 사람이 "○○ 구현해줘"
1. 최신화: `git fetch origin && git switch main && git pull --ff-only && git switch -c <아이디>/<fN>-<설명>`
   이미 그 작업 브랜치가 있으면 `git switch <브랜치> && git merge origin/main`.
2. 지시를 **요구사항**과 **완료 조건**(실행해서 참/거짓을 가릴 수 있는 동작, 체크박스)으로 정리하고 확인 방법(에뮬레이터 · Virtual Console 순서)을 쓴다. 지시가 모호하면 이 단계에서 묻는다.
3. 구현한다. 차량 신호에 의존하지 않는 로직은 단위 테스트를 쓴다. 로컬 빌드 명령이 통과해야 한다.
4. 에뮬레이터에서 완료 조건을 확인하고 결과(통과 · 실패 · 실행 못 함)를 사람에게 보고한다.
5. 커밋 · push 후 초안 PR: `git push -u origin HEAD` → `.github/pull_request_template.md` 를 채운 파일로
   `gh pr create --draft --title "[기능N] 한 일" --body-file <파일>`
6. CI 확인: `gh pr checks <N> --watch`. `build` 와 `pr-body` 가 모두 통과해야 한다. 실패하면 고쳐서 push.
7. 리뷰 요청: 자동 전송이 꺼져 있으면 사람에게 확인받은 뒤 `gh pr ready <N>` (Actions 가 리뷰어를 지정한다).
8. 피드백이 오면: `gh pr view <N> --comments` 와 `gh api repos/{owner}/{repo}/pulls/<N>/comments` 로 리뷰를 읽고,
   같은 브랜치에 고쳐 push → PR 에 "반영 내역" 댓글(지적별로 무엇을 바꿨는지) → 6번 → `gh pr edit <N> --add-reviewer <리뷰어>`.

## 리뷰어 모드 — 사람이 "리뷰해줘" / "PR N 리뷰"
1. 대상: `gh pr list --search "review-requested:@me"` (번호를 받았으면 그 PR).
2. 받기: `git fetch origin && gh pr checkout <N>`
3. 읽기: `gh pr view <N>` 의 요구사항 · 완료 조건 · 확인 방법, `gh pr checks <N>`. CI 가 실패 중이면 실행하지 말고 바로 Request changes("CI 실패: …").
4. 실행: 빌드 → `adb install -r app/build/outputs/apk/debug/app-debug.apk` → 확인 방법대로 완료 조건을 **하나씩** 실행한다.
   조건마다 통과 · 실패 · 실행 못 함과 근거(화면 · logcat)를 남긴다. 실행하지 못한 조건을 통과로 치지 않는다.
5. 코드: `gh pr diff <N>` — 요구사항 밖 변경, 다른 파트 침범, 명백한 버그, 비밀 값, 테스트 누락.
6. 판정 초안을 사람에게 보여준다(자동 제출이 켜져 있으면 생략).
7. 제출
   - 모두 통과: `gh pr review <N> --approve --body "<조건별 결과>"` → `gh pr merge <N> --squash --delete-branch`.
     main 보다 뒤처졌으면 먼저 `gh pr update-branch <N>` 후 CI 통과를 기다린다. 충돌이면 merge 하지 말고 작성자에게 Request changes.
   - 하나라도 실패: `gh pr review <N> --request-changes --body-file <파일>` (형식은 CONTRIBUTING.md 4번).
   - 리뷰어는 작성자 브랜치에 커밋하지 않는다.
8. 정리: `git switch main && git pull --ff-only`
