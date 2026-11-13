// 기능 4 · 최근 이슈 브리핑 서버의 순수 로직(SDK · 네트워크 없음). 단위 테스트: test/briefing.test.ts
// 앱(BriefingJson.kt)과 주고받는 모양이 바뀌면 둘 다 고친다.

/** 앱의 Category 이름 → 화면 이름 · 검색 힌트 */
export const CATEGORIES = {
  IT: { label: "IT·테크", hint: "IT, 테크, 스마트폰, AI 서비스, 반도체, 보안" },
  GAME: { label: "게임", hint: "게임 신작, 게임 업계, e스포츠, 콘솔·모바일 게임" },
  TRAVEL: { label: "여행", hint: "국내외 여행, 항공, 관광지, 축제, 여행 제도 변화" },
  ECONOMY: { label: "경제", hint: "경제, 금리, 물가, 부동산, 증시 흐름, 산업" },
  SPORTS: { label: "스포츠", hint: "야구, 축구, 농구, 배구, 국가대표, 해외 리그 한국 선수" },
  CULTURE: { label: "문화", hint: "영화, 드라마, 음악, 공연, 전시, 도서" },
} as const;

export type CategoryName = keyof typeof CATEGORIES;

export interface BriefingRequest {
  category: CategoryName;
  /** 이미 들려준 소주제 제목(같은 이슈를 다시 고르지 않게). 최대 [MAX_AVOID]개 */
  avoid: string[];
}

export interface Subtopic {
  title: string;
  press: string;
  publishedAt: string;
  lines: string[];
  term: { word: string; meaning: string } | null;
  related: string[];
}

export interface BriefingResponse {
  category: CategoryName;
  generatedAt: string;
  subtopics: Subtopic[];
}

export const MAX_AVOID = 30;
export const MAX_SUBTOPICS = 4;
export const MAX_LINES = 3;
export const MAX_RELATED = 2;
/** 앱의 SCRIPT_CHAR_LIMIT(1,200자, 여는 말 포함)보다 조금 적게. 넘치면 뒤 소주제부터 뺀다 */
export const CHAR_LIMIT = 1100;

/** 요청 본문 검사. 잘못됐으면 오류 문장 */
export function parseRequest(body: unknown): BriefingRequest | string {
  if (typeof body !== "object" || body === null) return "본문이 JSON 객체가 아니에요";
  const { category, avoid } = body as Record<string, unknown>;
  if (typeof category !== "string" || !(category in CATEGORIES)) return "category 가 올바르지 않아요";
  const list = Array.isArray(avoid) ? avoid : [];
  const titles = list
    .filter((t): t is string => typeof t === "string")
    .map((t) => t.trim().slice(0, 80))
    .filter((t) => t.length > 0)
    .slice(-MAX_AVOID);
  return { category: category as CategoryName, avoid: titles };
}

/** 한국 시간 오늘 날짜(예: 2026년 11월 14일) */
export function koreanDate(now: Date): string {
  const kst = new Date(now.getTime() + 9 * 60 * 60 * 1000);
  return `${kst.getUTCFullYear()}년 ${kst.getUTCMonth() + 1}월 ${kst.getUTCDate()}일`;
}

export const SYSTEM_PROMPT = `너는 차 안 AI 비서의 '최근 이슈 브리핑' 대본 작가다. 운전자가 화면을 보지 않고 귀로만 듣는다.
web_search 로 최근 기사를 찾아 대주제 하나에 대한 소주제 3~4개짜리 브리핑을 만든다.

기사 고르기
- 최근 3일 안에 한국 언론이 보도한 기사를 쓴다. 3개가 안 되면 최근 7일까지 넓힌다. 날짜를 확인할 수 없는 기사는 쓰지 않는다.
- 소주제는 꼭 3개 이상(최대 4개) 만든다. 한 경기 · 한 발표를 여러 소주제로 쪼개지 않는다.
- subtopics 를 빈 배열로 내지 않는다. 검색어를 바꿔 다시 찾아서라도 3개를 채운다.
- 소주제끼리는 서로 다른 이슈여야 한다. '이미 들려준 소주제'와 같은 이슈는 고르지 않는다.
- 사고 · 범죄 · 재난을 자극적으로 다루거나, 정치적으로 한쪽을 평가하는 내용은 피한다.

대본 쓰기
- lines: 해요체 2~3문장. 첫 문장은 "(언론사) 소식이에요." 로 시작한다. 한 문장은 60자 안팎으로 짧게.
- 기사 문장을 그대로 옮기지 말고 네 말로 짧게 요약한다. 기사에 있는 사실만 쓰고 추측하지 않는다.
- 귀로 듣기 쉽게 쓴다. 괄호 · 기호 · 영문 약어 나열 · URL · 각주 번호를 쓰지 않는다. 큰 숫자는 "3조 원"처럼 읽기 쉽게.
- title: 화면에 보일 짧은 제목(30자 안팎). press: 언론사 이름. published_at: "11월 13일"처럼 월 · 일.
- term: 운전자가 모를 만한 용어가 있으면 하나만, word 와 해요체 한 문장 풀이(meaning, "~예요."로 끝남). 없으면 null.
- related: 같은 이슈를 다룬 다른 기사가 있으면 그 내용을 해요체 1~2문장으로. 첫 문장은 "관련 기사로는, " 으로 시작. 없으면 빈 배열.
- 모든 소주제 lines 를 합쳐 1,000자를 넘기지 않는다.`;

