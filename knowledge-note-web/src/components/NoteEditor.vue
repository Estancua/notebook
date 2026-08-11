<template>
  <div class="note-editor-outer" :class="{ 'with-side-nav': navDrawerVisible }">
    <div class="note-editor" :class="{ mindmap: mode === 'mindmap', 'with-pdf': showPdfPanel && bindInfo }" v-if="noteId">
      <!-- 标题栏(导图模式隐藏) -->
      <div class="editor-title-bar" v-show="mode !== 'mindmap'">
        <input
          class="title-input"
          v-model="title"
          placeholder="笔记标题"
          @input="markDirty"
        />
      </div>
      <!-- 工具栏 -->
      <div class="editor-toolbar">
        <template v-if="mode !== 'mindmap'">
        <button class="tool-btn" title="加粗 (Ctrl+B)" @click="wrapText('**')">B</button>
        <button class="tool-btn" title="斜体 (Ctrl+I)" @click="wrapText('*')"><em>I</em></button>
        <button class="tool-btn" title="标题1" @click="insertLine('# ')">H1</button>
        <button class="tool-btn" title="标题2" @click="insertLine('## ')">H2</button>
        <button class="tool-btn" title="标题3" @click="insertLine('### ')">H3</button>
        <button class="tool-btn" title="无序列表" @click="insertLine('- ')">• 列表</button>
        <button class="tool-btn" title="有序列表" @click="insertLine('1. ')">1. 列表</button>
        <button class="tool-btn" title="引用" @click="insertLine('> ')">"</button>
        <button class="tool-btn" title="链接" @click="wrapLink()">🔗</button>
        <button class="tool-btn" title="代码块" @click="wrapText('`')">&lt;/&gt;</button>
        <span class="toolbar-sep"></span>
        </template>
        <button class="tool-btn" title="文档管理" @click="$emit('toggle-document-preview')">📄</button>
        <span class="toolbar-sep"></span>
        <button class="tool-btn" @click="save" title="保存 (Ctrl+S)">💾 保存</button>
        <span class="toolbar-sep"></span>
        <button
          class="tool-btn"
          :class="{ active: mode === 'edit' }"
          @click="mode = 'edit'"
        >✏ 编辑</button>
        <button
          class="tool-btn"
          :class="{ active: mode === 'preview' }"
          @click="mode = 'preview'"
        >👁 预览</button>
        <button
          class="tool-btn"
          :class="{ active: mode === 'split' }"
          @click="mode = 'split'"
        >◫ 分屏</button>
        <span class="toolbar-sep"></span>
        <button
          class="tool-btn"
          :class="{ active: mode === 'mindmap' }"
          @click="switchToMindmap"
        >🧠 导图</button>

        <template v-if="bindInfo">
          <span class="toolbar-sep"></span>
          <button
            class="tool-btn"
            :class="{ active: showPdfPanel }"
            @click="showPdfPanel = !showPdfPanel"
            :title="showPdfPanel ? '关闭原文对照' : '开启原文对照'"
          >
            📑 原文对照 {{ showPdfPanel ? 'ON' : 'OFF' }}
          </button>
          <button
            class="tool-btn"
            :class="{ active: navDrawerVisible }"
            @click="navDrawerVisible = !navDrawerVisible"
            title="知识点导航"
          >
            🗺️ 知识点导航
          </button>
        </template>
      </div>

      <!-- 主体区 -->
      <div class="editor-main" v-if="showPdfPanel && bindInfo">
        <!-- 左栏（编辑/预览/导图） -->
        <div class="editor-left" :style="{ width: leftWidth + '%' }">
          <editor-content-area
            :mode="mode"
            :content="content"
            :rendered-content="renderedContent"
            :textarea-ref="textareaRef"
            :title-input-focus="false"
            @content-input="onContentInput"
            @tab="onTab"
            @save="save"
            @keydown="onKeydown"
            @mindmap-update="onMindmapUpdate"
            @mindmap-back="mode = 'edit'"
            :mindmap-content="content"
            :note-id="noteId"
            :pdf-refs="pdfRefs"
            ref="mindMapViewerRef"
            @jump-pdf-page="onJumpPdfPage"
            @ref-changed="refreshPdfRefs"
          />
        </div>
        <!-- 分隔条 -->
        <div
          class="splitter"
          @mousedown="onSplitterMouseDown"
          title="拖动调整宽度"
        ></div>
        <!-- 右栏（PDF预览） -->
        <div class="editor-right" :style="{ width: (100 - leftWidth) + '%' }">
          <div class="pdf-toolbar">
            <button class="pdf-nav-btn" @click="prevPage" :disabled="pdfPage <= 1">◀上一页</button>
            <input
              v-model.number="pdfPageInput"
              type="number"
              min="1"
              class="pdf-page-input"
              @keyup.enter="jumpToPage(pdfPageInput)"
            />
            <button class="pdf-nav-btn" @click="jumpToPage(pdfPageInput)">跳转</button>
            <button class="pdf-nav-btn" @click="nextPage">下一页▶</button>
          </div>
          <iframe
            class="pdf-iframe"
            :key="pdfIframeKey"
            :src="pdfIframeSrc"
            frameborder="0"
          ></iframe>
        </div>
      </div>

      <!-- 非分栏模式下的编辑区 -->
      <editor-content-area
        v-else
        :mode="mode"
        :content="content"
        :rendered-content="renderedContent"
        :textarea-ref="textareaRef"
        @content-input="onContentInput"
        @tab="onTab"
        @save="save"
        @keydown="onKeydown"
        @link-suggest-visible-change="(v) => linkSuggestVisible = v"
        @link-suggestions-change="(list) => linkSuggestions = list"
        @link-suggest-style-change="(s) => linkSuggestStyle = s"
        @insert-link="insertLink"
        :link-suggest-visible="linkSuggestVisible"
        :link-suggestions="linkSuggestions"
        :link-suggest-style="linkSuggestStyle"
        @mindmap-update="onMindmapUpdate"
        @mindmap-back="mode = 'edit'"
        :mindmap-content="content"
        :note-id="noteId"
        :pdf-refs="pdfRefs"
        ref="mindMapViewerRef"
        @jump-pdf-page="onJumpPdfPage"
        @ref-changed="refreshPdfRefs"
      />
    </div>
    <div class="editor-placeholder" v-else>
      <div class="placeholder-icon">📝</div>
      <p>请选择一个笔记开始编辑</p>
    </div>

    <!-- 知识点导航抽屉 -->
    <div v-if="navDrawerVisible" class="nav-drawer" :class="{ visible: navDrawerVisible }">
      <div class="nav-drawer-header">
        <span class="nav-drawer-title">🗺️ 知识点导航</span>
        <button class="close-btn" @click="navDrawerVisible = false">✕</button>
      </div>
      <div class="nav-drawer-search">
        <input
          v-model="navSearchKeyword"
          type="text"
          class="form-input"
          placeholder="搜索节点标题/摘录..."
        />
      </div>
      <div class="nav-drawer-list">
        <div v-if="filteredPdfRefs.length === 0" class="nav-empty">
          暂无关联知识点
        </div>
        <div
          v-for="ref in filteredPdfRefs"
          :key="ref.id"
          class="nav-item"
        >
          <div class="nav-item-main" @click="onNavItemClick(ref)">
            <span class="nav-page">[📖 P.{{ ref.pageStart || '?' }}]</span>
            <span class="nav-title">{{ ref.nodeTitle }}</span>
          </div>
          <div class="nav-item-actions">
            <button class="nav-btn" title="编辑" @click.stop="editRefFromNav(ref)">✏️</button>
            <button class="nav-btn danger" title="删除" @click.stop="deleteRefFromNav(ref)">🗑️</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 抽屉遮罩 -->
    <div v-if="navDrawerVisible" class="nav-drawer-mask" @click="navDrawerVisible = false"></div>
  </div>
