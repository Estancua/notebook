<template>
  <Teleport to="body">
    <div class="dialog-overlay" v-if="visible" @click.self="$emit('close')">
      <div class="dialog">
        <div class="dialog-header">
          <h3>上传文档</h3>
          <button class="dialog-close" @click="$emit('close')">✕</button>
        </div>
        <div class="dialog-body">
          <div class="form-group">
            <label class="form-label">选择笔记本</label>
            <select class="form-input" v-model="selectedNotebookId">
              <option :value="null" disabled>请选择笔记本</option>
              <option v-for="nb in flatNotebooks" :key="nb.id" :value="nb.id">
                {{ '  '.repeat(nb._depth || 0) }}{{ nb.name }}
              </option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">选择文件 (PDF/DOCX)</label>
            <input
              class="form-input"
              type="file"
              ref="fileInput"
              accept=".pdf,.docx"
              @change="onFileChange"
            />
          </div>
          <div class="file-info" v-if="selectedFile">
            已选择: {{ selectedFile.name }} ({{ formatSize(selectedFile.size) }})
          </div>
          <div class="upload-progress" v-if="uploading">上传解析中，请稍候...</div>
        </div>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="$emit('close')">取消</button>
          <button class="btn-submit" :disabled="!canUpload || uploading" @click="doUpload">
            {{ uploading ? '上传中...' : '上传并解析' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, watch, nextTick, inject, computed } from 'vue'
import { getNotebookTree } from '../api/notebookApi'
import { uploadDocument } from '../api/documentApi'

const props = defineProps({
  visible: { type: Boolean, default: false }
})
const emit = defineEmits(['close', 'uploaded'])
const toast = inject('showToast', () => {})

const fileInput = ref(null)
const selectedNotebookId = ref(null)
const selectedFile = ref(null)
const uploading = ref(false)
const flatNotebooks = ref([])

const flattenTree = (nodes, depth = 0) => {
  const result = []
  for (const node of nodes) {
    result.push({ ...node, _depth: depth })
    if (node.children && node.children.length) {
      result.push(...flattenTree(node.children, depth + 1))
    }
  }
  return result
}

watch(() => props.visible, async (v) => {
  if (v) {
    selectedNotebookId.value = null
    selectedFile.value = null
    uploading.value = false
    try {
      const tree = await getNotebookTree()
      flatNotebooks.value = flattenTree(tree || [])
    } catch (e) { /* handled */ }
    nextTick(() => {
      if (fileInput.value) fileInput.value.value = ''
    })
  }
})

const canUpload = computed(() => {
  return selectedNotebookId.value && selectedFile.value && !uploading.value
})

const onFileChange = (e) => {
  const file = e.target.files && e.target.files[0]
  if (file) {
    selectedFile.value = file
  }
}

const formatSize = (bytes) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

const doUpload = async () => {
  if (!canUpload.value) return
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    formData.append('notebookId', selectedNotebookId.value)
    const data = await uploadDocument(formData)
    toast('文档上传成功，正在解析...', 'success')
    emit('uploaded', data)
    emit('close')
  } catch (e) { /* handled */ } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0,0,0,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
}
.dialog {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.15);
  width: 420px;
  max-height: 80vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #e5e7eb;
}
.dialog-header h3 {
  font-size: 16px;
  font-weight: 600;
}
.dialog-close {
  border: none;
  background: none;
  font-size: 16px;
  cursor: pointer;
  color: #9ca3af;
}
.dialog-close:hover {
  color: #374151;
}
.dialog-body {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
}
.form-group {
  margin-bottom: 16px;
}
.form-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 6px;
}
.form-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 14px;
  outline: none;
  color: #374151;
  transition: border-color 0.2s;
  box-sizing: border-box;
}
.form-input:focus {
  border-color: #3b82f6;
}
select.form-input {
  cursor: pointer;
}
.file-info {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #f9fafb;
  border-radius: 6px;
}
.upload-progress {
  font-size: 13px;
  color: #3b82f6;
  padding: 10px 0;
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid #e5e7eb;
}
.btn-cancel {
  padding: 8px 20px;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: #374151;
}
.btn-cancel:hover {
  background: #f3f4f6;
}
.btn-submit {
  padding: 8px 20px;
  border: none;
  background: #3b82f6;
  color: #fff;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}
.btn-submit:hover {
  background: #2563eb;
}
.btn-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
