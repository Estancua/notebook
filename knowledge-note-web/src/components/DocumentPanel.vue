<template>
  <div class="document-panel">
    <div class="panel-header">
      <span>文档解析</span>
      <div class="header-actions">
        <button class="btn-small" @click="$emit('upload')">上传文档</button>
        <button class="close-btn" @click="$emit('close')">✕</button>
      </div>
    </div>
    <div class="panel-body">
      <div v-if="loading" class="loading-state">加载中...</div>
      <div v-else-if="documents.length === 0" class="empty-state">
        暂未上传文档，请点击上方的"上传文档"按钮
      </div>
      <div v-else>
        <div v-for="doc in documents" :key="doc.id" class="doc-item">
          <div class="doc-header">
            <div class="doc-info">
              <span class="doc-name">{{ doc.fileName }}</span>
              <span class="doc-type">{{ doc.fileType }}</span>
            </div>
            <div class="doc-actions">
              <button class="btn-small" @click="previewDoc(doc)">预览</button>
              <button class="btn-small" @click="toggleSections(doc.id)">
                {{ expandedId === doc.id ? '收起' : '展开' }}
              </button>
              <button class="btn-small danger" @click="onDelete(doc.id)">删除</button>
            </div>
          </div>
          <div v-if="expandedId === doc.id" class="sections">
            <div v-if="editingDocId === doc.id" class="edit-area">
              <textarea
                v-model="editText"
                class="edit-textarea"
                rows="10"
                placeholder="请输入章节JSON格式"
              ></textarea>
              <div class="edit-actions">
                <button class="btn-small" @click="saveEdit(doc.id)">保存</button>
                <button class="btn-small" @click="cancelEdit">取消</button>
              </div>
            </div>
            <div v-else>
              <div
                v-for="(section, idx) in (parseSections(doc))"
                :key="idx"
                class="section-item"
                :style="{ paddingLeft: ((section.level || 1) - 1) * 16 + 12 + 'px' }"
              >
                <span class="section-title">{{ section.title }}</span>
                <button
                  class="btn-small"
                  :disabled="generatingSection && generatingSection.key === idx"
                  @click="generateMindmapForSection(doc.id, section, idx)"
                >
                  {{ (generatingSection && generatingSection.key === idx) ? '生成中...' : '生成脑图' }}
                </button>
              </div>
              <button class="btn-link" @click="startEdit(doc)">编辑章节</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, inject } from 'vue'
import {
  getDocumentsByNotebook,
  updateParseResult,
  deleteDocument,
  generateMindmap,
  getDocumentText
} from '../api/documentApi'

const props = defineProps({
  notebookId: { type: [Number, String], default: null }
})
const emit = defineEmits(['close', 'upload', 'open-note', 'preview-doc'])
const toast = inject('showToast', () => {})

const loading = ref(false)
const documents = ref([])
const expandedId = ref(null)
const editingDocId = ref(null)
const editText = ref('')
const generatingSection = ref(null)

const loadDocuments = async () => {
  if (!props.notebookId) return
  loading.value = true
  try {
    const data = await getDocumentsByNotebook(props.notebookId)
    documents.value = Array.isArray(data) ? data : []
  } catch (e) { /* handled */ } finally {
    loading.value = false
  }
}

watch(() => props.notebookId, (newId) => {
  if (newId) {
    loadDocuments()
  } else {
    documents.value = []
    expandedId.value = null
  }
}, { immediate: true })

const parseSections = (doc) => {
  if (!doc.parseResult) return []
  try {
    const result = typeof doc.parseResult === 'string'
      ? JSON.parse(doc.parseResult)
      : doc.parseResult
    return Array.isArray(result) ? result : []
  } catch {
    return []
  }
}

const toggleSections = (id) => {
  expandedId.value = expandedId.value === id ? null : id
  editingDocId.value = null
}

