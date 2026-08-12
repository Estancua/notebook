<template>
  <div class="mindmap-editor">
    <div class="mindmap-toolbar">
      <span class="toolbar-title">🧠 思维导图</span>
      <div class="toolbar-actions">
        <button class="tool-btn" @click="addChildNode" title="添加子节点">＋</button>
        <button class="tool-btn" @click="deleteNode" title="删除选中节点">✕</button>
        <span class="toolbar-sep"></span>
        <button class="tool-btn" @click="expandAllNodes" title="展开全部">⊞◢</button>
        <button class="tool-btn" @click="collapseAllNodes" title="收缩全部">⊞◣</button>
        <span class="toolbar-sep"></span>
        <button class="tool-btn" @click="fitCanvas" title="适应画布">⊞</button>
        <button class="tool-btn" @click="zoomIn" title="放大">🔍+</button>
        <button class="tool-btn" @click="zoomOut" title="缩小">🔍−</button>
        <span class="toolbar-sep"></span>
        <button class="tool-btn" @click="emitBack" title="返回编辑">⬅</button>
      </div>
    </div>
    <div class="mindmap-canvas" ref="canvasRef" @dragover.prevent @drop="onDrop"></div>

    <!-- 选中节点悬浮工具栏 -->
    <div
      v-if="selectedNode && !refDialogVisible"
      class="node-float-toolbar"
      :style="floatToolbarStyle"
    >
      <button class="ft-btn" title="关联PDF" @click="openRefDialog">🔖</button>
    </div>

    <!-- 关联对话框 -->
    <div v-if="refDialogVisible" class="dialog-overlay" @click.self="refDialogVisible = false">
      <div class="dialog">
        <div class="dialog-header">
          <span>🔖 关联知识点 - {{ refForm.nodeTitle }}</span>
          <button class="close-btn" @click="refDialogVisible = false">✕</button>
        </div>
        <div class="dialog-body">
          <div class="form-row">
            <div class="form-group">
              <label class="form-label">起始页</label>
              <input
                v-model.number="refForm.pageStart"
                type="number"
                min="1"
                class="form-input"
                placeholder="页码"
              />
            </div>
            <div class="form-group">
              <label class="form-label">结束页</label>
              <input
                v-model.number="refForm.pageEnd"
                type="number"
                min="1"
                class="form-input"
                placeholder="可选，不填则同起始页"
              />
            </div>
          </div>
          <div class="form-group">
            <label class="form-label">原文摘录</label>
            <textarea
              v-model="refForm.excerpt"
              rows="4"
              class="form-input"
              placeholder="可粘贴原文内容..."
            ></textarea>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-small" @click="refDialogVisible = false">❌取消</button>
          <button
            v-if="currentRefId"
            class="btn-small danger"
            @click="onDeleteRef"
          >🗑️删除关联</button>
          <button class="btn-small btn-primary" @click="onSaveRef">✅保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick, inject, computed } from 'vue'
import MindMap from 'simple-mind-map'
import { savePdfRef, deletePdfRef } from '../api/noteApi'

