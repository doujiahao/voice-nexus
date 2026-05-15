<template>
  <div class="cs-container">
    <div class="cs-main">
      <AppHeader
        :current-date="currentDate"
        :agent-info="agentInfo"
        :ws-state="javaWs.wsState.value"
        :ws-reconnect-count="javaWs.reconnectCount.value"
        :show-reload-hint="javaWs.showReloadHint.value"
        :agent-status="agentStatus"
        :agent-status-label="agentStatusLabel"
        :agent-status-color="agentStatusColor"
        :status-meta="statusMeta"
        @status-change="setAgentStatus"
      />

      <CallInfoBar
        v-model:caller-name="callerName"
        v-model:ai-enabled="aiEnabled"
        :phone-number="phoneNumber"
        :name-error="callerNameError"
        :duration="duration"
        :call-active="showCallInfo"
        @blur:callerName="validateCallerName"
      />

      <div class="cs-conversation">
        <ConversationTabs v-model:active-tab="activeTab" :asr-state="asrState" />
        <TranscriptPanel v-show="activeTab === 'transcript'" :messages="asrMessages" :call-active="isCallActive" />
        <AnalysisPanel
          v-show="activeTab === 'analysis'"
          :ai-enabled="aiEnabled"
          :analysis-result="analysisResult"
          :is-analyzing="isAnalyzing"
          :analysis-error="analysisError"
          @enable-ai="aiEnabled = true"
        />
      </div>
    </div>

    <div class="cs-call-panel">
      <IncomingCallCard :phone-number="phoneNumber" :call-active="isCallActive" :has-incoming="hasIncomingCall" :caller-name="callerName" />
      <CallNoteSection v-model="callNote" :note-saved="noteSaved" @save="saveNote" />
      <div class="cs-action-btns">
        <button v-if="canHangup" class="cs-btn-hangup" @click="hangupCall">📵 挂断电话</button>
        <button v-if="isWrapUp" class="cs-btn-wrapup" @click="onFinishWrapUp">✅ 完成整理</button>
      </div>
      <CallHistoryList
        :records="callVisibleRecords"
        :total-count="callTotalCount"
        :expanded="callExpanded"
        :is-loading="callHistoryLoading"
        :is-loading-more="callHistoryLoadingMore"
        :has-more="callHistoryHasMore"
        :call-active="isCallActive"
        @toggle-expand="callExpanded = !callExpanded"
        @select="onRecordSelect"
        @refresh="refreshCallList"
        @load-more="fetchCallMore"
      />
    </div>

    <div class="cs-divider-icon">⚡</div>

    <transition name="toast">
      <div v-if="toastMsg" class="cs-toast">{{ toastMsg }}</div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useAsr }         from './composables/useAsr'
import { useAnalysis }    from './composables/useAnalysis'
import { useCallHistory } from './composables/useCallHistory'
import { useCallNotify }  from './composables/useCallNotify'
import { useCallState }   from './composables/useCallState'
import { useJavaWs }      from './composables/useJavaWs'
import { useAgentStatus } from './composables/useAgentStatus'
import { useAgentInfo }   from './composables/useAgentInfo'
import { ASR_WS_MSG_TYPE } from './config/index'
import { formatDate }     from './utils/format'
import type { CallDetail, CallTurnItem } from './types'

import AppHeader        from './components/layout/AppHeader.vue'
import CallInfoBar      from './components/call/CallInfoBar.vue'
import ConversationTabs from './components/call/ConversationTabs.vue'
import TranscriptPanel  from './components/call/TranscriptPanel.vue'
import AnalysisPanel    from './components/call/AnalysisPanel.vue'
import IncomingCallCard from './components/sidebar/IncomingCallCard.vue'
import CallNoteSection  from './components/sidebar/CallNoteSection.vue'
import CallHistoryList  from './components/sidebar/CallHistoryList.vue'

