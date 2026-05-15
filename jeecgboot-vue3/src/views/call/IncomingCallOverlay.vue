<template>
  <div class="ic-overlay">
    <div class="ic-card">
      <div class="ic-title">📞 来电通知</div>
      <div class="ic-avatar-wrap">
        <div class="ic-pulse" />
        <div class="ic-avatar">{{ nameInitial }}</div>
      </div>
      <div class="ic-name">{{ callerName || '未知来电' }}</div>
      <div class="ic-phone">{{ phone }}</div>
      <div v-if="location" class="ic-location">📍 {{ location }}</div>
      <div class="ic-actions">
        <button class="ic-btn ic-btn-reject" @click="$emit('reject')">
          <span class="ic-btn-icon">✕</span>拒接
        </button>
        <button class="ic-btn ic-btn-accept" @click="$emit('accept')">
          <span class="ic-btn-icon">📞</span>接听
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props { callerName?: string; phone?: string; location?: string }
const props = withDefaults(defineProps<Props>(), { callerName: '', phone: '', location: '' })
defineEmits<{ accept: []; reject: [] }>()
const nameInitial = computed(() => props.callerName ? props.callerName.charAt(0) : '?')
</script>

<style scoped>
.ic-overlay { position: fixed; inset: 0; z-index: 1000; display: flex; align-items: center; justify-content: center; background: rgba(0,0,0,0.55); backdrop-filter: blur(6px); }
.ic-card { width: 320px; background: #fff; border-radius: 20px; padding: 36px 28px 32px; display: flex; flex-direction: column; align-items: center; gap: 16px; box-shadow: 0 24px 60px rgba(0,0,0,0.25); animation: card-in 0.3s cubic-bezier(0.34,1.56,0.64,1) both; }
@keyframes card-in { from { opacity: 0; transform: scale(0.85) translateY(20px); } to { opacity: 1; transform: scale(1) translateY(0); } }
.ic-title { font-size: 14px; font-weight: 600; color: #94a3b8; letter-spacing: 0.5px; }
.ic-avatar-wrap { position: relative; width: 88px; height: 88px; display: flex; align-items: center; justify-content: center; }
.ic-pulse { position: absolute; inset: -10px; border-radius: 50%; background: rgba(34,197,94,0.2); animation: pulse 1.4s ease-out infinite; }
@keyframes pulse { 0% { transform: scale(0.9); opacity: 1; } 70% { transform: scale(1.3); opacity: 0; } 100% { transform: scale(0.9); opacity: 0; } }
.ic-avatar { width: 80px; height: 80px; border-radius: 50%; background: linear-gradient(135deg, #22c55e, #16a34a); color: #fff; font-size: 32px; font-weight: 700; display: flex; align-items: center; justify-content: center; user-select: none; position: relative; z-index: 1; }
.ic-name { font-size: 22px; font-weight: 700; color: #1e293b; }
.ic-phone { font-size: 15px; color: #64748b; }
.ic-location { font-size: 12px; color: #94a3b8; margin-top: -8px; }
.ic-actions { display: flex; gap: 20px; margin-top: 8px; }
.ic-btn { width: 120px; padding: 14px 0; border: none; border-radius: 50px; font-size: 15px; font-weight: 700; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 6px; transition: transform 0.15s, box-shadow 0.15s; }
.ic-btn:active { transform: scale(0.96); }
.ic-btn-reject { background: #fff0f0; color: #ef4444; border: 2px solid #fecaca; }
.ic-btn-reject:hover { background: #fee2e2; box-shadow: 0 4px 12px rgba(239,68,68,0.2); }
.ic-btn-accept { background: linear-gradient(135deg, #22c55e, #16a34a); color: #fff; }
.ic-btn-accept:hover { box-shadow: 0 4px 16px rgba(34,197,94,0.4); }
.ic-btn-icon { font-size: 16px; }
</style>
