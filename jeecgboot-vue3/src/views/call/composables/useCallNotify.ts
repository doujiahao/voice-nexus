import { ref, readonly } from 'vue'
import { useJavaWs } from './useJavaWs'
import type { IncomingCallData } from '../types'

const _incomingCall  = ref<IncomingCallData | null>(null)
const _queue: IncomingCallData[] = []
const _acceptedCall  = ref<IncomingCallData | null>(null)
const _answeredCall  = ref<IncomingCallData | null>(null)
const _rejectedFromRinging = ref(false)
let _initialized = false

function _next(): void {
  _incomingCall.value = _queue.shift() ?? null
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
    if (_initialized) return
    _initialized = true
    javaWs.subscribe('incoming_call', _handleIncomingCall as any)
    javaWs.subscribe('incoming_call_cancelled', _handleIncomingCallCancelled as any)
    javaWs.subscribe('incoming_call_answered', _handleIncomingCallAnswered as any)
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
