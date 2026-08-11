<template>
  <div class="pdf-preview" v-if="visible && documentId">
    <div class="preview-header">
      <span>{{ fileType || '文件' }} 预览{{ currentPage ? ` · 第${currentPage}页` : '' }}</span>
      <div class="preview-actions">
        <button class="tool-btn" @click="prevPage" :disabled="currentPage <= 1" title="上一页">◀</button>
        <input
          v-model.number="pageInput"
          type="number"
          min="1"
          class="page-num-input"
          @keyup.enter="jumpToPage(pageInput)"
        />
        <button class="tool-btn" @click="jumpToPage(pageInput)" title="跳页">GO</button>
        <button class="tool-btn" @click="nextPage" title="下一页">▶</button>
        <button class="close-btn" @click="$emit('close')">✕</button>
      </div>
    </div>
    <div class="preview-body">
      <iframe
        v-if="fileType === 'PDF'"
        :key="iframeKey"
        :src="previewUrl"
        width="100%"
        height="100%"
        frameborder="0"
      ></iframe>
      <div v-else class="preview-placeholder">
        Word 文档预览暂不支持，请下载查看
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { getDocumentPreviewUrl } from '../api/documentApi'

const props = defineProps({
  visible: { type: Boolean, default: false },
  documentId: { type: [Number, String], default: null },
  fileType: { type: String, default: '' },
  page: { type: Number, default: null } // 外部传入：跳转到第几页
})
const emit = defineEmits(['close'])

const currentPage = ref(props.page || 1)
const pageInput = ref(props.page || 1)
// iframe key 改变会强制重载iframe，保证仅改 hash 时浏览器 PDF Viewer 也会跳页
const iframeKey = ref(0)

const previewUrl = computed(() => {
  if (!props.documentId) return ''
  const base = getDocumentPreviewUrl(props.documentId)
  const p = currentPage.value || 1
  // 加时间戳 query 绕过浏览器缓存不刷新的问题，确保每次跳页都能重新定位
  return `${base}?_t=${iframeKey.value}#page=${p}`
})

// 监听外部传入的 page 变化（来自章节"跳PDF"按钮）
watch(() => props.page, (newPage) => {
  if (newPage && newPage >= 1 && newPage !== currentPage.value) {
    currentPage.value = newPage
    pageInput.value = newPage
    iframeKey.value++
  }
})

// 切换文档时，若外部传了page则优先用，否则回到第1页
watch(() => [props.documentId, props.visible], () => {
  const target = props.page || 1
  currentPage.value = target
  pageInput.value = target
  iframeKey.value++
}, { immediate: true })

const jumpToPage = (p) => {
  const pageNum = parseInt(p, 10)
  if (!pageNum || pageNum < 1) return
  currentPage.value = pageNum
  pageInput.value = pageNum
  iframeKey.value++
}
const prevPage = () => {
  if (currentPage.value > 1) jumpToPage(currentPage.value - 1)
}
const nextPage = () => {
  jumpToPage((currentPage.value || 1) + 1)
}
</script>

<style scoped>
.pdf-preview {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
}
.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 14px;
  border-bottom: 1px solid #e5e7eb;
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  flex-shrink: 0;
}
.preview-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
.tool-btn {
  padding: 2px 8px;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  color: #374151;
}
.tool-btn:hover:not(:disabled) { background: #f3f4f6; }
.tool-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.page-num-input {
  width: 52px;
  padding: 2px 6px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  font-size: 12px;
  outline: none;
  text-align: center;
}
.close-btn {
  border: none;
  background: none;
  font-size: 15px;
  cursor: pointer;
  color: #9ca3af;
  padding: 2px 6px;
  border-radius: 4px;
  margin-left: 4px;
}
.close-btn:hover {
  color: #374151;
  background: #f3f4f6;
}
.preview-body {
  flex: 1;
  overflow: hidden;
  background: #525659;
}
.preview-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #9ca3af;
  font-size: 16px;
  background: #fff;
}
</style>