</template>

<script setup>
import { ref, computed, watch, inject, nextTick, onBeforeUnmount, defineComponent, h } from 'vue'
import { marked } from 'marked'
import {
  getNoteDetail,
  saveNote,
  getNoteList,
  getPdfRefList,
  savePdfRef,
  deletePdfRef
} from '../api/noteApi'
import MindMapViewer from './MindMapViewer.vue'

const EditorContentArea = defineComponent({
  name: 'EditorContentArea',
  props: {
    mode: String,
    content: String,
    renderedContent: String,
    textareaRef: Object,
    linkSuggestVisible: Boolean,
    linkSuggestions: Array,
    linkSuggestStyle: Object,
    mindmapContent: String,
    noteId: [Number, String],
    pdfRefs: Array
  },
  emits: [
    'contentInput', 'tab', 'save', 'keydown',
    'linkSuggestVisibleChange', 'linkSuggestionsChange', 'linkSuggestStyleChange',
    'insertLink', 'mindmapUpdate', 'mindmapBack',
    'jumpPdfPage', 'refChanged'
  ],
  setup(props, { emit, expose }) {
    const mindMapViewerRef = ref(null)
    expose({ mindMapViewerRef })

    return () => {
      const m = props.mode
      const showEditPane = m !== 'preview'
      const showPreviewPane = m === 'preview' || m === 'split'
      const showMindmapPane = m === 'mindmap'

      const style = {
        class: ['editor-content', m || 'edit']
      }

      return h('div', style, [
        // Edit pane
        showEditPane && !showMindmapPane ? h('div', { class: 'edit-pane' }, [
          h('textarea', {
            ref: (el) => { if (props.textareaRef) props.textareaRef.value = el },
            class: 'content-textarea',
            value: props.content,
            onInput: (e) => emit('contentInput', e),
            onKeydown: (e) => {
              if (e.key === 'Tab') { e.preventDefault(); emit('tab', e) }
              else if (e.ctrlKey && e.key === 's') { e.preventDefault(); emit('save') }
              else { emit('keydown', e) }
            },
            placeholder: '开始输入 Markdown 内容...'
          })
        ]) : null,

        // Preview pane
        showPreviewPane && !showMindmapPane ? h('div', { class: 'preview-pane' }, [
          h('div', {
            class: 'markdown-body',
            innerHTML: props.renderedContent || ''
          })
        ]) : null,

        // Link suggest dropdown
        !showMindmapPane && props.linkSuggestVisible ? h('div', {
          class: 'link-suggest-dropdown',
          style: props.linkSuggestStyle || {}
        }, [
          h('div', { class: 'suggest-title' }, '选择要引用的笔记'),
          (!props.linkSuggestions || props.linkSuggestions.length === 0)
            ? h('div', { class: 'suggest-empty' }, '无匹配笔记')
            : (props.linkSuggestions || []).map(item =>
                h('div', {
                  key: item.id,
                  class: 'suggest-item',
                  onClick: () => emit('insertLink', item.title)
                }, item.title)
              )
        ]) : null,

        // Mindmap pane
        showMindmapPane ? h('div', { class: 'mindmap-pane' }, [
          h(MindMapViewer, {
            ref: (r) => { mindMapViewerRef.value = r },
            content: props.mindmapContent,
            noteId: props.noteId,
            pdfRefs: props.pdfRefs || [],
            'onUpdate:content': (v) => emit('mindmapUpdate', v),
            onBack: () => emit('mindmapBack'),
            onJumpPdfPage: (v) => emit('jumpPdfPage', v),
            onRefChanged: () => emit('refChanged')
          })
        ]) : null
      ])
    }
  }
})

