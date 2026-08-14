<template>
  <div class="mindmap-editor">
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
import MindMap from 'simple-mind-map/full.js'
import { savePdfRef, deletePdfRef } from '../api/noteApi'

const props = defineProps({
  content: { type: String, default: '' },
  noteId: { type: [Number, String], default: null },
  pdfRefs: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:content', 'back', 'jumpPdfPage', 'openPdfRefDialog', 'refChanged', 'createPdfRef'])

const canvasRef = ref(null)
let mindMap = null
let resizeObserver = null
const toast = inject('showToast', () => {})
let isInternalUpdate = false
let isFirstDataChange = true
// 最近一次内部同步（doSync emit）的时间，用于拦截内部同步产生的 content 变化，避免误触发导图重建
let internalSyncAt = 0

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
  let pendingPara = []

  // 提取行尾的 <!-- uid:xxx --> 注释（用于持久化导图节点 uid，保证 OCR 创建的页码关联不丢）
  const extractUid = (raw) => {
    const s = String(raw)
    const m = s.match(/\s*<!--\s*uid:([A-Za-z0-9_\-]+)\s*-->\s*$/)
    if (!m) return { uid: null, text: s }
    return { uid: m[1], text: s.slice(0, m.index) }
  }

  const flushParagraph = () => {
    if (pendingPara.length === 0) return
    const { uid, text: rawText } = extractUid(pendingPara.join('\n'))
    pendingPara = []
    const text = sanitizeNodeText(rawText)
    if (!text) return
    const paraNode = {
      data: {
        text,
        uid: uid || `para_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
        _isParagraph: true
      },
      children: []
    }
    stack[stack.length - 1].node.children.push(paraNode)
  }

  for (const line of lines) {
    const match = line.match(/^(#{1,6})\s+(.+)/)
    if (!match) {
      // 空行作为段落分隔，非空行累积为段落文本
      if (line.trim()) {
        pendingPara.push(line.trim())
      } else {
        flushParagraph()
      }
      continue
    }
    // 遇到标题前先 flush 累积的段落
    flushParagraph()
    const level = match[1].length
    const { uid, text: rawText } = extractUid(match[2])
    const text = sanitizeNodeText(rawText)

    const newNode = {
      data: { text, uid: uid || `h${level}_${Date.now()}_${Math.random().toString(36).slice(2, 7)}` },
      children: []
    }

    while (stack.length > 1 && stack[stack.length - 1].level >= level) {
      stack.pop()
    }
    stack[stack.length - 1].node.children.push(newNode)
    stack.push({ node: newNode, level })
  }

  // 最后 flush 剩余段落
  flushParagraph()

  return root
}

// 清理节点文本：解码 HTML 实体 + 去除 HTML 标签，处理多重编码（如 <p>&lt;p&gt;…&lt;/p&gt;</p>）
const sanitizeNodeText = (text) => {
  if (!text) return ''
  let t = String(text)
  const decode = (s) => s
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&nbsp;/g, ' ')
  const strip = (s) => s.replace(/<\/?[^>]+(>|$)/g, '')
  for (let i = 0; i < 5; i++) {
    const prev = t
    t = strip(decode(t)).trim()
    if (t === prev) break
  }
  return t
}

const treeToMarkdown = (node, level = 0) => {
  if (!node) return ''
  let result = ''
  // 导出时把节点 uid 写成行尾注释，持久化到 markdown，重新加载后可还原
  const uidSuffix = node.data && node.data.uid && node.data.uid !== 'root'
    ? ` <!-- uid:${node.data.uid} -->`
    : ''
  if (level > 0) {
    if (node.data._isParagraph) {
      // 段落节点直接输出文本，不加 # 前缀，前后加空行与标题分隔
      result += `\n${sanitizeNodeText(node.data.text)}${uidSuffix}\n`
    } else {
      const prefix = '#'.repeat(Math.min(level, 6))
      result += `${prefix} ${sanitizeNodeText(node.data.text)}${uidSuffix}\n`
    }
  }
  if (node.children) {
    for (const child of node.children) {
      // 段落节点的子节点不增加层级（段落本身不是标题层级）
      result += treeToMarkdown(child, node.data._isParagraph ? level : level + 1)
    }
  }
  return result
}

let syncTimer = null
const syncToMarkdown = () => {
  if (!mindMap) return
  clearTimeout(syncTimer)
  syncTimer = setTimeout(() => doSync(), 500)
}
// 立即同步（拖入节点时使用，避免切换模式时丢失）
const syncImmediate = () => {
  if (!mindMap) return
  clearTimeout(syncTimer)
  doSync()
}
const doSync = () => {
  try {
    const data = mindMap.getData()
    const md = treeToMarkdown(data, 0)
    console.log('[doSync] hasUid=', /<!--\s*uid:/.test(md), 'mdHead=', JSON.stringify(md.slice(0, 160)))
    isInternalUpdate = true
    internalSyncAt = Date.now()
    emit('update:content', md)
  } catch { /* skip */ }
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
  console.log('[initMindMap] uidComments=', (props.content.match(/<!--\s*uid:/g) || []).length, 'contentHead=', JSON.stringify((props.content || '').slice(0, 160)))
  if (mindMap) {
    mindMap.destroy()
    mindMap = null
  }
  isFirstDataChange = true
  // 重建时旧节点对象已销毁，清除残留的选中节点引用和最近创建节点记录
  selectedNode.value = null
  floatToolbarStyle.value = { display: 'none' }
  lastInsertedNode = null

  const data = parseMdToTree(props.content)

  mindMap = new MindMap({
    el: canvasRef.value,
    data,
    layout: 'logicalStructure',
    theme: 'classic4',
    enableFreeDrag: false,
    mousewheelAction: 'zoom',
    readonly: false,
    initRootNodePosition: ['center', 'center'],
    expand: true,
    alwaysShowExpandBtn: true
  })

  mindMap.on('data_change', () => {
    // 跳过初始化时的首次 data_change，避免覆盖原始 content
    if (isFirstDataChange) {
      isFirstDataChange = false
      nextTick(() => {
        clearBadges()
        setTimeout(renderBadges, 100)
      })
      return
    }
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

  // 拖拽重组由 simple-mind-map 内置 Drag 插件自动处理
  // - 拖到其他节点上 → MOVE_NODE_TO 成为其子节点
  // - 拖到兄弟节点间 → INSERT_AFTER/INSERT_BEFORE 重排序
  mindMap.on('node_dragend', ({ overlapNodeUid }) => {
    // 无重叠且 enableFreeDrag=false 时，节点不动
    // 如果需要"拖到空白→移到根节点"，可在此处理
    if (!overlapNodeUid) {
      // 预留：可在此将节点移到根下作为独立主题
    }
    syncToMarkdown()
    nextTick(() => setTimeout(renderBadges, 200))
  })

  nextTick(() => {
    setTimeout(() => {
      renderBadges()
      if (mindMap) mindMap.view.fit()
    }, 300)
  })
}

const addChildNode = () => {
  if (!mindMap) return
  if (mindMap.renderer.activeNodeList.length === 0 && mindMap.renderer.root) {
    mindMap.renderer.addNodeToActiveList(mindMap.renderer.root)
  }
  // 指定 uid，渲染后记录为新节点，后续 OCR 创建节点时挂到它下面
  const nodeUid = `ocr_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
  mindMap.execCommand('INSERT_CHILD_NODE', true, [], { uid: nodeUid })
  rememberLastNode(nodeUid)
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
    let nodeInst = null
    if (ref.nodeUid && !usedUids.has(ref.nodeUid)) {
      // 优先按 uid 精确匹配（OCR 创建节点时，导图节点 uid 与 note_pdf_ref.nodeUid 一致）
      nodeInst = mindMap.renderer.nodeCache && mindMap.renderer.nodeCache[ref.nodeUid]
      if (nodeInst && nodeInst.group && nodeInst.group.node) {
        usedUids.add(ref.nodeUid)
        targetEl = nodeInst.group.node
      }
    }
    if (!targetEl) {
      targetEl = findNodeElByText(ref.nodeTitle, usedTexts)
    }
    if (!targetEl) {
      console.log('[badge] NOT FOUND → uid=', ref.nodeUid, 'title=', ref.nodeTitle)
      return
    }
    // simple-mind-map 重绘节点时会清掉手工追加的角标，这里做 DOM 级去重：
    // 已存在则跳过，被重绘清掉后下一次渲染会自动补加
    if (targetEl.querySelector('.pdf-ref-badge')) return
    console.log('[badge] matched → uid=', ref.nodeUid, 'byUid=', !!nodeInst, 'title=', ref.nodeTitle)

    // simple-mind-map 节点是 SVG 元素，HTML span 直接挂载不会被渲染，
    // 因此用 SVG <g> + <rect> + <text> 绘制页码角标，随节点一起缩放/移动
    const NS = 'http://www.w3.org/2000/svg'
    const badgeW = 46
    const badgeH = 20
    const nodeW = nodeInst && nodeInst.width ? nodeInst.width : 120
    const x = nodeW - badgeW + 4
    const y = -badgeH - 6

    const badge = document.createElementNS(NS, 'g')
    badge.setAttribute('class', 'pdf-ref-badge')
    badge.style.cursor = 'pointer'

    const rect = document.createElementNS(NS, 'rect')
    rect.setAttribute('x', x)
    rect.setAttribute('y', y)
    rect.setAttribute('width', badgeW)
    rect.setAttribute('height', badgeH)
    rect.setAttribute('rx', badgeH / 2)
    rect.setAttribute('fill', '#fef3c7')
    rect.setAttribute('stroke', '#fbbf24')
    rect.setAttribute('stroke-width', '1')

    const text = document.createElementNS(NS, 'text')
    text.setAttribute('x', x + badgeW / 2)
    text.setAttribute('y', y + badgeH / 2)
    text.setAttribute('text-anchor', 'middle')
    text.setAttribute('dominant-baseline', 'central')
    text.setAttribute('font-size', '11')
    text.setAttribute('fill', '#92400e')
    text.textContent = 'P.' + (ref.pageStart || '?')

    const tip = document.createElementNS(NS, 'title')
    tip.textContent = `P.${ref.pageStart || '?'} - ${ref.nodeTitle || ''}`

    badge.appendChild(rect)
    badge.appendChild(text)
    badge.appendChild(tip)

    badge.addEventListener('click', (e) => {
      e.stopPropagation()
      console.log('[badge] click, pageStart =', ref.pageStart)
      emit('jumpPdfPage', {
        pageStart: Number(ref.pageStart) || 1,
        ref
      })
    })

    targetEl.appendChild(badge)
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
  console.log('[watch-content] skipInternal=', isInternalUpdate, 'ageMs=', Date.now() - internalSyncAt, 'changed=', newVal !== oldVal)
  if (isInternalUpdate) {
    isInternalUpdate = false
    return
  }
  // 导图内部同步（含 render 后 debounce 延迟回调）产生的 content 变化不应触发重建
  if (Date.now() - internalSyncAt < 2000) return
  if (newVal !== oldVal) {
    nextTick(() => {
      requestAnimationFrame(() => {
        if (canvasRef.value && canvasRef.value.offsetWidth > 0) {
          initMindMap()
        }
      })
    })
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
  // 等待浏览器完成布局后再初始化导图，避免容器宽高为 0
  nextTick(() => {
    requestAnimationFrame(() => {
      if (canvasRef.value && canvasRef.value.offsetWidth > 0 && canvasRef.value.offsetHeight > 0) {
        initMindMap()
      } else {
        // 容器尺寸仍为0，通过 ResizeObserver 延迟初始化
        const ro = new ResizeObserver((entries) => {
          const entry = entries[0]
          if (entry && entry.contentRect.width > 0 && entry.contentRect.height > 0) {
            ro.disconnect()
            initMindMap()
          }
        })
        ro.observe(canvasRef.value)
      }
    })
  })

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
  clearTimeout(syncTimer)
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

// 最近一次创建的新节点（下一次新建挂到它下面）
let lastInsertedNode = null

// 节点实例是否仍然有效（仍存在于当前渲染缓存中，未销毁）
const isNodeValid = (node) => {
  if (!node || !node.getData) return false
  try {
    const uid = node.getData('uid')
    if (!uid) return false
    return mindMap.renderer.nodeCache && mindMap.renderer.nodeCache[uid] === node
  } catch { return false }
}

// 渲染完成后按 uid 记录新节点，作为下一次新建的父节点
// 注意：节点实例存放在 renderer.nodeCache[uid]，不存在 renderer.nodeList
const rememberLastNode = (nodeUid, delay = 200) => {
  setTimeout(() => {
    if (!mindMap) return
    const node = mindMap.renderer.nodeCache && mindMap.renderer.nodeCache[nodeUid]
    if (node) lastInsertedNode = node
  }, delay)
}

// 创建节点（OCR 文字），挂载优先级：选中的节点 > 最近创建的节点 > 根节点
const insertDragNode = (text, dragMeta) => {
  if (!mindMap) return
  // 按优先级选择目标父节点
  let targetNode = null
  if (selectedNode.value && isNodeValid(selectedNode.value)) {
    targetNode = selectedNode.value
  } else if (lastInsertedNode && isNodeValid(lastInsertedNode)) {
    targetNode = lastInsertedNode
  } else {
    targetNode = mindMap.renderer.root
  }
  if (!targetNode) return
  // 先清空激活列表，避免此前选中的节点仍在激活列表里，
  // 导致 INSERT_CHILD_NODE 对每个激活节点都插入子节点（创建出两个节点）
  mindMap.renderer.clearActiveNodeList()
  mindMap.renderer.addNodeToActiveList(targetNode)
  // 通过 appointData 直接指定新节点文字（openEdit=false）：
  // 若用默认 openEdit=true 会进入编辑态，导致激活列表被清空，后续 SET_NODE_TEXT 找不到新节点、文字填不进去
  // 优先使用外部传入的 uid（与 pdfRef.nodeUid 一致，保证编辑文字后页码关联不丢）
  const nodeUid = (dragMeta && dragMeta.uid) || `ocr_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
  const appointData = { text, uid: nodeUid }
  if (dragMeta) appointData._dragMeta = dragMeta
  mindMap.execCommand('INSERT_CHILD_NODE', false, [], appointData)
  rememberLastNode(nodeUid)
  // 立即同步，防止切换模式时 debounce 未触发导致内容丢失
  // 不再 moveNodeToCenter，保持用户当前的缩放和视图位置不变
  syncImmediate()
  nextTick(() => setTimeout(renderBadges, 200))
}

// 接收 HTML5 拖放
const onDrop = (e) => {
  const text = e.dataTransfer.getData('text/plain')
  if (!text || !mindMap) return

  let dragMeta = null
  try {
    const raw = e.dataTransfer.getData('application/json')
    if (raw) dragMeta = JSON.parse(raw)
  } catch { /* ignore */ }

  insertDragNode(text, dragMeta)

  // 通知父组件保存 pdf 关联（拖拽创建时父组件不经过 handleOcrCreateNode，需要单独入库）
  if (dragMeta && dragMeta.uid && dragMeta.documentId) {
    emit('createPdfRef', { uid: dragMeta.uid, text, ...dragMeta })
  }
}

defineExpose({ centerNodeByUid, renderBadges, insertDragNode, addChildNode, deleteNode, expandAllNodes, collapseAllNodes, fitCanvas, zoomIn, zoomOut })
</script>

<style scoped>
.mindmap-editor {
  display: flex;
  flex-direction: column;
  flex: 1;
  width: 100%;
  min-height: 0;
  background: #f8f9fc;
  position: relative;
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
