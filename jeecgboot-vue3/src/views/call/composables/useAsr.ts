import { ref, readonly } from 'vue'
import type { ChatMessage } from '../types'

// 模块级单例，切换路由后状态不丢失
const _asrState    = ref<'idle' | 'active' | 'error'>('idle')
const _asrMessages = ref<ChatMessage[]>([])
let _sessionId = ''

export function useAsr(_options?: {
  callerNameRef?: { value: string }
  customerSpeakerRef?: { value: unknown }
}) {
  function startAsr():                       void { _asrState.value = 'active' }
  function stopAsr():                        void { _asrState.value = 'idle' }
  function setSessionId(id: string):         void { _sessionId = id }
  function clearMessages():                  void { _asrMessages.value = [] }
  function setMessages(msgs: ChatMessage[]): void { _asrMessages.value = msgs }

  return {
    asrState:    readonly(_asrState),
    asrMessages: _asrMessages,
    startAsr,
    stopAsr,
    setSessionId,
    clearMessages,
    setMessages,
  }
}
