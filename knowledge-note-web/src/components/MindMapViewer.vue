<template>
  <div class="mindmap-editor">
    <div class="mindmap-toolbar">
      <span class="toolbar-title">🧠 思维导图</span>
      <div class="toolbar-actions">
        <button class="tool-btn" @click="addChildNode" title="添加子节点">＋</button>
        <button class="tool-btn" @click="deleteNode" title="删除选中节点">✕</button>
        <span class="toolbar-sep"></span>
        <button class="tool-btn" @click="fitCanvas" title="适应画布">⊞</button>
        <button class="tool-btn" @click="zoomIn" title="放大">🔍+</button>
        <button class="tool-btn" @click="zoomOut" title="缩小">🔍−</button>
        <span class="toolbar-sep"></span>
        <button class="tool-btn" @click="emitBack" title="返回编辑">⬅</button>
      </div>
    </div>
    <div class="mindmap-canvas" ref="canvasRef"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick, inject } from 'vue'
import MindMap from 'simple-mind-map'

const props = defineProps({
  content: { type: String, default: '' }
})
const emit = defineEmits(['update:content', 'back'])

const canvasRef = ref(null)
let mindMap = null
let resizeObserver = null
const toast = inject('showToast', () => {})

// 解析 Markdown 标题为树结构
const parseMdToTree = (mdText) => {
  if (!mdText || !mdText.trim()) {
    return {
      data: { text: '未命名', uid: 'root' },
      children: []
    }
  }

  const lines = mdText.split('\n')
  const root = { data: { text: '笔记', uid: 'root' }, children: [] }
  const stack = [{ node: root, level: 0 }]

  for (const line of lines) {
    const match = line.match(/^(#{1,6})\s+(.+)/)
    if (!match) continue
    const level = match[1].length
    const text = match[2].trim()

    const newNode = {
      data: { text, uid: `h${level}_${Date.now()}_${Math.random().toString(36).slice(2, 7)}` },
      children: []
    }

    while (stack.length > 1 && stack[stack.length - 1].level >= level) {
      stack.pop()
    }
    stack[stack.length - 1].node.children.push(newNode)
    stack.push({ node: newNode, level })
  }

  return root
}

// 树结构还原为 Markdown
const treeToMarkdown = (node, level = 0) => {
  if (!node) return ''
  let result = ''
  // 根节点不输出
  if (level > 0) {
    const prefix = '#'.repeat(Math.min(level, 6))
    result += `${prefix} ${node.data.text}\n`
  }
  if (node.children) {
    for (const child of node.children) {
      result += treeToMarkdown(child, level + 1)
    }
  }
  return result
}

// 同步导图变更到 Markdown
let syncTimer = null
const syncToMarkdown = () => {
  if (!mindMap) return
  clearTimeout(syncTimer)
  syncTimer = setTimeout(() => {
    try {
      const data = mindMap.getData()
      const md = treeToMarkdown(data, 0)
      emit('update:content', md)
    } catch { /* skip */ }
  }, 500)
}

const initMindMap = () => {
  if (mindMap) {
    mindMap.destroy()
    mindMap = null
  }

  const data = parseMdToTree(props.content)

  mindMap = new MindMap({
    el: canvasRef.value,
    data,
    layout: 'logicalStructure',
    theme: 'classic4',
    enableFreeDrag: true,
    mousewheelAction: 'zoom',
    readonly: false,
    initRootNodePosition: ['center', 'center'],
    expand: true
  })

  // 节点点击编辑
  mindMap.on('node_click', (node) => {
    if (node && node.nodeData) {
      // 双击可编辑
    }
  })

  // 结构变化同步
  mindMap.on('data_change', () => syncToMarkdown())

  // 节点文字变化同步
  mindMap.on('node_text_edit_change', () => syncToMarkdown())
}

const addChildNode = () => {
  if (!mindMap) return
  const activeNodes = mindMap.getSelectNode ? mindMap.getSelectNode() : null
  if (activeNodes && activeNodes.length > 0) {
    mindMap.execCommand('INSERT_CHILD_NODE', false, [], { text: '新节点' })
  } else {
    toast('请先选中一个节点')
  }
  syncToMarkdown()
}

const deleteNode = () => {
  if (!mindMap) return
  const activeNodes = mindMap.getSelectNode ? mindMap.getSelectNode() : null
  if (activeNodes && activeNodes.length > 0) {
    mindMap.execCommand('REMOVE_NODE')
    syncToMarkdown()
  } else {
    toast('请先选中一个节点')
  }
}

const fitCanvas = () => mindMap && mindMap.view.fit()
const zoomIn = () => mindMap && mindMap.view.enlarge()
const zoomOut = () => mindMap && mindMap.view.narrow()
const emitBack = () => {
  syncToMarkdown()
  emit('back')
}

watch(() => props.content, (newVal, oldVal) => {
  if (newVal !== oldVal) {
    nextTick(() => initMindMap())
  }
})

onMounted(() => {
  nextTick(() => initMindMap())

  resizeObserver = new ResizeObserver(() => {
    if (mindMap) mindMap.view.fit()
  })
  if (canvasRef.value) {
    resizeObserver.observe(canvasRef.value)
  }
})

onBeforeUnmount(() => {
  if (mindMap) {
    mindMap.destroy()
    mindMap = null
  }
  if (resizeObserver) {
    resizeObserver.disconnect()
  }
})
</script>

<style scoped>
.mindmap-editor {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f8f9fc;
}
.mindmap-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.toolbar-title {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}
.tool-btn {
  min-width: 28px;
  height: 28px;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  transition: all 0.15s;
  padding: 0 6px;
}
.tool-btn:hover {
  background: #f3f4f6;
  color: #1f2937;
}
.toolbar-sep {
  width: 1px;
  height: 20px;
  background: #e5e7eb;
  margin: 0 4px;
}
.mindmap-canvas {
  flex: 1;
  overflow: hidden;
}
</style>
