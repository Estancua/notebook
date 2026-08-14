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
              <button class="btn-small" @click="toggleSections(doc)">
                {{ expandedId === doc.id ? '收起' : '展开' }}
              </button>
              <button class="btn-small danger" @click="onDelete(doc.id)">删除</button>
            </div>
          </div>
          <div v-if="expandedId === doc.id" class="sections">
            <div v-if="chapterLoading[doc.id]" class="loading-state small">加载章节中...</div>
            <template v-else>
              <template v-for="chapter in chapterTrees[doc.id] || []" :key="chapter.id">
                <ChapterItem
                  :chapter="chapter"
                  :level="1"
                  :doc-notebook-id="doc.notebookId || notebookId"
                  :document-id="doc.id"
                  :editing-titles="editingTitlesDocId === doc.id"
                  @bind="openBindDialog"
                  @create-bind="createAndBindNote"
                  @unbind="handleUnbind"
                  @generate-mindmap="generateMindmapForChapter"
                  @open-note="openNote"
                  @update-chapter="onChapterUpdated"
                  @jump-page="onJumpPage"
                  @title-saved="onTitleSaved"
                />
              </template>
            </template>
            <!-- 内联添加章节行（与已有章节样式一致） -->
            <div v-if="addingChapterDocId === doc.id" class="section-item" style="padding-left: 12px;">
              <div class="section-main">
                <input
                  v-model="newChapterTitle"
                  class="add-chapter-title"
                  placeholder="新章节标题"
                  @keydown.enter="confirmAddChapter(doc)"
                />
                <div class="page-row">
                  <input
                    v-model.number="newChapterPageStart"
                    class="add-chapter-page"
                    type="number"
                    min="1"
                    placeholder="起页"
                  />
                  <span class="page-tilde">~</span>
                  <input
                    v-model.number="newChapterPageEnd"
                    class="add-chapter-page"
                    type="number"
                    min="1"
                    placeholder="止页"
                  />
                </div>
              </div>
              <div class="section-actions">
                <button class="btn-xsmall primary" @click="confirmAddChapter(doc)">💾保存</button>
                <button class="btn-xsmall" @click="cancelAddChapter">取消</button>
              </div>
            </div>
            <div class="chapter-bottom-actions">
              <button class="btn-link" @click="toggleEditTitles(doc)">
                {{ editingTitlesDocId === doc.id ? '完成编辑' : '编辑标题' }}
              </button>
              <button v-if="addingChapterDocId !== doc.id" class="btn-link" @click="startAddChapter(doc)">＋ 添加章节</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 绑定笔记对话框 -->
    <div v-if="bindDialogVisible" class="dialog-overlay" @click.self="bindDialogVisible = false">
      <div class="dialog">
        <div class="dialog-header">
          <span>绑定笔记 - {{ bindDialogChapter?.title }}</span>
          <button class="close-btn" @click="bindDialogVisible = false">✕</button>
        </div>
        <div class="dialog-body">
          <div class="form-group">
            <label class="form-label">选择笔记</label>
            <select v-model="selectedBindNoteId" class="form-input">
              <option value="">-- 请选择 --</option>
              <option v-for="note in availableNotes" :key="note.id" :value="note.id">
                {{ note.title }}
              </option>
            </select>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-small" @click="bindDialogVisible = false">取消</button>
          <button class="btn-small btn-primary" @click="confirmBind" :disabled="!selectedBindNoteId">
            确认绑定
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, inject, h } from 'vue'
import {
  getDocumentsByNotebook,
  deleteDocument,
  generateMindmap,
  getDocumentText,
  getChapterList,
  bindChapterNote,
  unbindChapterNote,
  updateChapter,
  createChapter
} from '../api/documentApi'
import { saveNote, listByNotebook } from '../api/noteApi'

