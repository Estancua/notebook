<template>
  <div class="note-edit-page">
    <TopBar />
    <div class="main-content">
      <!-- 左栏 -->
      <div class="left-panel">
        <NotebookTree
          ref="notebookTreeRef"
          @select-notebook="goHome"
          @select-view="goHome"
        />
      </div>
      <!-- 中栏 -->
      <div class="center-panel">
        <div class="back-bar">
          <button class="back-btn" @click="goHome">← 返回首页</button>
        </div>
        <NoteEditor
          v-if="noteId"
          ref="noteEditorRef"
          :note-id="noteId"
          @saved="onNoteSaved"
          @word-count-change="onWordCountChange"
        />
        <div v-else class="loading-placeholder">加载中...</div>
      </div>
      <!-- 右栏 -->
      <NoteDetailPanel :note="currentNoteDetail" @tags-update="onTagsUpdate" />
    </div>
    <StatusBar :word-count="wordCount" :save-status="saveStatus" />
    <Toast ref="toastRef" />
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TopBar from '../components/TopBar.vue'
import StatusBar from '../components/StatusBar.vue'
import NotebookTree from '../components/NotebookTree.vue'
import NoteEditor from '../components/NoteEditor.vue'
import NoteDetailPanel from '../components/NoteDetailPanel.vue'
import Toast from '../components/Toast.vue'
import { getNoteDetail, saveNote } from '../api/noteApi'

const route = useRoute()
const router = useRouter()

const noteId = ref(null)
const currentNoteDetail = ref({})
const wordCount = ref(0)
const saveStatus = ref('')
const noteEditorRef = ref(null)
const notebookTreeRef = ref(null)
const toastRef = ref(null)

onMounted(() => {
  window.__toastFn = (msg, type) => {
    if (toastRef.value) {
      toastRef.value.show(msg, type)
    }
  }
  loadNoteDetail()
})

watch(() => route.params.noteId, (newId) => {
  noteId.value = newId
  loadNoteDetail()
}, { immediate: true })

async function loadNoteDetail() {
  noteId.value = route.params.noteId
  if (!noteId.value) return
  try {
    const detail = await getNoteDetail(noteId.value)
    currentNoteDetail.value = detail || {}
  } catch (e) { /* handled */ }
}

const onNoteSaved = async () => {
  saveStatus.value = 'saved'
  setTimeout(() => { saveStatus.value = '' }, 2000)
  if (noteId.value) {
    try {
      const detail = await getNoteDetail(noteId.value)
      currentNoteDetail.value = detail || {}
    } catch (e) { /* handled */ }
  }
}

const onWordCountChange = (count) => {
  wordCount.value = count
}

const onTagsUpdate = async (tagIds) => {
  if (!noteId.value) return
  try {
    const detail = currentNoteDetail.value
    await saveNote({
      id: noteId.value,
      notebookId: detail.notebookId,
      title: detail.title,
      content: detail.content || '',
      tagIds
    })
    const newDetail = await getNoteDetail(noteId.value)
    currentNoteDetail.value = newDetail || {}
  } catch (e) { /* handled */ }
}

const goHome = () => {
  router.push('/')
}
</script>

<style scoped>
.note-edit-page {
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
.back-bar {
  padding: 8px 16px;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.back-btn {
  padding: 4px 12px;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  color: #374151;
}
.back-btn:hover {
  background: #f3f4f6;
}
.loading-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #9ca3af;
  font-size: 14px;
}
</style>
