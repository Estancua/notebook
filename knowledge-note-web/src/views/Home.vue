<template>
  <div class="home">
    <TopBar @search="onGlobalSearch" />
    <div class="main-content">
      <!-- 左栏 -->
      <div class="left-panel">
        <NotebookTree
          ref="notebookTreeRef"
          :current-view="currentView"
          :selected-notebook-id="selectedNotebookId"
          @select-notebook="onSelectNotebook"
          @select-view="onSelectView"
          @create-notebook="onCreateNotebook"
        />
        <div class="doc-manage-area" v-if="selectedNotebookId && selectedNotebookId !== 'all'">
          <button
            class="doc-manage-btn"
            :class="{ active: showDocPanel }"
            @click="toggleDocPanel"
            title="文档管理"
          >📄 文档管理</button>
        </div>
      </div>
      <!-- 中栏 -->
      <div class="center-panel" :class="{ 'mindmap-expanded': isMindmapMode }">
        <!-- 文档管理视图 -->
        <template v-if="showDocPanel">
          <div class="doc-view-area">
            <div class="doc-panel-col" :class="{ 'with-preview': docPreviewVisible }">
              <DocumentPanel
                :notebook-id="selectedNotebookId"
                @close="showDocPanel = false"
                @upload="showDocUploadDialog = true"
                @open-note="handleOpenNote"
                @preview-doc="handlePreviewDoc"
                @jump-page="handleJumpPage"
              />
            </div>
            <div class="doc-preview-col" v-if="docPreviewVisible">
              <PdfPreview
                :visible="docPreviewVisible"
                :document-id="previewDoc?.id"
                :file-type="previewDoc?.fileType"
                :page="previewPage"
                @close="closePreview"
              />
            </div>
          </div>
        </template>
        <!-- 回收站视图 -->
        <RecycleBin
          v-else-if="currentView === 'recycle'"
          @close="currentView = 'all'"
        />
        <!-- 笔记列表视图 -->
        <NoteList
          v-else-if="currentView !== 'note' || !selectedNoteId"
          ref="noteListRef"
          :notebook-id="selectedNotebookId"
          :notebook-name="selectedNotebookName"
          :selected-note-id="selectedNoteId"
          :is-favorites="currentView === 'favorites'"
          @select-note="onSelectNote"
          @create-note="showCreateNoteDialog = true"
          @refresh="refreshNoteList"
        />
        <!-- 编辑器视图 -->
        <NoteEditor
          v-else
          ref="noteEditorRef"
          :note-id="selectedNoteId"
          @saved="onNoteSaved"
          @word-count-change="onWordCountChange"
          @mindmap-active="onMindmapActive"
          @toggle-document-preview="toggleDocPanel"
        />
      </div>
      <!-- 右栏（导图模式隐藏） -->
      <NoteDetailPanel
        v-show="!isMindmapMode"
        :note="currentNoteDetail"
        @tags-update="onTagsUpdate"
      />
      <!-- Toast -->
      <Toast ref="toastRef" />
    </div>
    <StatusBar :word-count="wordCount" :save-status="saveStatus" />

    <!-- 弹窗 -->
    <CreateNoteDialog
      :visible="showCreateNoteDialog"
      @close="showCreateNoteDialog = false"
      @created="onNoteCreated"
    />
    <CreateNotebookDialog
      :visible="showCreateNotebookDialog"
      :default-parent-id="createNotebookParentId"
      @close="showCreateNotebookDialog = false"
      @created="onNotebookCreated"
    />
    <TagManageDialog
      :visible="showTagManageDialog"
      @close="showTagManageDialog = false"
      @updated="onTagsUpdated"
    />
    <DocumentUploadDialog
      :visible="showDocUploadDialog"
      :default-notebook-id="selectedNotebookId"
      @close="showDocUploadDialog = false"
      @uploaded="handleDocUploaded"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, provide } from 'vue'
import TopBar from '../components/TopBar.vue'
import StatusBar from '../components/StatusBar.vue'
import NotebookTree from '../components/NotebookTree.vue'
import NoteList from '../components/NoteList.vue'
import NoteEditor from '../components/NoteEditor.vue'
import NoteDetailPanel from '../components/NoteDetailPanel.vue'
import CreateNoteDialog from '../components/CreateNoteDialog.vue'
import CreateNotebookDialog from '../components/CreateNotebookDialog.vue'
import TagManageDialog from '../components/TagManageDialog.vue'
import RecycleBin from '../views/RecycleBin.vue'
import DocumentPanel from '../components/DocumentPanel.vue'
import PdfPreview from '../components/PdfPreview.vue'
import DocumentUploadDialog from '../components/DocumentUploadDialog.vue'
import Toast from '../components/Toast.vue'
import { getNoteDetail, saveNote } from '../api/noteApi'

// 当前视图：all / favorites / note / recycle
const currentView = ref('all')

// Tab 切换
const activeTab = ref('notebook')

// 选中的笔记本
const selectedNotebookId = ref('all')
const selectedNotebookName = ref('')

// 选中的笔记
const selectedNoteId = ref(null)

// 当前笔记详情（给右栏用）
const currentNoteDetail = ref({})
const wordCount = ref(0)
const saveStatus = ref('')
const isMindmapMode = ref(false)

// 弹窗状态
const showCreateNoteDialog = ref(false)
const showCreateNotebookDialog = ref(false)
const showTagManageDialog = ref(false)
const createNotebookParentId = ref(0)

// 文档管理状态
const showDocPanel = ref(false)
const docPreviewVisible = ref(false)
const previewDoc = ref(null)
const previewPage = ref(null)
const showDocUploadDialog = ref(false)

