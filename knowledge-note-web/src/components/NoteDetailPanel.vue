<template>
  <aside class="note-detail-panel" :class="{ collapsed }">
    <!-- 收起状态：只显示展开按钮 -->
    <div v-if="collapsed" class="panel-collapsed-strip">
      <button class="toggle-btn" @click="collapsed = false" title="展开笔记属性">◀</button>
    </div>
    <!-- 展开状态：完整面板 -->
    <template v-else>
      <div class="panel-header">
        <span class="panel-title">笔记属性</span>
        <button class="toggle-btn" @click="collapsed = true" title="收起">▶</button>
      </div>
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
    </template>
  </aside>
</template>

<script setup>
import { ref } from 'vue'
import NoteMeta from './NoteMeta.vue'
import TagSelect from './TagSelect.vue'
import LinkPanel from './LinkPanel.vue'

const props = defineProps({
  note: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['tags-update'])

const collapsed = ref(true)

const onTagsUpdate = (tagIds) => {
  emit('tags-update', tagIds)
}
</script>

<style scoped>
.note-detail-panel {
  height: 100%;
  background: #fff;
  border-left: 1px solid #e5e7eb;
  overflow-y: auto;
  flex-shrink: 0;
  transition: width 0.2s ease;
}
.note-detail-panel:not(.collapsed) {
  width: 280px;
}
.note-detail-panel.collapsed {
  width: 32px;
}
.panel-collapsed-strip {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 0;
  height: 100%;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  border-bottom: 1px solid #f3f4f6;
  flex-shrink: 0;
}
.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}
.toggle-btn {
  width: 24px;
  height: 24px;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 4px;
  cursor: pointer;
  font-size: 11px;
  color: #6b7280;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}
.toggle-btn:hover {
  background: #f3f4f6;
  color: #374151;
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
  overflow-y: auto;
  flex: 1;
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
