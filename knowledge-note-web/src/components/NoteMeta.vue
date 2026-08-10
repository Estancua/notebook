<template>
  <div class="note-meta">
    <div class="meta-row">
      <span class="meta-label">创建时间</span>
      <span class="meta-value">{{ formatTime(note.createdAt) }}</span>
    </div>
    <div class="meta-row">
      <span class="meta-label">更新时间</span>
      <span class="meta-value">{{ formatTime(note.updatedAt) }}</span>
    </div>
    <div class="meta-row">
      <span class="meta-label">字数</span>
      <span class="meta-value">{{ note.wordCount || 0 }}</span>
    </div>
    <div class="meta-row">
      <span class="meta-label">笔记本</span>
      <span class="meta-value">{{ note.notebookName || '-' }}</span>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  note: { type: Object, default: () => ({}) }
})

const formatTime = (time) => {
  if (!time) return '-'
  const d = new Date(time)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
.note-meta {
  padding: 12px 0;
}
.meta-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
}
.meta-label {
  font-size: 12px;
  color: #9ca3af;
}
.meta-value {
  font-size: 12px;
  color: #374151;
}
</style>