const ChapterItem = {
  name: 'ChapterItem',
  props: {
    chapter: { type: Object, required: true },
    level: { type: Number, default: 1 },
    docNotebookId: { type: [Number, String], default: null },
    documentId: { type: [Number, String], required: true },
    editingTitles: { type: Boolean, default: false }
  },
  emits: ['bind', 'create-bind', 'unbind', 'generate-mindmap', 'open-note', 'update-chapter', 'jump-page', 'title-saved'],
  setup(props, { emit }) {
    const toast = inject('showToast', () => {})
    const generating = ref(false)
    const editingPage = ref(false)
    const saving = ref(false)
    const inputStart = ref(null)
    const inputEnd = ref(null)
    // 标题编辑
    const editingTitle = ref(false)
    const editTitleText = ref('')
    const savingTitle = ref(false)

    const startEditTitle = () => {
      editTitleText.value = props.chapter.title || ''
      editingTitle.value = true
    }
    const cancelEditTitle = () => {
      editingTitle.value = false
      editTitleText.value = ''
    }
    const saveEditTitle = async () => {
      const newTitle = editTitleText.value.trim()
      if (!newTitle) {
        toast('标题不能为空', 'error'); return
      }
      if (newTitle === props.chapter.title) {
        editingTitle.value = false; return
      }
      savingTitle.value = true
      try {
        await updateChapter(props.chapter.id, { title: newTitle })
        // 直接更新本地数据
        props.chapter.title = newTitle
        toast('标题已保存', 'success')
        emit('title-saved', { chapterId: props.chapter.id, title: newTitle })
        editingTitle.value = false
      } catch (e) {
        // handled
      } finally {
        savingTitle.value = false
      }
    }

    const startEditPage = () => {
      inputStart.value = props.chapter.pageStart || ''
      inputEnd.value = props.chapter.pageEnd || props.chapter.pageStart || ''
      editingPage.value = true
    }
    const cancelEditPage = () => {
      editingPage.value = false
      inputStart.value = null
      inputEnd.value = null
    }
    const saveEditPage = async () => {
      const s = parseInt(inputStart.value, 10)
      const e = parseInt(inputEnd.value, 10)
      const payload = {}
      if (!isNaN(s) && s >= 1) payload.pageStart = s
      if (!isNaN(e) && e >= 1) payload.pageEnd = e
      if (payload.pageStart && payload.pageEnd && payload.pageEnd < payload.pageStart) {
        toast('结束页不能小于起始页', 'error'); return
      }
      if (!payload.pageStart && !payload.pageEnd) {
        // 两者都空 => 相当于清空，传 0 或 null？这里传null，但后端只处理非null，所以传null不会更新。
        // 允许用户"清空页码"的话就用 0 触发清除。但我们后端要求>=1，这里改为：若用户删空且都没值，则传空，作为不更新。
        // 想清除页码：用两个都填 null，但我们后端更新时null字段不更新。所以要加个特殊逻辑：当用户传 pageStart=0 表示清除？
        // 为简化，这里提示至少填起始页。
        toast('请至少填写起始页码（>=1），或点击取消', 'error'); return
      }
      saving.value = true
      try {
        const updated = await updateChapter(props.chapter.id, payload)
        toast('页码已保存', 'success')
        // 把 props.chapter 上对应字段改掉（父级会通过 onChapterUpdated 刷新章节树，所以这里只需关闭编辑态）
        emit('update-chapter', { chapterId: props.chapter.id, updated })
        editingPage.value = false
      } catch (e) {
        // handled
      } finally {
        saving.value = false
      }
    }

    const pageDisplay = () => {
      if (props.chapter.pageStart != null) {
        const end = props.chapter.pageEnd || props.chapter.pageStart
        return `[P.${props.chapter.pageStart}~${end}]`
      }
      return '[未绑页码]'
    }

    const hasBind = () => {
      return !!(props.chapter.noteId || props.chapter.noteTitle)
    }

    const handleGenerate = () => {
      if (generating.value) return
      generating.value = true
      emit('generate-mindmap', {
        chapter: props.chapter,
        documentId: props.documentId,
        onDone: () => { generating.value = false }
      })
    }

    const handleJumpPdf = () => {
      if (props.chapter.pageStart == null) {
        toast('该章节还没有绑定页码，先点 ✏️页码 填一下', 'error')
        return
      }
      emit('jump-page', {
        documentId: props.documentId,
        page: props.chapter.pageStart,
        chapterId: props.chapter.id,
        chapterTitle: props.chapter.title
      })
    }

    const handleOpenNote = () => {
      if (props.chapter.noteId) {
        emit('open-note', props.chapter.noteId)
      } else {
        toast('笔记不存在', 'error')
      }
    }

    const renderPageArea = () => {
      if (!editingPage.value) {
        return h('div', { class: 'page-row' }, [
          h('span', { class: 'section-page', title: '章节对应的PDF页码范围' }, pageDisplay()),
          h('button', {
            class: 'btn-xsmall ghost',
            title: '编辑该章节对应的PDF起始/结束页码',
            onClick: startEditPage
          }, '✏️页码'),
          props.chapter.pageStart != null ? h('button', {
            class: 'btn-xsmall primary',
            title: 'PDF预览跳转到该章节起始页',
            onClick: handleJumpPdf
          }, '📖跳PDF') : null
        ])
      }
      return h('div', { class: 'page-edit-row' }, [
        h('input', {
          class: 'page-num',
          type: 'number',
          min: 1,
          placeholder: '起页',
          value: inputStart.value,
          onInput: (e) => { inputStart.value = e.target.value }
        }),
        h('span', { class: 'page-tilde' }, '~'),
        h('input', {
          class: 'page-num',
          type: 'number',
          min: 1,
          placeholder: '止页(可空)',
          value: inputEnd.value,
          onInput: (e) => { inputEnd.value = e.target.value }
        }),
        h('button', {
          class: 'btn-xsmall primary',
          disabled: saving.value,
          onClick: saveEditPage
        }, saving.value ? '保存中' : '💾保存'),
        h('button', {
          class: 'btn-xsmall',
          disabled: saving.value,
          onClick: cancelEditPage
        }, '取消')
      ])
    }

    const renderTitle = () => {
      if (props.editingTitles && editingTitle.value) {
        return h('div', { class: 'title-edit-row' }, [
          h('input', {
            class: 'title-edit-input',
            value: editTitleText.value,
            onInput: (e) => { editTitleText.value = e.target.value },
            onKeydown: (e) => {
              if (e.key === 'Enter') saveEditTitle()
              if (e.key === 'Escape') cancelEditTitle()
            },
            placeholder: '章节标题'
          }),
          h('button', {
            class: 'btn-xsmall primary',
            disabled: savingTitle.value,
            onClick: saveEditTitle
          }, savingTitle.value ? '...' : '💾'),
          h('button', {
            class: 'btn-xsmall',
            disabled: savingTitle.value,
            onClick: cancelEditTitle
          }, '取消')
        ])
      }
      return h('span', {
        class: 'section-title',
        style: props.editingTitles ? { cursor: 'pointer', borderBottom: '1px dashed #bfdbfe', padding: '2px 4px', borderRadius: '3px' } : {},
        onClick: props.editingTitles ? startEditTitle : undefined
      }, props.chapter.title)
    }

    return () => h('div', { class: 'chapter-wrapper' }, [
      h('div', {
        class: 'section-item',
        style: { paddingLeft: ((props.level || 1) - 1) * 16 + 12 + 'px' }
      }, [
        h('div', { class: 'section-main' }, [
          renderTitle(),
          renderPageArea(),
          hasBind()
            ? h('span', { class: 'section-bound', onClick: handleOpenNote }, [
                '✅ ',
                h('span', { class: 'bound-note-title' }, props.chapter.noteTitle || '已绑定笔记')
              ])
            : h('span', { class: 'section-unbound' }, '⭕未绑定')
        ]),
        h('div', { class: 'section-actions' }, [
          !hasBind() ? h('button', {
            class: 'btn-xsmall',
            onClick: () => emit('bind', { chapter: props.chapter, notebookId: props.docNotebookId })
          }, '🔗绑定') : null,
          !hasBind() ? h('button', {
            class: 'btn-xsmall',
            onClick: () => emit('create-bind', { chapter: props.chapter, notebookId: props.docNotebookId })
          }, '➕新建绑定') : null,
          hasBind() ? h('button', {
            class: 'btn-xsmall',
            onClick: () => emit('bind', { chapter: props.chapter, notebookId: props.docNotebookId })
          }, '🔄重绑') : null,
          hasBind() ? h('button', {
            class: 'btn-xsmall',
            onClick: () => emit('unbind', props.chapter)
          }, '✂️解绑') : null,
          h('button', {
            class: 'btn-xsmall',
            disabled: generating.value,
            onClick: handleGenerate
          }, generating.value ? '生成中...' : '🧠脑图')
        ])
      ]),
      ...(props.chapter.children || []).map(child =>
        h(ChapterItem, {
          chapter: child,
          level: props.level + 1,
          docNotebookId: props.docNotebookId,
          documentId: props.documentId,
          editingTitles: props.editingTitles,
          onBind: (d) => emit('bind', d),
          onCreateBind: (d) => emit('create-bind', d),
          onUnbind: (c) => emit('unbind', c),
          onGenerateMindmap: (d) => emit('generate-mindmap', d),
          onOpenNote: (id) => emit('open-note', id),
          onUpdateChapter: (d) => emit('update-chapter', d),
          onJumpPage: (d) => emit('jump-page', d),
          onTitleSaved: (d) => emit('title-saved', d)
        })
      )
    ])
  }
}

