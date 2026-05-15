<template>
  <div class="cs-header">
    <div class="cs-header-left">
      <div class="cs-logo">✦</div>
      <div class="cs-title-info">
        <div class="cs-title">前景无忧智能话务客服</div>
        <div class="cs-date">当前日期：{{ currentDate }}</div>
      </div>
    </div>
    <div class="cs-header-right">
      <div class="cs-ws-status" :title="wsStatusTooltip">
        <span class="cs-ws-dot" :class="wsDotClass"></span>
        <span class="cs-ws-label">{{ wsStatusLabel }}</span>
      </div>

      <div class="cs-agent-status-wrap" @click.stop="toggleDropdown">
        <span class="cs-status-dot" :style="{ background: agentStatusColor }"></span>
        <span class="cs-status-text" :style="{ color: agentStatusColor }">{{ agentStatusLabel }}</span>
        <span class="cs-status-arrow">▾</span>
        <div v-if="dropdownOpen" class="cs-status-dropdown" @click.stop>
          <div
            v-for="opt in manualOptions" :key="opt"
            class="cs-status-option"
            :class="{ active: agentStatus === opt }"
            :style="{ '--opt-color': statusMeta[opt].color }"
            @click="onSelectStatus(opt)"
          >
            <span class="cs-opt-dot" :style="{ background: statusMeta[opt].color }"></span>
            {{ statusMeta[opt].label }}
          </div>
        </div>
      </div>

      <div v-if="showReloadHint" class="cs-reload-hint" @click="reload">⚠ 连接失败，点击刷新</div>

      <div class="cs-agent-wrap">
        <div class="cs-agent-name">
          <div class="cs-agent-text">
            <span class="cs-agent-title-name">{{ agentInfo.name }}</span>
            <span class="cs-agent-role">{{ agentInfo.role }}</span>
          </div>
          <div class="cs-agent-avatar">{{ agentInfo.avatarChar }}</div>
        </div>
        <div class="cs-agent-dropdown">
          <div class="cs-agent-dropdown-item" @click="onLogout">
            <span>⏻</span><span>退出登录</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useUserStore } from '/@/store/modules/user'
import { AGENT_STATUS_MANUAL } from '../../composables/useAgentStatus'
import type { AgentInfo, AgentStatus, WsStateEnum } from '../../types'

interface Props {
  currentDate:       string
  agentInfo:         AgentInfo
  wsState?:          WsStateEnum
  wsReconnectCount?: number
  showReloadHint?:   boolean
  agentStatus?:      AgentStatus
  agentStatusLabel?: string
  agentStatusColor?: string
  statusMeta?:       Record<AgentStatus, { label: string; color: string; dotColor: string }>
}
const props = withDefaults(defineProps<Props>(), {
  wsState: 'idle', wsReconnectCount: 0, showReloadHint: false,
  agentStatus: 'idle', agentStatusLabel: '空闲', agentStatusColor: '#22c55e',
})
const emit = defineEmits<{ 'status-change': [status: AgentStatus] }>()

const manualOptions = AGENT_STATUS_MANUAL
const dropdownOpen  = ref(false)

const wsDotClass = computed(() => {
  switch (props.wsState) {
    case 'active':       return 'dot-green'
    case 'connecting':
    case 'reconnecting': return 'dot-yellow'
    default:             return 'dot-red'
  }
})
const wsStatusLabel = computed(() => {
  switch (props.wsState) {
    case 'active':       return '已连接'
    case 'connecting':   return '连接中'
    case 'reconnecting': return `重连(${props.wsReconnectCount})`
    case 'error':        return '连接失败'
    default:             return '未连接'
  }
})
const wsStatusTooltip = computed(() => {
  if (props.wsState === 'error') return '连接失败次数过多，请刷新页面'
  if (props.wsState === 'reconnecting') return `正在进行第 ${props.wsReconnectCount} 次重连...`
  return wsStatusLabel.value
})

