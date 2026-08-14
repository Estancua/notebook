<template>
  <div class="pdf-preview" v-if="visible && documentId">
    <!-- 顶部工具栏 -->
    <div class="preview-header">
      <span>{{ fileType || '文件' }} 预览{{ currentPage ? ` · 第${currentPage}页` : '' }}</span>
      <div class="preview-actions">
        <button class="tool-btn" @click="scrollToPage(currentPage - 1)" :disabled="currentPage <= 1" title="上一页">◀</button>
        <input v-model.number="pageInput" type="number" min="1" class="page-num-input"
          @keyup.enter="jumpToPage(pageInput)" />
        <button class="tool-btn" @click="jumpToPage(pageInput)" title="跳页">GO</button>
        <button class="tool-btn" @click="scrollToPage(currentPage + 1)" title="下一页">▶</button>
        <button v-if="fileType === 'PDF'" class="tool-btn ocr-toggle-btn"
          :class="{ active: ocrMode }"
          @click="toggleOcrMode"
          :title="ocrMode ? '返回普通预览' : 'OCR文字识别选区'">
          {{ ocrMode ? '返回预览' : 'OCR选区' }}
        </button>
        <button class="close-btn" @click="$emit('close')">✕</button>
      </div>
    </div>

    <!-- OCR选区浮动操作栏：可拖拽到导图 -->
    <div v-if="ocrMode && selectedText" class="ocr-float-bar"
      draggable="true"
      @dragstart="onDragStart"
      @dragend="onDragEnd">
      <span class="float-selected">已选: "{{ selectedTextTruncated }}" <span class="drag-hint">↗ 拖到导图</span></span>
      <button class="float-btn" @click="createMindmapNode" @mousedown.stop>+ 创建导图节点</button>
    </div>

    <!-- 内容区 -->
    <div class="preview-body">
      <!-- 普通 iframe 预览模式 -->
      <iframe v-if="!ocrMode && fileType === 'PDF'" :key="iframeKey" :src="previewUrl"
        width="100%" height="100%" frameborder="0"></iframe>

      <!-- OCR 选区模式：可滚动的多页图片 + 文字层 -->
      <div v-else-if="ocrMode" class="ocr-scroll-viewer" ref="scrollViewerRef" @scroll="onScroll">
        <div class="ocr-pages-stack">
          <div v-for="p in visiblePageRange" :key="p"
            :data-ocr-page="p"
            class="ocr-page-wrapper">
            <div class="ocr-page-container" :style="{ aspectRatio: pageAspectRatio(p) }">
              <!-- PDF 页面图片（后端 PDFBox 渲染，按需加载） -->
              <img :src="pageImageUrl(p)" class="ocr-page-image" :alt="`第${p}页`" />
              <!-- OCR 文字叠加层（有数据时才渲染） -->
              <div v-if="pageDataMap[p]" class="ocr-text-overlay"
                @mousedown.prevent="onOverlayMouseDown($event, p)"
                @mousemove="onOverlayMouseMove($event, p)"
                @mouseup="onOverlayMouseUp($event, p)">
                <template v-for="(line, lineIdx) in pageDataMap[p].textLines" :key="lineIdx">
                  <span class="ocr-line-group">
                    <span v-for="(ch, chIdx) in (line.text || '').split('')" :key="chIdx"
                      class="ocr-char-block"
                      :class="{ selected: isCharSelected(p, lineIdx, chIdx) }"
                      :style="charStyle(line, chIdx, (line.text || '').length)"
                      :data-page="p"
                      :data-line="lineIdx"
                      :data-char="chIdx"
                    ></span>
                  </span>
                </template>
              </div>
              <!-- OCR 失败提示角标 -->
              <div v-if="pageErrors[p] && !pageDataMap[p]" class="ocr-error-badge">
                <span>⚠ OCR失败</span>
                <button class="ocr-retry-btn" @click.stop="retryOcr(p)" :disabled="retryingPages.has(p)">
                  {{ retryingPages.has(p) ? '重试中…' : '重试' }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Word 文档不支持 -->
      <div v-else class="preview-placeholder">Word 文档不支持预览</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, reactive, onBeforeUnmount } from 'vue'
