<template>
  <div class="tp-wrap" ref="chatList" @scroll="onScroll">

    <!-- 顶部分页 sentinel -->
    <div class="tp-load-sentinel">
      <template v-if="hasMoreMessages">
        <div v-if="isLoadingMore" class="tp-loading">
          <span class="tp-spinner"></span> 加载中…
        </div>
        <div v-else class="tp-load-hint">上滑加载更多记录</div>
      </template>
    </div>

    <!-- 消息列表 -->
    <div
      v-for="(msg, index) in visibleMessages"
      :key="index"
      class="tp-row"
      :class="msg.role"
    >
      <!-- 客服：右侧头像 + 左侧气泡列（row-reverse） -->
      <template v-if="msg.role === 'agent'">
        <div class="tp-msg-col agent-col">
          <div class="tp-bubble agent-bubble" :class="{ 'tp-bubble--active': playingIndex === index }">
            {{ msg.content }}
            <button
              v-if="msg.audioUrl && !callActive"
              class="tp-audio-btn"
              :class="{ playing: playingIndex === index }"
              :title="playingIndex === index ? '暂停' : '播放'"
              @click="toggleAudio(index, msg.audioUrl!)"
            >{{ playingIndex === index ? '⏸' : '▶' }}</button>
          </div>
          <div v-if="msg.intent" class="tp-bubble intent-bubble">
            <span class="intent-label">意图</span>{{ msg.intent }}
          </div>
          <div class="tp-timestamp agent-ts">{{ msg.time }}</div>
        </div>
        <div class="tp-avatar agent-avatar">客</div>
      </template>

      <!-- 用户：左侧头像 + 右侧气泡列 -->
      <template v-else>
        <div class="tp-avatar user-avatar">{{ userInitial(msg.name) }}</div>
        <div class="tp-msg-col">
          <div class="tp-bubble user-bubble" :class="{ 'tp-bubble--active': playingIndex === index }">
            {{ msg.content }}
            <button
              v-if="msg.audioUrl && !callActive"
              class="tp-audio-btn"
              :class="{ playing: playingIndex === index }"
              :title="playingIndex === index ? '暂停' : '播放'"
              @click="toggleAudio(index, msg.audioUrl!)"
            >{{ playingIndex === index ? '⏸' : '▶' }}</button>
          </div>
          <div v-if="msg.intent" class="tp-bubble intent-bubble intent-bubble--user">
            <span class="intent-label">意图</span>{{ msg.intent }}
          </div>
          <div class="tp-timestamp user-ts">{{ msg.time }}</div>
        </div>
      </template>
    </div>

    <!-- 空状态 -->
    <div v-if="messages.length === 0" class="tp-empty">
      <div class="tp-empty-icon">🎙️</div>
      <div>等待通话开始...</div>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { TRANSCRIPT_CONFIG } from '../../config/index'
import type { ChatMessage } from '../../types'

interface Props {
  messages?: ChatMessage[]
  callActive?: boolean
}
const props = withDefaults(defineProps<Props>(), { messages: () => [], callActive: false })

const chatList = ref<HTMLElement | null>(null)
const autoScroll = ref(true)
const visibleCount = ref(TRANSCRIPT_CONFIG.renderLimit ?? 200)
const isLoadingMore = ref(false)

// ── 音频播放 ───────────────────────────────────────────────────────────────────
const playingIndex = ref<number | null>(null)
let _audio: HTMLAudioElement | null = null

function stopAudio(): void {
  if (_audio) {
    _audio.pause()
    _audio.src = ''
    _audio = null
  }
  playingIndex.value = null
}

function toggleAudio(index: number, url: string): void {
  if (playingIndex.value === index) { stopAudio(); return }
  stopAudio()
  playingIndex.value = index
  _audio = new Audio(url)
  _audio.onended = () => { playingIndex.value = null; _audio = null }
  _audio.onerror = () => { playingIndex.value = null; _audio = null }
  _audio.play().catch(() => { playingIndex.value = null; _audio = null })
}

onBeforeUnmount(stopAudio)

// ── 分页和渲染 ──────────────────────────────────────────────────────────────────

const visibleMessages = computed(() => props.messages.slice(-visibleCount.value))
const hasMoreMessages = computed(() => props.messages.length > visibleCount.value)

watch(() => props.messages, (newMsgs, oldMsgs) => {
  if (newMsgs === oldMsgs) return
  stopAudio()
  if (newMsgs.length === 0) {
    visibleCount.value = TRANSCRIPT_CONFIG.renderLimit ?? 200
    autoScroll.value = true
    return
  }
  visibleCount.value = newMsgs.length
  autoScroll.value = true
  nextTick(() => scrollToBottom(true))
})

watch(() => props.messages.length, (newLen, oldLen) => {
  if (newLen > oldLen) scrollToBottom()
})

onMounted(() => scrollToBottom(true))

