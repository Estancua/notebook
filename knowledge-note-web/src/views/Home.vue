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
      </div>
      <!-- 中栏 -->
      <div class="center-panel">
        <!-- 回收站视图 -->
        <RecycleBin
          v-if="currentView === 'recycle'"
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
        />
      </div>
      <!-- 右栏 -->
      <NoteDetailPanel
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
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

// 弹窗状态
const showCreateNoteDialog = ref(false)
const showCreateNotebookDialog = ref(false)
const showTagManageDialog = ref(false)
const createNotebookParentId = ref(0)

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

// 标签更新后
const onTagsUpdated = () => {
  refreshNoteList()
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
.left-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
</style>
