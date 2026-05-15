<template>
  <div class="icc-wrap">
    <div class="icc-icon-wrap">
      <div class="icc-icon" :class="{ 'icc-icon--ringing': hasIncoming }">📞</div>
      <div class="icc-badge">{{ callerName || '用户' }}</div>
    </div>
    <div class="icc-label" :class="labelClass">
      {{ label }}
    </div>
    <div v-if="phoneNumber" class="icc-number">{{ phoneNumber }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  phoneNumber?: string
  callActive?: boolean
  hasIncoming?: boolean
  callerName?: string
}
const props = withDefaults(defineProps<Props>(), { phoneNumber: '', callActive: false, hasIncoming: false, callerName: '' })

const label = computed(() => {
  if (props.hasIncoming) return '来电振铃中...'
  if (props.callActive) return '通话中'
  return '来电'
})

const labelClass = computed(() => ({
  'icc-label--active': props.callActive,
  'icc-label--ringing': props.hasIncoming,
}))
</script>

<style scoped>
.icc-wrap { display: flex; flex-direction: column; align-items: center; padding: 16px 0 8px; }
.icc-icon-wrap { position: relative; margin-bottom: 8px; }
.icc-icon {
  width: 64px; height: 64px; background: #dbeafe;
  border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 28px;
}
.icc-badge {
  position: absolute; bottom: 0; right: 0;
  background: #22c55e; color: #fff; font-size: 10px;
  padding: 2px 5px; border-radius: 10px; font-weight: 600;
}
.icc-label { font-size: 12px; color: #94a3b8; margin-bottom: 4px; }
.icc-label--active { color: #2563eb; font-weight: 600; }
.icc-label--ringing { color: #f59e0b; font-weight: 600; }
.icc-icon--ringing { animation: ring-shake 0.6s ease-in-out infinite; }
@keyframes ring-shake { 0%,100% { transform: rotate(0); } 25% { transform: rotate(14deg); } 75% { transform: rotate(-14deg); } }
.icc-number { font-size: 22px; font-weight: 700; color: #1e293b; letter-spacing: 0.5px; }
</style>
