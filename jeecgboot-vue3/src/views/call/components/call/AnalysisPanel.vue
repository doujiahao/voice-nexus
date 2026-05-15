<template>
  <div class="ap-wrap">
    <!-- AI 关闭占位 -->
    <div v-if="!aiEnabled" class="ap-disabled">
      <div class="ap-disabled-icon">🔒</div>
      <div class="ap-disabled-title">AI实时语义分析已关闭</div>
      <div class="ap-disabled-sub">请开启后查看分析结果</div>
      <button class="ap-enable-btn" @click="$emit('enable-ai')">立即开启</button>
    </div>

    <!-- 分析中 loading -->
    <div v-else-if="isAnalyzing && !analysisResult" class="ap-loading">
      <div class="ap-loading-dot"></div>
      <span>正在分析对话内容...</span>
    </div>

    <!-- 分析错误 -->
    <div v-else-if="analysisError && !analysisResult" class="ap-error">
      <div>⚠️ 分析失败：{{ analysisError }}</div>
    </div>

    <!-- 无数据占位 -->
    <div v-else-if="!analysisResult" class="ap-empty">
      <div class="ap-empty-icon">📊</div>
      <div>等待对话内容进行分析...</div>
    </div>

    <!-- 分析结果 -->
    <template v-else>
      <!-- 情感分析 -->
      <div class="ap-card">
        <div class="ap-card-title">
          情感分析
          <span v-if="isAnalyzing" class="ap-updating">更新中...</span>
        </div>
        <div class="ap-sentiment-list">
          <div v-for="s in analysisResult.sentiments" :key="s.label" class="ap-sentiment-item">
            <span class="ap-s-emoji">{{ s.emoji }}</span>
            <span class="ap-s-label">{{ s.label }}</span>
            <div class="ap-s-track">
              <div class="ap-s-fill" :style="{ width: s.value + '%', background: s.color }"></div>
            </div>
            <span class="ap-s-pct" :style="{ color: s.color }">{{ s.value }}%</span>
          </div>
        </div>
      </div>

      <!-- 用户意图 -->
      <div class="ap-card">
        <div class="ap-card-title">用户意图</div>
        <div class="ap-tag-list">
          <span v-for="intent in analysisResult.intents" :key="intent" class="ap-intent-tag">{{ intent }}</span>
          <span v-if="analysisResult.intents.length === 0" class="ap-empty-tag">暂未识别</span>
        </div>
      </div>

      <!-- 关键词 -->
      <div class="ap-card">
        <div class="ap-card-title">关键词提取</div>
        <div class="ap-tag-list">
          <span
            v-for="kw in analysisResult.keywords" :key="kw.word"
            class="ap-kw-tag"
            :style="{ fontSize: kw.size + 'px' }"
          >{{ kw.word }}</span>
          <span v-if="analysisResult.keywords.length === 0" class="ap-empty-tag">暂未提取</span>
        </div>
      </div>

      <!-- AI 建议 -->
      <div class="ap-card ap-suggest">
        <div class="ap-card-title">🤖 AI处理建议</div>
        <div class="ap-suggest-list">
          <div v-for="(s, i) in analysisResult.suggestions" :key="i" class="ap-suggest-item">
            <span class="ap-suggest-num">{{ i + 1 }}</span>
            <span>{{ s }}</span>
          </div>
          <div v-if="analysisResult.suggestions.length === 0" class="ap-empty-tag">暂无建议</div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import type { AnalysisResult } from '../../types'

interface Props {
  aiEnabled?: boolean
  analysisResult?: AnalysisResult | null
  isAnalyzing?: boolean
  analysisError?: string | null
}
withDefaults(defineProps<Props>(), {
  aiEnabled: true,
  analysisResult: null,
  isAnalyzing: false,
  analysisError: null,
})
defineEmits<{ 'enable-ai': [] }>()
</script>

<style scoped>
.ap-wrap {
  flex: 1; overflow-y: auto; min-height: 0;
  display: flex; flex-direction: column; gap: 12px; padding-right: 2px;
}
.ap-wrap::-webkit-scrollbar { width: 4px; }
.ap-wrap::-webkit-scrollbar-thumb { background: #e2e8f0; border-radius: 2px; }

.ap-disabled, .ap-loading, .ap-error, .ap-empty {
  flex: 1; display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 8px; color: #94a3b8; font-size: 13px; text-align: center;
}
.ap-disabled-icon, .ap-empty-icon { font-size: 36px; }
.ap-disabled-title { font-size: 14px; font-weight: 600; color: #475569; }
.ap-disabled-sub { font-size: 12px; }
.ap-enable-btn {
  margin-top: 8px; padding: 8px 20px; background: #3b82f6;
  color: #fff; border: none; border-radius: 20px; font-size: 13px; cursor: pointer;
}
.ap-enable-btn:hover { background: #2563eb; }
.ap-loading-dot {
  width: 24px; height: 24px; border-radius: 50%;
  border: 3px solid #dbeafe; border-top-color: #3b82f6;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.ap-error { color: #ef4444; }

.ap-card { background: #f8fafc; border-radius: 8px; padding: 12px 14px; border: 1px solid #f1f5f9; }
.ap-card-title { font-size: 12px; font-weight: 600; color: #475569; margin-bottom: 10px; display: flex; align-items: center; gap: 8px; }
.ap-updating { font-size: 11px; color: #3b82f6; font-weight: 400; animation: pulse 1s infinite; }
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.4; } }

.ap-sentiment-list { display: flex; flex-direction: column; gap: 8px; }
.ap-sentiment-item { display: flex; align-items: center; gap: 8px; }
.ap-s-emoji { font-size: 16px; }
.ap-s-label { width: 28px; font-size: 12px; color: #475569; }
.ap-s-track { flex: 1; height: 6px; background: #e2e8f0; border-radius: 3px; overflow: hidden; }
.ap-s-fill { height: 100%; border-radius: 3px; transition: width 0.6s ease; }
.ap-s-pct { font-size: 12px; font-weight: 600; width: 36px; text-align: right; }

.ap-tag-list { display: flex; flex-wrap: wrap; gap: 6px; }
.ap-intent-tag {
  padding: 3px 10px; background: #eff6ff; color: #2563eb;
  border: 1px solid #bfdbfe; border-radius: 12px; font-size: 12px;
}
.ap-kw-tag {
  padding: 3px 10px; background: #faf5ff; color: #7c3aed;
  border: 1px solid #ddd6fe; border-radius: 12px; font-weight: 500;
}
.ap-empty-tag { font-size: 12px; color: #94a3b8; }

.ap-suggest { background: #fffbeb; border-color: #fde68a; }
.ap-suggest-list { display: flex; flex-direction: column; gap: 8px; }
.ap-suggest-item { display: flex; align-items: flex-start; gap: 8px; font-size: 12px; color: #44403c; }
.ap-suggest-num {
  width: 18px; height: 18px; border-radius: 50%; background: #f59e0b;
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 700; flex-shrink: 0; margin-top: 1px;
}
</style>
