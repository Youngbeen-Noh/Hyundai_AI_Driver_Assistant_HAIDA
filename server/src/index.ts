// HAIDA 브리핑 서버(Cloudflare Workers). 앱은 분야만 보내고, 서버가 Claude 웹 검색으로 대본을 만들어 돌려준다.
// API 키는 서버에만 둔다(로컬: .dev.vars, 배포: wrangler secret). 앱은 APP_TOKEN 으로만 부른다.
import Anthropic from "@anthropic-ai/sdk";
import type { BetaMessageParam, BetaContentBlock } from "@anthropic-ai/sdk/resources/beta/messages/messages";
import {
  OUTPUT_SCHEMA,
  SYSTEM_PROMPT,
  koreanDate,
  parseRequest,
  shapeBriefing,
  tokenMatches,
  userPrompt,
} from "./briefing.ts";

export interface Env {
  ANTHROPIC_API_KEY: string;
  APP_TOKEN: string;
  /** 바꾸고 싶을 때만(기본 claude-opus-5-5) */
  MODEL?: string;
}

const DEFAULT_MODEL = "claude-opus-5-5";
/** 검색이 길어져 서버 쪽 반복이 끝나면(pause_turn) 이어서 부르는 최대 횟수 */
const MAX_CONTINUES = 3;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === "/health") return json(200, { ok: true });
    if (url.pathname !== "/v1/briefing" || request.method !== "POST") return json(404, { error: "없는 주소예요" });
    if (!tokenMatches(request.headers.get("x-haida-token"), env.APP_TOKEN ?? "")) return json(401, { error: "앱 토큰이 맞지 않아요" });
    if (!env.ANTHROPIC_API_KEY) return json(503, { error: "서버에 ANTHROPIC_API_KEY 가 없어요" });

    const req = parseRequest(await request.json().catch(() => null));
    if (typeof req === "string") return json(400, { error: req });

    const client = new Anthropic({ apiKey: env.ANTHROPIC_API_KEY, timeout: 150_000, maxRetries: 2 });
    const now = new Date();
    const messages: BetaMessageParam[] = [{ role: "user", content: userPrompt(req, koreanDate(now)) }];

    try {
      for (let i = 0; i <= MAX_CONTINUES; i++) {
        const res = await client.beta.messages.create({
          model: env.MODEL || DEFAULT_MODEL,
          max_tokens: 16000,
          system: SYSTEM_PROMPT,
          messages,
          tools: [
            {
              type: "web_search_20260209",
              name: "web_search",
              max_uses: 5,
              user_location: { type: "approximate", country: "KR", timezone: "Asia/Seoul" },
            },
          ],
          output_config: { effort: "medium", format: { type: "json_schema", schema: OUTPUT_SCHEMA } },
          // 안전 분류기가 거절하면 서버가 알맞은 모델로 다시 돌린다
          betas: ["server-side-fallback-2026-07-01"],
          fallbacks: "default",
        });
        console.log(`claude stop=${res.stop_reason} in=${res.usage.input_tokens} out=${res.usage.output_tokens} search=${res.usage.server_tool_use?.web_search_requests ?? 0}`);

        if (res.stop_reason === "pause_turn") {
          messages.push({ role: "assistant", content: res.content });
          continue;
        }
        if (res.stop_reason === "refusal") return json(502, { error: "브리핑을 만들지 못했어요(거절)" });
        if (res.stop_reason === "max_tokens") return json(502, { error: "브리핑이 너무 길어 잘렸어요" });

        const text = finalText(res.content);
        const briefing = shapeBriefing(req.category, text, now);
        console.log(`${req.category} 응답 ${text.length}자 → 소주제 ${briefing?.subtopics.length ?? 0}개 (원본 ${(text.match(/"lines"/g) ?? []).length}개)`);
        if (!briefing) {
          console.log(`형식 불일치: blocks=${res.content.map((b) => b.type).join(",")} text=${finalText(res.content).slice(0, 300)}`);
          return json(502, { error: "쓸 만한 기사를 찾지 못했어요" });
        }
        return json(200, briefing);
      }
      return json(504, { error: "검색이 너무 오래 걸려요" });
    } catch (e) {
      if (e instanceof Anthropic.RateLimitError) return json(429, { error: "요청이 많아요. 잠시 뒤에 다시" });
      if (e instanceof Anthropic.AuthenticationError) return json(503, { error: "서버의 API 키가 맞지 않아요" });
      if (e instanceof Anthropic.APIError) {
        console.log(`claude 오류 ${e.status}: ${e.message}`);
        return json(502, { error: "Claude 호출에 실패했어요" });
      }
      throw e;
    }
  },
};

/** 마지막 검색 결과 뒤에 온 글(JSON 답). 검색 전에 한 말은 버린다 */
function finalText(content: BetaContentBlock[]): string {
  let lastTool = -1;
  content.forEach((b, i) => {
    if (b.type !== "text" && b.type !== "thinking" && b.type !== "redacted_thinking") lastTool = i;
  });
  return content
    .slice(lastTool + 1)
    .map((b) => (b.type === "text" ? b.text : ""))
    .join("");
}

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}
