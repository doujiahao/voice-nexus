/** 话务模块配置，从 .env.development 读取 */

export const CALL_WS_CONFIG = {
  wsUrl:                (import.meta.env.VITE_CALL_WS_URL ?? '') as string,
  heartbeatIntervalMs:  20000,
  heartbeatTimeoutMs:   60000,
  reconnectDelayMs:     1000,
  maxReconnectDelayMs:  30000,
  maxReconnectAttempts: 10,
  authMode: 'query' as 'query' | 'message',
}

export const CALL_API_BASE = (import.meta.env.VITE_CALL_API_BASE ?? '') as string

/** Java WS 推送的实时转写消息 type 字段值 */
export const ASR_WS_MSG_TYPE = (import.meta.env.VITE_CALL_ASR_MSG_TYPE ?? 'asr_result') as string

/** AI 分析服务配置 */
export const AI_CONFIG = {
  baseUrl:   (import.meta.env.VITE_AI_BASE_URL   ?? '') as string,
  apiKey:    (import.meta.env.VITE_AI_API_KEY     ?? '') as string,
  model:     (import.meta.env.VITE_AI_MODEL       ?? 'deepseek-chat') as string,
  timeoutMs: Number(import.meta.env.VITE_AI_TIMEOUT_MS ?? 30000),
}

export const TRANSCRIPT_CONFIG = {
  maxMessages:           500,
  renderLimit:           200,
  autoScrollThreshold:   60,
  pageSize:              40,
}