import { getDocumentPreviewUrl, getPageOcrResult, getDocumentPageImageUrl } from '../api/documentApi'

const props = defineProps({
  visible: { type: Boolean, default: false },
  documentId: { type: [Number, String], default: null },
  fileType: { type: String, default: '' },
  page: { type: Number, default: null }
})
const emit = defineEmits(['close', 'createMindmapNode'])

const currentPage = ref(props.page || 1)
const pageInput = ref(props.page || 1)
const iframeKey = ref(0)

// ===== OCR 模式状态 =====
const ocrMode = ref(false)
const currentOcrPage = ref(props.page || 1)  // 当前 OCR 聚焦页

// 每页 OCR 数据缓存 { pageNumber: OcrPageVO, ... }
const pageDataMap = reactive({})
// 每页错误信息
const pageErrors = reactive({})
// 正在加载中的页码
const loadingPages = reactive(new Set())
// 正在重试的页码
const retryingPages = reactive(new Set())

const scrollViewerRef = ref(null)
const selectedText = ref('')
const selectedLineIndices = ref([])
const isDragging = ref(false)

// ===== Word式拖拽选区状态 =====
const selectedLineKeys = reactive(new Set())
let selStartPage = 0
let selStartLineIdx = -1
let selStartCharIdx = -1

// 根据百分比坐标找到最近的字符块
const findCharAtPos = (pageNum, percentX, percentY) => {
  const data = pageDataMap[pageNum]
  if (!data || !data.textLines) return null
  let best = null
  let bestDist = Infinity
  data.textLines.forEach((line, lineIdx) => {
    if (percentY < line.y || percentY > line.y + line.height) return
    const chars = (line.text || '').split('')
    const charW = line.width / Math.max(chars.length, 1)
    chars.forEach((ch, chIdx) => {
      const cx = line.x + chIdx * (line.width / Math.max(chars.length, 1))
      const cw = charW
      const distY = Math.abs(percentY - (line.y + line.height / 2))
      const distX = percentX >= cx && percentX <= cx + cw ? 0 : Math.min(Math.abs(percentX - cx), Math.abs(percentX - cx - cw))
      const dist = distX * 2 + distY
      if (dist < bestDist) {
        bestDist = dist
        best = { page: pageNum, lineIdx, chIdx, text: ch }
      }
    })
  })
  return best
}

// 选中两个字符之间的所有字符（按出现顺序）
const selectCharRange = (from, to) => {
  if (!from || !to || from.page !== to.page) return
  selectedLineKeys.clear()
  const data = pageDataMap[from.page]
  if (!data) return

  const allChars = []
  data.textLines.forEach((line, lineIdx) => {
    (line.text || '').split('').forEach((ch, chIdx) => {
      allChars.push({ page: from.page, lineIdx, chIdx, text: ch })
    })
  })

  // 找到 from 和 to 在 allChars 中的索引
  const findIdx = (pos) => allChars.findIndex(c => c.lineIdx === pos.lineIdx && c.chIdx === pos.chIdx)
  let i1 = findIdx(from)
  let i2 = findIdx(to)
  if (i1 === -1 || i2 === -1) return
  const start = Math.min(i1, i2)
  const end = Math.max(i1, i2)

  for (let i = start; i <= end; i++) {
    const c = allChars[i]
    selectedLineKeys.add(`${c.page}_${c.lineIdx}_${c.chIdx}`)
  }
}

const clearSelection = () => {
  selectedText.value = ''
  selectedLineKeys.clear()
  selStartPage = 0
  selStartLineIdx = -1
  selStartCharIdx = -1
}