// ── 坐席信息 ──────────────────────────────────────────────────────────────────
const { agentInfo } = useAgentInfo()
const currentDate = ref(formatDate(new Date()))
const aiEnabled   = ref(true)
const callerName  = ref('')
const phoneNumber = ref('')
const callerNameError = ref(false)
const callNote    = ref('')
const savedNote   = ref('')
const noteSaved   = ref(false)
const duration    = ref(0)
const activeTab   = ref<'transcript' | 'analysis'>('transcript')
const toastMsg    = ref('')

// ── 通话状态机 ────────────────────────────────────────────────────────────────
const { callPhase, isCallActive, canHangup, showCallInfo, toActive, toIdle, toReviewing } = useCallState()
// 标记是否真正进入过通话（区分拒接/未接通 vs 通话后挂断）
const hadRealCall = ref(false)
// 标记前端已主动处理挂断，防止 call_state:idle 重复处理
let _hangupHandled = false

// ── Composables ───────────────────────────────────────────────────────────────
const { analysisResult, isAnalyzing, analysisError, addRealtimeIntent, reset: resetAnalysis, setResult: setAnalysisResult, runFullAnalysis } = useAnalysis({ enabled: aiEnabled })
const { asrState, asrMessages, startAsr, stopAsr, setSessionId, clearMessages, setMessages } = useAsr({ callerNameRef: callerName as any })
const javaWs = useJavaWs()

// ── WS 订阅 ───────────────────────────────────────────────────────────────────
javaWs.subscribe('call_state', (payload) => {
  if (payload.state === 'active') {
    hadRealCall.value = true
    toActive()
  }
  if (payload.state === 'idle') {
    // 前端 hangupCall 已处理过，跳过
    if (_hangupHandled) {
      _hangupHandled = false
      hadRealCall.value = false
      return
    }
    // 清除计时器但不切换 callPhase（由后续逻辑决定）
    if (durationTimer) { clearInterval(durationTimer); durationTimer = null }
    stopAsr()
    // 保存转写副本用于 AI 分析
    const messagesForAnalysis = [...asrMessages.value]
    clearMessages()
    resetAnalysis()
    if (hadRealCall.value) {
      // 有实际通话 → 话后整理
      setWrapUp()
      // 先切 idle 再切 reviewing（toReviewing 要求 callPhase !== active）
      toIdle()
      toReviewing()
      // 通话结束后触发 AI 完整分析
      if (aiEnabled.value && messagesForAnalysis.length > 0) {
        runFullAnalysis(messagesForAnalysis)
      }
      // 持久化备注 + 刷新通话列表
      if (callSessionId.value && savedNote.value) updateCallRecordNote(callSessionId.value, savedNote.value)
      savedNote.value = ''
      callNote.value  = ''
      callSessionId.value = ''
      fetchCallList()
    } else {
      // 拒接/未接通 → 从振铃恢复空闲
      resetFromRinging()
      toIdle()
    }
    hadRealCall.value = false
  }
})
javaWs.subscribe('call_session', (payload) => {
  if (payload.call_session_id) {
    callSessionId.value = String(payload.call_session_id)
    setSessionId(callSessionId.value)
  }
})
javaWs.subscribe(ASR_WS_MSG_TYPE, (payload) => {
  // 后端 asr_result 字段: corrected_text, text, speaker_role, speaker_name, ts, intent, turnId, durationMs
  // 注意: 后端用驼峰 durationMs，不是 duration_ms
  const content = String(payload.corrected_text ?? payload.text ?? '').trim()
  if (!content) return

  const rawIntent = payload.intent
  const intentLabel: string | undefined = rawIntent != null
    ? (typeof rawIntent === 'object'
        ? String((rawIntent as any).label ?? '')
        : String(rawIntent))
    : undefined

  const durationValue = payload.durationMs ?? payload.duration_ms
  const audioUrl = payload.audio_url ?? payload.audioUrl
  const entities = payload.entities && typeof payload.entities === 'object' && !Array.isArray(payload.entities)
    ? Object.fromEntries(Object.entries(payload.entities).map(([key, value]) => [key, String(value)]))
    : undefined
  const keywords = Array.isArray(payload.keywords)
    ? payload.keywords.map((item) => String(item)).filter(Boolean)
    : undefined

  asrMessages.value.push({
    role:       payload.speaker_role === 'agent' ? 'agent' : 'customer',
    time:       payload.ts
                ? new Date(String(payload.ts)).toLocaleTimeString('zh-CN', { hour12: false })
                : new Date().toLocaleTimeString('zh-CN', { hour12: false }),
    content,
    name:       String(payload.speaker_name ?? (payload.speaker_role === 'agent' ? '客服' : callerName.value)),
    intent:     intentLabel || undefined,
    intentConfidence: typeof payload.intent_confidence === 'number' ? payload.intent_confidence : undefined,
    keywords,
    entities,
    emotion:    payload.emotion ? String(payload.emotion) : undefined,
    utteranceSummary: payload.utterance_summary ? String(payload.utterance_summary) : undefined,
    audioUrl:   audioUrl ? String(audioUrl) : undefined,
    durationMs: typeof durationValue === 'number' ? durationValue : undefined,
  })

  if (intentLabel) addRealtimeIntent(intentLabel)
})

