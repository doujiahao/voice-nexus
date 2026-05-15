import { ref, readonly } from 'vue'
import type { AnalysisResult, ChatMessage } from '../types'
import { DeepSeekService } from '../services/ai/DeepSeekService'
import { buildAnalysisPrompt } from '../services/ai/prompts'
import { AI_CONFIG } from '../config'

export function useAnalysis(_options: { enabled?: { value: boolean } } = {}) {
  const analysisResult = ref<AnalysisResult | null>(null)
  const isAnalyzing    = ref(false)
  const analysisError  = ref<string | null>(null)

  function addRealtimeIntent(label: string): void {
    if (!label) return
    if (!analysisResult.value) {
      analysisResult.value = { sentiments: [], intents: [], keywords: [], suggestions: [] }
    }
    if (!analysisResult.value.intents.includes(label)) {
      analysisResult.value = { ...analysisResult.value, intents: [...analysisResult.value.intents, label] }
    }
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

  async function runFullAnalysis(messages: ChatMessage[]): Promise<void> {
    if (!AI_CONFIG.baseUrl || !AI_CONFIG.apiKey) {
      analysisError.value = 'AI 服务未配置（VITE_AI_BASE_URL / VITE_AI_API_KEY）'
      return
    }
    if (messages.length === 0) return

    isAnalyzing.value   = true
    analysisError.value = null

    try {
      const service = new DeepSeekService(AI_CONFIG)
      const prompt  = buildAnalysisPrompt(messages)
      const result  = await service.analyze(messages, prompt)
      setResult(result)
    } catch (err: any) {
      isAnalyzing.value  = false
      analysisError.value = err.message || 'AI 分析失败'
    }
  }

  return { analysisResult, isAnalyzing: readonly(isAnalyzing), analysisError: readonly(analysisError), addRealtimeIntent, reset, setResult, runFullAnalysis }
}
