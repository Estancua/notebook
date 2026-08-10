<template>
  <aside class="notebook-tree">
    <div class="tree-header">
      <span class="tree-title">笔记本</span>
      <button class="add-btn" title="新建笔记本" @click="$emit('create-notebook')">+</button>
    </div>
    <div class="quick-entries">
      <div
        class="quick-entry"
        :class="{ active: currentView === 'all' }"
        @click="$emit('select-view', 'all')"
      >
        <span class="entry-icon">📁</span>
        <span>全部笔记</span>
      </div>
      <div
        class="quick-entry"
        :class="{ active: currentView === 'favorites' }"
        @click="$emit('select-view', 'favorites')"
      >
        <span class="entry-icon">⭐</span>
        <span>收藏</span>
      </div>
      <div
        class="quick-entry"
        :class="{ active: currentView === 'recycle' }"
        @click="goRecycle"
      >
        <span class="entry-icon">🗑</span>
        <span>回收站</span>
      </div>
    </div>
    <div class="tree-body">
      <div v-if="loading" class="tree-loading">加载中...</div>
      <div v-else-if="tree.length === 0" class="tree-empty">暂无笔记本</div>
      <TreeItem
        v-for="node in tree"
        :key="node.id"
        :node="node"
        :level="0"
        :selected-id="selectedNotebookId"
        @select="(id) => $emit('select-notebook', id)"
        @contextmenu-node="onContextMenu"
        @delete-node="onDeleteNode"
      />
    </div>

    <!-- 右键菜单 -->
    <Teleport to="body">
      <div
        v-if="contextMenu.visible"
        class="context-menu"
        :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
      >
        <div class="context-item" @click="addSubNotebook">新建子笔记本</div>
        <div class="context-item" @click="renameNotebook">重命名</div>
        <div class="context-divider"></div>
        <div class="context-item danger" @click="removeNotebook">删除</div>
      </div>
    </Teleport>
  </aside>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, inject } from 'vue'
import { useRouter } from 'vue-router'
import TreeItem from './TreeItem.vue'
import { getNotebookTree, deleteNotebook, saveNotebook } from '../api/notebookApi'

const props = defineProps({
  currentView: { type: String, default: 'all' },
  selectedNotebookId: { type: [Number, String], default: null }
})

const emit = defineEmits(['select-notebook', 'select-view', 'create-notebook'])
const router = useRouter()
const toast = inject('showToast', () => {})

const tree = ref([])
const loading = ref(false)

const contextMenu = ref({
  visible: false,
  x: 0,
  y: 0,
  node: null
})

const loadTree = async () => {
  loading.value = true
  try {
    const data = await getNotebookTree()
    tree.value = Array.isArray(data) ? data : []
  } catch (e) {
    // error handled in interceptor
  } finally {
    loading.value = false
  }
}

const onContextMenu = ({ node, event }) => {
  contextMenu.value = {
    visible: true,
    x: event.clientX,
    y: event.clientY,
    node
  }
}

const closeContextMenu = () => {
  contextMenu.value.visible = false
}

const addSubNotebook = () => {
  closeContextMenu()
  emit('create-notebook', contextMenu.value.node?.id || null)
}

const renameNotebook = () => {
  closeContextMenu()
  const node = contextMenu.value.node
  if (!node) return
  const newName = prompt('请输入新名称', node.name)
  if (newName && newName.trim()) {
    saveNotebook({ id: node.id, name: newName.trim(), parentId: node.parentId || 0 }).then(() => {
      loadTree()
      toast('重命名成功', 'success')
    })
  }
}

const removeNotebook = () => {
  closeContextMenu()
  const node = contextMenu.value.node
  if (!node) return
  doDeleteNotebook(node)
}

const onDeleteNode = (node) => {
  doDeleteNotebook(node)
}

const doDeleteNotebook = (node) => {
  if (!confirm(`确定删除笔记本"${node.name}"吗？该操作不可恢复。`)) return
  deleteNotebook(node.id).then(() => {
    loadTree()
    toast('删除成功', 'success')
  }).catch(() => {
    // error handled in interceptor
  })
}

const goRecycle = () => {
  router.push('/recycle')
}

const handleClick = () => {
  closeContextMenu()
}

onMounted(() => {
  loadTree()
  document.addEventListener('click', handleClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleClick)
})

defineExpose({ loadTree })
</script>

<style scoped>
.notebook-tree {
  width: 260px;
  height: 100%;
  background: #f9fafb;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.tree-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 12px 8px;
  flex-shrink: 0;
}
.tree-title {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 1px;
}
.add-btn {
  width: 24px;
  height: 24px;
  border: none;
  background: #e5e7eb;
  border-radius: 4px;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  color: #374151;
  display: flex;
  align-items: center;
  justify-content: center;
}
.add-btn:hover {
  background: #d1d5db;
}
.quick-entries {
  padding: 0 8px 8px;
  flex-shrink: 0;
}
.quick-entry {
  display: flex;
  align-items: center;
  height: 32px;
  padding: 0 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  color: #374151;
  margin-bottom: 2px;
  transition: background 0.15s;
}
.quick-entry:hover {
  background: #e5e7eb;
}
.quick-entry.active {
  background: #dbeafe;
  color: #2563eb;
}
.entry-icon {
  margin-right: 8px;
  font-size: 14px;
}
.tree-body {
  flex: 1;
  overflow-y: auto;
  padding: 0 4px;
}
.tree-loading,
.tree-empty {
  padding: 16px;
  text-align: center;
  font-size: 13px;
  color: #9ca3af;
}
</style>

<style>
/* 全局右键菜单样式 */
.context-menu {
  position: fixed;
  z-index: 10001;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.12);
  padding: 4px 0;
  min-width: 140px;
}
.context-item {
  padding: 6px 16px;
  font-size: 13px;
  color: #374151;
  cursor: pointer;
  transition: background 0.15s;
}
.context-item:hover {
  background: #f3f4f6;
}
.context-item.danger {
  color: #ef4444;
}
.context-item.danger:hover {
  background: #fef2f2;
}
.context-divider {
  height: 1px;
  background: #e5e7eb;
  margin: 4px 0;
}
</style>
