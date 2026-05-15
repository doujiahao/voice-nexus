<template>
  <div class="cib-wrap">
    <div class="cib-section-title">
      <span class="cib-info-icon">ℹ</span>
      实时通话信息
      <div class="cib-duration">
        通话时长：<span class="cib-timer">{{ formattedDuration }}</span>
      </div>
    </div>

    <div v-if="callActive" class="cib-form-row">
      <div class="cib-form-item">
        <label class="cib-label">来电人姓名 <span class="cib-required">*</span></label>
        <input
          class="cib-input"
          :class="{ 'cib-input-error': nameError }"
          :value="callerName"
          placeholder="请输入姓名"
          @input="$emit('update:callerName', ($event.target as HTMLInputElement).value)"
          @blur="$emit('blur:callerName')"
        />
        <span v-if="nameError" class="cib-error-msg">姓名不能为空</span>
      </div>
      <div class="cib-form-item">
        <label class="cib-label">电话号码</label>
        <input class="cib-input cib-input-phone" :value="phoneNumber" readonly />
      </div>
    </div>

    <div class="cib-ai-bar" :class="{ 'ai-on': aiEnabled }">
      <div class="cib-ai-icon">🤖</div>
      <div class="cib-ai-text">
        <div class="cib-ai-title">AI实时语义分析</div>
        <div class="cib-ai-desc">开启后系统将自动提取客户诉求与情感倾向</div>
      </div>
      <div class="cib-toggle" :class="{ active: aiEnabled }" @click="$emit('update:aiEnabled', !aiEnabled)">
        <div class="cib-toggle-ball"></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  callerName?: string
  phoneNumber?: string
  nameError?: boolean
  aiEnabled?: boolean
  duration?: number
  callActive?: boolean
}
const props = withDefaults(defineProps<Props>(), {
  callerName: '',
  phoneNumber: '',
  nameError: false,
  aiEnabled: true,
  duration: 0,
  callActive: false,
})
defineEmits<{
  'update:callerName': [value: string]
  'update:aiEnabled': [value: boolean]
  'blur:callerName': []
}>()

const formattedDuration = computed(() => {
  const total = props.duration ?? 0
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  const mm = String(m).padStart(2, '0')
  const ss = String(s).padStart(2, '0')
  return h > 0
    ? `${String(h).padStart(2, '0')}:${mm}:${ss}`
    : `${mm}:${ss}`
})
</script>

<style scoped>
.cib-wrap { padding: 14px 20px; border-bottom: 1px solid #edf0f5; flex-shrink: 0; }
.cib-section-title {
  display: flex; align-items: center; gap: 6px;
  font-size: 14px; font-weight: 600; color: #1a2a4a; margin-bottom: 12px;
}
.cib-info-icon {
  width: 18px; height: 18px; background: #3b82f6; border-radius: 50%;
  color: #fff; display: inline-flex; align-items: center;
  justify-content: center; font-size: 11px; font-style: normal;
}
.cib-duration { margin-left: auto; font-size: 13px; color: #666; display: flex; align-items: center; gap: 6px; font-weight: 400; }
.cib-timer {
  background: #dbeafe; color: #2563eb;
  padding: 2px 10px; border-radius: 4px;
  font-weight: 700; font-size: 14px; letter-spacing: 1px;
}
.cib-form-row { display: flex; gap: 20px; margin-bottom: 12px; }
.cib-form-item { flex: 1; }
.cib-label { display: block; font-size: 12px; color: #666; margin-bottom: 5px; }
.cib-required { color: #ef4444; margin-left: 2px; }
.cib-input {
  width: 100%; padding: 8px 12px; border: 1px solid #e2e8f0;
  border-radius: 6px; font-size: 14px; color: #333; outline: none;
  background: #fff; transition: border-color 0.2s;
}
.cib-input:focus { border-color: #3b82f6; }
.cib-input-error { border-color: #ef4444 !important; }
.cib-error-msg { font-size: 11px; color: #ef4444; margin-top: 3px; display: block; }
.cib-input-phone { background: #f8fafc; color: #555; }
.cib-ai-bar {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px; background: #f8f9fb; border-radius: 8px;
  border: 1px solid transparent; transition: all 0.2s;
}
.cib-ai-bar.ai-on { border-color: #bfdbfe; background: #eff6ff; }
.cib-ai-icon { font-size: 22px; }
.cib-ai-title { font-size: 13px; font-weight: 600; color: #1a2a4a; }
.cib-ai-desc { font-size: 11px; color: #999; margin-top: 2px; }
.cib-toggle {
  margin-left: auto; width: 40px; height: 22px; border-radius: 11px;
  background: #d1d5db; position: relative; cursor: pointer; transition: background 0.2s;
}
.cib-toggle.active { background: #3b82f6; }
.cib-toggle-ball {
  width: 18px; height: 18px; background: #fff; border-radius: 50%;
  position: absolute; top: 2px; left: 2px; transition: left 0.2s;
  box-shadow: 0 1px 3px rgba(0,0,0,0.2);
}
.cib-toggle.active .cib-toggle-ball { left: 20px; }
</style>