const props = defineProps({
  noteId: { type: [Number, String], default: null }
})

const emit = defineEmits(['saved', 'word-count-change', 'mindmap-active', 'toggle-document-preview'])
const toast = inject('showToast', () => {})

const title = ref('')
const content = ref('')
const notebookId = ref(null)
const mode = ref('edit')
const textareaRef = ref(null)
const autoSaveTimer = ref(null)
const saving = ref(false)

const bindInfo = ref(null)
const pdfRefs = ref([])

const showPdfPanel = ref(true)
const leftWidth = ref(60)
const isDragging = ref(false)
const pdfPage = ref(1)
const pdfPageInput = ref(1)
const pdfIframeKey = ref(0)

const navDrawerVisible = ref(false)
const navSearchKeyword = ref('')

const linkSuggestVisible = ref(false)
const linkSuggestions = ref([])
const linkSuggestStyle = ref({})
const linkTriggerPos = ref(-1)

const mindMapViewerRef = ref(null)

const renderedContent = computed(() => {
  try {
    return marked(content.value || '', { breaks: true, gfm: true })
  } catch {
    return content.value
  }
})

const pdfIframeSrc = computed(() => {
  if (!bindInfo.value || !bindInfo.value.documentId) return ''
  const page = pdfPage.value || bindInfo.value.pageStart || 1
  return `/api/document/${bindInfo.value.documentId}/preview?_t=${pdfIframeKey.value}#page=${page}`
})

