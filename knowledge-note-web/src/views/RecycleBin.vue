<template>
  <div class="recycle-bin">
    <div class="recycle-header">
      <div class="header-info">
        <h2 class="header-title">🗑 回收站</h2>
        <span class="header-count">共 {{ total }} 条已删除笔记</span>
      </div>
      <div class="header-actions">
        <button
          class="batch-btn"
          :disabled="selectedIds.length === 0"
          @click="onBatchRecover"
        >批量恢复</button>
        <button
          class="batch-btn danger"
          :disabled="selectedIds.length === 0"
          @click="onBatchPermanentDelete"
        >批量永久删除</button>
      </div>
    </div>

    <div class="recycle-body">
      <div v-if="loading" class="body-loading">加载中...</div>
      <div v-else-if="notes.length === 0" class="body-empty">
        <div class="empty-icon">🗑</div>
        <p>回收站为空</p>
      </div>
      <table v-else class="recycle-table">
        <thead>
          <tr>
            <th class="col-check">
              <input type="checkbox" :checked="allChecked" @change="toggleAll" />
            </th>
            <th class="col-title">标题</th>
            <th class="col-notebook">原笔记本</th>
            <th class="col-time">删除时间</th>
            <th class="col-actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="note in notes" :key="note.id">
            <td class="col-check">
              <input
                type="checkbox"
                :checked="selectedIds.includes(note.id)"
                @change="toggleSelect(note.id)"
              />
            </td>
            <td class="col-title">
              <span class="note-title">{{ note.title || '无标题' }}</span>
            </td>
            <td class="col-notebook">{{ note.notebookName || '-' }}</td>
            <td class="col-time">{{ formatTime(note.deletedAt) }}</td>
            <td class="col-actions">
              <button class="action-btn recover" @click="onRecover(note.id)">恢复</button>
              <button class="action-btn delete" @click="onPermanentDelete(note.id)">永久删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="recycle-footer" v-if="total > 0">
      <div class="pagination">
        <button :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
        <span class="page-info">{{ page }} / {{ Math.max(1, Math.ceil(total / size)) }}</span>
        <button :disabled="page >= Math.ceil(total / size)" @click="goPage(page + 1)">下一页</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, inject } from 'vue'
import { getNoteList, recoverNote, permanentDelete, batchRecycle } from '../api/noteApi'

const toast = inject('showToast', () => {})
const emit = defineEmits(['close'])

const notes = ref([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(20)
const selectedIds = ref([])

const allChecked = computed(() => {
  return notes.value.length > 0 && selectedIds.value.length === notes.value.length
})

const loadData = async () => {
  loading.value = true
  try {
    const data = await getNoteList({ page: page.value, size: size.value, isDeleted: 1 })
    if (data) {
      notes.value = data.records || []
      total.value = data.total || 0
    }
  } catch (e) { /* handled */ } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})

const goPage = (p) => {
  page.value = p
  selectedIds.value = []
  loadData()
}

const toggleAll = () => {
  if (allChecked.value) {
    selectedIds.value = []
  } else {
    selectedIds.value = notes.value.map(n => n.id)
  }
}

const toggleSelect = (id) => {
  const idx = selectedIds.value.indexOf(id)
  if (idx === -1) {
    selectedIds.value.push(id)
  } else {
    selectedIds.value.splice(idx, 1)
  }
}

const onRecover = async (id) => {
  if (!confirm('确定恢复该笔记吗？')) return
  try {
    await recoverNote(id)
    toast('已恢复', 'success')
    loadData()
  } catch (e) { /* handled */ }
}

const onPermanentDelete = async (id) => {
  if (!confirm('确定永久删除该笔记吗？此操作不可恢复！')) return
  try {
    await permanentDelete(id)
    toast('已永久删除', 'success')
    loadData()
  } catch (e) { /* handled */ }
}

const onBatchRecover = async () => {
  if (!confirm(`确定恢复选中的 ${selectedIds.value.length} 条笔记吗？`)) return
  try {
    for (const id of selectedIds.value) {
      await recoverNote(id)
    }
    toast('批量恢复成功', 'success')
    selectedIds.value = []
    loadData()
  } catch (e) { /* handled */ }
}

const onBatchPermanentDelete = async () => {
  if (!confirm(`确定永久删除选中的 ${selectedIds.value.length} 条笔记吗？此操作不可恢复！`)) return
  try {
    for (const id of selectedIds.value) {
      await permanentDelete(id)
    }
    toast('已批量永久删除', 'success')
    selectedIds.value = []
    loadData()
  } catch (e) { /* handled */ }
}

const formatTime = (time) => {
  if (!time) return '-'
  const d = new Date(time)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
.recycle-bin {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
}
.recycle-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.header-info {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.header-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
}
.header-count {
  font-size: 12px;
  color: #9ca3af;
}
.header-actions {
  display: flex;
  gap: 8px;
}
.batch-btn {
  padding: 6px 14px;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: #374151;
  transition: all 0.15s;
}
.batch-btn:hover:not(:disabled) {
  background: #f3f4f6;
}
.batch-btn.danger {
  color: #ef4444;
}
.batch-btn.danger:hover:not(:disabled) {
  background: #fef2f2;
}
.batch-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.recycle-body {
  flex: 1;
  overflow-y: auto;
}
.body-loading,
.body-empty {
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
.recycle-table {
  width: 100%;
  border-collapse: collapse;
}
.recycle-table thead {
  position: sticky;
  top: 0;
  background: #f9fafb;
  z-index: 1;
}
.recycle-table th {
  padding: 10px 16px;
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  text-align: left;
  border-bottom: 1px solid #e5e7eb;
}
.recycle-table td {
  padding: 10px 16px;
  font-size: 13px;
  color: #374151;
  border-bottom: 1px solid #f3f4f6;
}
.recycle-table tr:hover td {
  background: #f9fafb;
}
.col-check {
  width: 40px;
}
.col-title {
  width: auto;
}
.col-notebook {
  width: 140px;
}
.col-time {
  width: 160px;
}
.col-actions {
  width: 180px;
}
.note-title {
  font-weight: 500;
}
.action-btn {
  padding: 3px 10px;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  margin-right: 4px;
  transition: all 0.15s;
}
.action-btn.recover {
  color: #3b82f6;
}
.action-btn.recover:hover {
  background: #eff6ff;
  border-color: #3b82f6;
}
.action-btn.delete {
  color: #ef4444;
}
.action-btn.delete:hover {
  background: #fef2f2;
  border-color: #ef4444;
}
.recycle-footer {
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