const props = defineProps({
  notebookId: { type: [Number, String], default: null }
})
const emit = defineEmits(['close', 'upload', 'open-note', 'preview-doc', 'jump-page'])
const toast = inject('showToast', () => {})

const loading = ref(false)
const documents = ref([])
const expandedId = ref(null)
const editingTitlesDocId = ref(null)

const chapterTrees = ref({})
const chapterLoading = ref({})

const bindDialogVisible = ref(false)
const bindDialogChapter = ref(null)
const bindDialogNotebookId = ref(null)
const selectedBindNoteId = ref('')
const availableNotes = ref([])

// 添加章节（内联）
const addingChapterDocId = ref(null)
const newChapterTitle = ref('')
const newChapterPageStart = ref(null)
const newChapterPageEnd = ref(null)

const startAddChapter = (doc) => {
  addingChapterDocId.value = doc.id
  newChapterTitle.value = ''
  newChapterPageStart.value = null
  newChapterPageEnd.value = null
}

const cancelAddChapter = () => {
  addingChapterDocId.value = null
  newChapterTitle.value = ''
  newChapterPageStart.value = null
  newChapterPageEnd.value = null
}

const confirmAddChapter = async (doc) => {
  const title = newChapterTitle.value.trim()
  if (!title) return
  const payload = {
    documentId: doc.id,
    parentId: 0,
    title,
    level: 1
  }
  const ps = parseInt(newChapterPageStart.value, 10)
  const pe = parseInt(newChapterPageEnd.value, 10)
  if (!isNaN(ps) && ps >= 1) payload.pageStart = ps
  if (!isNaN(pe) && pe >= 1) payload.pageEnd = pe
  try {
    await createChapter(payload)
    toast('章节添加成功', 'success')
    cancelAddChapter()
    // 刷新章节树
    chapterTrees.value[doc.id] = null
    loadChapters(doc)
  } catch (e) { /* handled */ }
}

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

