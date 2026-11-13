import { test } from "node:test";
import assert from "node:assert/strict";
import { CHAR_LIMIT, MAX_AVOID, cleanLine, koreanDate, parseRequest, shapeBriefing, tokenMatches, userPrompt } from "../src/briefing.ts";

const now = new Date("2026-11-13T16:30:00Z"); // 한국 시간 11월 14일 새벽

test("요청은 분야를 확인하고 들은 제목은 최근 30개만 남긴다", () => {
  assert.equal(typeof parseRequest({ category: "NEWS" }), "string");
  assert.equal(typeof parseRequest(null), "string");
  const avoid = Array.from({ length: 40 }, (_, i) => `제목${i}`);
  const r = parseRequest({ category: "IT", avoid: [...avoid, 3, "  "] });
  assert.ok(typeof r !== "string");
  assert.equal(r.avoid.length, MAX_AVOID);
  assert.equal(r.avoid.at(-1), "제목39");
});

test("오늘 날짜는 한국 시간으로 넣고 들은 제목을 프롬프트에 적는다", () => {
  assert.equal(koreanDate(now), "2026년 11월 14일");
  const p = userPrompt({ category: "GAME", avoid: ["신작 출시"] }, koreanDate(now));
  assert.match(p, /게임/);
  assert.match(p, /- 신작 출시/);
});

const sub = (title: string, lines: string[], extra: Record<string, unknown> = {}) => ({
  title, press: "한빛일보", published_at: "11월 13일", lines, term: null, related: [], ...extra,
});

test("응답 JSON 을 앱 모양으로 바꾸고 각주 · URL · 기호를 지운다", () => {
  const text = JSON.stringify({
    subtopics: [
      sub("AI 요약 확산", ["한빛일보 소식이에요. **AI 요약**이 늘어요 [1].", "자세한 건 https://example.com 참고"], {
        term: { word: "생성형 AI", meaning: "글이나 그림을 새로 만들어 내는 AI 예요." },
        related: ["관련 기사로는, 회사 메신저에도 들어온대요."],
      }),
      sub("빈 소주제", []),
    ],
  });
  const b = shapeBriefing("IT", "검색해 봤어요.\n" + text, now);
  assert.ok(b);
  assert.equal(b.subtopics.length, 1);
  const s = b.subtopics[0];
  assert.deepEqual(s.lines, ["한빛일보 소식이에요. AI 요약이 늘어요 .", "자세한 건 참고"]);
  assert.equal(s.publishedAt, "11월 13일");
  assert.equal(s.term?.word, "생성형 AI");
  assert.equal(s.related.length, 1);
});

test("글자 수 상한을 넘는 뒤 소주제는 빼되 첫 소주제는 남긴다", () => {
  const long = "가".repeat(CHAR_LIMIT - 10);
  const b = shapeBriefing("IT", JSON.stringify({ subtopics: [sub("하나", [long]), sub("둘", ["나".repeat(50)])] }), now);
  assert.equal(b?.subtopics.length, 1);
});

test("JSON 이 아니거나 쓸 소주제가 없으면 null", () => {
  assert.equal(shapeBriefing("IT", "기사를 찾지 못했어요", now), null);
  assert.equal(shapeBriefing("IT", JSON.stringify({ subtopics: [] }), now), null);
});

test("cleanLine 은 공백을 하나로", () => {
  assert.equal(cleanLine("  a \n  b [2, 3] "), "a b");
});

test("앱 토큰은 정확히 같아야 한다", () => {
  assert.ok(tokenMatches("secret-token", "secret-token"));
  assert.ok(!tokenMatches("secret-tokem", "secret-token"));
  assert.ok(!tokenMatches("secret", "secret-token"));
  assert.ok(!tokenMatches(null, "secret-token"));
  assert.ok(!tokenMatches("x", ""));
});