// 可见页面范围（当前页 ± 3）
const visiblePageRange = computed(() => {
  const start = Math.max(1, currentOcrPage.value - 3)
  const end = currentOcrPage.value + 3
  const pages = []
  for (let i = start; i <= end; i++) pages.push(i)
  return pages
})

const selectedTextTruncated = computed(() => {
  const t = selectedText.value
  return t.length > 30 ? t.substring(0, 30) + '...' : t
})

const previewUrl = computed(() => {
  if (!props.documentId) return ''
  const base = getDocumentPreviewUrl(props.documentId)
  return `${base}?_t=${iframeKey.value}#page=${currentPage.value || 1}`
})

// PDF 单页渲染图片地址（后端 PDFBox 渲染，按需加载）
const pageImageUrl = (pageNum) => {
  if (!props.documentId) return ''
  return getDocumentPageImageUrl(props.documentId, pageNum)
}

// 页面宽高比：优先用 OCR 结果尺寸（图片加载前占位，保证文字层可交互）
const pageAspectRatio = (pageNum) => {
  const d = pageDataMap[pageNum]
  if (d && d.imageWidth && d.imageHeight) {
    return `${d.imageWidth} / ${d.imageHeight}`
  }
  return '595 / 842'
}

// 翻页（外部）
watch(() => props.page, (newPage) => {
  console.log('[watch:page] newPage =', newPage, ', currentPage =', currentPage.value, ', ocrMode =', ocrMode.value)
  if (newPage && newPage >= 1 && newPage !== currentPage.value) {
    currentPage.value = newPage
    pageInput.value = newPage
    iframeKey.value++
    if (ocrMode.value) {
      currentOcrPage.value = newPage
      scrollToPageSlot(newPage)
      ensurePagesLoaded(newPage)
    }
  }
})

// 切换文档
watch(() => [props.documentId, props.visible], () => {
  const target = props.page || 1
  currentPage.value = target
  pageInput.value = target
  iframeKey.value++
  ocrMode.value = false
  Object.keys(pageDataMap).forEach(k => delete pageDataMap[k])
  Object.keys(pageErrors).forEach(k => delete pageErrors[k])
  loadingPages.clear()
}, { immediate: true })

const jumpToPage = (p) => {
  const pageNum = parseInt(p, 10)
  if (!pageNum || pageNum < 1) return
  currentPage.value = pageNum
  pageInput.value = pageNum
  iframeKey.value++
  if (ocrMode.value) {
    currentOcrPage.value = pageNum
    scrollToPageSlot(pageNum)
    ensurePagesLoaded(pageNum)
  }
}

const prevPage = () => { if (currentPage.value > 1) jumpToPage(currentPage.value - 1) }
const nextPage = () => { jumpToPage((currentPage.value || 1) + 1) }

// ===== 滚动式 OCR =====
const toggleOcrMode = async () => {
  if (ocrMode.value) {
    ocrMode.value = false
    return
  }
  ocrMode.value = true
  selectedText.value = ''
  selectedLineIndices.value = []
  currentOcrPage.value = currentPage.value
  // 清空内存数据缓存（pageErrors 保留，避免失败的页重复调用 OCR API）
  Object.keys(pageDataMap).forEach(k => delete pageDataMap[k])
  loadingPages.clear()
  // 加载当前页及附近页的 OCR
  ensurePagesLoaded(currentPage.value)
}

