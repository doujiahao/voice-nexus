<template>
  <div class="cs-container">
    <!-- 重进话务平台通话中提示（需求4） -->
    <div v-if="showResumeAlert" class="cs-resume-alert">
      <span>📞 当前仍处于通话中，通话计时已恢复</span>
      <button class="cs-resume-close" @click="showResumeAlert = false">×</button>
    </div>

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
        @update:ai-enabled="onAiToggle"
      />

      <div class="cs-conversation">
        <!-- 回溯模式顶部提示条 -->
        <div v-if="isReviewing" class="cs-reviewing-bar">
          <span>📋 正在查看历史通话记录</span>
          <button class="cs-reviewing-back" @click="exitReviewing">← 返回当前会话</button>
        </div>
        <ConversationTabs
          v-model:active-tab="activeTab"
          :asr-state="asrState"
          :call-active="isCallActive"
        />
        <TranscriptPanel v-show="activeTab === 'transcript'" :messages="asrMessages" :call-active="isCallActive" />
        <AnalysisPanel
          v-show="activeTab === 'analysis'"
          :ai-enabled="aiEnabled"
          :analysis-result="analysisResult"
          :is-analyzing="isAnalyzing"
          :analysis-error="analysisError"
          @enable-ai="onAiToggle(true)"
        />
      </div>
    </div>

    <div class="cs-call-panel">
      <IncomingCallCard :phone-number="phoneNumber" :call-active="isCallActive" :has-incoming="hasIncomingCall" :caller-name="callerName" />
      <CallNoteSection v-model="callNote" :note-saved="noteSaved" @save="saveNote" />
      <div class="cs-action-btns">
        <button v-if="canHangup" class="cs-btn-hangup" @click="hangupCall">📵 挂断电话</button>
      </div>
      <CallHistoryList
        :records="callVisibleRecords"
        :total-count="callTotalCount"
        :expanded="callExpanded"
        :is-loading="callHistoryLoading"
        :is-loading-more="callHistoryLoadingMore"
        :has-more="callHistoryHasMore"
        :call-active="isCallActive"
        :show-assist="isCallActive && aiEnabled"
        :assist-result="assistResult"
        :assist-loading="assistLoading"
        :assist-error="assistError"
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
import { useAsr }          from './composables/useAsr'
import { useAnalysis }     from './composables/useAnalysis'
import { useAgentAssist }  from './composables/useAgentAssist'
import { useCallHistory }  from './composables/useCallHistory'
import { useCallNotify }   from './composables/useCallNotify'
import { useCallState }    from './composables/useCallState'
import { useJavaWs }       from './composables/useJavaWs'
import { useAgentStatus }  from './composables/useAgentStatus'
import { useAgentInfo }    from './composables/useAgentInfo'
import { ASR_WS_MSG_TYPE, AGENT_ASSIST_WS_MSG_TYPE } from './config/index'
import { formatDate }      from './utils/format'
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

// 重进话务平台通话中提示（需求4）
const showResumeAlert = ref(false)

// ── 通话状态机（模块级，切换路由不丢失） ────────────────────────────────────────
const { callPhase, isCallActive, canHangup, showCallInfo, toActive, toIdle, toReviewing } = useCallState()
const isReviewing = computed(() => callPhase.value === 'reviewing')
// 是否真正进入过通话
let hadRealCall = false
// 防止前端 hangup 与 call_state:idle 双重触发
let _hangupHandledSessionId = ''

// ── Composables（状态均为模块级单例） ─────────────────────────────────────────
const { analysisResult, isAnalyzing, analysisError, addRealtimeIntent, mergeAgentAssist, loadFromSummary, markAnalyzing, setError: setAnalysisError, reset: resetAnalysis } = useAnalysis()
const { asrState, asrMessages, startAsr, stopAsr, setSessionId, clearMessages, setMessages } = useAsr({ callerNameRef: callerName as any })
const { assistResult, assistLoading, assistError, updateFromWs: updateAssistFromWs, reset: resetAssist, setEnabled: setAssistEnabled } = useAgentAssist()
const javaWs = useJavaWs()

// ── 通话会话 ID ───────────────────────────────────────────────────────────────
const callSessionId = ref('')

// ── AI 开关处理（需求6/11） ───────────────────────────────────────────────────
function onAiToggle(val: boolean): void {
  aiEnabled.value = val
  setAssistEnabled(val)
}

