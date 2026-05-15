import type { AsrResult, ChatMessage } from "../../types";

function _mapRole(speakerRole: string): "agent" | "customer" {
  if (speakerRole === "agent") return "agent";
  return "customer";
}

function _stripLangTag(text: string): string {
  return text.replace(/^language\w+/i, "").trim();
}

export function parseBatchResponse(rawJson: unknown): AsrResult | null {
  try {
    const data =
      typeof rawJson === "string" ? JSON.parse(rawJson) : (rawJson as any);
    // 非流式接口：优先取 data.raw_text，回退到其他字段
    const payload = data?.data ?? data;
    const text = _stripLangTag(
      payload?.raw_text ??
        payload?.corrected_text ??
        payload?.corrected ??
        payload?.text ??
        payload?.result ??
        payload?.transcript ??
        "",
    );
    if (!text) return null;
    return {
      type: "final",
      text,
      role: payload?.speaker_role
        ? _mapRole(payload.speaker_role)
        : payload?.speaker === "agent"
          ? "agent"
          : "customer",
      speakerId: payload?.speaker_id,
      speakerName: payload?.speaker_name,
      turnId: payload?.turn_id,
      audioUrl: payload?.audio_url,
      audioPath: payload?.audio_path,
      durationMs: payload?.duration_ms,
      method: payload?.method,
      intent: payload?.intent || undefined,
      timestamp: Date.now(),
    };
  } catch {
    return null;
  }
}

export function asrResultToChatMessage(
  result: AsrResult,
  fallbackName?: string,
): ChatMessage {
  const time = _formatTime(result.timestamp);
  const msg: ChatMessage = {
    role: result.role,
    time,
    content: result.text,
    name:
      result.role === "customer"
        ? result.speakerName || fallbackName || "用户"
        : result.speakerName || "客服",
  };
  if (result.turnId) (msg as any).turnId = result.turnId;
  if (result.audioUrl) (msg as any).audioUrl = result.audioUrl;
  if (result.durationMs) (msg as any).durationMs = result.durationMs;
  if (result.intent) msg.intent = typeof result.intent === 'object' ? result.intent.label : String(result.intent);
  return msg;
}

function _formatTime(ts: number): string {
  const d = new Date(ts);
  return [d.getHours(), d.getMinutes(), d.getSeconds()]
    .map((v) => String(v).padStart(2, "0"))
    .join(":");
}