// 滚动检测：判断当前可见的页面
const onScroll = () => {
  const container = scrollViewerRef.value
  if (!container) return
  const wrappers = container.querySelectorAll('.ocr-page-wrapper')
  let bestPage = currentOcrPage.value
  let bestRatio = 0
  const containerRect = container.getBoundingClientRect()
  const containerMid = containerRect.top + containerRect.height * 0.3  // 30% 位置

  wrappers.forEach(w => {
    const rect = w.getBoundingClientRect()
    const visibleTop = Math.max(rect.top, containerRect.top)
    const visibleBottom = Math.min(rect.bottom, containerRect.bottom)
    const visibleHeight = Math.max(0, visibleBottom - visibleTop)
    const ratio = visibleHeight / rect.height
    // 页面顶部接近 30% 视口位置时为"当前页"
    if (rect.top <= containerMid && rect.bottom >= containerMid && ratio > bestRatio) {
      bestRatio = ratio
      bestPage = Number(w.dataset.ocrPage)
    }
  })

  if (bestPage !== currentOcrPage.value) {
    currentOcrPage.value = bestPage
    currentPage.value = bestPage
    pageInput.value = bestPage
    ensurePagesLoaded(bestPage)
  }
}

// 滚动到指定页的位置
const scrollToPageSlot = (p) => {
  nextTick(() => {
    const container = scrollViewerRef.value
    if (!container) return
    const el = container.querySelector(`[data-ocr-page="${p}"]`)
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  })
}

const scrollToPage = (p) => {
  if (p < 1) return
  currentPage.value = p
  pageInput.value = p
  currentOcrPage.value = p
  ensurePagesLoaded(p)
  scrollToPageSlot(p)
}

// 确保指定页及附近页面已加载 OCR
const ensurePagesLoaded = (centerPage) => {
  const start = Math.max(1, centerPage - 2)
  const end = centerPage + 2
  const tasks = []
  for (let i = start; i <= end; i++) {
    // OCR 数据加载
    if (!pageDataMap[i] && !pageErrors[i] && !loadingPages.has(i)) {
      tasks.push(loadPageOcr(i))
    }
  }
  // 并行执行，不阻塞滚动
  Promise.all(tasks).catch(() => {})
}

const loadPageOcr = async (pageNum, force = false) => {
  loadingPages.add(pageNum)
  try {
    const res = await getPageOcrResult(props.documentId, pageNum, force)
    if (res && res.textLines && res.textLines.length > 0) {
      pageDataMap[pageNum] = res
    } else {
      pageErrors[pageNum] = 'OCR 未返回数据'
    }
  } catch (e) {
    pageErrors[pageNum] = e.response?.data?.msg || e.message || 'OCR 失败'
  } finally {
    loadingPages.delete(pageNum)
  }
}

// 重试失败页的 OCR
const retryOcr = async (pageNum) => {
  retryingPages.add(pageNum)
  delete pageErrors[pageNum]
  delete pageDataMap[pageNum]
  try {
    await loadPageOcr(pageNum, true)
  } finally {
    retryingPages.delete(pageNum)
  }
}

// 文字选区
const charStyle = (line, chIdx, totalChars) => {
  const charW = Math.max(line.width, 0.2) / Math.max(totalChars, 1)
  return {
    left: (line.x + chIdx * (line.width / Math.max(totalChars, 1))) + '%',
    top: line.y + '%',
    width: charW + '%',
    height: line.height + '%'
  }
}

const isCharSelected = (page, lineIdx, chIdx) => {
  return selectedLineKeys.has(`${page}_${lineIdx}_${chIdx}`)
}

// 获取鼠标在 overlay 中的百分比坐标
const getPercentPos = (e) => {
  const container = e.currentTarget?.closest('.ocr-page-container')
  if (!container) return { x: 0, y: 0 }
  const rect = container.getBoundingClientRect()
  return {
    x: ((e.clientX - rect.left) / rect.width) * 100,
    y: ((e.clientY - rect.top) / rect.height) * 100
  }
}

const onOverlayMouseDown = (e, pageNum) => {
  const pos = getPercentPos(e)
  const char = findCharAtPos(pageNum, pos.x, pos.y)
  if (char) {
    selectedLineKeys.clear()
    selStartPage = char.page
    selStartLineIdx = char.lineIdx
    selStartCharIdx = char.chIdx
    selectedLineKeys.add(`${char.page}_${char.lineIdx}_${char.chIdx}`)
    isDragging.value = true
    window.addEventListener('mouseup', globalMouseUp, { once: true })
  }
}

