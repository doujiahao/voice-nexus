import { ref, readonly } from 'vue'
import type { AnalysisResult, CallDetail } from '../types'

const SENTIMENT_TEMPLATE = {
  negative: { label: '负面', emoji: '😠', color: '#ef4444' },
  neutral:  { label: '中性', emoji: '😐', color: '#f59e0b' },
  positive: { label: '正面', emoji: '😊', color: '#22c55e' },
} as const

type SentimentBucket = keyof typeof SENTIMENT_TEMPLATE

const NEGATIVE_EMOTIONS = new Set(['angry', 'sad', 'fear', 'disgust', 'frustrated', 'negative', '负面', '愤怒', '悲伤', '不满', '生气'])
const POSITIVE_EMOTIONS = new Set(['happy', 'joy', 'satisfied', 'excited', 'positive', '正面', '高兴', '满意', '愉悦'])

function _classifyEmotion(emotion: string): SentimentBucket {
  const key = emotion.trim().toLowerCase()
  if (!key) return 'neutral'
  if (NEGATIVE_EMOTIONS.has(key) || NEGATIVE_EMOTIONS.has(emotion)) return 'negative'
  if (POSITIVE_EMOTIONS.has(key) || POSITIVE_EMOTIONS.has(emotion)) return 'positive'
  return 'neutral'
}

function _buildSentiments(bucket: SentimentBucket) {
  return (['negative', 'neutral', 'positive'] as SentimentBucket[]).map(b => ({
    ...SENTIMENT_TEMPLATE[b],
    value: b === bucket ? 100 : 0,
  }))
}

function _collectStrings(input: unknown): string[] {
  if (!Array.isArray(input)) return []
  const out: string[] = []
  for (const item of input) {
    if (typeof item === 'string') {
      const t = item.trim()
      if (t) out.push(t)
    } else if (item && typeof item === 'object') {
      const candidate =
        (item as any).text ??
        (item as any).question ??
        (item as any).suggestion ??
        (item as any).label
      if (typeof candidate === 'string' && candidate.trim()) out.push(candidate.trim())
    }
  }
  return out
}

// 模块级单例，切换路由后状态不丢失
const _analysisResult = ref<AnalysisResult | null>(null)
const _isAnalyzing    = ref(false)
const _analysisError  = ref<string | null>(null)

export function useAnalysis() {
  const analysisResult = _analysisResult
  const isAnalyzing    = _isAnalyzing
  const analysisError  = _analysisError

  function _ensureResult(): AnalysisResult {
    if (!analysisResult.value) {
      analysisResult.value = { sentiments: [], intents: [], keywords: [], suggestions: [] }
    }
    return analysisResult.value
  }

  function reset(): void {
    analysisResult.value = null
    isAnalyzing.value    = false
    analysisError.value  = null
  }

  function setResult(result: AnalysisResult): void {
    analysisResult.value = result
    isAnalyzing.value    = false
    analysisError.value  = null
  }

  function setError(msg: string): void {
    analysisError.value = msg
    isAnalyzing.value   = false
  }

  function markAnalyzing(): void {
    isAnalyzing.value   = true
    analysisError.value = null
  }

  function addRealtimeIntent(label: string): void {
    if (!label) return
    const target = _ensureResult()
    if (!target.intents.includes(label)) {
      analysisResult.value = { ...target, intents: [...target.intents, label] }
    }
  }

  function mergeAgentAssist(payload: Record<string, unknown>): void {
    const target = _ensureResult()
    const next: AnalysisResult = {
      sentiments:  target.sentiments,
      intents:     [...target.intents],
      keywords:    [...target.keywords],
      suggestions: [...target.suggestions],
    }

    const intent = typeof payload.current_intent === 'string' ? payload.current_intent.trim() : ''
    if (intent && !next.intents.includes(intent)) next.intents.push(intent)

    if (Array.isArray(payload.keywords)) {
      const exists = new Set(next.keywords.map(k => k.word))
      for (const raw of payload.keywords) {
        const word = String(raw ?? '').trim()
        if (!word || exists.has(word)) continue
        next.keywords.push({ word, size: 14 })
        exists.add(word)
      }
    }

    const reply = typeof payload.suggested_reply === 'string' ? payload.suggested_reply.trim() : ''
    const merged: string[] = []
    if (reply) merged.push(reply)
    merged.push(..._collectStrings(payload.decision_suggestions))
    merged.push(..._collectStrings(payload.recommended_questions))
    merged.push(..._collectStrings(payload.suggested_followup_questions))

    const seen = new Set(next.suggestions)
    for (const s of merged) {
      if (!seen.has(s)) { next.suggestions.push(s); seen.add(s) }
    }

    if (typeof payload.emotion === 'string' && payload.emotion.trim()) {
      next.sentiments = _buildSentiments(_classifyEmotion(payload.emotion))
    }

    analysisResult.value = next
    isAnalyzing.value    = false
    analysisError.value  = null
  }

  function loadFromSummary(summary: CallDetail['summary'] | null | undefined): void {
    if (!summary) { reset(); return }
    const intents = summary.customer_intent ? [summary.customer_intent] : []
    const keywords = (summary.keywords ?? []).map(w => ({ word: w, size: 14 }))
    const suggestions: string[] = []
    if (summary.summary)    suggestions.push(summary.summary)
    if (summary.conclusion) suggestions.push(summary.conclusion)
    setResult({ sentiments: [], intents, keywords, suggestions })
  }

  return {
    analysisResult,
    isAnalyzing:   readonly(isAnalyzing),
    analysisError: readonly(analysisError),
    addRealtimeIntent,
    mergeAgentAssist,
    loadFromSummary,
    markAnalyzing,
    setError,
    reset,
    setResult,
  }
}