// 页码变化时强制刷新 iframe（否则仅改#page=N不会触发PDF跳转）
watch(pdfPage, () => {
  pdfIframeKey.value++
})

const filteredPdfRefs = computed(() => {
  if (!navSearchKeyword.value) return pdfRefs.value
  const kw = navSearchKeyword.value.toLowerCase()
  return pdfRefs.value.filter(r =>
    (r.nodeTitle && r.nodeTitle.toLowerCase().includes(kw)) ||
    (r.excerpt && r.excerpt.toLowerCase().includes(kw))
  )
})

const loadNote = async () => {
  if (!props.noteId) return
  try {
    const data = await getNoteDetail(props.noteId)
    if (data) {
      title.value = data.title || ''
      content.value = data.content || ''
      notebookId.value = data.notebookId
      emit('word-count-change', data.wordCount || 0)
      bindInfo.value = data.bindInfo || null
      if (bindInfo.value) {
        pdfPage.value = bindInfo.value.pageStart || 1
        pdfPageInput.value = pdfPage.value
      }
    }
  } catch (e) { /* handled */ }
  // 并行加载 refs（如果笔记保存了的话）
  refreshPdfRefs()
}

const refreshPdfRefs = async () => {
  if (!props.noteId) {
    pdfRefs.value = []
    return
  }
  try {
    const list = await getPdfRefList(props.noteId)
    pdfRefs.value = Array.isArray(list) ? list : []
  } catch {
    pdfRefs.value = []
  }
}

watch(() => props.noteId, (newId) => {
  if (newId) {
    loadNote()
  } else {
    title.value = ''
    content.value = ''
    bindInfo.value = null
    pdfRefs.value = []
    showPdfPanel.value = true
    leftWidth.value = 60
    pdfPage.value = 1
    pdfPageInput.value = 1
  }
}, { immediate: true })

const save = async () => {
  if (!props.noteId) return
  saving.value = true
  emit('word-count-change', content.value.length)
  try {
    await saveNote({
      id: props.noteId,
      notebookId: notebookId.value,
      title: title.value,
      content: content.value
    })
    emit('saved')
    toast('保存成功', 'success')
    // 保存后刷新bindInfo，以防后端更新了bindInfo
    try {
      const data = await getNoteDetail(props.noteId)
      if (data && data.bindInfo) {
        bindInfo.value = data.bindInfo
      }
    } catch { /* skip */ }
  } catch (e) {
    // handled
  } finally {
    saving.value = false
  }
}

