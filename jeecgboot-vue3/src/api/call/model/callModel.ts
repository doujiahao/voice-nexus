export type AgentStatusEnum = 'ONLINE' | 'OFFLINE' | 'REST' | 'RINGING' | 'TALKING' | 'WRAP_UP'

export interface AgentStatusResult {
  status: AgentStatusEnum
  reason?: string
}

export interface CallListResult {
  items: {
    call_session_id: string
    started_at: string
    ended_at: string | null
    duration_ms: number
    phone: string
    customer_name: string
    agent_id: string
    agent_name: string
    direction: string
    status: string
    turn_count: number
    summary_short: string
  }[]
  total: number
  page: number
  page_size: number
}

export interface CallTurnResult {
  items: {
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
    intent: { label: string; confidence?: number; [key: string]: unknown } | null
    keywords: string[]
    entities: Record<string, string>
    emotion: string | null
    summary: string
    nlp_enabled: boolean
    ts: string
  }[]
}

export interface CallDetailResult {
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
  } | null
  record_dir?: string
  remark?: string
}
