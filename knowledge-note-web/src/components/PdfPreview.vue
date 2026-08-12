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
            <!-- 已加载的页面 -->
            <template v-if="pageDataMap[p]">
              <div class="ocr-page-container"
                :style="{ aspectRatio: pageDataMap[p].imageWidth + '/' + pageDataMap[p].imageHeight }"
                @mouseup="handleTextSelect"
                @mousedown="clearSelection">
                <img :src="'data:image/jpeg;base64,' + pageDataMap[p].imageBase64"
                  class="ocr-page-image" />
                <div class="ocr-text-overlay">
                  <span v-for="(line, idx) in pageDataMap[p].textLines" :key="idx"
                    class="ocr-text-line"
                    :style="lineStyle(line)"
                    @mousedown.stop>{{ line.text }}</span>
                </div>
              </div>
            </template>
            <!-- 未加载的占位 -->
            <div v-else class="ocr-page-placeholder">
              <span v-if="pageErrors[p]" class="ocr-page-error">{{ pageErrors[p] }}</span>
              <span v-else>加载中...</span>
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
import { ref, computed, watch, nextTick, reactive } from 'vue'
import { getDocumentPreviewUrl, getPageOcrResult } from '../api/documentApi'

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

const scrollViewerRef = ref(null)
const selectedText = ref('')
const selectedLineIndices = ref([])
const isDragging = ref(false)

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

// 翻页（外部）
watch(() => props.page, (newPage) => {
  if (newPage && newPage >= 1 && newPage !== currentPage.value) {
    currentPage.value = newPage
    pageInput.value = newPage
    iframeKey.value++
    if (ocrMode.value) {
      currentOcrPage.value = newPage
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
  // 清空旧缓存
  Object.keys(pageDataMap).forEach(k => delete pageDataMap[k])
  Object.keys(pageErrors).forEach(k => delete pageErrors[k])
  loadingPages.clear()
  // 加载当前页及附近页
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
  for (let i = start; i <= end; i++) {
    if (!pageDataMap[i] && !pageErrors[i] && !loadingPages.has(i)) {
      loadPageOcr(i)
    }
  }
}

const loadPageOcr = async (pageNum) => {
  loadingPages.add(pageNum)
  try {
    const res = await getPageOcrResult(props.documentId, pageNum)
    if (res && res.textLines) {
      pageDataMap[pageNum] = res
    } else {
      pageErrors[pageNum] = `第${pageNum}页: OCR 未返回数据`
    }
  } catch (e) {
    pageErrors[pageNum] = `第${pageNum}页: ` + (e.response?.data?.msg || e.message)
  } finally {
    loadingPages.delete(pageNum)
  }
}

// 文字选区
const lineStyle = (line) => ({
  left: line.x + '%',
  top: line.y + '%',
  width: Math.max(line.width, 0.2) + '%',
  height: line.height + '%',
  fontSize: (line.height || 2) + '%',
  lineHeight: line.height + '%'
})

const clearSelection = () => {
  selectedText.value = ''
  selectedLineIndices.value = []
}

const handleTextSelect = () => {
  nextTick(() => {
    const sel = window.getSelection()
    if (!sel || sel.isCollapsed || !sel.toString().trim()) {
      clearSelection()
      return
    }
    const text = sel.toString().trim()
    if (!text) return

    const indices = []
    if (sel.rangeCount > 0) {
      const range = sel.getRangeAt(0)
      const container = scrollViewerRef.value
      if (container) {
        const spans = container.querySelectorAll('.ocr-text-line')
        spans.forEach((span, idx) => {
          if (range.intersectsNode(span)) indices.push(idx)
        })
      }
    }
    selectedText.value = text
    selectedLineIndices.value = indices
  })
}

// HTML5 拖拽：将选中文字传递到导图
const onDragStart = (e) => {
  if (!selectedText.value) return
  isDragging.value = true
  e.dataTransfer.effectAllowed = 'copy'
  e.dataTransfer.setData('text/plain', selectedText.value)
  // 附带元信息，供 MindMapViewer 创建 PDF 关联
  const meta = JSON.stringify({
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
  emit('createMindmapNode', {
    text: selectedText.value,
    documentId: props.documentId,
    page: currentOcrPage.value,
    pageStart: currentOcrPage.value,
    pageEnd: currentOcrPage.value
  })
  selectedText.value = ''
  selectedLineIndices.value = []
}
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
.ocr-page-image { display: block; width: 100%; height: 100%; object-fit: contain; user-select: none; }
.ocr-text-overlay {
  position: absolute; top: 0; left: 0; width: 100%; height: 100%;
  pointer-events: none;
  user-select: text; -webkit-user-select: text;
}
.ocr-text-line {
  position: absolute; pointer-events: auto; cursor: text;
  color: transparent; overflow: hidden; white-space: nowrap;
  background: transparent; transition: background 0.15s;
}
.ocr-text-line::selection { color: #fff; background: rgba(59,130,246,0.7); }
.ocr-text-line::-moz-selection { color: #fff; background: rgba(59,130,246,0.7); }
.ocr-text-line:hover { background: rgba(250, 204, 21, 0.25); color: transparent; }
.ocr-page-placeholder {
  width: 100%; max-width: 850px; aspect-ratio: 0.77;
  display: flex; align-items: center; justify-content: center;
  background: #fff; color: #9ca3af; font-size: 14px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
}
.ocr-page-error { color: #ef4444; }
</style>
