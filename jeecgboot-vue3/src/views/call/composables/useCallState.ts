import { ref, computed, readonly } from 'vue'

export type CallPhase = 'idle' | 'active' | 'reviewing'

export function useCallState() {
  const _phase = ref<CallPhase>('idle')

  function _transition(next: CallPhase): void {
    if (_phase.value === next) return
    _phase.value = next
  }

  const callPhase    = readonly(_phase)
  const isCallActive = computed(() => _phase.value === 'active')
  const canHangup    = computed(() => _phase.value === 'active')
  const canAccept    = computed(() => _phase.value !== 'active')
  const showCallInfo = computed(() => _phase.value !== 'idle')

  function toActive():    void { _transition('active') }
  function toIdle():      void { _transition('idle') }
  function toReviewing(): void { _transition('reviewing') }

  return { callPhase, isCallActive, canHangup, canAccept, showCallInfo, toActive, toIdle, toReviewing }
}
