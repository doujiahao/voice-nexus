<template>
  <div class="ct-tabs">
    <button
      class="ct-tab"
      :class="activeTab === 'transcript' ? 'tab-active' : 'tab-secondary'"
      @click="$emit('update:activeTab', 'transcript')"
    >● 会话实时转写</button>
    <button
      class="ct-tab"
      :class="activeTab === 'analysis' ? 'tab-active' : 'tab-secondary'"
      @click="$emit('update:activeTab', 'analysis')"
    >▶ 会话分析</button>
    <div v-if="activeTab === 'transcript'" class="ct-recognizing">
      <span class="ct-rec-dot" :class="{ active: asrConnected }"></span>
      {{ asrConnected ? '识别中...' : '未连接' }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { WsStateEnum } from '../../types'

interface Props {
  activeTab?: string
  asrState?: WsStateEnum | string
}
const props = withDefaults(defineProps<Props>(), {
  activeTab: 'transcript',
  asrState: 'idle',
})
defineEmits<{ 'update:activeTab': [value: string] }>()

const asrConnected = computed(() =>
  props.asrState === 'active' || props.asrState === 'connecting' || props.asrState === 'reconnecting'
)
</script>

<style scoped>
.ct-tabs { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-shrink: 0; }
.ct-tab {
  padding: 6px 16px; border-radius: 20px; font-size: 13px;
  font-weight: 500; cursor: pointer; border: none; outline: none; transition: all 0.2s;
}
.tab-active { background: linear-gradient(135deg, #3b82f6, #2563eb); color: #fff; }
.tab-secondary { background: #e2e8f0; color: #475569; }
.tab-secondary:hover { background: #cbd5e1; }
.ct-recognizing { margin-left: auto; font-size: 12px; color: #94a3b8; display: flex; align-items: center; gap: 5px; }
.ct-rec-dot {
  width: 7px; height: 7px; border-radius: 50%; background: #d1d5db; transition: background 0.3s;
}
.ct-rec-dot.active { background: #ef4444; animation: blink 1s infinite; }
@keyframes blink { 0%,100% { opacity: 1; } 50% { opacity: 0.2; } }
</style>
