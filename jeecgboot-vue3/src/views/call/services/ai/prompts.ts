import type { ChatMessage } from '../../types'

export function buildAnalysisPrompt(messages: ChatMessage[]): string {
  const transcript = messages
    .map(m => `[${m.role === 'agent' ? '客服' : '用户'}] ${m.content}`)
    .join('\n')

  return `你是一个专业的客服通话分析助手。请分析以下通话转写内容，以严格 JSON 格式返回分析结果，不要添加任何额外说明或 markdown 代码块。

## 对话记录
${transcript}

## 返回格式（严格 JSON）
{
  "sentiments": [
    { "label": "负面", "emoji": "😠", "value": 0, "color": "#ef4444" },
    { "label": "中性", "emoji": "😐", "value": 0, "color": "#f59e0b" },
    { "label": "正面", "emoji": "😊", "value": 0, "color": "#22c55e" }
  ],
  "intents": ["意图1", "意图2"],
  "keywords": [{ "word": "关键词", "size": 14 }],
  "suggestions": ["建议1", "建议2", "建议3"]
}

要求：
- sentiments 三项 value 之和必须等于 100
- keywords 的 size 范围 12~18，根据重要程度分配
- suggestions 提供 2~4 条简洁的处理建议`
}