export function userPrompt(req: BriefingRequest, today: string): string {
  const c = CATEGORIES[req.category];
  const avoid = req.avoid.length > 0 ? req.avoid.map((t) => `- ${t}`).join("\n") : "(없음)";
  return `오늘은 ${today}이다.
대주제: ${c.label} (예: ${c.hint})
이미 들려준 소주제:
${avoid}

웹 검색으로 기사를 찾은 뒤, 정해진 JSON 형식으로만 답한다.`;
}

/** Claude 가 돌려줄 JSON 형식(structured outputs) */
export const OUTPUT_SCHEMA = {
  type: "object",
  properties: {
    subtopics: {
      type: "array",
      items: {
        type: "object",
        properties: {
          title: { type: "string" },
          press: { type: "string" },
          published_at: { type: "string" },
          lines: { type: "array", items: { type: "string" } },
          term: {
            anyOf: [
              {
                type: "object",
                properties: { word: { type: "string" }, meaning: { type: "string" } },
                required: ["word", "meaning"],
                additionalProperties: false,
              },
              { type: "null" },
            ],
          },
          related: { type: "array", items: { type: "string" } },
        },
        required: ["title", "press", "published_at", "lines", "term", "related"],
        additionalProperties: false,
      },
    },
  },
  required: ["subtopics"],
  additionalProperties: false,
} as const;

/** 귀로 듣는 문장에서 각주 · URL · 마크다운 기호를 지우고 공백을 정리한다 */
export function cleanLine(s: string): string {
  return s
    .replace(/https?:\/\/\S+/g, "")
    .replace(/\[\d+(?:[,\s]*\d+)*\]/g, "")
    .replace(/[*_`#>]/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

/**
 * Claude 응답 텍스트(JSON) → 앱에 보낼 브리핑. 빈 소주제는 빼고, 개수 · 글자 수 상한을 맞춘다.
 * 쓸 소주제가 하나도 없으면 null.
 */
export function shapeBriefing(category: CategoryName, text: string, now: Date): BriefingResponse | null {
  const raw = parseJsonObject(text);
  const items = Array.isArray(raw?.subtopics) ? (raw.subtopics as unknown[]) : [];
  const subtopics: Subtopic[] = [];
  let total = 0;
  for (const item of items) {
    if (subtopics.length >= MAX_SUBTOPICS) break;
    if (typeof item !== "object" || item === null) continue;
    const o = item as Record<string, unknown>;
    const lines = strings(o.lines).map(cleanLine).filter((l) => l.length > 0).slice(0, MAX_LINES);
    const title = typeof o.title === "string" ? cleanLine(o.title) : "";
    const press = typeof o.press === "string" ? cleanLine(o.press) : "";
    if (lines.length === 0 || title.length === 0 || press.length === 0) continue;
    const size = lines.reduce((n, l) => n + l.length, 0);
    if (subtopics.length > 0 && total + size > CHAR_LIMIT) break;
    total += size;
    const t = o.term as Record<string, unknown> | null | undefined;
    const term =
      t && typeof t.word === "string" && typeof t.meaning === "string" && t.word.trim() && t.meaning.trim()
        ? { word: cleanLine(t.word), meaning: cleanLine(t.meaning) }
        : null;
    subtopics.push({
      title,
      press,
      publishedAt: typeof o.published_at === "string" ? cleanLine(o.published_at) : "",
      lines,
      term,
      related: strings(o.related).map(cleanLine).filter((l) => l.length > 0).slice(0, MAX_RELATED),
    });
  }
  if (subtopics.length === 0) return null;
  return { category, generatedAt: now.toISOString(), subtopics };
}

function strings(v: unknown): string[] {
  return Array.isArray(v) ? v.filter((x): x is string => typeof x === "string") : [];
}

/** 텍스트에서 JSON 객체를 읽는다. 앞뒤에 다른 말이 붙어 있으면 가장 바깥 { … } 만 */
function parseJsonObject(text: string): Record<string, unknown> | null {
  const attempts = [text.trim()];
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start >= 0 && end > start) attempts.push(text.slice(start, end + 1));
  for (const a of attempts) {
    try {
      const v = JSON.parse(a);
      if (typeof v === "object" && v !== null && !Array.isArray(v)) return v as Record<string, unknown>;
    } catch {
      // 다음 방법
    }
  }
  return null;
}

/** 앱 토큰 비교(길이가 달라도 시간이 크게 다르지 않게) */
export function tokenMatches(given: string | null, expected: string): boolean {
  if (!given || !expected) return false;
  let diff = given.length ^ expected.length;
  for (let i = 0; i < expected.length; i++) diff |= (given.charCodeAt(i % given.length) || 0) ^ expected.charCodeAt(i);
  return diff === 0;
}
