<template>
  <div class="chl-outer">
    <!-- 通话记录区 -->
    <div class="chl-wrap">
      <div class="chl-title">通话记录</div>

      <div v-if="isLoading" class="chl-status">加载中...</div>
      <div v-else-if="totalCount === 0" class="chl-status chl-empty">暂无通话记录</div>

      <template v-else>
        <div
          ref="listEl"
          :class="expanded ? 'chl-list chl-list--expanded' : 'chl-list chl-list--collapsed'"
          @scroll.passive="onScroll"
        >
          <div
            v-for="record in records"
            :key="record.call_session_id"
            class="chl-item"
            :class="{ 'chl-item--disabled': callActive }"
            @click="!callActive && emit('select', record.call_session_id)"
          >
            <span class="chl-dot" :class="record.active ? 'dot-active' : 'dot-inactive'" />
            <div class="chl-content">
              <div class="chl-header">
                <span class="chl-phone">{{ record.phone }}</span>
                <span class="chl-label">用户来电</span>
                <span class="chl-date">{{ record.date }}</span>
                <span v-if="record.time" class="chl-time">{{ record.time }}</span>
              </div>
              <div class="chl-note">{{ record.note }}</div>
            </div>
          </div>

          <div v-if="expanded && isLoadingMore" class="chl-loading-more">加载中…</div>
          <div v-else-if="expanded && !hasMore && totalCount > 0" class="chl-no-more">已显示全部记录</div>
        </div>

        <div
          v-if="totalCount > PREVIEW_CNT"
          class="chl-more"
          @click="emit('toggle-expand')"
        >
          {{ expanded ? '↑ 收起记录' : '查看其他记录' }}
        </div>
      </template>
    </div>

    <!-- 实时辅助决策区（通话中且 AI 开启时显示） -->
    <div v-if="showAssist" class="chl-assist-wrap">
      <div class="chl-assist-divider"></div>
      <AgentAssistPanel
        :assist-result="assistResult"
        :assist-loading="assistLoading"
        :assist-error="assistError"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { CallRecord } from '../../types'
import type { AgentAssistResult } from '../../composables/useAgentAssist'
import AgentAssistPanel from '../call/AgentAssistPanel.vue'

const PREVIEW_CNT = 2

interface Props {
  records?:       CallRecord[]
  totalCount?:    number
  expanded?:      boolean
  isLoading?:     boolean
  isLoadingMore?: boolean
  hasMore?:       boolean
  callActive?:    boolean
  showAssist?:    boolean
  assistResult?:  AgentAssistResult | null
  assistLoading?: boolean
  assistError?:   string | null
}
const props = withDefaults(defineProps<Props>(), {
  records:       () => [],
  totalCount:    0,
  expanded:      false,
  isLoading:     false,
  isLoadingMore: false,
  hasMore:       false,
  callActive:    false,
  showAssist:    false,
  assistResult:  null,
  assistLoading: false,
  assistError:   null,
})

const emit = defineEmits<{
  'toggle-expand': []
  'select':        [id: string]
  'refresh':       []
  'load-more':     []
}>()

const listEl = ref<HTMLElement | null>(null)

let _scrollTimer: ReturnType<typeof setTimeout> | null = null

function onScroll(e: Event): void {
  if (_scrollTimer) return
  _scrollTimer = setTimeout(() => {
    _scrollTimer = null
    _handleScroll(e.target as HTMLElement)
  }, 150)
}

function _handleScroll(el: HTMLElement): void {
  const scrollable = el.scrollHeight - el.clientHeight
  if (scrollable <= 0 || !props.expanded) return
  const ratio = el.scrollTop / scrollable
  if (el.scrollTop === 0) { emit('refresh'); return }
  if (ratio >= 2 / 3 && props.hasMore && !props.isLoadingMore) emit('load-more')
}
</script>

<style scoped>
.chl-outer { display: flex; flex-direction: column; flex: 1; min-height: 0; }

.chl-wrap  { flex-shrink: 0; }
.chl-title { font-size: 13px; font-weight: 600; color: #475569; margin-bottom: 12px; }

.chl-status { font-size: 12px; color: #94a3b8; text-align: center; padding: 16px 0; }
.chl-empty  { color: #cbd5e1; }

.chl-list--collapsed { overflow: hidden; }
.chl-list--expanded  {
  overflow-y: auto;
  max-height: 280px;
  padding-right: 2px;
}
.chl-list--expanded::-webkit-scrollbar { width: 4px; }
.chl-list--expanded::-webkit-scrollbar-track { background: transparent; }
.chl-list--expanded::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 2px; }

.chl-item {
  display: flex; align-items: flex-start; gap: 10px;
  margin-bottom: 14px; cursor: pointer;
  border-radius: 6px; padding: 4px 2px; transition: background 0.15s;
}
.chl-item:hover { background: #f8fafc; }
.chl-item:last-child { margin-bottom: 0; }
.chl-item--disabled { cursor: not-allowed; opacity: 0.45; }
.chl-item--disabled:hover { background: transparent; }

.chl-dot      { width: 8px; height: 8px; border-radius: 50%; margin-top: 4px; flex-shrink: 0; }
.dot-active   { background: #3b82f6; }
.dot-inactive { background: #d1d5db; }

.chl-content { flex: 1; min-width: 0; }
.chl-header  { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.chl-phone   { font-size: 13px; font-weight: 600; color: #1e293b; }
.chl-label   { font-size: 12px; color: #64748b; }
.chl-date    { font-size: 12px; color: #94a3b8; }
.chl-time    { font-size: 12px; color: #94a3b8; }
.chl-note    { font-size: 12px; color: #94a3b8; margin-top: 2px;
               white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.chl-loading-more, .chl-no-more {
  text-align: center; font-size: 11px; color: #cbd5e1; padding: 8px 0; user-select: none;
}
.chl-more {
  text-align: center; font-size: 12px; color: #3b82f6;
  cursor: pointer; margin-top: 4px; padding: 4px 0;
}
.chl-more:hover { text-decoration: underline; }

/* 实时辅助决策 */
.chl-assist-wrap    { flex: 1; min-height: 0; overflow-y: auto; }
.chl-assist-divider { height: 1px; background: #e2e8f0; margin: 8px 0; }
.chl-assist-wrap::-webkit-scrollbar { width: 4px; }
.chl-assist-wrap::-webkit-scrollbar-track { background: transparent; }
.chl-assist-wrap::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 2px; }
</style>
