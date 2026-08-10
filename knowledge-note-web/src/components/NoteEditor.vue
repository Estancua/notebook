<template>
  <div class="note-editor" :class="{ mindmap: mode === 'mindmap' }" v-if="noteId">
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
    </div>

    <!-- 编辑区 -->
    <div class="editor-content" :class="mode">
      <div class="edit-pane" v-show="mode !== 'preview'">
        <textarea
          ref="textareaRef"
          class="content-textarea"
          :value="content"
          @input="onContentInput"
          @keydown.tab.prevent="onTab"
          @keydown.ctrl.s.prevent="save"
          @keydown="onKeydown"
          placeholder="开始输入 Markdown 内容..."
        ></textarea>
      </div>
      <div class="preview-pane" v-show="mode === 'preview' || mode === 'split'">
        <div class="markdown-body" v-html="renderedContent"></div>
      </div>

      <!-- 链接提示下拉 -->
      <div v-if="linkSuggestVisible" class="link-suggest-dropdown" :style="linkSuggestStyle">
        <div class="suggest-title">选择要引用的笔记</div>
        <div v-if="linkSuggestions.length === 0" class="suggest-empty">无匹配笔记</div>
        <div
          v-for="item in linkSuggestions"
          :key="item.id"
          class="suggest-item"
          @click="insertLink(item.title)"
        >{{ item.title }}</div>
      </div>

      <!-- 思维导图视图 -->
      <div class="mindmap-pane" v-show="mode === 'mindmap'">
        <MindMapViewer
          :content="content"
          @update:content="onMindmapUpdate"
          @back="mode = 'edit'"
        />
      </div>
    </div>
  </div>
  <div class="editor-placeholder" v-else>
    <div class="placeholder-icon">📝</div>
    <p>请选择一个笔记开始编辑</p>
  </div>
</template>

<script setup>
import { ref, computed, watch, inject, nextTick } from 'vue'
import { marked } from 'marked'
import { getNoteDetail, saveNote } from '../api/noteApi'
import { getNoteList } from '../api/noteApi'
import MindMapViewer from './MindMapViewer.vue'

const props = defineProps({
  noteId: { type: [Number, String], default: null }
})

const emit = defineEmits(['saved', 'word-count-change', 'mindmap-active'])
const toast = inject('showToast', () => {})

const title = ref('')
const content = ref('')
const notebookId = ref(null)
const mode = ref('edit') // edit / preview / split
const textareaRef = ref(null)
const autoSaveTimer = ref(null)
const saving = ref(false)

// 链接提示
const linkSuggestVisible = ref(false)
const linkSuggestions = ref([])
const linkSuggestStyle = ref({})
const linkTriggerPos = ref(-1)

const renderedContent = computed(() => {
  try {
    return marked(content.value || '', { breaks: true, gfm: true })
  } catch {
    return content.value
  }
})

// 加载笔记
const loadNote = async () => {
  if (!props.noteId) return
  try {
    const data = await getNoteDetail(props.noteId)
    if (data) {
      title.value = data.title || ''
      content.value = data.content || ''
      notebookId.value = data.notebookId
      emit('word-count-change', data.wordCount || 0)
    }
  } catch (e) {
    // handled
  }
}

watch(() => props.noteId, (newId) => {
  if (newId) {
    loadNote()
  } else {
    title.value = ''
    content.value = ''
  }
}, { immediate: true })

// 保存
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
  } catch (e) {
    // handled
  } finally {
    saving.value = false
  }
}

// 自动保存
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

// 工具栏操作
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
  // find line start
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

// Tab 键
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

// [[ 链接提示
const detectLinkTrigger = async (e) => {
  const ta = textareaRef.value
  if (!ta) return
  const cursorPos = ta.selectionStart
  const textBefore = content.value.substring(0, cursorPos)

  // 检查是否有未闭合的 [[
  const lastOpen = textBefore.lastIndexOf('[[')
  const lastClose = textBefore.lastIndexOf(']]')

  if (lastOpen !== -1 && (lastClose === -1 || lastClose < lastOpen)) {
    const query = textBefore.substring(lastOpen + 2)
    if (!query.includes(']]') && !query.includes('\n')) {
      linkSuggestVisible.value = true
      linkTriggerPos.value = lastOpen
      // 计算下拉位置
      const rect = ta.getBoundingClientRect()
      // 粗略估算光标位置
      const lines = content.value.substring(0, cursorPos).split('\n')
      const lineNum = lines.length
      linkSuggestStyle.value = {
        left: '20px',
        top: (lineNum * 20 + 80) + 'px'
      }
      // 搜索笔记
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

const insertLink = (title) => {
  const pos = linkTriggerPos.value
  const ta = textareaRef.value
  const cursorPos = ta ? ta.selectionStart : pos + title.length + 2
  content.value =
    content.value.substring(0, pos) +
    `[[${title}]]` +
    content.value.substring(cursorPos)
  linkSuggestVisible.value = false
  nextTick(() => {
    if (ta) {
      ta.focus()
      const newPos = pos + title.length + 4
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

// 切换到思维导图模式
const switchToMindmap = () => {
  mode.value = mode.value === 'mindmap' ? 'edit' : 'mindmap'
}
// 导图模式切换时通知父组件
watch(mode, (val) => {
  emit('mindmap-active', val === 'mindmap')
})
const onMindmapUpdate = (newContent) => {
  content.value = newContent
  markDirty()
}

defineExpose({ loadNote, save })
</script>

<style scoped>
.note-editor {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
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
}
.editor-content.mindmap {
  flex: 1;
}
.editor-content.mindmap .mindmap-pane {
  flex: 1;
}
/* 导图模式下 hidden edit/preview panes */
.editor-content.mindmap .edit-pane,
.editor-content.mindmap .preview-pane {
  display: none !important;
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
}
.placeholder-icon {
  font-size: 64px;
  margin-bottom: 16px;
}
/* 链接提示下拉 */
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
</style>
