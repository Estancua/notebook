<template>
  <div class="pdf-preview" v-if="visible && documentId">
    <div class="preview-header">
      <span>{{ fileType || '文件' }} 预览</span>
      <button class="close-btn" @click="$emit('close')">✕</button>
    </div>
    <div class="preview-body">
      <iframe
        v-if="fileType === 'PDF'"
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
import { computed } from 'vue'
import { getDocumentPreviewUrl } from '../api/documentApi'

const props = defineProps({
  visible: { type: Boolean, default: false },
  documentId: { type: [Number, String], default: null },
  fileType: { type: String, default: '' }
})
defineEmits(['close'])

const previewUrl = computed(() => {
  return props.documentId ? getDocumentPreviewUrl(props.documentId) : ''
})
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
  padding: 10px 16px;
  border-bottom: 1px solid #e5e7eb;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  flex-shrink: 0;
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