function userInitial(name?: string): string {
  return name ? name.charAt(0) : '用'
}

function onScroll(): void {
  const el = chatList.value
  if (!el) return
  const { scrollHeight, scrollTop, clientHeight } = el
  autoScroll.value = scrollHeight - scrollTop - clientHeight < TRANSCRIPT_CONFIG.autoScrollThreshold
  if (scrollTop < 60 && hasMoreMessages.value && !isLoadingMore.value) {
    loadMoreMessages()
  }
}

function loadMoreMessages(): void {
  const el = chatList.value
  if (!el) return
  isLoadingMore.value = true
  const heightBefore = el.scrollHeight
  visibleCount.value = Math.min(
    visibleCount.value + (TRANSCRIPT_CONFIG.pageSize ?? 40),
    props.messages.length
  )
  nextTick(() => {
    const heightAfter = el.scrollHeight
    el.scrollTop = heightAfter - heightBefore
    isLoadingMore.value = false
  })
}

function scrollToBottom(force = false): void {
  if (!force && !autoScroll.value) return
  nextTick(() => {
    const el = chatList.value
    if (el) el.scrollTop = el.scrollHeight
  })
}
</script>

<style scoped>
/* ── 滚动容器 ── */
.tp-wrap {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  padding: 8px 12px 12px;
}
.tp-wrap::-webkit-scrollbar { width: 4px; }
.tp-wrap::-webkit-scrollbar-thumb { background: #e2e8f0; border-radius: 2px; }
.tp-wrap::-webkit-scrollbar-track { background: transparent; }

/* ── 分页 sentinel ── */
.tp-load-sentinel { display: flex; justify-content: center; min-height: 24px; margin-bottom: 4px; }
.tp-load-hint { font-size: 11px; color: #cbd5e1; user-select: none; }
.tp-loading { display: flex; align-items: center; gap: 6px; font-size: 11px; color: #94a3b8; }
.tp-spinner { display: inline-block; width: 12px; height: 12px; border: 2px solid #e2e8f0; border-top-color: #3b82f6; border-radius: 50%; animation: spin 0.7s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* ── 消息行 ── */
.tp-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  animation: bubble-in 0.25s ease-out both;
  border-radius: 8px;
  margin: 0 -8px;
  padding: 4px 8px;
}
.tp-row.agent { flex-direction: row-reverse; }

@keyframes bubble-in {
  from { opacity: 0; transform: translateY(10px) scale(0.96); }
  to   { opacity: 1; transform: translateY(0) scale(1); }
}

/* ── 头像圆圈 ── */
.tp-avatar {
  width: 28px; height: 28px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 700; flex-shrink: 0;
  margin-top: 2px; user-select: none;
}
.agent-avatar { background: #3b82f6; color: #fff; }
.user-avatar  { background: #64748b; color: #fff; }

/* ── 气泡列 ── */
.tp-msg-col { display: flex; flex-direction: column; gap: 3px; max-width: 78%; }
.agent-col  { align-items: flex-end; }

/* ── 气泡 ── */
.tp-bubble {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.65;
  word-break: break-word;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}
.agent-bubble { background: #f0f4ff; color: #1e293b; border-top-right-radius: 4px; }
.user-bubble  { background: #dbeafe; color: #1e293b; border-top-left-radius: 4px; }
.tp-bubble--active {
  background: #bfdbfe !important;
  box-shadow: 0 0 0 2px #3b82f6, 0 2px 8px rgba(59,130,246,0.25);
}

/* ── 意图气泡 ── */
.intent-bubble {
  background: #fff7ed; color: #92400e; border: 1px solid #fed7aa;
  border-radius: 8px; font-size: 12px;
  padding: 5px 10px; display: flex; align-items: center; gap: 5px; box-shadow: none;
}
.intent-bubble--user { align-self: flex-start; }
.intent-label { background: #f97316; color: #fff; font-size: 10px; font-weight: 700; padding: 1px 5px; border-radius: 4px; flex-shrink: 0; }

/* ── 时间戳 ── */
.tp-timestamp { font-size: 10px; color: #94a3b8; line-height: 1; padding: 0 2px; }
.agent-ts { text-align: right; }
.user-ts  { text-align: left; }

/* ── 音频播放按钮（气泡内） ── */
.tp-audio-btn {
  display: inline-flex; align-items: center; justify-content: center;
  margin-left: 8px; width: 22px; height: 22px;
  border: none; border-radius: 50%; background: rgba(0,0,0,0.08);
  color: #555; font-size: 10px; cursor: pointer;
  vertical-align: middle; flex-shrink: 0;
}
.tp-audio-btn:hover { background: rgba(0,0,0,0.15); }
.tp-audio-btn.playing { background: #3b82f6; color: #fff; }

/* ── 空状态 ── */
.tp-empty {
  flex: 1; display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 8px; color: #94a3b8; font-size: 13px;
}
.tp-empty-icon { font-size: 32px; }
</style>
