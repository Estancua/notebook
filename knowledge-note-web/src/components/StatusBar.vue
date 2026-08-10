<template>
  <footer class="statusbar">
    <span class="status-item" v-if="wordCount !== undefined">字数 {{ wordCount }}</span>
    <span class="status-item save-status" :class="saveStatusClass">{{ saveStatusText }}</span>
  </footer>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  wordCount: { type: Number, default: 0 },
  saveStatus: { type: String, default: '' } // '', 'saving', 'saved'
})

const saveStatusText = computed(() => {
  switch (props.saveStatus) {
    case 'saving': return '保存中...'
    case 'saved': return '已保存'
    default: return ''
  }
})

const saveStatusClass = computed(() => {
  switch (props.saveStatus) {
    case 'saving': return 'status-saving'
    case 'saved': return 'status-saved'
    default: return ''
  }
})
</script>

<style scoped>
.statusbar {
  height: 28px;
  background: #f9fafb;
  border-top: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  padding: 0 16px;
  gap: 16px;
  font-size: 12px;
  color: #9ca3af;
  flex-shrink: 0;
}
.status-item {
  display: flex;
  align-items: center;
}
.status-saving {
  color: #f59e0b;
}
.status-saved {
  color: #10b981;
}
</style>