const props = defineProps({
  content: { type: String, default: '' },
  noteId: { type: [Number, String], default: null },
  pdfRefs: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:content', 'back', 'jumpPdfPage', 'openPdfRefDialog', 'refChanged'])

const canvasRef = ref(null)
let mindMap = null
let resizeObserver = null
const toast = inject('showToast', () => {})
let isInternalUpdate = false

const selectedNode = ref(null)
const floatToolbarStyle = ref({ display: 'none' })

const refDialogVisible = ref(false)
const refForm = ref({
  nodeUid: '',
  nodeTitle: '',
  pageStart: 1,
  pageEnd: null,
  excerpt: ''
})
const currentRefId = ref(null)
const addedBadgeUids = ref(new Set())

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

const treeToMarkdown = (node, level = 0) => {
  if (!node) return ''
  let result = ''
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

let syncTimer = null
const syncToMarkdown = () => {
  if (!mindMap) return
  clearTimeout(syncTimer)
  syncTimer = setTimeout(() => {
    try {
      const data = mindMap.getData()
      const md = treeToMarkdown(data, 0)
      isInternalUpdate = true
      emit('update:content', md)
    } catch { /* skip */ }
  }, 500)
}

const updateFloatToolbar = () => {
  if (!selectedNode.value || !mindMap) {
    floatToolbarStyle.value = { display: 'none' }
    return
  }
  try {
    const nodeView = selectedNode.value._nodeView
    if (!nodeView) {
      floatToolbarStyle.value = { display: 'none' }
      return
    }
    const rect = nodeView.getBoundingClientRect
      ? nodeView.getBoundingClientRect()
      : (nodeView.getBoundingClientRect ? nodeView.getBoundingClientRect() : null)
    const canvasRect = canvasRef.value.getBoundingClientRect()
    if (rect) {
      const top = rect.top - canvasRect.top + 2
      const left = rect.right - canvasRect.left + 4
      floatToolbarStyle.value = {
        top: top + 'px',
        left: left + 'px',
        display: 'flex'
      }
    } else {
      floatToolbarStyle.value = { display: 'none' }
    }
  } catch {
    floatToolbarStyle.value = { display: 'none' }
  }
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
    expand: true,
    alwaysShowExpandBtn: true
  })

  mindMap.on('data_change', () => {
    syncToMarkdown()
    nextTick(() => {
      clearBadges()
      setTimeout(renderBadges, 100)
    })
  })

  mindMap.on('node_text_edit_change', () => {
    syncToMarkdown()
  })

  mindMap.on('node_active', (nodeList) => {
    if (nodeList && nodeList.length > 0) {
      selectedNode.value = nodeList[0]
    } else {
      selectedNode.value = null
    }
    nextTick(() => {
      updateFloatToolbar()
    })
  })

  mindMap.on('node_inactive', () => {
    selectedNode.value = null
    floatToolbarStyle.value = { display: 'none' }
  })

  mindMap.on('expand', () => {
    nextTick(() => setTimeout(renderBadges, 100))
  })
  mindMap.on('unexpand', () => {
    nextTick(() => setTimeout(renderBadges, 100))
  })

  nextTick(() => setTimeout(renderBadges, 300))
}

const addChildNode = () => {
  if (!mindMap) return
  if (mindMap.renderer.activeNodeList.length === 0 && mindMap.renderer.root) {
    mindMap.renderer.addNodeToActiveList(mindMap.renderer.root)
  }
  mindMap.execCommand('INSERT_CHILD_NODE')
  syncToMarkdown()
  nextTick(() => setTimeout(renderBadges, 100))
}

const deleteNode = () => {
  if (!mindMap) return
  if (mindMap.renderer.activeNodeList.length === 0) {
    toast('请先点击选中一个节点', 'info')
    return
  }
  mindMap.execCommand('REMOVE_NODE')
  syncToMarkdown()
  nextTick(() => setTimeout(renderBadges, 100))
}

const fitCanvas = () => {
  if (mindMap) mindMap.view.fit()
  nextTick(() => setTimeout(renderBadges, 200))
}
const zoomIn = () => {
  if (mindMap) mindMap.view.enlarge()
  nextTick(() => setTimeout(renderBadges, 100))
}
const zoomOut = () => {
  if (mindMap) mindMap.view.narrow()
  nextTick(() => setTimeout(renderBadges, 100))
}
const expandAllNodes = () => {
  if (mindMap) mindMap.execCommand('EXPAND_ALL')
  nextTick(() => setTimeout(renderBadges, 200))
}
const collapseAllNodes = () => {
  if (mindMap) mindMap.execCommand('UNEXPAND_ALL')
  nextTick(() => setTimeout(renderBadges, 200))
}

const emitBack = () => {
  syncToMarkdown()
  emit('back')
}

const clearBadges = () => {
  if (!canvasRef.value) return
  const badges = canvasRef.value.querySelectorAll('.pdf-ref-badge')
  badges.forEach(b => b.remove())
  addedBadgeUids.value.clear()
}

const findNodeElByUid = (uid) => {
  if (!canvasRef.value) return null
  const all = canvasRef.value.querySelectorAll('*')
  for (const el of all) {
    if (el.dataset && el.dataset.uid === uid) return el
    if (el.getAttribute && el.getAttribute('data-uid') === uid) return el
  }
  return null
}

const findNodeElByText = (text, usedTexts) => {
  if (!canvasRef.value || !text) return null
  const walker = document.createTreeWalker(
    canvasRef.value,
    NodeFilter.SHOW_TEXT,
    {
      acceptNode(node) {
        if (node.nodeValue && node.nodeValue.trim() === text.trim()) {
          const key = `${text}_${node.parentNode}`
          if (!usedTexts.has(key)) {
            usedTexts.add(key)
            return NodeFilter.FILTER_ACCEPT
          }
        }
        return NodeFilter.FILTER_REJECT
      }
    }
  )
  const n = walker.nextNode()
  return n ? n.parentElement : null
}

const renderBadges = () => {
  if (!canvasRef.value || !props.pdfRefs || props.pdfRefs.length === 0) return
  const usedTexts = new Set()
  const usedUids = new Set()
  const uidToRef = {}
  props.pdfRefs.forEach(ref => {
    if (ref.nodeUid) uidToRef[ref.nodeUid] = ref
  })

  props.pdfRefs.forEach(ref => {
    let targetEl = null
    if (ref.nodeUid && !usedUids.has(ref.nodeUid)) {
      targetEl = findNodeElByUid(ref.nodeUid)
      if (targetEl) {
        usedUids.add(ref.nodeUid)
        const badgeKey = `badge_${ref.nodeUid}`
        if (addedBadgeUids.value.has(badgeKey)) return
        addedBadgeUids.value.add(badgeKey)
      }
    }
    if (!targetEl) {
      targetEl = findNodeElByText(ref.nodeTitle, usedTexts)
    }
    if (!targetEl) return

    const badge = document.createElement('span')
    badge.className = 'pdf-ref-badge'
    badge.textContent = '📖'
    badge.title = `P.${ref.pageStart || '?'} - ${ref.nodeTitle || ''}`
    badge.dataset.refId = ref.id
    badge.dataset.pageStart = ref.pageStart || 1
    badge.style.cssText = `
      position: absolute;
      top: -6px;
      right: -6px;
      background: #fef3c7;
      border: 1px solid #fbbf24;
      color: #92400e;
      font-size: 12px;
      padding: 0 4px;
      border-radius: 10px;
      cursor: pointer;
      z-index: 20;
      line-height: 16px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.15);
      user-select: none;
    `
    targetEl.style.position = targetEl.style.position || 'relative'
    targetEl.appendChild(badge)

    badge.addEventListener('click', (e) => {
      e.stopPropagation()
      emit('jumpPdfPage', {
        pageStart: Number(badge.dataset.pageStart) || 1,
        ref
      })
    })
  })
}

const openRefDialog = () => {
  if (!selectedNode.value) {
    toast('请先选中一个节点', 'info')
    return
  }
  const node = selectedNode.value
  const nodeUid = node.data && node.data.uid ? node.data.uid : ''
  const nodeTitle = node.data && node.data.text ? node.data.text : ''

  let existingRef = null
  if (nodeUid && props.pdfRefs) {
    existingRef = props.pdfRefs.find(r => r.nodeUid === nodeUid)
  }
  if (!existingRef && nodeTitle && props.pdfRefs) {
    existingRef = props.pdfRefs.find(r => r.nodeTitle === nodeTitle)
  }

  if (existingRef) {
    refForm.value = {
      nodeUid,
      nodeTitle,
      pageStart: existingRef.pageStart || 1,
      pageEnd: existingRef.pageEnd || null,
      excerpt: existingRef.excerpt || ''
    }
    currentRefId.value = existingRef.id
  } else {
    refForm.value = {
      nodeUid,
      nodeTitle,
      pageStart: 1,
      pageEnd: null,
      excerpt: ''
    }
    currentRefId.value = null
  }

  refDialogVisible.value = true
}

const onSaveRef = async () => {
  if (!props.noteId) {
    toast('请先保存笔记', 'error')
    return
  }
  if (!refForm.value.nodeTitle) {
    toast('缺少节点信息', 'error')
    return
  }
  if (!refForm.value.pageStart || refForm.value.pageStart < 1) {
    toast('请输入有效起始页码', 'error')
    return
  }
  try {
    const data = {
      id: currentRefId.value || undefined,
      noteId: props.noteId,
      nodeUid: refForm.value.nodeUid,
      nodeTitle: refForm.value.nodeTitle,
      pageStart: refForm.value.pageStart,
      pageEnd: refForm.value.pageEnd || null,
      excerpt: refForm.value.excerpt || ''
    }
    await savePdfRef(data)
    toast('保存成功', 'success')
    refDialogVisible.value = false
    emit('refChanged')
  } catch (e) { /* handled */ }
}

const onDeleteRef = async () => {
  if (!currentRefId.value) return
  if (!confirm('确定要删除此关联吗？')) return
  try {
    await deletePdfRef(currentRefId.value)
    toast('已删除', 'success')
    refDialogVisible.value = false
    currentRefId.value = null
    emit('refChanged')
  } catch (e) { /* handled */ }
}

const centerNodeByUid = (uid) => {
  if (!mindMap || !uid) return
  try {
    const nodes = mindMap.renderer.nodeList || []
    const target = nodes.find(n => n.data && n.data.uid === uid)
    if (target && mindMap.view && mindMap.view.centerNode) {
      mindMap.view.centerNode(target)
    }
  } catch { /* skip */ }
}

watch(() => props.content, (newVal, oldVal) => {
  if (isInternalUpdate) {
    isInternalUpdate = false
    return
  }
  if (newVal !== oldVal) {
    nextTick(() => initMindMap())
  }
})

watch(() => props.pdfRefs, () => {
  nextTick(() => {
    clearBadges()
    setTimeout(renderBadges, 100)
  })
}, { deep: true })

let scrollHandler = null
let resizeHandler = null

onMounted(() => {
  nextTick(() => initMindMap())

  resizeObserver = new ResizeObserver(() => {
    if (mindMap) mindMap.view.fit()
    nextTick(() => setTimeout(renderBadges, 200))
  })
  if (canvasRef.value) {
    resizeObserver.observe(canvasRef.value)
  }

  scrollHandler = () => updateFloatToolbar()
  resizeHandler = () => {
    updateFloatToolbar()
    nextTick(() => setTimeout(renderBadges, 150))
  }
  window.addEventListener('scroll', scrollHandler, true)
  window.addEventListener('resize', resizeHandler)
})

onBeforeUnmount(() => {
  if (scrollHandler) window.removeEventListener('scroll', scrollHandler, true)
  if (resizeHandler) window.removeEventListener('resize', resizeHandler)
  clearBadges()
  if (mindMap) {
    mindMap.destroy()
    mindMap = null
  }
  if (resizeObserver) {
    resizeObserver.disconnect()
  }
})

// 拖放创建节点
const insertDragNode = (text, dragMeta) => {
  if (!mindMap) return
  if (mindMap.renderer.activeNodeList.length === 0 && mindMap.renderer.root) {
    mindMap.renderer.addNodeToActiveList(mindMap.renderer.root)
  }
  mindMap.execCommand('INSERT_CHILD_NODE')
  // 获取新创建的节点并设置文字
  const activeNodes = mindMap.renderer.activeNodeList
  if (activeNodes && activeNodes.length > 0) {
    const newNode = activeNodes[0]
    newNode.setData({ ...newNode.getData(), text, _dragMeta: dragMeta })
    mindMap.render()
  }
  syncToMarkdown()
  nextTick(() => setTimeout(renderBadges, 200))
}

const onDrop = (e) => {
  const text = e.dataTransfer.getData('text/plain')
  if (!text || !mindMap) return

  // 尝试读取 PDF 元信息（来自 PdfPreview 的拖拽）
  let dragMeta = null
  try {
    const raw = e.dataTransfer.getData('application/json')
    if (raw) dragMeta = JSON.parse(raw)
  } catch { /* ignore */ }

  insertDragNode(text, dragMeta)
}

defineExpose({ centerNodeByUid, renderBadges, insertDragNode })
</script>

<style scoped>
.mindmap-editor {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background: #f8f9fc;
  position: relative;
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
  position: relative;
}
.node-float-toolbar {
  position: absolute;
  display: flex;
  gap: 2px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 2px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  z-index: 50;
}
.ft-btn {
  width: 24px;
  height: 24px;
  border: none;
  background: #fef3c7;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.ft-btn:hover {
  background: #fde68a;
}

/* Dialog */
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;
}
.dialog {
  background: #fff;
  border-radius: 8px;
  width: 480px;
  max-width: 92%;
  box-shadow: 0 10px 40px rgba(0,0,0,0.2);
}
.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  border-bottom: 1px solid #e5e7eb;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}
.dialog-body {
  padding: 18px;
}
.dialog-footer {
  padding: 12px 18px;
  border-top: 1px solid #e5e7eb;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.form-row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}
.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}
.form-label {
  font-size: 13px;
  color: #374151;
  font-weight: 500;
}
.form-input {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 13px;
  color: #374151;
  outline: none;
  box-sizing: border-box;
  background: #fff;
  font-family: inherit;
}
.form-input:focus {
  border-color: #3b82f6;
}
textarea.form-input {
  resize: vertical;
  min-height: 80px;
  font-family: "Consolas", "Monaco", monospace;
}
.btn-small {
  padding: 5px 12px;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  color: #374151;
  white-space: nowrap;
}
.btn-small:hover {
  background: #f3f4f6;
}
.btn-small.danger {
  color: #ef4444;
  border-color: #fecaca;
}
.btn-small.danger:hover {
  background: #fef2f2;
}
.btn-small.btn-primary {
  background: #3b82f6;
  border-color: #3b82f6;
  color: #fff;
}
.btn-small.btn-primary:hover {
  background: #2563eb;
  border-color: #2563eb;
}
.close-btn {
  border: none;
  background: none;
  font-size: 16px;
  cursor: pointer;
  color: #9ca3af;
  padding: 2px 6px;
  border-radius: 4px;
}
.close-btn:hover {
  color: #374151;
  background: #f3f4f6;
}
</style>
