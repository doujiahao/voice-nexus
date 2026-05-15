import { ref, computed, readonly } from 'vue'
import { useJavaWs } from './useJavaWs'
import { getAgentStatus, updateAgentStatus } from '/@/api/call'
import type { AgentStatusEnum } from '/@/api/call/model/callModel'
import type { AgentStatus } from '../types'

export type { AgentStatus }

const _status = ref<AgentStatus>('idle')
let _initialized = false

const STATUS_META: Record<AgentStatus, { label: string; color: string; dotColor: string }> = {
  idle:     { label: '空闲',   color: '#22c55e', dotColor: '#22c55e' },
  on_call:  { label: '通话中', color: '#3b82f6', dotColor: '#3b82f6' },
  ringing:  { label: '振铃中', color: '#a855f7', dotColor: '#a855f7' },
  wrap_up:  { label: '话后整理', color: '#6366f1', dotColor: '#6366f1' },
  busy:     { label: '忙碌',   color: '#f97316', dotColor: '#f97316' },
  offline:  { label: '离线',   color: '#94a3b8', dotColor: '#94a3b8' },
}

export const AGENT_STATUS_MANUAL: AgentStatus[] = ['idle', 'busy', 'offline']

const TO_BACKEND: Record<AgentStatus, AgentStatusEnum> = {
  idle:     'ONLINE',
  on_call:  'TALKING',
  ringing:  'RINGING',
  wrap_up:  'WRAP_UP',
  busy:     'REST',
  offline:  'OFFLINE',
}

const FROM_BACKEND: Record<string, AgentStatus> = {
  ONLINE:   'idle',
  OFFLINE:  'offline',
  REST:     'busy',
  RINGING:  'ringing',
  TALKING:  'on_call',
  HOLDING:  'on_call',
  WRAP_UP:  'wrap_up',
}

function _handleServerStatus(payload: { status?: unknown }): void {
  const raw = payload.status
  if (raw == null) return
  const asStr = String(raw)
  if (asStr in STATUS_META) {
    _status.value = asStr as AgentStatus
    return
  }
  const mapped = FROM_BACKEND[asStr]
  if (mapped) _status.value = mapped
}

export function useAgentStatus() {
  const javaWs = useJavaWs()

  async function init(): Promise<void> {
    if (_initialized) return
    _initialized = true
    javaWs.subscribe('agent_status', _handleServerStatus as any)
    await fetchStatus()
  }

  async function fetchStatus(): Promise<void> {
    try {
      const res = await getAgentStatus()
      const backendCode = (res as any).code === 200 ? (res as any).result : (res as any).status
      if (backendCode) {
        const mapped = FROM_BACKEND[String(backendCode)]
        if (mapped) _status.value = mapped
      }
    } catch (err: any) {
      console.warn('[AgentStatus] 获取坐席状态失败:', err.message)
    }
  }

  const agentStatusLabel    = computed(() => STATUS_META[_status.value].label)
  const agentStatusColor    = computed(() => STATUS_META[_status.value].color)
  const agentStatusDotColor = computed(() => STATUS_META[_status.value].dotColor)

  function setRinging(): void {
    if (_status.value === 'idle' || _status.value === 'busy') {
      _status.value = 'ringing'
    }
  }

  function resetFromRinging(): void {
    if (_status.value === 'ringing') {
      _status.value = 'idle'
    }
  }

  function setWrapUp(): void {
    if (_status.value === 'on_call') {
      _status.value = 'wrap_up'
    }
  }

  function finishWrapUp(): void {
    if (_status.value === 'wrap_up') {
      _status.value = 'idle'
      javaWs.send({ type: 'agent_status_update', status: 'idle' })
      updateAgentStatus(TO_BACKEND.idle, '话后整理完成')
        .catch((err: any) => console.warn('[AgentStatus] 话后整理完成状态同步失败:', err.message))
    }
  }

  async function setStatus(status: AgentStatus): Promise<void> {
    if (status === 'on_call' || status === 'ringing') return
    const prev = _status.value
    _status.value = status
    javaWs.send({ type: 'agent_status_update', status })
    try {
      await updateAgentStatus(TO_BACKEND[status])
    } catch (err: any) {
      console.error('[AgentStatus] 更新坐席状态失败，回滚:', err.message)
      _status.value = prev
    }
  }

  async function syncWithCallPhase(phase: string): Promise<void> {
    if (phase === 'active') {
      _status.value = 'on_call'
      javaWs.send({ type: 'agent_status_update', status: 'on_call' })
      try {
        await updateAgentStatus(TO_BACKEND.on_call, '坐席接听')
      } catch (err: any) {
        console.warn('[AgentStatus] 同步通话状态失败:', err.message)
      }
    } else if (phase === 'idle' && _status.value === 'on_call') {
      _status.value = 'idle'
      javaWs.send({ type: 'agent_status_update', status: 'idle' })
      try {
        await updateAgentStatus(TO_BACKEND.idle, '通话结束')
      } catch (err: any) {
        console.warn('[AgentStatus] 同步空闲状态失败:', err.message)
      }
    }
  }

  return {
    agentStatus:        readonly(_status),
    agentStatusLabel,
    agentStatusColor,
    agentStatusDotColor,
    statusMeta:         STATUS_META,
    manualOptions:      AGENT_STATUS_MANUAL,
    init,
    fetchStatus,
    setStatus,
    setRinging,
    resetFromRinging,
    setWrapUp,
    finishWrapUp,
    syncWithCallPhase,
  }
}
