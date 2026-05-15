import { ref, readonly } from 'vue'
import type { ChatMessage } from '../types'

export function useAsr(_options?: {
  callerNameRef?: { value: string }
  customerSpeakerRef?: { value: unknown }
}) {
  const asrState    = ref<'idle' | 'active' | 'error'>('idle')
  const asrMessages = ref<ChatMessage[]>([])
  let _sessionId = ''

  function startAsr():                    void { asrState.value = 'active' }
  function stopAsr():                     void { asrState.value = 'idle' }
  function setSessionId(id: string):      void { _sessionId = id }
  function clearMessages():               void { asrMessages.value = [] }
  function setMessages(msgs: ChatMessage[]): void { asrMessages.value = msgs }

  return { asrState: readonly(asrState), asrMessages, startAsr, stopAsr, setSessionId, clearMessages, setMessages }
}