const markDirty = () => {
  clearTimeout(autoSaveTimer.value)
  autoSaveTimer.value = setTimeout(() => {
    if (props.noteId) {
      save()
    }
  }, 3000)
}

const onContentInput = (e) => {
  content.value = e.target.value
  markDirty()
  detectLinkTrigger(e)
}

const wrapText = (wrapper) => {
  const ta = textareaRef.value
  if (!ta) return
  const start = ta.selectionStart
  const end = ta.selectionEnd
  const selected = content.value.substring(start, end)
  const newText = wrapper + selected + wrapper
  content.value = content.value.substring(0, start) + newText + content.value.substring(end)
  nextTick(() => {
    ta.focus()
    ta.setSelectionRange(start + wrapper.length, end + wrapper.length)
  })
  markDirty()
}

const insertLine = (prefix) => {
  const ta = textareaRef.value
  if (!ta) return
  const start = ta.selectionStart
  const before = content.value.substring(0, start)
  const after = content.value.substring(start)
  const lastNewline = before.lastIndexOf('\n')
  const lineStart = lastNewline === -1 ? 0 : lastNewline + 1
  content.value = content.value.substring(0, lineStart) + prefix + content.value.substring(lineStart)
  nextTick(() => {
    ta.focus()
    ta.setSelectionRange(lineStart + prefix.length, lineStart + prefix.length)
  })
  markDirty()
}

const wrapLink = () => {
  const ta = textareaRef.value
  if (!ta) return
  const start = ta.selectionStart
  const end = ta.selectionEnd
  const selected = content.value.substring(start, end) || '链接文字'
  const newText = `[${selected}](url)`
  content.value = content.value.substring(0, start) + newText + content.value.substring(end)
  nextTick(() => {
    ta.focus()
    ta.setSelectionRange(start + newText.length - 4, start + newText.length - 1)
  })
  markDirty()
}

const onTab = (e) => {
  const ta = textareaRef.value
  if (!ta) return
  const start = ta.selectionStart
  content.value = content.value.substring(0, start) + '\t' + content.value.substring(ta.selectionEnd)
  nextTick(() => {
    ta.focus()
    ta.selectionStart = ta.selectionEnd = start + 1
  })
  markDirty()
}

const detectLinkTrigger = async (e) => {
  const ta = textareaRef.value
  if (!ta) return
  const cursorPos = ta.selectionStart
  const textBefore = content.value.substring(0, cursorPos)

  const lastOpen = textBefore.lastIndexOf('[[')
  const lastClose = textBefore.lastIndexOf(']]')

  if (lastOpen !== -1 && (lastClose === -1 || lastClose < lastOpen)) {
    const query = textBefore.substring(lastOpen + 2)
    if (!query.includes(']]') && !query.includes('\n')) {
      linkSuggestVisible.value = true
      linkTriggerPos.value = lastOpen
      const rect = ta.getBoundingClientRect()
      const lines = content.value.substring(0, cursorPos).split('\n')
      const lineNum = lines.length
      linkSuggestStyle.value = {
        left: '20px',
        top: (lineNum * 20 + 80) + 'px'
      }
      try {
        const data = await getNoteList({ keyword: query, size: 10 })
        linkSuggestions.value = (data && data.records) ? data.records : []
      } catch {
        linkSuggestions.value = []
      }
      return
    }
  }
  linkSuggestVisible.value = false
}

const insertLink = (titleToInsert) => {
  const pos = linkTriggerPos.value
  const ta = textareaRef.value
  const cursorPos = ta ? ta.selectionStart : pos + titleToInsert.length + 2
  content.value =
    content.value.substring(0, pos) +
    `[[${titleToInsert}]]` +
    content.value.substring(cursorPos)
  linkSuggestVisible.value = false
  nextTick(() => {
    if (ta) {
      ta.focus()
      const newPos = pos + titleToInsert.length + 4
      ta.setSelectionRange(newPos, newPos)
    }
  })
  markDirty()
}

