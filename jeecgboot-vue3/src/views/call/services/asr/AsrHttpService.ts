import type { AsrConfig, AsrResult, Speaker } from "../../types";
import { parseBatchResponse } from "./AsrMessageParser";

type AsrState = "idle" | "active" | "error";

interface AsrServiceHandlers {
  onMessage?: (result: AsrResult) => void;
  onError?: (err: Error) => void;
  onStateChange?: (state: AsrState) => void;
}

interface AsrServiceContext {
  customerSpeaker?: Speaker | null;
}

export class AsrHttpService {
  private _config: AsrConfig;
  private _handlers: AsrServiceHandlers;
  private _state: AsrState = "idle";
  private _controllers = new Set<AbortController>();
  private _customerSpeaker: Speaker | null;
  private _sessionId: string = "";

  constructor(
    config: AsrConfig,
    handlers: AsrServiceHandlers,
    context: AsrServiceContext = {},
  ) {
    this._config = config;
    this._handlers = handlers;
    this._customerSpeaker = context.customerSpeaker ?? null;
  }

  setCustomerSpeaker(speaker: Speaker | null): void {
    this._customerSpeaker = speaker;
  }

  setSessionId(id: string): void {
    this._sessionId = id;
  }

  start(): void {
    if (this._state === "active") return;
    this._setState("active");
    console.info("[ASR] 服务启动, sessionId:", this._sessionId);
  }

  stop(): void {
    for (const ctrl of this._controllers) ctrl.abort();
    this._controllers.clear();
    this._setState("idle");
    console.info("[ASR] 服务停止");
  }

  abort(): void {
    this.stop();
  }

  writeAudio(chunk: ArrayBuffer | Blob): void {
    if (this._state !== "active") return;
    const blob =
      chunk instanceof Blob ? chunk : new Blob([chunk], { type: "audio/wav" });
    this._sendStream(blob);
  }

  private _setState(state: AsrState): void {
    this._state = state;
    this._handlers.onStateChange?.(state);
  }

  private async _sendStream(blob: Blob): Promise<void> {
    const controller = new AbortController();
    this._controllers.add(controller);

    const formData = new FormData();
    formData.append("file", blob, "audio.wav");
    if (this._config.language) {
      formData.append("language", this._config.language);
    }
    formData.append(
      "enable_correction",
      String(this._config.enableCorrection !== false),
    );
    if (this._sessionId) formData.append("call_session_id", this._sessionId);

    const speaker = this._customerSpeaker;
    if (speaker) {
      if (speaker.id) formData.append("speaker_id", speaker.id);
      if (speaker.name) formData.append("speaker_name", speaker.name);
      if (speaker.role) formData.append("speaker_role", speaker.role);
    }

    const timeoutId = setTimeout(
      () => controller.abort(),
      this._config.timeoutMs,
    );

    try {
      const response = await fetch(
        `${this._config.baseUrl}/api/v1/asr/transcribe`,
        { method: "POST", body: formData, signal: controller.signal },
      );
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }
      await this._readJson(response);
    } catch (err: any) {
      if (err.name !== "AbortError") {
        console.error("[ASR] 请求失败:", err.message);
        this._handlers.onError?.(err);
        this._setState("error");
      }
    } finally {
      clearTimeout(timeoutId);
      this._controllers.delete(controller);
    }
  }

  private async _readJson(response: Response): Promise<void> {
    const raw = await response.json();
    console.log("[ASR] JSON raw:", raw);

    if (
      raw &&
      typeof raw === "object" &&
      raw.detail &&
      !raw.data &&
      !raw.text &&
      !raw.raw_text
    ) {
      const msg =
        typeof raw.detail === "string"
          ? raw.detail
          : JSON.stringify(raw.detail);
      console.error("[ASR] 服务端错误:", msg);
      this._handlers.onError?.(new Error(msg));
      return;
    }

    const result = parseBatchResponse(raw);
    if (result) {
      console.info(
        `[ASR] raw_text: "${result.text}" speaker="${result.speakerName}"(${result.role})`,
      );
      this._handlers.onMessage?.(result);
    } else {
      console.warn("[ASR] 响应解析无文字内容，原始数据:", raw);
    }
  }
}