// ── 坐席状态 ──────────────────────────────────────────────────────────────────
const { agentStatus, agentStatusLabel, agentStatusColor, statusMeta, setStatus: setAgentStatus, syncWithCallPhase, setRinging, resetFromRinging, setWrapUp, finishWrapUp } = useAgentStatus()
watch(callPhase, (phase) => { syncWithCallPhase(phase) })

const isWrapUp = computed(() => agentStatus.value === 'wrap_up')

function onFinishWrapUp(): void {
  finishWrapUp()
  toIdle()
  showToast('话后整理已完成')
}

// ── 通话记录 ──────────────────────────────────────────────────────────────────
const callHistory = useCallHistory()
const {
  visibleRecords: callVisibleRecords, totalCount: callTotalCount, expanded: callExpanded,
  isLoading: callHistoryLoading, isLoadingMore: callHistoryLoadingMore, hasMore: callHistoryHasMore,
  fetchList: fetchCallList, fetchMore: fetchCallMore, fetchTurns: fetchCallTurns,
  fetchDetail: fetchCallDetail, selectOnly: selectCallOnly, refresh: refreshCallList, updateRecordNote: updateCallRecordNote,
} = callHistory

// ── 通话会话 ID ───────────────────────────────────────────────────────────────
const callSessionId = ref('')

// ── 来电通知 ──────────────────────────────────────────────────────────────────
const { acceptedCall, autoRejected, rejectedFromRinging, incomingCall, clearAccepted, clearAutoRejected, clearRejectedFromRinging } = useCallNotify()
const hasIncomingCall = computed(() => incomingCall.value !== null && !isCallActive.value)

// 来电到达 → 推断坐席振铃状态（后端路由分配 RINGING 不推送 agent_status）
watch(incomingCall, (call) => {
  if (call) setRinging()
})

watch(acceptedCall, async (call) => {
  if (!call) return
  callerName.value  = call.callerName
  phoneNumber.value = call.phone
  await acceptCall()
  clearAccepted()
})

watch(autoRejected, (rejected) => {
  if (rejected) {
    showToast('来电振铃超时，已自动拒接')
    clearAutoRejected()
  }
})

watch(rejectedFromRinging, (rejected) => {
  if (rejected) {
    resetFromRinging()
    clearRejectedFromRinging()
  }
})

// ── 计时器 ────────────────────────────────────────────────────────────────────
let durationTimer:  ReturnType<typeof setInterval> | null = null
let noteSaveTimer:  ReturnType<typeof setTimeout>  | null = null
let toastTimer:     ReturnType<typeof setTimeout>  | null = null