const onKeydown = (e) => {
  if (e.key === 'Escape') {
    linkSuggestVisible.value = false
  }
}

const switchToMindmap = () => {
  mode.value = mode.value === 'mindmap' ? 'edit' : 'mindmap'
}

watch(mode, (val) => {
  emit('mindmap-active', val === 'mindmap')
})

const onMindmapUpdate = (newContent) => {
  content.value = newContent
  markDirty()
}

// Splitter 拖拽
const onSplitterMouseDown = (e) => {
  isDragging.value = true
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  window.addEventListener('mousemove', onSplitterMouseMove)
  window.addEventListener('mouseup', onSplitterMouseUp)
}

const onSplitterMouseMove = (e) => {
  if (!isDragging.value) return
  const outer = document.querySelector('.note-editor-outer')
  if (!outer) return
  const rect = outer.getBoundingClientRect()
  const totalWidth = rect.width - 280 - 6 // 减去抽屉和分隔条宽度（如果抽屉开了）
  const drawerAdjust = navDrawerVisible.value ? totalWidth : rect.width - 6
  const x = e.clientX - rect.left
  let percent = (x / drawerAdjust) * 100
  percent = Math.max(30, Math.min(80, percent))
  leftWidth.value = percent
}

const onSplitterMouseUp = () => {
  isDragging.value = false
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  window.removeEventListener('mousemove', onSplitterMouseMove)
  window.removeEventListener('mouseup', onSplitterMouseUp)
}

// PDF 导航
const prevPage = () => {
  if (pdfPage.value > 1) {
    pdfPage.value--
    pdfPageInput.value = pdfPage.value
  }
}

const nextPage = () => {
  pdfPage.value++
  pdfPageInput.value = pdfPage.value
}

const jumpToPage = (p) => {
  const n = Number(p)
  if (n >= 1) {
    pdfPage.value = n
    pdfPageInput.value = n
  }
}

const onJumpPdfPage = ({ pageStart, ref }) => {
  if (showPdfPanel.value && bindInfo.value) {
    jumpToPage(pageStart)
  } else {
    toast(`跳转到 P.${pageStart}${ref && ref.nodeTitle ? ' - ' + ref.nodeTitle : ''}`, 'info')
  }
}

// 知识点导航抽屉
const onNavItemClick = (ref) => {
  if (showPdfPanel.value && bindInfo.value) {
    jumpToPage(ref.pageStart)
  }
  if (mode.value === 'mindmap' && mindMapViewerRef.value && mindMapViewerRef.value.mindMapViewerRef) {
    const mm = mindMapViewerRef.value.mindMapViewerRef.value
    if (mm && mm.centerNodeByUid && ref.nodeUid) {
      mm.centerNodeByUid(ref.nodeUid)
    }
  }
}

const editRefFromNav = async (ref) => {
  if (!ref) return
  try {
    const data = {
      id: ref.id,
      noteId: props.noteId,
      nodeUid: ref.nodeUid,
      nodeTitle: ref.nodeTitle,
      pageStart: ref.pageStart,
      pageEnd: ref.pageEnd,
      excerpt: ref.excerpt
    }
    await savePdfRef(data)
    toast('保存成功', 'success')
    await refreshPdfRefs()
  } catch {
    // 如果直接保存不行，我们可以提示用户去导图模式编辑，或者用一个简单prompt
    const newPage = prompt(`编辑 "${ref.nodeTitle}" 的起始页码`, ref.pageStart || '')
    if (newPage != null) {
      try {
        await savePdfRef({
          id: ref.id,
          noteId: props.noteId,
          nodeUid: ref.nodeUid,
          nodeTitle: ref.nodeTitle,
          pageStart: Number(newPage) || ref.pageStart,
          pageEnd: ref.pageEnd,
          excerpt: ref.excerpt
        })
        toast('保存成功', 'success')
        await refreshPdfRefs()
      } catch (e2) { /* handled */ }
    }
  }
}