// Refs
const noteListRef = ref(null)
const noteEditorRef = ref(null)
const notebookTreeRef = ref(null)
const toastRef = ref(null)

onMounted(() => {
  window.__toastFn = (msg, type) => {
    if (toastRef.value) {
      toastRef.value.show(msg, type)
    }
  }
})

// 为子组件提供 toast
provide('showToast', (msg, type) => {
  if (toastRef.value) {
    toastRef.value.show(msg, type || 'info')
  }
})

// 选择笔记本
const onSelectNotebook = (id) => {
  currentView.value = 'all'
  selectedNotebookId.value = id
  selectedNoteId.value = null
  currentNoteDetail.value = {}
}

// 选择视图（全部笔记、收藏）
const onSelectView = (view) => {
  currentView.value = view
  selectedNotebookId.value = 'all'
  selectedNoteId.value = null
  currentNoteDetail.value = {}
}

// 全局搜索
const onGlobalSearch = (keyword) => {
  currentView.value = 'all'
  selectedNotebookId.value = 'all'
  selectedNoteId.value = null
  currentNoteDetail.value = {}
  // NoteList 会通过 keyword 参数搜索
}

// 选择笔记
const onSelectNote = async (id) => {
  // 先保存当前笔记
  if (selectedNoteId.value && noteEditorRef.value) {
    await noteEditorRef.value.save()
  }
  selectedNoteId.value = id
  currentView.value = 'note'
  // 加载笔记详情
  try {
    const detail = await getNoteDetail(id)
    currentNoteDetail.value = detail || {}
  } catch (e) { /* handled */ }
}

// 笔记保存后刷新
const onNoteSaved = async () => {
  saveStatus.value = 'saved'
  setTimeout(() => { saveStatus.value = '' }, 2000)
  if (selectedNoteId.value) {
    try {
      const detail = await getNoteDetail(selectedNoteId.value)
      currentNoteDetail.value = detail || {}
    } catch (e) { /* handled */ }
  }
}

// 字数变化
const onWordCountChange = (count) => {
  wordCount.value = count
}

// 标签更新
const onTagsUpdate = async (tagIds) => {
  if (!selectedNoteId.value) return
  try {
    const detail = currentNoteDetail.value
    await saveNote({
      id: selectedNoteId.value,
      notebookId: detail.notebookId,
      title: detail.title,
      content: detail.content || '',
      tagIds
    })
    // 刷新详情
    const newDetail = await getNoteDetail(selectedNoteId.value)
    currentNoteDetail.value = newDetail || {}
  } catch (e) { /* handled */ }
}

// 创建笔记本
const onCreateNotebook = (parentId) => {
  createNotebookParentId.value = parentId || 0
  showCreateNotebookDialog.value = true
}

// 笔记本创建后
const onNotebookCreated = () => {
  notebookTreeRef.value?.loadTree()
}

// 笔记创建后
const onNoteCreated = (data) => {
  noteListRef.value?.loadNotes()
  if (data && data.id) {
    onSelectNote(data.id)
  }
}

// 刷新笔记列表
const refreshNoteList = () => {
  noteListRef.value?.loadNotes()
  if (selectedNoteId.value) {
    getNoteDetail(selectedNoteId.value).then(detail => {
      currentNoteDetail.value = detail || {}
    })
  }
}

// 导图模式切换
const onMindmapActive = (active) => {
  isMindmapMode.value = active
}

// 标签更新后
const onTagsUpdated = () => {
  refreshNoteList()
}

// 文档管理
const toggleDocPanel = () => {
  showDocPanel.value = !showDocPanel.value
  if (!showDocPanel.value) {
    docPreviewVisible.value = false
    previewDoc.value = null
  }
}

const handlePreviewDoc = (doc) => {
  previewDoc.value = doc
  previewPage.value = null
  docPreviewVisible.value = true
}

const handleJumpPage = ({ documentId, page }) => {
  // 确保对应文档已在预览；如未匹配，尝试从已加载文档列表找（DocumentPanel会先发preview-doc，这里兜底）
  if (previewDoc.value == null || String(previewDoc.value.id) !== String(documentId)) {
    // 没有的话也尝试设置页面，等下preview-doc来后会触发渲染
  }
  if (page && page >= 1) {
    previewPage.value = page
    docPreviewVisible.value = true
  }
}

const closePreview = () => {
  docPreviewVisible.value = false
  previewDoc.value = null
  previewPage.value = null
}

const handleDocUploaded = () => {
  // 刷新文档列表 - DocumentPanel 通过 expose 方法
}

const handleOpenNote = (noteId) => {
  // 关闭文档面板，打开生成的笔记
  showDocPanel.value = false
  docPreviewVisible.value = false
  onSelectNote(noteId)
}
</script>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}
.main-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}
.center-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}
.center-panel.mindmap-expanded {
  flex: 3;
}
.left-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.doc-manage-area {
  padding: 8px;
  border-top: 1px solid #e5e7eb;
}
.doc-manage-btn {
  width: 100%;
  padding: 6px 12px;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: #374151;
  text-align: left;
}
.doc-manage-btn:hover {
  background: #f3f4f6;
}
.doc-manage-btn.active {
  background: #dbeafe;
  color: #2563eb;
  border-color: #3b82f6;
}
.doc-view-area {
  display: flex;
  height: 100%;
  overflow: hidden;
}
.doc-panel-col {
  flex: 0 0 340px;
  border-right: 1px solid #e5e7eb;
  overflow: hidden;
}
.doc-panel-col.with-preview {
  flex: 0 0 280px;
}
.doc-preview-col {
  flex: 1;
  overflow: hidden;
}
</style>