// ── WS 订阅（定义为具名函数，onBeforeUnmount 时可取消，防止重复订阅） ────────────

function _onCallState(payload: any): void {
  if (payload.state === 'active') {
    hadRealCall = true
    toActive()
    resetAnalysis()
  }
  if (payload.state === 'idle') {
    // linphone 挂断 → 自动执行前端挂断逻辑（需求5）
    // 前端主动挂断已处理过则跳过
    if (_hangupHandledSessionId && _hangupHandledSessionId === callSessionId.value) {
      _hangupHandledSessionId = ''
      hadRealCall = false
      return
    }
    _doHangupCleanup('remote')
  }
}

function _onCallSession(payload: any): void {
  if (payload.call_session_id) {
    callSessionId.value = String(payload.call_session_id)
    setSessionId(callSessionId.value)
  }
}

function _onAsrResult(payload: any): void {
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
    ? payload.keywords.map((item: any) => String(item)).filter(Boolean)
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
}

function _onAgentAssist(payload: any): void {
  if (!aiEnabled.value) return
  updateAssistFromWs(payload)
  mergeAgentAssist(payload as Record<string, unknown>)
}

javaWs.subscribe('call_state',  _onCallState)
javaWs.subscribe('call_session', _onCallSession)
javaWs.subscribe(ASR_WS_MSG_TYPE, _onAsrResult)
javaWs.subscribe(AGENT_ASSIST_WS_MSG_TYPE, _onAgentAssist)

// ── 坐席状态 ──────────────────────────────────────────────────────────────────
const { agentStatus, agentStatusLabel, agentStatusColor, statusMeta, setStatus: setAgentStatus, syncWithCallPhase, setRinging, resetFromRinging } = useAgentStatus()
watch(callPhase, (phase) => { syncWithCallPhase(phase) })

// ── 通话记录 ──────────────────────────────────────────────────────────────────
const callHistory = useCallHistory()
const {
  visibleRecords: callVisibleRecords, totalCount: callTotalCount, expanded: callExpanded,
  isLoading: callHistoryLoading, isLoadingMore: callHistoryLoadingMore, hasMore: callHistoryHasMore,
  fetchList: fetchCallList, fetchMore: fetchCallMore, fetchTurns: fetchCallTurns,
  fetchTurnsWithAudio: fetchCallTurnsWithAudio,
  fetchDetail: fetchCallDetail, selectOnly: selectCallOnly, refresh: refreshCallList, updateRecordNote: updateCallRecordNote,
} = callHistory

// ── 来电通知 ──────────────────────────────────────────────────────────────────
const { acceptedCall, answeredCall, rejectedFromRinging, incomingCall, clearAccepted, clearAnswered, clearRejectedFromRinging } = useCallNotify()
const hasIncomingCall = computed(() => incomingCall.value !== null && !isCallActive.value)

watch(incomingCall, (call) => { if (call) setRinging() })

watch(acceptedCall, async (call) => {
  if (!call) return
  callerName.value  = call.callerName
  phoneNumber.value = call.phone
  await acceptCall()
  clearAccepted()
})

watch(answeredCall, async (call) => {
  if (!call) return
  callerName.value  = call.callerName
  phoneNumber.value = call.phone
  await acceptCall()
  clearAnswered()
})

watch(rejectedFromRinging, (rejected) => {
  if (rejected) { resetFromRinging(); clearRejectedFromRinging() }
})

// ── 计时器 ────────────────────────────────────────────────────────────────────
let durationTimer: ReturnType<typeof setInterval> | null = null
let noteSaveTimer: ReturnType<typeof setTimeout>  | null = null
let toastTimer:    ReturnType<typeof setTimeout>  | null = null

function startCallTimer(): void {
  if (durationTimer) clearInterval(durationTimer)
  duration.value = 0
  toActive()
  durationTimer = setInterval(() => { duration.value++ }, 1000)
}

onMounted(() => {
  console.info('[CustomerServiceView] onMounted')
  fetchCallList()
  // 重进话务平台时，若状态已是 active（模块级状态保留），恢复计时并提示（需求4）
  if (isCallActive.value) {
    showResumeAlert.value = true
    if (!durationTimer) {
      durationTimer = setInterval(() => { duration.value++ }, 1000)
    }
  }
})