function toggleDropdown(): void { dropdownOpen.value = !dropdownOpen.value }
function onSelectStatus(status: AgentStatus): void { dropdownOpen.value = false; emit('status-change', status) }
function reload(): void { window.location.reload() }

async function onLogout(): Promise<void> {
  const userStore = useUserStore()
  await userStore.logout()
}

function onDocClick(): void { dropdownOpen.value = false }
onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))
</script>

<style scoped>
.cs-header { display: flex; align-items: center; justify-content: space-between; padding: 14px 20px; border-bottom: 1px solid #edf0f5; flex-shrink: 0; }
.cs-header-left { display: flex; align-items: center; gap: 12px; }
.cs-logo { width: 40px; height: 40px; background: linear-gradient(135deg, #4f8ef7, #2563eb); border-radius: 8px; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 18px; }
.cs-title { font-size: 16px; font-weight: 600; color: #1a2a4a; }
.cs-date  { font-size: 12px; color: #999; margin-top: 2px; }
.cs-header-right { display: flex; align-items: center; gap: 14px; }
.cs-ws-status { display: flex; align-items: center; gap: 5px; font-size: 12px; color: #64748b; }
.cs-ws-dot { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; }
.dot-green  { background: #22c55e; }
.dot-red    { background: #ef4444; }
.dot-yellow { background: #f59e0b; animation: blink 1s infinite; }
@keyframes blink { 0%,100% { opacity: 1; } 50% { opacity: 0.25; } }
.cs-agent-status-wrap { position: relative; display: flex; align-items: center; gap: 5px; padding: 4px 10px; border-radius: 20px; background: #f1f5f9; cursor: pointer; user-select: none; font-size: 13px; }
.cs-agent-status-wrap:hover { background: #e2e8f0; }
.cs-status-dot  { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.cs-status-text { font-weight: 600; }
.cs-status-arrow { font-size: 10px; color: #94a3b8; margin-left: 2px; }
.cs-status-dropdown { position: absolute; top: calc(100% + 6px); left: 0; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; box-shadow: 0 4px 16px rgba(0,0,0,0.10); z-index: 100; min-width: 110px; overflow: hidden; }
.cs-status-option { display: flex; align-items: center; gap: 8px; padding: 9px 14px; font-size: 13px; cursor: pointer; color: #374151; transition: background 0.15s; }
.cs-status-option:hover { background: #f8fafc; }
.cs-status-option.active { background: #eff6ff; font-weight: 600; color: var(--opt-color); }
.cs-opt-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.cs-reload-hint { font-size: 12px; color: #ef4444; cursor: pointer; padding: 4px 10px; background: #fef2f2; border: 1px solid #fecaca; border-radius: 6px; }
.cs-reload-hint:hover { background: #fee2e2; }
.cs-agent-wrap { position: relative; display: flex; align-items: center; }
.cs-agent-name { display: flex; align-items: center; gap: 8px; cursor: default; }
.cs-agent-text { text-align: right; }
.cs-agent-title-name { display: block; font-size: 13px; font-weight: 600; color: #1a2a4a; }
.cs-agent-role { display: block; font-size: 11px; color: #999; }
.cs-agent-avatar { width: 36px; height: 36px; border-radius: 50%; background: linear-gradient(135deg, #f97316, #ef4444); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 600; flex-shrink: 0; }
.cs-agent-dropdown { position: absolute; top: calc(100% + 8px); right: -16px; min-width: 140px; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; box-shadow: 0 4px 16px rgba(0,0,0,0.10); overflow: hidden; opacity: 0; pointer-events: none; transform: translateY(-4px); transition: opacity 0.18s, transform 0.18s; z-index: 100; }
.cs-agent-wrap:hover .cs-agent-dropdown { opacity: 1; pointer-events: auto; transform: translateY(0); }
.cs-agent-dropdown-item { display: flex; align-items: center; gap: 8px; padding: 12px 20px; font-size: 13px; color: #374151; cursor: pointer; transition: background 0.15s, color 0.15s; white-space: nowrap; }
.cs-agent-dropdown-item:hover { background: #fef2f2; color: #ef4444; }
</style>