const deleteRefFromNav = async (ref) => {
  if (!ref || !confirm(`确定删除关联 "${ref.nodeTitle}" 吗？`)) return
  try {
    await deletePdfRef(ref.id)
    toast('已删除', 'success')
    await refreshPdfRefs()
  } catch (e) { /* handled */ }
}

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onSplitterMouseMove)
  window.removeEventListener('mouseup', onSplitterMouseUp)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
})

defineExpose({ loadNote, save })
</script>

<style scoped>
.note-editor-outer {
  display: flex;
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
}
.note-editor {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
  flex: 1;
  min-width: 0;
  overflow: hidden;
}
.editor-title-bar {
  padding: 12px 16px 8px;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.title-input {
  width: 100%;
  border: none;
  outline: none;
  font-size: 20px;
  font-weight: 600;
  color: #1f2937;
  padding: 4px 0;
}
.title-input::placeholder {
  color: #d1d5db;
}
.editor-toolbar {
  display: flex;
  align-items: center;
  padding: 4px 12px;
  border-bottom: 1px solid #e5e7eb;
  gap: 2px;
  flex-shrink: 0;
  flex-wrap: wrap;
  background: #fafafa;
}
.tool-btn {
  height: 28px;
  padding: 0 8px;
  border: 1px solid transparent;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  color: #374151;
  white-space: nowrap;
  transition: all 0.15s;
}
.tool-btn:hover {
  background: #e5e7eb;
}
.tool-btn.active {
  background: #dbeafe;
  color: #2563eb;
}
.toolbar-sep {
  width: 1px;
  height: 20px;
  background: #e5e7eb;
  margin: 0 4px;
}
.editor-main {
  flex: 1;
  display: flex;
  overflow: hidden;
  position: relative;
  min-height: 0;
}
.editor-left {
  display: flex;
  overflow: hidden;
  min-width: 0;
  flex-shrink: 0;
}
.editor-left > :deep(.editor-content) {
  flex: 1;
  display: flex;
  overflow: hidden;
  position: relative;
  min-width: 0;
}
.splitter {
  width: 6px;
  flex-shrink: 0;
  background: #e5e7eb;
  cursor: col-resize;
  position: relative;
  z-index: 5;
  transition: background 0.15s;
}
.splitter:hover {
  background: #3b82f6;
}
.editor-right {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #f3f4f6;
  flex-shrink: 0;
  min-width: 0;
}
.pdf-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
  flex-shrink: 0;
}
.pdf-nav-btn {
  padding: 4px 8px;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  color: #374151;
}
.pdf-nav-btn:hover:not(:disabled) {
  background: #f3f4f6;
}
.pdf-nav-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.pdf-page-input {
  width: 60px;
  padding: 4px 6px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  font-size: 12px;
  outline: none;
  text-align: center;
}
.pdf-page-input:focus {
  border-color: #3b82f6;
}
.pdf-iframe {
  flex: 1;
  width: 100%;
  border: none;
  background: #fff;
}