function startCallTimer(): void {
  if (durationTimer) clearInterval(durationTimer)
  duration.value = 0
  toActive()
  durationTimer = setInterval(() => { duration.value++ }, 1000)
}

onMounted(() => fetchCallList())
onBeforeUnmount(() => {
  if (durationTimer) clearInterval(durationTimer)
  if (noteSaveTimer) clearTimeout(noteSaveTimer)
  if (toastTimer)    clearTimeout(toastTimer)
  stopAsr()
})

// ── 方法 ──────────────────────────────────────────────────────────────────────
function validateCallerName(): void { callerNameError.value = !callerName.value.trim() }

function showToast(msg: string): void {
  if (toastTimer) clearTimeout(toastTimer)
  toastMsg.value = msg
  toastTimer = setTimeout(() => { toastMsg.value = '' }, 2500)
}

function saveNote(): void {
  if (!callNote.value.trim()) { showToast('备注内容不能为空'); return }
  savedNote.value = callNote.value
  if (noteSaveTimer) clearTimeout(noteSaveTimer)
  noteSaved.value = true
  showToast('备注保存成功')
  noteSaveTimer = setTimeout(() => { noteSaved.value = false }, 2500)
  // 立即持久化到后端
  if (callSessionId.value) {
    updateCallRecordNote(callSessionId.value, callNote.value)
  }
}

async function acceptCall(): Promise<void> {
  startAsr()
  startCallTimer()
  showToast('已接通来电')
}

async function hangupCall(): Promise<void> {
  showToast('通话已挂断')
  _hangupHandled = true
  // 通知后端坐席挂断
  if (callSessionId.value) {
    javaWs.send({ type: 'call_response', call_id: callSessionId.value, action: 'hangup' })
  }
  if (durationTimer) { clearInterval(durationTimer); durationTimer = null }
  stopAsr()
  // 保存转写副本用于 AI 分析，再清空
  const messagesForAnalysis = [...asrMessages.value]
  clearMessages()
  resetAnalysis()
  // 前端主动挂断 → 进入话后整理
  setWrapUp()
  toIdle()
  toReviewing()
  // 通话结束后触发 AI 完整分析
  if (aiEnabled.value && messagesForAnalysis.length > 0) {
    runFullAnalysis(messagesForAnalysis)
  }
  await fetchCallList()
  if (callSessionId.value && savedNote.value) updateCallRecordNote(callSessionId.value, savedNote.value)
  savedNote.value = ''
  callNote.value  = ''
  callSessionId.value = ''
}

function turnsToMessages(turns: CallTurnItem[], customerName: string) {
  return turns.map(turn => {
    const d = new Date(turn.ts)
    const time = [String(d.getHours()).padStart(2,'0'), String(d.getMinutes()).padStart(2,'0'), String(d.getSeconds()).padStart(2,'0')].join(':')
    return {
      role:       turn.speaker_role === 'agent' ? 'agent' as const : 'customer' as const,
      time,
      content:    turn.corrected_text || turn.raw_text,
      name:       turn.speaker_role === 'agent' ? (turn.speaker_name || '客服') : (customerName || turn.speaker_name || '用户'),
      intent:     turn.intent ? (typeof turn.intent === 'object' ? turn.intent.label : String(turn.intent)) : undefined,
      intentConfidence: turn.intent && typeof turn.intent === 'object' && typeof turn.intent.confidence === 'number' ? turn.intent.confidence : undefined,
      keywords:   Array.isArray(turn.keywords) ? turn.keywords : undefined,
      entities:   turn.entities && Object.keys(turn.entities).length ? turn.entities : undefined,
      emotion:    turn.emotion || undefined,
      utteranceSummary: turn.summary || undefined,
      audioUrl:   turn.audio_url || undefined,
      durationMs: turn.duration_ms || undefined,
    }
  })
}

const cachedDetail = ref<CallDetail | null>(null)