const onOverlayMouseMove = (e, pageNum) => {
  if (!isDragging.value || pageNum !== selStartPage) return
  const pos = getPercentPos(e)
  const char = findCharAtPos(pageNum, pos.x, pos.y)
  if (char) {
    selectCharRange(
      { page: selStartPage, lineIdx: selStartLineIdx, chIdx: selStartCharIdx },
      char
    )
  }
}

const globalMouseUp = () => {
  if (!isDragging.value) return
  isDragging.value = false
  // 收集选中文本
  const page = selStartPage
  const data = pageDataMap[page]
  if (!data) { selectedText.value = ''; return }
  const texts = []
  data.textLines.forEach((line, lineIdx) => {
    (line.text || '').split('').forEach((ch, chIdx) => {
      if (selectedLineKeys.has(`${page}_${lineIdx}_${chIdx}`)) {
        texts.push(ch)
      }
    })
  })
  selectedText.value = texts.join('')
}

const onOverlayMouseUp = () => { /* handled by globalMouseUp */ }

// 快捷键：Ctrl+B 以选中文字创建导图节点
const onKeydown = (e) => {
  if (e.ctrlKey && (e.code === 'KeyB' || e.key === 'b' || e.key === 'B')) {
    e.preventDefault()
    if (selectedText.value) {
      createMindmapNode()
    }
  }
}

// OCR 模式开启时注册全局快捷键，关闭时移除
watch(ocrMode, (on) => {
  if (on) {
    window.addEventListener('keydown', onKeydown)
  } else {
    window.removeEventListener('keydown', onKeydown)
  }
})

// 旧方法 - 保留
const handleTextSelect = () => {}

// HTML5 拖拽：将选中文字传递到导图
const onDragStart = (e) => {
  if (!selectedText.value) return
  isDragging.value = true
  e.dataTransfer.effectAllowed = 'copy'
  e.dataTransfer.setData('text/plain', selectedText.value)
  // 附带元信息，供 MindMapViewer 创建 PDF 关联
  const meta = JSON.stringify({
    uid: 'ocr_' + Date.now() + '_' + Math.random().toString(36).slice(2, 7),
    text: selectedText.value,
    documentId: props.documentId,
    page: currentOcrPage.value,
    pageStart: currentOcrPage.value,
    pageEnd: currentOcrPage.value
  })
  e.dataTransfer.setData('application/json', meta)
}

const onDragEnd = () => {
  isDragging.value = false
}

// 创建导图节点（点击按钮方式）
const createMindmapNode = () => {
  if (!selectedText.value) return
  // 清理 HTML 标签防止编码问题
  const cleanText = selectedText.value.replace(/<\/?[^>]+(>|$)/g, '')
  emit('createMindmapNode', {
    text: cleanText,
    documentId: props.documentId,
    page: currentOcrPage.value,
    pageStart: currentOcrPage.value,
    pageEnd: currentOcrPage.value
  })
  selectedText.value = ''
  selectedLineIndices.value = []
}

