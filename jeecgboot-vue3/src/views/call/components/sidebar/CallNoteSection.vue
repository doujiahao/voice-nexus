<template>
  <div class="cns-wrap">
    <div class="cns-title">☰ 通话备注记录</div>
    <textarea
      class="cns-input"
      :value="modelValue"
      :maxlength="200"
      placeholder="请输入备注..."
      @input="$emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
    ></textarea>
    <div class="cns-footer">
      <span class="cns-count">{{ modelValue.length }}/200</span>
      <button class="cns-save" :class="{ saved: noteSaved }" @click="$emit('save')">
        {{ noteSaved ? '✓ 已保存' : '保存备注' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  modelValue?: string
  noteSaved?: boolean
}
withDefaults(defineProps<Props>(), { modelValue: '', noteSaved: false })
defineEmits<{
  'update:modelValue': [value: string]
  'save': []
}>()
</script>

<style scoped>
.cns-wrap { display: flex; flex-direction: column; gap: 6px; }
.cns-title { font-size: 13px; font-weight: 600; color: #475569; }
.cns-input {
  width: 100%; height: 80px; padding: 8px 10px;
  border: 1px solid #e2e8f0; border-radius: 6px;
  font-size: 13px; color: #333; resize: none; outline: none; font-family: inherit;
  transition: border-color 0.2s;
}
.cns-input:focus { border-color: #3b82f6; }
.cns-footer { display: flex; align-items: center; justify-content: space-between; }
.cns-count { font-size: 11px; color: #94a3b8; }
.cns-save {
  padding: 5px 14px; border: 1px solid #3b82f6; color: #3b82f6;
  background: #fff; border-radius: 5px; font-size: 12px; cursor: pointer; transition: all 0.2s;
}
.cns-save:hover { background: #eff6ff; }
.cns-save.saved { background: #f0fdf4; border-color: #22c55e; color: #22c55e; }
</style>