const buildTree = (flatList) => {
  const map = {}
  const roots = []
  const list = Array.isArray(flatList) ? flatList : []
  list.forEach(item => {
    map[item.id] = { ...item, children: [] }
  })
  list.forEach(item => {
    const node = map[item.id]
    if (item.parentId && map[item.parentId]) {
      map[item.parentId].children.push(node)
    } else {
      roots.push(node)
    }
  })
  return roots
}

const loadChapters = async (doc) => {
  chapterLoading.value[doc.id] = true
  try {
    const list = await getChapterList(doc.id)
    chapterTrees.value[doc.id] = buildTree(list)
  } catch (e) {
    chapterTrees.value[doc.id] = []
  } finally {
    chapterLoading.value[doc.id] = false
  }
}

const toggleSections = (doc) => {
  if (expandedId.value === doc.id) {
    expandedId.value = null
  } else {
    expandedId.value = doc.id
    loadChapters(doc)
  }
  editingTitlesDocId.value = null
}

const toggleEditTitles = (doc) => {
  if (editingTitlesDocId.value === doc.id) {
    editingTitlesDocId.value = null
  } else {
    editingTitlesDocId.value = doc.id
  }
}

const onTitleSaved = () => {
  // 标题更新后无需特殊处理，ChapterItem 已直接更新本地数据
}

