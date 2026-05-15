import { ref, readonly, computed } from 'vue'
import type { WsStateEnum } from '../types'

export interface JavaWsPayload {
  type: string
  [key: string]: unknown
}

type MessageHandler = (payload: JavaWsPayload) => void

const _wsState        = ref<WsStateEnum>('idle')
const _wsError        = ref<string | null>(null)
const _reconnectCount = ref(0)
const _showReloadHint = ref(false)

const _subscribers = new Map<string, Set<MessageHandler>>()

let _ws:                   WebSocket | null = null
let _wsUrl:                string = ''
let _token:                string = ''
let _authMode:             'query' | 'message' = 'query'
let _heartbeatIntervalMs:  number = 20000
let _heartbeatTimeoutMs:   number = 60000
let _reconnectDelayMs:     number = 1000
let _maxReconnectDelayMs:  number = 30000
let _maxReconnectAttempts: number = 10

let _heartbeatTimer:        ReturnType<typeof setInterval>  | null = null
let _heartbeatTimeoutTimer: ReturnType<typeof setTimeout>   | null = null
let _reconnectTimer:        ReturnType<typeof setTimeout>   | null = null
let _lastMessageAt = 0
let _intentionalClose = false
let _visibilityListenerAdded = false
let _onlineListenerAdded = false

const TAG = '[JavaWs]'

function _setState(next: WsStateEnum): void {
  if (_wsState.value === next) return
  _wsState.value = next
}

function _clearTimers(): void {
  if (_heartbeatTimer)        { clearInterval(_heartbeatTimer);        _heartbeatTimer = null }
  if (_heartbeatTimeoutTimer) { clearTimeout(_heartbeatTimeoutTimer);  _heartbeatTimeoutTimer = null }
  if (_reconnectTimer)        { clearTimeout(_reconnectTimer);         _reconnectTimer = null }
}

function _resetHeartbeatWatch(): void {
  _lastMessageAt = Date.now()
  if (_heartbeatTimeoutTimer) clearTimeout(_heartbeatTimeoutTimer)
  _heartbeatTimeoutTimer = setTimeout(() => {
    const silent = Date.now() - _lastMessageAt
    if (silent >= _heartbeatTimeoutMs && _ws?.readyState === WebSocket.OPEN) {
      _wsError.value = '心跳超时，正在重连'
      _ws?.close()
    }
  }, _heartbeatTimeoutMs)
}

function _startHeartbeat(): void {
  _heartbeatTimer = setInterval(() => {
    if (_ws?.readyState === WebSocket.OPEN) {
      _ws.send(JSON.stringify({ type: 'ping', ts: Date.now() }))
    }
  }, _heartbeatIntervalMs)
}

function _stopHeartbeat(): void {
  if (_heartbeatTimer) { clearInterval(_heartbeatTimer); _heartbeatTimer = null }
  if (_heartbeatTimeoutTimer) { clearTimeout(_heartbeatTimeoutTimer); _heartbeatTimeoutTimer = null }
}

function _publish(payload: JavaWsPayload): void {
  const handlers = _subscribers.get(payload.type)
  if (handlers) handlers.forEach(fn => fn(payload))
  const wildcard = _subscribers.get('*')
  if (wildcard) wildcard.forEach(fn => fn(payload))
}

function _scheduleReconnect(): void {
  if (_intentionalClose) return
  if (_reconnectCount.value >= _maxReconnectAttempts) {
    _setState('error')
    _wsError.value = '连接失败次数过多，请刷新页面'
    _showReloadHint.value = true
    return
  }
  const delay = Math.min(
    _reconnectDelayMs * Math.pow(2, _reconnectCount.value),
    _maxReconnectDelayMs,
  )
  _reconnectCount.value += 1
  _setState('reconnecting')
  _reconnectTimer = setTimeout(() => {
    if (!_intentionalClose) _doConnect()
  }, delay)
}

