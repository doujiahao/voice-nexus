<template>
  <div class="chl-wrap">
    <div class="chl-title">通话记录</div>

    <!-- 初始加载中 -->
    <div v-if="isLoading" class="chl-status">加载中...</div>

    <!-- 空状态 -->
    <div v-else-if="totalCount === 0" class="chl-status chl-empty">暂无通话记录</div>

    <template v-else>
      <!--
        折叠：无滚动容器，高度贴合 2 条记录
        展开：固定高度 viewport（= 5 条），overflow-y: auto，滚动到 2/3 触发加载更多
      -->
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
            </div>
            <div class="chl-note">{{ record.note }}</div>
          </div>
        </div>

        <!-- 加载更多指示器（仅展开时显示在列表底部） -->
        <div v-if="expanded && isLoadingMore" class="chl-loading-more">加载中…</div>
        <div v-else-if="expanded && !hasMore && totalCount > 0" class="chl-no-more">已显示全部记录</div>
      </div>

      <!-- 展开/折叠按钮（总数 > 折叠条数时显示） -->
      <div
        v-if="totalCount > PREVIEW_CNT"
        class="chl-more"
        @click="emit('toggle-expand')"
      >
        {{ expanded ? '↑ 收起记录' : '查看其他记录' }}
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { CallRecord } from '../../types'

/** 折叠时显示条数，与 useCallHistory 中的 PREVIEW_CNT 一致 */
const PREVIEW_CNT = 2

interface Props {
  records?:       CallRecord[]
  /** 后端总记录数（非当前已加载数），用于展开/折叠按钮的显示判断 */
  totalCount?:    number
  expanded?:      boolean
  isLoading?:     boolean
  isLoadingMore?: boolean
  hasMore?:       boolean
  callActive?:    boolean
}
const props = withDefaults(defineProps<Props>(), {
  records:       () => [],
  totalCount:    0,
  expanded:      false,
  isLoading:     false,
  isLoadingMore: false,
  hasMore:       false,
  callActive:    false,
})

const emit = defineEmits<{
  'toggle-expand': []
  'select':        [id: string]
  'refresh':       []
  'load-more':     []
}>()

const listEl = ref<HTMLElement | null>(null)

// ── 滚动处理（节流 150ms） ──────────────────────────────────────────────────────
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
  if (scrollable <= 0) return

  // 折叠模式不做无限滚动
  if (!props.expanded) return

  const ratio = el.scrollTop / scrollable

  // 滚动到顶部：刷新（重新从第 1 页加载）
  if (el.scrollTop === 0) {
    emit('refresh')
    return
  }

  // 滚动到 2/3：加载下一页
  if (ratio >= 2 / 3 && props.hasMore && !props.isLoadingMore) {
    emit('load-more')
  }
}
</script>

<style scoped>
.chl-wrap  { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.chl-title { font-size: 13px; font-weight: 600; color: #475569; margin-bottom: 12px; flex-shrink: 0; }

/* 状态提示 */
.chl-status { font-size: 12px; color: #94a3b8; text-align: center; padding: 16px 0; flex-shrink: 0; }
.chl-empty  { color: #cbd5e1; }

/* ── 列表容器 ────────────────────────────────────────────────────────────────── */

/* 折叠：高度跟随内容（2 条记录），无滚动 */
.chl-list--collapsed {
  overflow: hidden;
}

/* 展开：固定 viewport = 5 条记录高度，内部滚动 */
.chl-list--expanded {
  overflow-y: auto;
  /* 单条记录高约 58px（含 margin-bottom:14px），5 条 ≈ 290px；
     减去最后一条的 margin 得 276px，取整为 280px */
  max-height: 280px;
  padding-right: 2px;
}
.chl-list--expanded::-webkit-scrollbar { width: 4px; }
.chl-list--expanded::-webkit-scrollbar-track { background: transparent; }
.chl-list--expanded::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 2px; }

/* ── 单条记录 ── */
.chl-item {
  display: flex; align-items: flex-start; gap: 10px;
  margin-bottom: 14px; cursor: pointer;
  border-radius: 6px; padding: 4px 2px;
  transition: background 0.15s;
}
.chl-item:hover { background: #f8fafc; }
.chl-item:last-child { margin-bottom: 0; }
.chl-item--disabled { cursor: not-allowed; opacity: 0.45; }
.chl-item--disabled:hover { background: transparent; }

/* ── 圆点 ── */
.chl-dot      { width: 8px; height: 8px; border-radius: 50%; margin-top: 4px; flex-shrink: 0; }
.dot-active   { background: #3b82f6; }
.dot-inactive { background: #d1d5db; }

.chl-content { flex: 1; min-width: 0; }
.chl-header  { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.chl-phone   { font-size: 13px; font-weight: 600; color: #1e293b; }
.chl-label   { font-size: 12px; color: #64748b; }
.chl-date    { font-size: 12px; color: #94a3b8; }
.chl-note    { font-size: 12px; color: #94a3b8; margin-top: 2px;
               white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

/* ── 底部状态 ── */
.chl-loading-more,
.chl-no-more {
  text-align: center; font-size: 11px; color: #cbd5e1;
  padding: 8px 0; user-select: none;
}

/* ── 展开/折叠按钮 ── */
.chl-more {
  text-align: center; font-size: 12px; color: #3b82f6;
  cursor: pointer; margin-top: 4px; flex-shrink: 0; padding: 4px 0;
}
.chl-more:hover { text-decoration: underline; }
</style>