const onDelete = async (id) => {
  if (!confirm('确定要删除该文档吗？')) return
  try {
    await deleteDocument(id)
    toast('文档已删除', 'success')
    if (expandedId.value === id) expandedId.value = null
    delete chapterTrees.value[id]
    delete chapterLoading.value[id]
    await loadDocuments()
  } catch (e) { /* handled */ }
}

const previewDoc = (doc) => {
  emit('preview-doc', doc)
}

const openNote = (noteId) => {
  emit('open-note', noteId)
}

// 单个章节页码保存后，刷新文档的章节树以便展示最新pageStart/pageEnd
const onChapterUpdated = async ({ chapterId, updated }) => {
  // 找到章节属于哪个 doc
  for (const doc of documents.value) {
    const tree = chapterTrees.value[doc.id]
    if (tree && treeContains(tree, chapterId)) {
      chapterTrees.value[doc.id] = null
      await loadChapters(doc)
      return
    }
  }
}

const onJumpPage = (payload) => {
  // 先确保该文档处于预览状态
  const doc = documents.value.find(d => String(d.id) === String(payload.documentId))
  if (doc) emit('preview-doc', doc)
  emit('jump-page', payload)
}

const generateMindmapForChapter = async ({ chapter, documentId, onDone }) => {
  try {
    const fullText = await getDocumentText(documentId)
    const sectionContent = extractSectionContent(fullText, chapter.title)
    const result = await generateMindmap(documentId, {
      sectionTitle: chapter.title,
      sectionContent,
      chapterId: chapter.id
    })
    toast('思维导图生成成功', 'success')
    if (result && result.noteId) {
      try {
        await bindChapterNote(chapter.id, result.noteId)
        const doc = documents.value.find(d => d.id === documentId)
        if (doc) {
          chapterTrees.value[doc.id] = null
          loadChapters(doc)
        }
      } catch (e) { /* ignore bind error */ }
      emit('open-note', result.noteId)
    }
  } catch (e) { /* handled */ } finally {
    onDone && onDone()
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

const openBindDialog = async ({ chapter, notebookId }) => {
  bindDialogChapter.value = chapter
  bindDialogNotebookId.value = notebookId
  selectedBindNoteId.value = chapter.noteId || ''
  availableNotes.value = []
  try {
    const nbId = notebookId || props.notebookId
    if (nbId) {
      const data = await listByNotebook(nbId)
      availableNotes.value = (data && data.records) ? data.records : []
    }
  } catch (e) { /* handled */ }
  bindDialogVisible.value = true
}

const confirmBind = async () => {
  if (!bindDialogChapter.value || !selectedBindNoteId.value) return
  try {
    await bindChapterNote(bindDialogChapter.value.id, selectedBindNoteId.value)
    toast('绑定成功', 'success')
    bindDialogVisible.value = false
    const doc = documents.value.find(d => {
      return chapterTrees.value[d.id] && treeContains(chapterTrees.value[d.id], bindDialogChapter.value.id)
    })
    if (doc) {
      chapterTrees.value[doc.id] = null
      loadChapters(doc)
    }
  } catch (e) { /* handled */ }
}

const treeContains = (nodes, id) => {
  for (const n of nodes) {
    if (n.id === id) return true
    if (n.children && n.children.length) {
      if (treeContains(n.children, id)) return true
    }
  }
  return false
}

const createAndBindNote = async ({ chapter, notebookId }) => {
  if (!chapter) return
  const nbId = notebookId || props.notebookId
  if (!nbId) {
    toast('缺少笔记本信息', 'error')
    return
  }
  try {
    const result = await saveNote({
      notebookId: nbId,
      title: chapter.title,
      content: ''
    })
    const noteId = result && (result.id || result)
    if (noteId) {
      await bindChapterNote(chapter.id, noteId)
      toast('新建笔记并绑定成功', 'success')
      const doc = documents.value.find(d => {
        return chapterTrees.value[d.id] && treeContains(chapterTrees.value[d.id], chapter.id)
      })
      if (doc) {
        chapterTrees.value[doc.id] = null
        loadChapters(doc)
      }
    }
  } catch (e) { /* handled */ }
}

const handleUnbind = async (chapter) => {
  if (!chapter || !confirm('确定要解绑该章节与笔记的关联吗？')) return
  try {
    await unbindChapterNote(chapter.id)
    toast('解绑成功', 'success')
    const doc = documents.value.find(d => {
      return chapterTrees.value[d.id] && treeContains(chapterTrees.value[d.id], chapter.id)
    })
    if (doc) {
      chapterTrees.value[doc.id] = null
      loadChapters(doc)
    }
  } catch (e) { /* handled */ }
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
.loading-state.small {
  padding: 20px;
  font-size: 12px;
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
.btn-small.btn-primary {
  background: #3b82f6;
  border-color: #3b82f6;
  color: #fff;
}
.btn-small.btn-primary:hover {
  background: #2563eb;
  border-color: #2563eb;
}
.btn-xsmall {
  padding: 2px 6px;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 3px;
  cursor: pointer;
  font-size: 11px;
  color: #374151;
  white-space: nowrap;
}
.btn-xsmall:hover:not(:disabled) {
  background: #f3f4f6;
}
.btn-xsmall:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn-xsmall.primary {
  background: #3b82f6;
  border-color: #3b82f6;
  color: #fff;
}
.btn-xsmall.primary:hover:not(:disabled) {
  background: #2563eb;
  border-color: #2563eb;
}
.btn-xsmall.ghost {
  border-style: dashed;
  color: #3b82f6;
  border-color: #bfdbfe;
  background: #eff6ff;
}
.btn-xsmall.ghost:hover:not(:disabled) {
  background: #dbeafe;
}
.sections {
  padding: 8px 0;
  border-top: 1px solid #e5e7eb;
}
.section-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 8px 14px;
  gap: 8px;
  border-bottom: 1px dashed #f3f4f6;
}
.section-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}
.section-title {
  font-size: 13px;
  color: #1f2937;
  font-weight: 500;
  word-break: break-all;
}
.title-edit-row {
  display: flex;
  align-items: center;
  gap: 4px;
}
.title-edit-input {
  flex: 1;
  min-width: 0;
  padding: 3px 8px;
  border: 1px solid #3b82f6;
  border-radius: 4px;
  font-size: 13px;
  color: #1f2937;
  outline: none;
  font-weight: 500;
  background: #fff;
}
.title-edit-input:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 2px rgba(59,130,246,0.15);
}
.page-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.page-edit-row {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
  padding: 4px 0;
}
.page-num {
  width: 60px;
  padding: 2px 6px;
  border: 1px solid #bfdbfe;
  border-radius: 3px;
  font-size: 11px;
  outline: none;
  background: #eff6ff;
}
.page-num:focus {
  border-color: #3b82f6;
}
.page-tilde {
  color: #6b7280;
  font-size: 11px;
}
.section-page {
  font-size: 11px;
  color: #6b7280;
}
.section-unbound {
  font-size: 11px;
  color: #9ca3af;
}
.section-bound {
  font-size: 12px;
  color: #059669;
  cursor: pointer;
}
.section-bound:hover {
  text-decoration: underline;
}
.bound-note-title {
  font-weight: 500;
}
.section-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
  flex-shrink: 0;
  justify-content: flex-end;
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
.chapter-bottom-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  border-top: 1px solid #f3f4f6;
  padding: 2px 0;
}
.add-chapter-title {
  flex: 1;
  min-width: 0;
  padding: 3px 8px;
  border: 1px solid #3b82f6;
  border-radius: 4px;
  font-size: 13px;
  color: #1f2937;
  outline: none;
  font-weight: 500;
  background: #fff;
}
.add-chapter-title:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 2px rgba(59,130,246,0.15);
}
.add-chapter-page {
  width: 72px;
  padding: 3px 6px;
  border: 1px solid #bfdbfe;
  border-radius: 4px;
  font-size: 12px;
  color: #374151;
  outline: none;
  background: #fff;
  text-align: center;
}
.add-chapter-page:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59,130,246,0.1);
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
  width: 420px;
  max-width: 90%;
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
.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
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
}
.form-input:focus {
  border-color: #3b82f6;
}
</style>
