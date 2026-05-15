export type AgentStatus = 'idle' | 'on_call' | 'ringing' | 'wrap_up' | 'busy' | 'offline'
export type AsrStateEnum = 'idle' | 'active' | 'error'
export type WsStateEnum = 'idle' | 'connecting' | 'active' | 'reconnecting' | 'error'
export type CallPhase = 'idle' | 'active' | 'reviewing'

export interface AgentInfo {
  name: string
  role: string
  id: string
  avatarChar: string
}

export interface IncomingCallData {
  callId: string
  phone: string
  callerName: string
  fsCallId: string
  location?: string
}

export interface Speaker {
  id: string
  name: string
  role: 'customer' | 'agent'
  phone?: string
}

export interface ChatMessage {
  role: 'agent' | 'customer'
  time: string
  content: string
  name?: string
  intent?: string
  audioUrl?: string
  durationMs?: number
}

export interface IntentResult {
  label: string
  confidence?: number
  [key: string]: unknown
}

export interface CallRecord {
  call_session_id: string
  phone: string
  customer_name: string
  date: string
  note: string
  duration_sec: number
  active: boolean
}

export interface CallListItem {
  call_session_id: string
  started_at: string
  ended_at: string | null
  duration_ms: number
  phone: string
  customer_name: string
  agent_id: string
  agent_name: string
  status: string
  turn_count: number
  summary_short: string
}

export interface CallTurnItem {
  call_session_id: string
  turn_id: string
  speaker_role: 'customer' | 'agent'
  speaker_id: string
  speaker_name: string
  raw_text: string
  corrected_text: string
  language: string
  method: string
  duration_ms: number
  audio_file: string
  audio_path: string
  audio_url: string
  intent: IntentResult | null
  keywords: string[]
  entities: Record<string, string>
  emotion: string | null
  summary: string
  nlp_enabled: boolean
  ts: string
}

export interface CallDetail {
  call_session_id: string
  phone: string
  customer_name: string
  fs_call_id: string
  agent_id: string
  agent_name: string
  started_at: string
  ended_at: string | null
  duration_sec: number
  status: string
  turn_count: number
  speaker_seq: { customer: number; agent: number }
  summary: {
    call_session_id: string
    customer_intent: string | null
    keywords: string[]
    entities: Record<string, string>
    summary: string
    conclusion: string
    nlp_enabled: boolean
    updated_at: string
  }
  record_dir?: string
  remark?: string
}

export interface SentimentItem {
  label: string
  emoji: string
  value: number
  color: string
}

export interface KeywordItem {
  word: string
  size: number
}

export interface AnalysisResult {
  sentiments: SentimentItem[]
  intents: string[]
  keywords: KeywordItem[]
  suggestions: string[]
}

export interface AiConfig {
  baseUrl: string
  apiKey: string
  model: string
  timeoutMs: number
}

export interface AsrConfig {
  baseUrl: string
  language?: string
  enableCorrection?: boolean
  timeoutMs: number
}

export interface AsrResult {
  type: string
  text: string
  role: 'agent' | 'customer'
  speakerId?: string
  speakerName?: string
  turnId?: string
  audioUrl?: string
  audioPath?: string
  durationMs?: number
  method?: string
  intent?: IntentResult
  timestamp: number
}

export interface TranscriptConfig {
  maxMessages: number
  renderLimit: number
  autoScrollThreshold: number
  pageSize: number
}
