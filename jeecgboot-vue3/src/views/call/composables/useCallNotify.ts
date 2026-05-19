import { ref, readonly } from 'vue'
import { useJavaWs } from './useJavaWs'
import type { IncomingCallData } from '../types'

const _incomingCall  = ref<IncomingCallData | null>(null)
const _queue: IncomingCallData[] = []
const _acceptedCall  = ref<IncomingCallData | null>(null)
const _answeredCall  = ref<IncomingCallData | null>(null)
const _rejectedFromRinging = ref(false)
let _initialized = false

// incoming_call_pending 缓存：收到预告不弹窗，等 incoming_call 再弹
let _pendingCall: IncomingCallData | null = null
let _pendingTimeout: ReturnType<typeof setTimeout> | null = null

const PENDING_TIMEOUT_MS = 25_000

function _next(): void {
  _incomingCall.value = _queue.shift() ?? null
}

function _clearPendingTimeout(): void {
  if (_pendingTimeout) { clearTimeout(_pendingTimeout); _pendingTimeout = null }
}

function _handleIncomingCallPending(msg: Record<string, unknown>): void {
  console.log('[CallNotify] 收到来电预告 WS 消息 (不弹窗):', msg)
  const call: IncomingCallData = {
    callId:     String(msg.call_id ?? ''),
    phone:      String(msg.phone ?? ''),
    callerName: String(msg.caller_name ?? ''),
    fsCallId:   String(msg.fs_call_id ?? ''),
    location:   msg.location ? String(msg.location) : undefined,
  }
  _pendingCall = call
  _clearPendingTimeout()
  _pendingTimeout = setTimeout(() => {
    if (_pendingCall) {
      console.warn('[CallNotify] incoming_call_pending 超时 25s，自动弹窗')
      _handleIncomingCall(msg)
      _pendingCall = null
    }
  }, PENDING_TIMEOUT_MS)
}

function _handleIncomingCall(msg: Record<string, unknown>): void {
  console.log('[CallNotify] 收到来电 WS 消息:', msg)
  _clearPendingTimeout()
  // 如果有匹配的 pending，清除它（正式消息已到）
  if (_pendingCall) {
    const pendingFsCallId = _pendingCall.fsCallId
    const msgFsCallId = String(msg.fs_call_id ?? '')
    if (pendingFsCallId && msgFsCallId && pendingFsCallId === msgFsCallId) {
      _pendingCall = null
    }
  }
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

  // 清理对应的 pending
  if (_pendingCall && matches(_pendingCall)) {
    _clearPendingTimeout()
    _pendingCall = null
  }

  if (_incomingCall.value && matches(_incomingCall.value)) {
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

  function init(): void {
    if (_initialized) {
      console.info('[CallNotify] init 已初始化，跳过')
      return
    }
    _initialized = true
    javaWs.subscribe('incoming_call_pending', _handleIncomingCallPending as any)
    javaWs.subscribe('incoming_call', _handleIncomingCall as any)
    javaWs.subscribe('incoming_call_cancelled', _handleIncomingCallCancelled as any)
    javaWs.subscribe('incoming_call_answered', _handleIncomingCallAnswered as any)
    console.info('[CallNotify] init 完成，已订阅 incoming_call_pending / incoming_call / incoming_call_cancelled / incoming_call_answered')
  }

  function accept(): void {
    if (!_incomingCall.value) return
    console.log('[CallNotify] 接听来电, callId:', _incomingCall.value.callId)
    javaWs.send({ type: 'call_response', call_id: _incomingCall.value.callId, action: 'accept' })
    _acceptedCall.value = _incomingCall.value
    _next()
  }

  function reject(): void {
    if (!_incomingCall.value) return
    console.log('[CallNotify] 拒接来电, callId:', _incomingCall.value.callId)
    javaWs.send({ type: 'call_response', call_id: _incomingCall.value.callId, action: 'reject' })
    _rejectedFromRinging.value = true
    _next()
  }

  function clearAccepted(): void {
    _acceptedCall.value = null
  }

  function clearAnswered(): void {
    _answeredCall.value = null
  }

  function clearRejectedFromRinging(): void {
    _rejectedFromRinging.value = false
  }

  return {
    incomingCall: readonly(_incomingCall),
    acceptedCall: readonly(_acceptedCall),
    answeredCall: readonly(_answeredCall),
    rejectedFromRinging: readonly(_rejectedFromRinging),
    init,
    accept,
    reject,
    clearAccepted,
    clearAnswered,
    clearRejectedFromRinging,
  }
}
