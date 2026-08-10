<template>
  <div class="outgoing-links">
    <h4 class="links-title">我引用了</h4>
    <div v-if="links.length === 0" class="links-empty">暂无引用</div>
    <div
      v-for="link in links"
      :key="link.noteId"
      class="link-item"
      @click="goNote(link.noteId)"
    >
      <span class="link-icon">→</span>
      <span class="link-title">{{ link.title }}</span>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const props = defineProps({
  links: { type: Array, default: () => [] }
})

const router = useRouter()
const goNote = (id) => {
  router.push(`/note/${id}`)
}
</script>

<style scoped>
.links-title {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 6px;
}
.links-empty {
  font-size: 12px;
  color: #9ca3af;
}
.link-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 0;
  font-size: 12px;
  color: #3b82f6;
  cursor: pointer;
  border-radius: 4px;
  padding: 4px;
  transition: background 0.15s;
}
.link-item:hover {
  background: #eff6ff;
}
.link-icon {
  font-size: 10px;
  color: #9ca3af;
}
.link-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