/* Editor Content */
.editor-content {
  flex: 1;
  display: flex;
  overflow: hidden;
  position: relative;
}
.editor-content.split .edit-pane,
.editor-content.split .preview-pane {
  width: 50%;
}
.edit-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.content-textarea {
  flex: 1;
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  padding: 16px;
  font-size: 14px;
  line-height: 1.8;
  color: #374151;
  font-family: "Consolas", "Monaco", "Courier New", monospace;
}
.content-textarea::placeholder {
  color: #d1d5db;
}
.preview-pane {
  flex: 0 0 auto;
  overflow-y: auto;
  border-left: 1px solid #e5e7eb;
}
.editor-content.preview .preview-pane {
  flex: 1;
  border-left: none;
}
.markdown-body {
  padding: 16px;
  font-size: 14px;
  line-height: 1.8;
  color: #374151;
}
.markdown-body :deep(h1) { font-size: 24px; margin: 16px 0 8px; }
.markdown-body :deep(h2) { font-size: 20px; margin: 14px 0 8px; }
.markdown-body :deep(h3) { font-size: 16px; margin: 12px 0 6px; }
.markdown-body :deep(p) { margin: 8px 0; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 24px; margin: 8px 0; }
.markdown-body :deep(blockquote) {
  border-left: 3px solid #e5e7eb;
  padding-left: 12px;
  color: #6b7280;
  margin: 8px 0;
}
.markdown-body :deep(code) {
  background: #f3f4f6;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
}
.markdown-body :deep(pre) {
  background: #1f2937;
  color: #e5e7eb;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 8px 0;
}
.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
}
.mindmap-pane {
  flex: 1;
  display: flex;
  width: 100%;
  height: 100%;
}
.editor-content.mindmap {
  flex: 1;
}
.editor-content.mindmap .mindmap-pane {
  flex: 1;
}
.editor-content.mindmap .edit-pane,
.editor-content.mindmap .preview-pane {
  display: none !important;
}
.link-suggest-dropdown {
  position: absolute;
  z-index: 100;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.1);
  max-height: 200px;
  overflow-y: auto;
  min-width: 240px;
}
.suggest-title {
  padding: 8px 12px;
  font-size: 11px;
  color: #9ca3af;
  border-bottom: 1px solid #f3f4f6;
}
.suggest-item {
  padding: 8px 12px;
  font-size: 13px;
  cursor: pointer;
  color: #374151;
}
.suggest-item:hover {
  background: #f3f4f6;
}
.suggest-empty {
  padding: 8px 12px;
  font-size: 12px;
  color: #9ca3af;
}
.editor-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #9ca3af;
  font-size: 16px;
  flex: 1;
}
.placeholder-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

/* 知识点导航抽屉 */
.nav-drawer-mask {
  position: absolute;
  inset: 0;
  background: rgba(0,0,0,0.15);
  z-index: 98;
}
.nav-drawer {
  position: absolute;
  top: 0;
  right: 0;
  height: 100%;
  width: 280px;
  background: #fff;
  border-left: 1px solid #e5e7eb;
  box-shadow: -4px 0 16px rgba(0,0,0,0.08);
  z-index: 99;
  display: flex;
  flex-direction: column;
  transform: translateX(100%);
  transition: transform 0.25s ease;
}
.nav-drawer.visible {
  transform: translateX(0);
}
.note-editor-outer.with-side-nav .note-editor {
  padding-right: 0;
}
.nav-drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.nav-drawer-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
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
.nav-drawer-search {
  padding: 10px 12px;
  border-bottom: 1px solid #f3f4f6;
  flex-shrink: 0;
}
.form-input {
  width: 100%;
  padding: 6px 10px;
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
.nav-drawer-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px 0;
}
.nav-empty {
  padding: 30px 20px;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
}
.nav-item {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 8px 12px;
  border-bottom: 1px dashed #f3f4f6;
  cursor: pointer;
}
.nav-item:hover {
  background: #f9fafb;
}
.nav-item-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.nav-page {
  font-size: 11px;
  color: #d97706;
  background: #fef3c7;
  padding: 1px 5px;
  border-radius: 3px;
  align-self: flex-start;
  font-weight: 500;
}
.nav-title {
  font-size: 13px;
  color: #1f2937;
  word-break: break-all;
  line-height: 1.4;
}
.nav-item-actions {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
  padding-top: 2px;
}
.nav-btn {
  width: 22px;
  height: 22px;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 3px;
  cursor: pointer;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.nav-btn:hover {
  background: #f3f4f6;
}
.nav-btn.danger:hover {
  background: #fef2f2;
  border-color: #fecaca;
}
</style>
