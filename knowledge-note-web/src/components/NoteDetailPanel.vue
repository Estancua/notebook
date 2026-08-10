<template>
  <aside class="note-detail-panel">
    <div v-if="!note || !note.id" class="panel-empty">
      <p>选择笔记查看详情</p>
    </div>
    <div v-else class="panel-content">
      <div class="panel-section">
        <h4 class="section-title">属性</h4>
        <NoteMeta :note="note" />
      </div>
      <div class="panel-divider"></div>
      <div class="panel-section">
        <TagSelect :tags="note.tags || []" @update:tags="onTagsUpdate" />
      </div>
      <div class="panel-divider"></div>
      <div class="panel-section">
        <h4 class="section-title">双向链接</h4>
        <LinkPanel
          :outgoing-links="note.outgoingLinks || []"
          :incoming-links="note.incomingLinks || []"
        />
      </div>
    </div>
  </aside>
</template>

<script setup>
import NoteMeta from './NoteMeta.vue'
import TagSelect from './TagSelect.vue'
import LinkPanel from './LinkPanel.vue'

const props = defineProps({
  note: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['tags-update'])

const onTagsUpdate = (tagIds) => {
  emit('tags-update', tagIds)
}
</script>

<style scoped>
.note-detail-panel {
  width: 280px;
  height: 100%;
  background: #fff;
  border-left: 1px solid #e5e7eb;
  overflow-y: auto;
}
.panel-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #9ca3af;
  font-size: 13px;
}
.panel-content {
  padding: 16px;
}
.panel-section {
  padding: 4px 0;
}
.section-title {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 8px;
}
.panel-divider {
  height: 1px;
  background: #f3f4f6;
  margin: 8px 0;
}
</style>
