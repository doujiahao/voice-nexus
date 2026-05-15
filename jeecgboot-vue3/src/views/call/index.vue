<template>
  <div class="workspace-shell">
    <transition name="ws-notice">
      <div v-if="wsNotice" class="ws-notice-bar" :class="wsNoticeClass">{{ wsNotice }}</div>
    </transition>

    <IncomingCallOverlay
      v-if="incomingCall && isConnected"
      :caller-name="incomingCall.callerName"
      :phone="incomingCall.phone"
      :location="incomingCall.location"
      @accept="callNotify.accept()"
      @reject="callNotify.reject()"
    />

    <CustomerServiceView />
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useJavaWs }     from './composables/useJavaWs'
import { useCallNotify } from './composables/useCallNotify'
import { useAgentStatus } from './composables/useAgentStatus'
import { useAgentInfo }  from './composables/useAgentInfo'
import { getToken }      from '/@/utils/auth'
import { CALL_WS_CONFIG } from './config/index'
import IncomingCallOverlay from './IncomingCallOverlay.vue'
import CustomerServiceView from './CustomerServiceView.vue'

const { wsState, reconnectCount, isConnected, connect, disconnect } = useJavaWs()
const callNotify  = useCallNotify()
const agentStatus = useAgentStatus()
const agentInfo   = useAgentInfo()
const { incomingCall } = callNotify

const wsNotice      = ref('')
const wsNoticeClass = ref('')
let wsNoticeTimer: ReturnType<typeof setTimeout> | null = null

function clearWsNoticeTimer(): void {
  if (wsNoticeTimer) { clearTimeout(wsNoticeTimer); wsNoticeTimer = null }
}

watch(wsState, (state, prev) => {
  if (state === prev) return
  clearWsNoticeTimer()
  if (state === 'reconnecting') {
    wsNotice.value      = `连接异常，正在重连（第 ${reconnectCount.value} 次）`
    wsNoticeClass.value = 'ws-notice--warn'
  } else if (state === 'error') {
    wsNotice.value      = '连接失败，请检查服务状态'
    wsNoticeClass.value = 'ws-notice--error'
  } else if (state === 'active') {
    wsNotice.value      = '连接恢复成功'
    wsNoticeClass.value = 'ws-notice--ok'
    wsNoticeTimer = setTimeout(() => { wsNotice.value = '' }, 2000)
  }
})

onMounted(() => {
  if (CALL_WS_CONFIG.wsUrl) {
    connect({
      wsUrl:                CALL_WS_CONFIG.wsUrl,
      token:                getToken() ?? '',
      authMode:             CALL_WS_CONFIG.authMode,
      heartbeatIntervalMs:  CALL_WS_CONFIG.heartbeatIntervalMs,
      heartbeatTimeoutMs:   CALL_WS_CONFIG.heartbeatTimeoutMs,
      reconnectDelayMs:     CALL_WS_CONFIG.reconnectDelayMs,
      maxReconnectDelayMs:  CALL_WS_CONFIG.maxReconnectDelayMs,
      maxReconnectAttempts: CALL_WS_CONFIG.maxReconnectAttempts,
    })
  }
  callNotify.init()
  void agentStatus.init()
  void agentInfo.init()
})

onBeforeUnmount(() => {
  clearWsNoticeTimer()
  disconnect()
})
</script>

<style scoped>
.workspace-shell { min-height: 100vh; }
.ws-notice-bar { position: fixed; top: 0; left: 0; right: 0; z-index: 2000; padding: 10px 16px; text-align: center; font-size: 13px; font-weight: 600; pointer-events: none; }
.ws-notice--warn  { background: #fef3c7; color: #92400e; }
.ws-notice--error { background: #fef2f2; color: #dc2626; }
.ws-notice--ok    { background: #dcfce7; color: #15803d; }
.ws-notice-enter-active, .ws-notice-leave-active { transition: transform 0.3s ease, opacity 0.3s ease; }
.ws-notice-enter-from, .ws-notice-leave-to { transform: translateY(-100%); opacity: 0; }
</style>