async function onRecordSelect(id: string): Promise<void> {
  if (isCallActive.value) { showToast('通话进行中，请挂断后再查看历史记录'); return }
  toReviewing()
  selectCallOnly(id)
  resetAnalysis()
  clearMessages()
  cachedDetail.value = null

  const [detail, turns] = await Promise.all([fetchCallDetail(id), fetchCallTurns(id)])
  if (detail) {
    callerName.value  = detail.customer_name
    phoneNumber.value = detail.phone
    duration.value    = detail.duration_sec ?? 0
    cachedDetail.value = detail
  }
  if (turns.length > 0) setMessages(turnsToMessages(turns, detail?.customer_name ?? callerName.value))
}

watch(activeTab, (tab) => {
  if (tab !== 'analysis' || isCallActive.value || !cachedDetail.value) return
  const summary = cachedDetail.value.summary
  if (summary) {
    setAnalysisResult({
      sentiments:  [],
      intents:     summary.customer_intent ? [summary.customer_intent] : [],
      keywords:    (summary.keywords ?? []).map((w: string) => ({ word: w, size: 14 })),
      suggestions: [],
    })
  } else if (asrMessages.value.length > 0 && aiEnabled.value) {
    // 后端无 summary，用本地转写触发 AI 分析
    runFullAnalysis(asrMessages.value)
  } else {
    resetAnalysis()
  }
})
</script>

<style scoped>
* { box-sizing: border-box; margin: 0; padding: 0; }
.cs-container { display: flex; width: 100%; height: 100vh; background: #f0f4fa; font-family: "PingFang SC","Microsoft YaHei",sans-serif; font-size: 14px; color: #333; position: relative; overflow: hidden; }
.cs-main { flex: 1; display: flex; flex-direction: column; border: 2px dashed #7cb8f5; margin: 16px; background: #fff; border-radius: 8px; overflow: hidden; min-height: 0; }
.cs-conversation { flex: 1; display: flex; flex-direction: column; padding: 14px 20px; overflow: hidden; min-height: 0; }
.cs-call-panel { width: 300px; background: #fff; border-left: 1px solid #e2e8f0; display: flex; flex-direction: column; padding: 20px 16px; gap: 16px; overflow-y: auto; }
.cs-action-btns { display: flex; gap: 10px; flex-shrink: 0; }
.cs-btn-hangup { width: 100%; padding: 10px 0; border: none; border-radius: 8px; font-size: 13px; font-weight: 600; cursor: pointer; background: linear-gradient(135deg, #ef4444, #dc2626); color: #fff; display: flex; align-items: center; justify-content: center; gap: 6px; }
.cs-btn-hangup:hover { background: linear-gradient(135deg, #dc2626, #b91c1c); }
.cs-btn-wrapup { width: 100%; padding: 10px 0; border: none; border-radius: 8px; font-size: 13px; font-weight: 600; cursor: pointer; background: linear-gradient(135deg, #6366f1, #4f46e5); color: #fff; display: flex; align-items: center; justify-content: center; gap: 6px; }
.cs-btn-wrapup:hover { background: linear-gradient(135deg, #4f46e5, #4338ca); }
.cs-divider-icon { position: absolute; left: calc(100% - 300px - 20px); top: 50%; transform: translateY(-50%); width: 32px; height: 32px; background: #fff; border: 1px solid #e2e8f0; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 16px; box-shadow: 0 2px 6px rgba(0,0,0,0.08); z-index: 10; }
.cs-toast { position: fixed; bottom: 30px; left: 50%; transform: translateX(-50%); background: rgba(30,41,59,0.88); color: #fff; padding: 10px 22px; border-radius: 20px; font-size: 13px; z-index: 999; pointer-events: none; white-space: nowrap; }
.toast-enter-active, .toast-leave-active { transition: opacity 0.3s, transform 0.3s; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateX(-50%) translateY(10px); }
</style>
