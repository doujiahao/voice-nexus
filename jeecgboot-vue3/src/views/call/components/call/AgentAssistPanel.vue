<template>
  <div class="aap-wrap">
    <div class="aap-header">
      <span class="aap-title">🤖 实时辅助决策</span>
      <span v-if="assistLoading" class="aap-loading-badge">更新中…</span>
      <span v-else-if="assistResult" class="aap-ok-badge">已更新</span>
    </div>

    <div v-if="assistError && !assistResult" class="aap-error">⚠ {{ assistError }}</div>

    <div v-else-if="!assistResult" class="aap-empty">等待对话内容...</div>

    <template v-else>
      <!-- 阶段摘要 -->
      <div v-if="assistResult.stage_summary" class="aap-section">
        <div class="aap-label">📋 阶段摘要</div>
        <div class="aap-text">{{ assistResult.stage_summary }}</div>
      </div>

      <!-- 当前意图 -->
      <div v-if="assistResult.current_intent" class="aap-section">
        <div class="aap-label">🎯 当前意图</div>
        <div class="aap-row">
          <span class="aap-intent-tag">{{ assistResult.current_intent }}</span>
          <span class="aap-conf">置信度 {{ Math.round((assistResult.intent_confidence ?? 0) * 100) }}%</span>
        </div>
      </div>

      <!-- 客户情绪 -->
      <div v-if="assistResult.emotion" class="aap-section">
        <div class="aap-label">💬 客户情绪</div>
        <div class="aap-row">
          <span class="aap-emotion-tag" :class="`emotion-${assistResult.emotion}`">
            {{ EMOTION_MAP[assistResult.emotion] ?? assistResult.emotion }}
          </span>
          <span v-if="assistResult.emotion_trend" class="aap-trend">
            {{ TREND_MAP[assistResult.emotion_trend] ?? assistResult.emotion_trend }}
          </span>
        </div>
      </div>

      <!-- 处理建议 -->
      <div v-if="assistResult.decision_suggestions?.length" class="aap-section">
        <div class="aap-label">✅ 处理建议</div>
        <ol class="aap-list">
          <li v-for="(s, i) in assistResult.decision_suggestions" :key="i" class="aap-suggest-item">
            <div class="aap-suggest-title">
              <span class="aap-tag aap-tag--type">{{ s.type }}</span>
              {{ s.title }}
            </div>
            <div class="aap-suggest-script">「{{ s.suggested_script }}」</div>
          </li>
        </ol>
      </div>

      <!-- 推荐话术 -->
      <div v-if="assistResult.suggested_reply" class="aap-section">
        <div class="aap-label">💡 推荐话术</div>
        <div class="aap-script">{{ assistResult.suggested_reply }}</div>
      </div>

      <!-- 推荐追问 -->
      <div v-if="assistResult.recommended_questions?.length || assistResult.suggested_followup_questions?.length" class="aap-section">
        <div class="aap-label">❓ 追问建议</div>
        <ol class="aap-list">
          <li v-for="(q, i) in mergedQuestions" :key="i" class="aap-q-item">{{ q }}</li>
        </ol>
      </div>

      <!-- 风险提示 -->
      <div v-if="assistResult.risk_flags?.length" class="aap-section aap-risk">
        <div class="aap-label">⚠ 风险提示</div>
        <div class="aap-row aap-row--wrap">
          <span v-for="(f, i) in assistResult.risk_flags" :key="i" class="aap-tag aap-tag--risk">
            {{ RISK_MAP[f] ?? f }}
          </span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AgentAssistResult } from '../../composables/useAgentAssist'

interface Props {
  assistResult?:  AgentAssistResult | null
  assistLoading?: boolean
  assistError?:   string | null
}
const props = withDefaults(defineProps<Props>(), {
  assistResult:  null,
  assistLoading: false,
  assistError:   null,
})

const EMOTION_MAP: Record<string, string> = {
  urgent:    '紧急',
  angry:     '愤怒',
  sad:       '悲伤',
  neutral:   '平静',
  happy:     '愉快',
  satisfied: '满意',
  anxious:   '焦虑',
}
const TREND_MAP: Record<string, string> = {
  rising:  '↑ 上升',
  falling: '↓ 下降',
  stable:  '→ 平稳',
}
const RISK_MAP: Record<string, string> = {
  urgent_customer:    '客户情绪紧急',
  complaint_risk:     '投诉风险',
  escalation_risk:    '升级风险',
  churn_risk:         '流失风险',
}

const mergedQuestions = computed(() => {
  const set = new Set<string>()
  const out: string[] = []
  const push = (arr?: string[]) => arr?.forEach(q => { if (q && !set.has(q)) { set.add(q); out.push(q) } })
  push(props.assistResult?.recommended_questions)
  push(props.assistResult?.suggested_followup_questions)
  return out
})
</script>

<style scoped>
.aap-wrap { padding: 10px 0 4px; font-size: 12px; color: #334155; }

.aap-header { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; }
.aap-title  { font-size: 12px; font-weight: 600; color: #1e293b; }
.aap-loading-badge { font-size: 11px; color: #f59e0b; background: #fef3c7; padding: 1px 6px; border-radius: 10px; }
.aap-ok-badge      { font-size: 11px; color: #22c55e; background: #dcfce7; padding: 1px 6px; border-radius: 10px; }

.aap-error { color: #ef4444; font-size: 12px; padding: 6px 0; }
.aap-empty { color: #94a3b8; font-size: 12px; padding: 6px 0; }

.aap-section { margin-bottom: 8px; }
.aap-label   { font-size: 11px; font-weight: 600; color: #64748b; margin-bottom: 3px; }
.aap-text    { font-size: 12px; color: #334155; line-height: 1.5; }
.aap-script  { font-size: 12px; color: #1e40af; background: #eff6ff; border-left: 3px solid #3b82f6; padding: 5px 8px; border-radius: 4px; line-height: 1.5; }

.aap-row      { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.aap-row--wrap { flex-wrap: wrap; }

.aap-intent-tag { background: #dbeafe; color: #1d4ed8; padding: 2px 8px; border-radius: 10px; font-size: 12px; font-weight: 500; }
.aap-conf       { font-size: 11px; color: #94a3b8; }

.aap-emotion-tag { padding: 2px 8px; border-radius: 10px; font-size: 12px; font-weight: 500; }
.emotion-urgent  { background: #fef3c7; color: #b45309; }
.emotion-angry   { background: #fee2e2; color: #dc2626; }
.emotion-sad     { background: #ede9fe; color: #7c3aed; }
.emotion-neutral { background: #f1f5f9; color: #475569; }
.emotion-happy,
.emotion-satisfied { background: #dcfce7; color: #16a34a; }
.emotion-anxious   { background: #ffedd5; color: #c2410c; }

.aap-trend { font-size: 11px; color: #94a3b8; }

.aap-list { padding-left: 16px; margin: 0; }
.aap-suggest-item { margin-bottom: 6px; }
.aap-suggest-title  { font-size: 12px; font-weight: 500; color: #1e293b; display: flex; align-items: center; gap: 4px; margin-bottom: 2px; }
.aap-suggest-script { font-size: 11px; color: #64748b; font-style: italic; padding-left: 2px; }

.aap-q-item { font-size: 12px; color: #334155; margin-bottom: 3px; line-height: 1.5; }

.aap-risk { }
.aap-tag         { display: inline-block; padding: 1px 6px; border-radius: 8px; font-size: 11px; }
.aap-tag--type   { background: #e0f2fe; color: #0369a1; }
.aap-tag--risk   { background: #fef2f2; color: #dc2626; border: 1px solid #fecaca; }
</style>
