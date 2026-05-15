import { ref, readonly } from 'vue'
import { useJavaWs } from './useJavaWs'
import type { IncomingCallData } from '../types'

const _incomingCall  = ref<IncomingCallData | null>(null)
const _queue: IncomingCallData[] = []
const _acceptedCall  = ref<IncomingCallData | null>(null)
const _answeredCall  = ref<IncomingCallData | null>(null)
const _autoRejected  = ref(false)
const _rejectedFromRinging = ref(false)
const RING_TIMEOUT_MS = 30_000
let _initialized = false
let _ringTimer: ReturnType<typeof setTimeout> | null = null
let _javaWsRef: ReturnType<typeof useJavaWs> | null = null

function _clearRingTimer(): void {
  if (_ringTimer) { clearTimeout(_ringTimer); _ringTimer = null }
}

function _getWs() {
  return _javaWsRef ?? useJavaWs()
}

function _startRingTimer(): void {
  _clearRingTimer()
  _ringTimer = setTimeout(() => {
    if (!_incomingCall.value) return
    _getWs().send({ type: 'call_response', call_id: _incomingCall.value.callId, action: 'reject' })
    _autoRejected.value = true
    _rejectedFromRinging.value = true
    _next()
  }, RING_TIMEOUT_MS)
}

function _next(): void {
  _clearRingTimer()
  _incomingCall.value = _queue.shift() ?? null
  if (_incomingCall.value) _startRingTimer()
}

function _handleIncomingCall(msg: Record<string, unknown>): void {
  console.log('[CallNotify] 收到来电 WS 消息:', msg)
  const call: IncomingCallData = {
    callId:     String(msg.call_id ?? ''),
    phone:      String(msg.phone ?? ''),
    callerName: String(msg.caller_name ?? ''),
    fsCallId:   String(msg.fs_call_id ?? ''),
    location:   msg.location ? String(msg.location) : undefined,
  }
  console.log('[CallNotify] 解析后来电数据:', call)
  if (_incomingCall.value === null) {
    _incomingCall.value = call
    _startRingTimer()
  } else {
    _queue.push(call)
  }
}

function _matchesCall(msg: Record<string, unknown>) {
  const callId = String(msg.call_id ?? '')
  const fsCallId = String(msg.fs_call_id ?? '')
  return (call: IncomingCallData) => (callId && call.callId === callId) || (fsCallId && call.fsCallId === fsCallId)
}

function _handleIncomingCallCancelled(msg: Record<string, unknown>): void {
  console.log('[CallNotify] 收到来电取消 WS 消息:', msg)
  const matches = _matchesCall(msg)

  if (_incomingCall.value && matches(_incomingCall.value)) {
    _clearRingTimer()
    _rejectedFromRinging.value = true
    _incomingCall.value = null
    _next()
    return
  }

  const index = _queue.findIndex(matches)
  if (index >= 0) _queue.splice(index, 1)
}

function _handleIncomingCallAnswered(msg: Record<string, unknown>): void {
  console.log('[CallNotify] 收到来电接听 WS 消息:', msg)
  const matches = _matchesCall(msg)

  if (_incomingCall.value && matches(_incomingCall.value)) {
    _clearRingTimer()
    _answeredCall.value = _incomingCall.value
    _incomingCall.value = null
    _next()
    return
  }

  const index = _queue.findIndex(matches)
  if (index >= 0) {
    _answeredCall.value = _queue[index]
    _queue.splice(index, 1)
  }
}

export function useCallNotify() {
  const javaWs = useJavaWs()
  _javaWsRef = javaWs

  function init(): void {
    if (_initialized) return
    _initialized = true
    javaWs.subscribe('incoming_call', _handleIncomingCall as any)
    javaWs.subscribe('incoming_call_cancelled', _handleIncomingCallCancelled as any)
    javaWs.subscribe('incoming_call_answered', _handleIncomingCallAnswered as any)
  }

  function accept(): void {
    if (!_incomingCall.value) return
    _clearRingTimer()
    _autoRejected.value = false
    console.log('[CallNotify] 接听来电, callId:', _incomingCall.value.callId)
    javaWs.send({ type: 'call_response', call_id: _incomingCall.value.callId, action: 'accept' })
    _acceptedCall.value = _incomingCall.value
    _next()
  }

  function reject(): void {
    if (!_incomingCall.value) return
    _clearRingTimer()
    _autoRejected.value = false
    console.log('[CallNotify] 拒接来电, callId:', _incomingCall.value.callId)
    javaWs.send({ type: 'call_response', call_id: _incomingCall.value.callId, action: 'reject' })
    // 拒接后坐席应从振铃恢复空闲（后端不会推送 call_state:idle）
    _rejectedFromRinging.value = true
    _next()
  }

  function clearAccepted(): void {
    _acceptedCall.value = null
  }

  function clearAnswered(): void {
    _answeredCall.value = null
  }

  function clearAutoRejected(): void {
    _autoRejected.value = false
  }

  function clearRejectedFromRinging(): void {
    _rejectedFromRinging.value = false
  }

  return {
    incomingCall: readonly(_incomingCall),
    acceptedCall: readonly(_acceptedCall),
    answeredCall: readonly(_answeredCall),
    autoRejected: readonly(_autoRejected),
    rejectedFromRinging: readonly(_rejectedFromRinging),
    init,
    accept,
    reject,
    clearAccepted,
    clearAnswered,
    clearAutoRejected,
    clearRejectedFromRinging,
  }
}