onBeforeUnmount(() => {
  window.removeEventListener('mouseup', globalMouseUp)
  window.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.pdf-preview { display: flex; flex-direction: column; height: 100%; background: #fff; }
.preview-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 14px; border-bottom: 1px solid #e5e7eb;
  font-size: 13px; font-weight: 600; color: #1f2937; flex-shrink: 0;
}
.preview-actions { display: flex; align-items: center; gap: 4px; }
.tool-btn {
  padding: 2px 8px; border: 1px solid #e5e7eb; background: #fff;
  border-radius: 4px; cursor: pointer; font-size: 12px; color: #374151;
}
.tool-btn:hover:not(:disabled) { background: #f3f4f6; }
.tool-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.ocr-toggle-btn { color: #7c3aed; border-color: #c4b5fd; font-weight: 600; margin-left: 8px; }
.ocr-toggle-btn.active { background: #7c3aed; color: #fff; border-color: #7c3aed; }
.ocr-toggle-btn:hover:not(.active) { background: #f5f3ff; }
.page-num-input { width: 52px; padding: 2px 6px; border: 1px solid #e5e7eb; border-radius: 4px; font-size: 12px; outline: none; text-align: center; }
.close-btn { border: none; background: none; font-size: 15px; cursor: pointer; color: #9ca3af; padding: 2px 6px; border-radius: 4px; margin-left: 4px; }
.close-btn:hover { color: #374151; background: #f3f4f6; }

.ocr-float-bar {
  display: flex; align-items: center; gap: 10px;
  padding: 6px 14px; background: #7c3aed; color: #fff;
  font-size: 12px; flex-shrink: 0; z-index: 10;
  cursor: grab; user-select: none;
}
.ocr-float-bar:active { cursor: grabbing; }
.float-selected { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #ede9fe; }
.drag-hint { color: #c4b5fd; font-size: 11px; margin-left: 8px; opacity: 0.8; }
.float-btn {
  padding: 3px 12px; border: 1px solid #ddd6fe; background: #fff;
  border-radius: 4px; cursor: pointer; font-size: 12px; color: #7c3aed; font-weight: 600; white-space: nowrap;
}
.float-btn:hover { background: #ede9fe; }

.preview-body { flex: 1; overflow: auto; background: #525659; }
.preview-placeholder { display: flex; align-items: center; justify-content: center; height: 100%; color: #9ca3af; font-size: 16px; background: #fff; }

/* 滚动式 OCR 视图 */
.ocr-scroll-viewer { overflow-y: auto; height: 100%; background: #525659; }
.ocr-pages-stack { display: flex; flex-direction: column; align-items: center; padding: 10px 0; gap: 12px; }
.ocr-page-wrapper { margin: 0 auto; width: 100%; }
.ocr-page-container { position: relative; margin: 0 auto; background: #fff; box-shadow: 0 2px 12px rgba(0,0,0,0.15); width: 100%; max-width: 850px; }
.ocr-page-image { display: block; width: 100%; height: 100%; object-fit: fill; user-select: none; }
.ocr-text-overlay {
  position: absolute; top: 0; left: 0; width: 100%; height: 100%;
  user-select: none; -webkit-user-select: none;
}
.ocr-line-group {
  /* 行容器，用于 hover 整行高亮 */
}
.ocr-char-block {
  position: absolute;
  background: transparent;
  border: none;
  cursor: text;
  border-radius: 0;
  transition: background 0.1s;
}
/* hover 整行时，该行所有字符显示淡色 */
.ocr-line-group:hover .ocr-char-block {
  background: rgba(250, 204, 21, 0.25);
  border: 1px solid rgba(250, 204, 21, 0.35);
  margin: -1px;
}
/* 拖拽选中的字符显示蓝色 */
.ocr-char-block.selected {
  background: rgba(59, 130, 246, 0.4) !important;
  border: 1px solid rgba(59, 130, 246, 0.65) !important;
  margin: -1px;
}
.ocr-error-badge {
  position: absolute;
  top: 6px;
  right: 10px;
  background: rgba(239, 68, 68, 0.85);
  color: #fff;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  z-index: 20;
  pointer-events: auto;
  display: flex;
  align-items: center;
  gap: 6px;
}
.ocr-retry-btn {
  padding: 1px 6px;
  border: 1px solid rgba(255,255,255,0.5);
  background: rgba(255,255,255,0.15);
  color: #fff;
  font-size: 10px;
  border-radius: 3px;
  cursor: pointer;
  white-space: nowrap;
}
.ocr-retry-btn:hover:not(:disabled) {
  background: rgba(255,255,255,0.3);
}
.ocr-retry-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.ocr-page-error { color: #ef4444; }
</style>
