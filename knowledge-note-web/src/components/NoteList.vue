<template>
  <div class="note-list">
    <div class="list-header">
      <div class="header-info">
        <h3 class="header-title">{{ notebookName || '全部笔记' }}</h3>
        <span class="header-count">共 {{ total }} 篇</span>
      </div>
      <div class="header-actions">
        <SearchInput
          v-model="keyword"
          @search="onSearch"
          style="width: 180px; margin-right: 8px;"
        />
        <button class="new-note-btn" @click="$emit('create-note')">+ 新建笔记</button>
      </div>
    </div>

    <div class="list-body">
      <div v-if="loading" class="list-loading">加载中...</div>
      <div v-else-if="notes.length === 0" class="list-empty">
        <div class="empty-icon">📝</div>
        <p>暂无笔记</p>
        <button class="new-note-btn" @click="$emit('create-note')">+ 新建第一篇笔记</button>
      </div>
      <div
        v-for="note in notes"
        :key="note.id"
        class="note-item"
        :class="{ selected: selectedNoteId === note.id }"
        @click="$emit('select-note', note.id)"
      >
        <div class="note-main">
          <div class="note-title">{{ note.title || '无标题' }}</div>
          <div class="note-meta">
            <span class="note-tags" v-if="note.tags && note.tags.length">
              <span
                v-for="tag in note.tags"
                :key="tag.id"
                class="tag-capsule"
                :style="{ background: tag.color + '20', color: tag.color, borderColor: tag.color + '40' }"
              >{{ tag.name }}</span>
            </span>
            <span class="note-time">{{ formatTime(note.updatedAt) }}</span>
          </div>
        </div>
        <div class="note-actions">
          <span
            class="favorite-star"
            :class="{ favorited: note.isFavorite }"
            @click.stop="onToggleFavorite(note)"
          >{{ note.isFavorite ? '⭐' : '☆' }}</span>
        </div>
      </div>
    </div>

    <div class="list-footer" v-if="total > 0">
      <div class="pagination">
        <button :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
        <span class="page-info">{{ page }} / {{ totalPages }}</span>
        <button :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, inject } from 'vue'
import SearchInput from './SearchInput.vue'
import { getNoteList, toggleFavorite } from '../api/noteApi'

const props = defineProps({
  notebookId: { type: [Number, String], default: null },
  notebookName: { type: String, default: '' },
  selectedNoteId: { type: [Number, String], default: null },
  isFavorites: { type: Boolean, default: false }
})

const emit = defineEmits(['select-note', 'create-note', 'refresh'])
const toast = inject('showToast', () => {})

const notes = ref([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

const loadNotes = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined
    }
    if (props.isFavorites) {
      params.isFavorite = 1
    } else if (props.notebookId !== null && props.notebookId !== 'all') {
      params.notebookId = props.notebookId
    }
    const data = await getNoteList(params)
    if (data) {
      notes.value = data.records || []
      total.value = data.total || 0
    }
  } catch (e) {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

const onSearch = () => {
  page.value = 1
  loadNotes()
}

const goPage = (p) => {
  page.value = p
  loadNotes()
}

const onToggleFavorite = async (note) => {
  try {
    await toggleFavorite(note.id)
    note.isFavorite = note.isFavorite ? 0 : 1
    emit('refresh')
  } catch (e) {
    // handled
  }
}

const formatTime = (time) => {
  if (!time) return ''
  const d = new Date(time)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

watch(
  () => [props.notebookId, props.isFavorites],
  () => {
    page.value = 1
    keyword.value = ''
    loadNotes()
  },
  { immediate: true }
)

defineExpose({ loadNotes })
</script>

<style scoped>
.note-list {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
}
.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
  gap: 12px;
}
.header-info {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}
.header-count {
  font-size: 12px;
  color: #9ca3af;
}
.header-actions {
  display: flex;
  align-items: center;
}
.new-note-btn {
  padding: 6px 14px;
  background: #3b82f6;
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.2s;
}
.new-note-btn:hover {
  background: #2563eb;
}
.list-body {
  flex: 1;
  overflow-y: auto;
}
.list-loading,
.list-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: #9ca3af;
  font-size: 14px;
}
.empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
}
.note-item {
  display: flex;
  align-items: center;
  padding: 10px 16px;
  border-bottom: 1px solid #f3f4f6;
  cursor: pointer;
  transition: background 0.15s;
}
.note-item:hover {
  background: #f9fafb;
}
.note-item.selected {
  background: #eff6ff;
}
.note-main {
  flex: 1;
  min-width: 0;
}
.note-title {
  font-size: 14px;
  font-weight: 500;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}
.note-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #9ca3af;
}
.note-tags {
  display: flex;
  gap: 4px;
}
.tag-capsule {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 10px;
  font-size: 11px;
  border: 1px solid;
}
.note-time {
  white-space: nowrap;
}
.note-actions {
  flex-shrink: 0;
}
.favorite-star {
  cursor: pointer;
  font-size: 16px;
  color: #d1d5db;
}
.favorite-star.favorited {
  color: #f59e0b;
}
.list-footer {
  padding: 8px 16px;
  border-top: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}
.pagination button {
  padding: 4px 12px;
  border: 1px solid #d1d5db;
  background: #fff;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  color: #374151;
}
.pagination button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.pagination button:hover:not(:disabled) {
  background: #f3f4f6;
}
.page-info {
  font-size: 12px;
  color: #6b7280;
}
</style>
