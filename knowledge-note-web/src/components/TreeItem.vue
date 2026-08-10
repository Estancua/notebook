<template>
  <div class="tree-item">
    <div
      class="tree-node"
      :class="{ selected: selectedId === node.id }"
      :style="{ paddingLeft: level * 20 + 8 + 'px' }"
      @click="onSelect"
      @contextmenu.prevent="onContextMenu"
    >
      <span
        class="arrow"
        :class="{ expanded: isExpanded, invisible: !hasChildren }"
        @click.stop="toggle"
      >▶</span>
      <span class="icon">📁</span>
      <span class="name">{{ node.name }}</span>
      <span class="note-count" v-if="node.noteCount !== undefined">({{ node.noteCount }})</span>
      <span class="delete-icon" @click.stop="$emit('delete-node', node)" title="删除笔记本">×</span>
    </div>
    <div v-if="isExpanded && hasChildren" class="children">
      <TreeItem
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :level="level + 1"
        :selected-id="selectedId"
        @select="(id) => $emit('select', id)"
        @contextmenu-node="(data) => $emit('contextmenu-node', data)"
        @delete-node="(n) => $emit('delete-node', n)"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  node: { type: Object, required: true },
  level: { type: Number, default: 0 },
  selectedId: { type: [Number, String], default: null }
})

const emit = defineEmits(['select', 'contextmenu-node', 'delete-node'])

const isExpanded = ref(props.level === 0)

const hasChildren = computed(() => {
  return props.node.children && props.node.children.length > 0
})

const toggle = () => {
  if (hasChildren.value) {
    isExpanded.value = !isExpanded.value
  }
}

const onSelect = () => {
  emit('select', props.node.id)
}

const onContextMenu = (e) => {
  emit('contextmenu-node', { node: props.node, event: e })
}
</script>

<script>
export default { name: 'TreeItem' }
</script>

<style scoped>
.tree-item {
  user-select: none;
}
.tree-node {
  display: flex;
  align-items: center;
  height: 32px;
  padding-right: 8px;
  cursor: pointer;
  border-radius: 4px;
  margin: 1px 4px;
  font-size: 13px;
  color: #374151;
  transition: background 0.15s;
}
.tree-node:hover {
  background: #e5e7eb;
}
.tree-node.selected {
  background: #dbeafe;
  color: #2563eb;
}
.arrow {
  font-size: 10px;
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s;
  flex-shrink: 0;
  color: #6b7280;
}
.arrow.expanded {
  transform: rotate(90deg);
}
.arrow.invisible {
  visibility: hidden;
}
.icon {
  margin: 0 4px;
  font-size: 14px;
}
.name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.note-count {
  margin-left: 4px;
  font-size: 11px;
  color: #9ca3af;
}
.delete-icon {
  margin-left: auto;
  font-size: 16px;
  font-weight: 700;
  color: #d1d5db;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s, color 0.15s;
  padding: 0 4px;
}
.tree-node:hover .delete-icon {
  opacity: 1;
}
.delete-icon:hover {
  color: #ef4444;
}
.children {
  padding: 0;
}
</style>
