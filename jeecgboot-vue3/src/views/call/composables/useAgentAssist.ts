import { ref, readonly } from 'vue'

// ── 类型 ──────────────────────────────────────────────────────────────────────

export interface DecisionSuggestion {
  type: string
  priority: string
  title: string
  reason: string
  suggested_script: string
}

export interface TaskSuggestion {
  task_type: string
  task_title: string
  priority: string
  reason: string
  required_slots: string[]
  missing_slots: string[]
  task_ready: boolean
  suggested_reply: string
  suggested_followup_questions: string[]
}

export interface AgentAssistResult {
  stage_summary: string
  current_intent: string
  intent_confidence: number
  keywords: string[]
  entities: Record<string, string>
  emotion: string
  emotion_trend: string
  collected_slots: Record<string, boolean>
  missing_slots: string[]
  decision_suggestions: DecisionSuggestion[]
  recommended_questions: string[]
  task_suggestion: TaskSuggestion | null
  suggested_reply: string
  suggested_followup_questions: string[]
  handoff_recommended: boolean
  risk_flags: string[]
}

// ── 模块级单例状态 ─────────────────────────────────────────────────────────────

const _result  = ref<AgentAssistResult | null>(null)
const _loading = ref(false)
const _error   = ref<string | null>(null)
const _enabled = ref(true)

// ── composable ────────────────────────────────────────────────────────────────

export function useAgentAssist() {
  function updateFromWs(payload: AgentAssistResult): void {
    if (!_enabled.value) return
    _result.value  = payload
    _error.value   = null
    _loading.value = false
  }

  function reset(): void {
    _result.value  = null
    _error.value   = null
    _loading.value = false
  }

  function setEnabled(val: boolean): void {
    _enabled.value = val
    if (!val) reset()
  }

  return {
    assistResult:  readonly(_result),
    assistLoading: readonly(_loading),
    assistError:   readonly(_error),
    assistEnabled: readonly(_enabled),
    updateFromWs,
    reset,
    setEnabled,
  }
}