const startEdit = (doc) => {
  editingDocId.value = doc.id
  editText.value = typeof doc.parseResult === 'string'
    ? doc.parseResult
    : JSON.stringify(doc.parseResult, null, 2)
}

const cancelEdit = () => {
  editingDocId.value = null
  editText.value = ''
}

const saveEdit = async (id) => {
  try {
    const parsed = JSON.parse(editText.value)
    await updateParseResult(id, parsed)
    toast('章节更新成功', 'success')
    editingDocId.value = null
    editText.value = ''
    await loadDocuments()
  } catch (e) {
    if (e instanceof SyntaxError) {
      toast('JSON 格式错误，请检查', 'error')
    }
  }
}

const onDelete = async (id) => {
  if (!confirm('确定要删除该文档吗？')) return
  try {
    await deleteDocument(id)
    toast('文档已删除', 'success')
    if (expandedId.value === id) expandedId.value = null
    await loadDocuments()
  } catch (e) { /* handled */ }
}

const previewDoc = (doc) => {
  emit('preview-doc', doc)
}

const generateMindmapForSection = async (docId, section, idx) => {
  generatingSection.value = { key: idx }
  try {
    const fullText = await getDocumentText(docId)
    const sectionContent = extractSectionContent(fullText, section.title)
    const result = await generateMindmap(docId, {
      sectionTitle: section.title,
      sectionContent
    })
    toast('思维导图生成成功', 'success')
    if (result && result.noteId) {
      emit('open-note', result.noteId)
    }
  } catch (e) { /* handled */ } finally {
    generatingSection.value = null
  }
}

const extractSectionContent = (fullText, sectionTitle) => {
  if (!fullText || !sectionTitle) return ''
  const escaped = sectionTitle.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const regex = new RegExp(`(?:^|\\n)#{1,6}\\s+${escaped}\\b`, 'i')
  const match = fullText.match(regex)
  if (!match) return fullText.substring(0, 2000)
  const startIdx = match.index
  const afterMatch = fullText.substring(startIdx + match[0].length)
  const nextHeading = afterMatch.match(/\n#{1,6}\s+/)
  const endIdx = nextHeading
    ? startIdx + match[0].length + nextHeading.index
    : fullText.length
  return fullText.substring(startIdx, endIdx)
}

defineExpose({ loadDocuments })
</script>

<style scoped>
.document-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
}
.panel-header {
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
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
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
.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}
.loading-state,
.empty-state {
  text-align: center;
  padding: 40px 20px;
  color: #9ca3af;
  font-size: 14px;
}
.doc-item {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  margin-bottom: 10px;
  overflow: hidden;
}
.doc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: #f9fafb;
}
.doc-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.doc-name {
  font-size: 14px;
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.doc-type {
  font-size: 11px;
  padding: 1px 6px;
  background: #dbeafe;
  color: #2563eb;
  border-radius: 4px;
  white-space: nowrap;
}
.doc-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}
.btn-small {
  padding: 4px 10px;
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
.btn-small:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn-small.danger {
  color: #ef4444;
  border-color: #fecaca;
}
.btn-small.danger:hover {
  background: #fef2f2;
}
.sections {
  padding: 8px 0;
  border-top: 1px solid #e5e7eb;
}
.section-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 14px;
}
.section-title {
  font-size: 13px;
  color: #374151;
}
.edit-area {
  padding: 10px 14px;
}
.edit-textarea {
  width: 100%;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 13px;
  font-family: "Consolas", "Monaco", monospace;
  resize: vertical;
  outline: none;
  color: #374151;
  box-sizing: border-box;
}
.edit-textarea:focus {
  border-color: #3b82f6;
}
.edit-actions {
  margin-top: 8px;
  display: flex;
  gap: 6px;
}
.btn-link {
  background: none;
  border: none;
  color: #3b82f6;
  cursor: pointer;
  font-size: 13px;
  padding: 6px 14px;
}
.btn-link:hover {
  text-decoration: underline;
}
</style>