function _doConnect(): void {
  if (!_wsUrl) return
  if (_ws && (_ws.readyState === WebSocket.OPEN || _ws.readyState === WebSocket.CONNECTING)) return

  _setState('connecting')
  let connectUrl = _wsUrl
  if (_token && _authMode === 'query') {
    const sep = connectUrl.includes('?') ? '&' : '?'
    connectUrl = `${connectUrl}${sep}token=${encodeURIComponent(_token)}`
  }
  console.info(`${TAG} 正在连接:`, connectUrl)
  const ws = new WebSocket(connectUrl)
  _ws = ws

  ws.onopen = () => {
    if (_ws !== ws) return
    _reconnectCount.value = 0
    _wsError.value = null
    _showReloadHint.value = false
    _setState('active')
    _resetHeartbeatWatch()
    _startHeartbeat()
    if (_token && _authMode === 'message') {
      ws.send(JSON.stringify({ type: 'auth', token: _token }))
    }
  }

  ws.onmessage = (ev: MessageEvent) => {
    if (_ws !== ws) return
    _lastMessageAt = Date.now()
    _resetHeartbeatWatch()
    if (typeof ev.data !== 'string') return
    try {
      const payload = JSON.parse(ev.data) as JavaWsPayload
      if (payload.type === 'pong') return
      console.info(`${TAG} 收到消息:`, payload)
      if (payload.type === 'connected') {
        console.info(`${TAG} 服务端确认连接, userId:`, (payload as any).userId)
        return
      }
      _publish(payload)
    } catch {
      console.warn(`${TAG} 消息解析失败:`, ev.data)
    }
  }

  ws.onerror = () => {
    if (_ws !== ws) return
    _wsError.value = 'WebSocket 连接错误'
  }

  ws.onclose = (ev: CloseEvent) => {
    if (_ws !== ws) return
    _clearTimers()
    _ws = null
    if (_intentionalClose) {
      _setState('idle')
    } else {
      _wsError.value = `连接已断开 (${ev.code})`
      _scheduleReconnect()
    }
  }
}

function _ensureGlobalListeners(): void {
  if (!_visibilityListenerAdded) {
    _visibilityListenerAdded = true
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') {
        _stopHeartbeat()
      } else {
        if (_ws?.readyState === WebSocket.OPEN) {
          _ws.send(JSON.stringify({ type: 'ping', ts: Date.now() }))
          _startHeartbeat()
          _resetHeartbeatWatch()
        } else if (!_intentionalClose && _wsUrl && _wsState.value !== 'connecting') {
          _reconnectCount.value = 0
          _doConnect()
        }
      }
    })
  }
  if (!_onlineListenerAdded) {
    _onlineListenerAdded = true
    window.addEventListener('online', () => {
      if (!_intentionalClose && _wsUrl &&
          (_wsState.value === 'reconnecting' || _wsState.value === 'error' || _wsState.value === 'idle')) {
        _showReloadHint.value = false
        _reconnectCount.value = 0
        if (_reconnectTimer) { clearTimeout(_reconnectTimer); _reconnectTimer = null }
        _doConnect()
      }
    })
  }
}

export function useJavaWs() {
  const isConnected = computed(() => _wsState.value === 'active')

  function connect(config?: {
    wsUrl:                string
    token?:               string
    authMode?:            'query' | 'message'
    heartbeatIntervalMs?:  number
    heartbeatTimeoutMs?:   number
    reconnectDelayMs?:     number
    maxReconnectDelayMs?:  number
    maxReconnectAttempts?: number
  }): void {
    if (config) {
      _wsUrl                = config.wsUrl
      _token                = config.token               ?? _token
      _authMode             = config.authMode            ?? _authMode
      _heartbeatIntervalMs  = config.heartbeatIntervalMs  ?? _heartbeatIntervalMs
      _heartbeatTimeoutMs   = config.heartbeatTimeoutMs   ?? _heartbeatTimeoutMs
      _reconnectDelayMs     = config.reconnectDelayMs     ?? _reconnectDelayMs
      _maxReconnectDelayMs  = config.maxReconnectDelayMs  ?? _maxReconnectDelayMs
      _maxReconnectAttempts = config.maxReconnectAttempts ?? _maxReconnectAttempts
    }
    if (!_wsUrl) return
    _intentionalClose = false
    _ensureGlobalListeners()
    _doConnect()
  }

  function disconnect(): void {
    _intentionalClose = true
    _clearTimers()
    _ws?.close()
    _ws = null
    _reconnectCount.value = 0
    _setState('idle')
  }

  function send(payload: JavaWsPayload): void {
    if (_ws?.readyState !== WebSocket.OPEN) {
      console.warn('[JavaWs] send 跳过: WS 未连接, readyState=', _ws?.readyState)
      return
    }
    const json = JSON.stringify(payload)
    console.log('[JavaWs] send →', json)
    _ws.send(json)
  }

  function subscribe(type: string, handler: MessageHandler): void {
    if (!_subscribers.has(type)) _subscribers.set(type, new Set())
    _subscribers.get(type)!.add(handler)
  }

  function unsubscribe(type: string, handler: MessageHandler): void {
    _subscribers.get(type)?.delete(handler)
  }

  return {
    wsState:        readonly(_wsState),
    wsError:        readonly(_wsError),
    reconnectCount: readonly(_reconnectCount),
    showReloadHint: readonly(_showReloadHint),
    isConnected,
    connect,
    disconnect,
    send,
    subscribe,
    unsubscribe,
  }
}