onBeforeUnmount(() => {
  // 取消 WS 订阅，防止组件重建后重复订阅（需求3）
  javaWs.unsubscribe('call_state',   _onCallState)
  javaWs.unsubscribe('call_session', _onCallSession)
  javaWs.unsubscribe(ASR_WS_MSG_TYPE, _onAsrResult)
  javaWs.unsubscribe(AGENT_ASSIST_WS_MSG_TYPE, _onAgentAssist)
  // 只清理计时器，不清 ASR/分析结果（状态模块级，离开时保留）
  if (durationTimer) { clearInterval(durationTimer); durationTimer = null }
  if (noteSaveTimer) clearTimeout(noteSaveTimer)
  if (toastTimer)    clearTimeout(toastTimer)
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
  if (callSessionId.value) updateCallRecordNote(callSessionId.value, callNote.value)
}

async function acceptCall(): Promise<void> {
  startAsr()
  startCallTimer()
  showToast('已接通来电')
}

// 统一挂断清理逻辑（需求5：linphone/前端挂断走同一路径）
function _doHangupCleanup(source: 'local' | 'remote'): void {
  if (durationTimer) { clearInterval(durationTimer); durationTimer = null }
  stopAsr()
  clearMessages()
  resetAssist()
  toIdle()
  // 切回 transcript tab，避免通话后停留在分析 tab
  activeTab.value = 'transcript'

  if (hadRealCall) {
    if (aiEnabled.value) markAnalyzing()
    if (callSessionId.value && savedNote.value) updateCallRecordNote(callSessionId.value, savedNote.value)
  }
  savedNote.value = ''
  callNote.value  = ''
  const endedSessionId = callSessionId.value
  callSessionId.value  = ''
  hadRealCall = false

  fetchCallList()
  if (endedSessionId && aiEnabled.value) loadSummaryForSession(endedSessionId)
}

// 前端主动挂断（需求5：只通知后端，不再调 FreeSWITCH，后端负责）
async function hangupCall(): Promise<void> {
  showToast('通话已挂断')
  _hangupHandledSessionId = callSessionId.value
  // 通知后端（后端负责通知 FreeSWITCH，前端不再直接调）
  if (callSessionId.value) {
    javaWs.send({ type: 'call_response', call_id: callSessionId.value, action: 'hangup' })
  }
  _doHangupCleanup('local')
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

// 进入历史回溯前保存实时状态快照，退出时恢复
interface LiveSnapshot {
  messages:    typeof asrMessages.value
  callerName:  string
  phoneNumber: string
  duration:    number
  activeTab:   'transcript' | 'analysis'
}
let _liveSnapshot: LiveSnapshot | null = null

async function onRecordSelect(id: string): Promise<void> {
  if (isCallActive.value) { showToast('通话进行中，请挂断后再查看历史记录'); return }

  // 首次进入回溯时保存实时快照（已在回溯中切换记录则不重复保存）
  if (callPhase.value !== 'reviewing') {
    _liveSnapshot = {
      messages:    [...asrMessages.value],
      callerName:  callerName.value,
      phoneNumber: phoneNumber.value,
      duration:    duration.value,
      activeTab:   activeTab.value,
    }
  }

  toReviewing()
  selectCallOnly(id)
  resetAnalysis()
  clearMessages()
  cachedDetail.value = null
  activeTab.value = 'transcript'

  const [detail, turns] = await Promise.all([fetchCallDetail(id), fetchCallTurnsWithAudio(id)])
  if (detail) {
    callerName.value   = detail.customer_name
    phoneNumber.value  = detail.phone
    duration.value     = detail.duration_sec ?? 0
    cachedDetail.value = detail
  }
  if (turns.length > 0) setMessages(turnsToMessages(turns, detail?.customer_name ?? callerName.value))
}

function exitReviewing(): void {
  if (_liveSnapshot) {
    setMessages(_liveSnapshot.messages)
    callerName.value  = _liveSnapshot.callerName
    phoneNumber.value = _liveSnapshot.phoneNumber
    duration.value    = _liveSnapshot.duration
    activeTab.value   = _liveSnapshot.activeTab
    _liveSnapshot = null
  } else {
    clearMessages()
    callerName.value  = ''
    phoneNumber.value = ''
    duration.value    = 0
    activeTab.value   = 'transcript'
  }
  resetAnalysis()
  cachedDetail.value = null
  selectCallOnly('')
  toIdle()
}

watch(activeTab, (tab) => {
  if (tab !== 'analysis' || isCallActive.value || !cachedDetail.value) return
  loadFromSummary(cachedDetail.value.summary)
})

async function loadSummaryForSession(sessionId: string, attempt = 0): Promise<void> {
  const maxAttempts = 10
  const DELAYS = [500, 1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000]
  try {
    const detail = await fetchCallDetail(sessionId)
    if (detail?.summary) {
      cachedDetail.value = detail
      loadFromSummary(detail.summary)
      fetchCallList()
      return
    }
  } catch (err: any) {
    if (attempt >= maxAttempts - 1) {
      setAnalysisError(err?.message || '会话总结加载失败')
      fetchCallList()
      return
    }
  }
  if (attempt >= maxAttempts - 1) {
    setAnalysisError('会话总结生成超时，请稍后在历史记录中查看')
    fetchCallList()
    return
  }
  setTimeout(() => loadSummaryForSession(sessionId, attempt + 1), DELAYS[attempt] ?? 5000)
}
</script>

<style scoped>
* { box-sizing: border-box; margin: 0; padding: 0; }
.cs-container { display: flex; width: 100%; height: 100vh; background: #f0f4fa; font-family: "PingFang SC","Microsoft YaHei",sans-serif; font-size: 14px; color: #333; position: relative; overflow: hidden; }
.cs-main { flex: 1; display: flex; flex-direction: column; border: 2px dashed #7cb8f5; margin: 16px; background: #fff; border-radius: 8px; overflow: hidden; min-height: 0; }
.cs-conversation { flex: 1; display: flex; flex-direction: column; padding: 14px 20px; overflow: hidden; min-height: 0; }
.cs-call-panel { width: 300px; background: #fff; border-left: 1px solid #e2e8f0; display: flex; flex-direction: column; padding: 20px 16px; gap: 16px; overflow: hidden; }
.cs-action-btns { display: flex; gap: 10px; flex-shrink: 0; }
.cs-btn-hangup { width: 100%; padding: 10px 0; border: none; border-radius: 8px; font-size: 13px; font-weight: 600; cursor: pointer; background: linear-gradient(135deg, #ef4444, #dc2626); color: #fff; display: flex; align-items: center; justify-content: center; gap: 6px; }
.cs-btn-hangup:hover { background: linear-gradient(135deg, #dc2626, #b91c1c); }
.cs-divider-icon { position: absolute; left: calc(100% - 300px - 20px); top: 50%; transform: translateY(-50%); width: 32px; height: 32px; background: #fff; border: 1px solid #e2e8f0; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 16px; box-shadow: 0 2px 6px rgba(0,0,0,0.08); z-index: 10; }
.cs-toast { position: fixed; bottom: 30px; left: 50%; transform: translateX(-50%); background: rgba(30,41,59,0.88); color: #fff; padding: 10px 22px; border-radius: 20px; font-size: 13px; z-index: 999; pointer-events: none; white-space: nowrap; }
.toast-enter-active, .toast-leave-active { transition: opacity 0.3s, transform 0.3s; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateX(-50%) translateY(10px); }
/* 通话恢复提示 */
.cs-resume-alert { position: fixed; top: 0; left: 0; right: 0; z-index: 2000; background: #1d4ed8; color: #fff; padding: 10px 16px; font-size: 13px; font-weight: 600; display: flex; align-items: center; justify-content: center; gap: 12px; }
.cs-resume-close { background: transparent; border: 1px solid rgba(255,255,255,0.5); color: #fff; border-radius: 4px; padding: 2px 8px; cursor: pointer; font-size: 14px; }
.cs-resume-close:hover { background: rgba(255,255,255,0.15); }
/* 回溯模式提示条 */
.cs-reviewing-bar { display: flex; align-items: center; justify-content: space-between; background: #fef9c3; border: 1px solid #fde68a; border-radius: 6px; padding: 6px 12px; margin-bottom: 8px; font-size: 12px; color: #92400e; flex-shrink: 0; }
.cs-reviewing-back { background: #fff; border: 1px solid #f59e0b; color: #b45309; border-radius: 4px; padding: 3px 10px; font-size: 12px; cursor: pointer; font-weight: 500; }
.cs-reviewing-back:hover { background: #fef3c7; }
</style>
