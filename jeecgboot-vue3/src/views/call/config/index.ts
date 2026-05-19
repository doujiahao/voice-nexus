/** 话务模块配置，从当前构建环境读取 */

function _buildWsUrl(): string {
  const envUrl = (import.meta.env.VITE_CALL_WS_URL ?? '').trim()
  if (envUrl) return envUrl
  // 开发和生产环境统一：/call 前缀由 Vite 代理（开发）或 Nginx（生产）转发到后端
  // /call/call/ws → 后端 /jeecg-boot/call/ws
  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${proto}//${location.host}/call/call/ws`
}

export const CALL_WS_CONFIG = {
  wsUrl:                _buildWsUrl(),
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

/** Java WS 推送的实时坐席辅助分析消息 type 字段值 */
export const AGENT_ASSIST_WS_MSG_TYPE = (import.meta.env.VITE_CALL_AGENT_ASSIST_MSG_TYPE ?? 'agent_assist') as string

export const TRANSCRIPT_CONFIG = {
  maxMessages:           500,
  renderLimit:           200,
  autoScrollThreshold:   60,
  pageSize:              40,
}
