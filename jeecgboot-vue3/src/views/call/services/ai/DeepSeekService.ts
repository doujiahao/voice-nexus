import type { AiConfig, AnalysisResult, ChatMessage } from '../../types'

const FALLBACK_RESULT: AnalysisResult = {
  sentiments: [
    { label: '负面', emoji: '😠', value: 0,   color: '#ef4444' },
    { label: '中性', emoji: '😐', value: 100, color: '#f59e0b' },
    { label: '正面', emoji: '😊', value: 0,   color: '#22c55e' },
  ],
  intents: [],
  keywords: [],
  suggestions: [],
}

export class DeepSeekService {
  private _config: AiConfig

  constructor(config: AiConfig) {
    this._config = config
  }

  async analyze(_messages: ChatMessage[], prompt: string): Promise<AnalysisResult> {
    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), this._config.timeoutMs)

    try {
      const response = await fetch(`${this._config.baseUrl}/chat/completions`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${this._config.apiKey}`,
        },
        body: JSON.stringify(this._buildRequestBody(prompt)),
        signal: controller.signal,
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`)
      }

      const data = await response.json()
      return this._parseResponse(data)
    } catch (err: any) {
      if (err.name === 'AbortError') throw new Error('AI 分析请求超时')
      throw err
    } finally {
      clearTimeout(timeoutId)
    }
  }

  async analyzeStream(
    _messages: ChatMessage[],
    prompt: string,
    onChunk?: (delta: string) => void
  ): Promise<AnalysisResult> {
    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), this._config.timeoutMs)
    let accumulated = ''

    try {
      const response = await fetch(`${this._config.baseUrl}/chat/completions`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${this._config.apiKey}`,
        },
        body: JSON.stringify({ ...this._buildRequestBody(prompt), stream: true }),
        signal: controller.signal,
      })

      if (!response.ok) throw new Error(`HTTP ${response.status}: ${response.statusText}`)

      const reader = response.body!.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() ?? ''
        for (const line of lines) {
          if (!line.startsWith('data: ')) continue
          const chunk = line.slice(6).trim()
          if (chunk === '[DONE]') break
          try {
            const parsed = JSON.parse(chunk)
            const delta: string | undefined = parsed.choices?.[0]?.delta?.content
            if (delta) {
              accumulated += delta
              onChunk?.(delta)
            }
          } catch { /* 忽略解析错误 */ }
        }
      }

      return this._parseResponse({
        choices: [{ message: { content: accumulated } }],
      })
    } catch (err: any) {
      if (err.name === 'AbortError') throw new Error('AI 分析请求超时')
      throw err
    } finally {
      clearTimeout(timeoutId)
    }
  }

  private _buildRequestBody(prompt: string): object {
    return {
      model: this._config.model,
      stream: false,
      messages: [{ role: 'user', content: prompt }],
      temperature: 0.3,
      response_format: { type: 'json_object' },
    }
  }

  private _parseResponse(rawJson: any): AnalysisResult {
    try {
      const content: string | undefined = rawJson.choices?.[0]?.message?.content
      if (!content) return { ...FALLBACK_RESULT }

      const jsonStr = content.replace(/```json\s*/g, '').replace(/```\s*/g, '').trim()
      const result = JSON.parse(jsonStr)

      return {
        sentiments: Array.isArray(result.sentiments) ? result.sentiments : FALLBACK_RESULT.sentiments,
        intents:    Array.isArray(result.intents)    ? result.intents    : [],
        keywords:   Array.isArray(result.keywords)   ? result.keywords   : [],
        suggestions:Array.isArray(result.suggestions)? result.suggestions: [],
      }
    } catch {
      return { ...FALLBACK_RESULT }
    }
  }
}
