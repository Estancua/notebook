<template>
  <Teleport to="body">
    <div class="dialog-overlay" @click.self="$emit('close')" v-if="visible">
      <div class="dialog">
        <div class="dialog-header">
          <h3>标签管理</h3>
          <button class="dialog-close" @click="$emit('close')">✕</button>
        </div>
        <div class="dialog-body">
          <div class="new-tag-area">
            <div class="new-tag-row">
              <input
                class="form-input"
                v-model="newName"
                placeholder="新建标签名称"
                @keydown.enter="addTag"
              />
              <input
                class="color-input"
                type="color"
                v-model="newColor"
              />
              <button class="add-tag-btn" @click="addTag">+</button>
            </div>
            <span v-if="addError" class="form-error">{{ addError }}</span>
          </div>
          <div class="tag-list">
            <div v-if="tags.length === 0" class="list-empty">暂无标签</div>
            <div v-for="tag in tags" :key="tag.id" class="tag-row">
              <span class="tag-color-dot" :style="{ background: tag.color }"></span>
              <span class="tag-name">{{ tag.name }}</span>
              <span class="tag-count" v-if="tag.noteCount !== undefined">({{ tag.noteCount }})</span>
              <button class="tag-delete" @click="removeTag(tag)" title="删除标签">✕</button>
            </div>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-submit" @click="$emit('close')">完成</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, watch, inject } from 'vue'
import { getTagList, saveTag, deleteTag } from '../api/tagApi'

const props = defineProps({
  visible: { type: Boolean, default: false }
})
const emit = defineEmits(['close', 'updated'])
const toast = inject('showToast', () => {})

const tags = ref([])
const newName = ref('')
const newColor = ref('#3b82f6')
const addError = ref('')

const loadTags = async () => {
  try {
    tags.value = await getTagList() || []
  } catch (e) { /* handled */ }
}

watch(() => props.visible, (v) => {
  if (v) {
    newName.value = ''
    newColor.value = '#3b82f6'
    addError.value = ''
    loadTags()
  }
})

const addTag = async () => {
  if (!newName.value.trim()) {
    addError.value = '标签名不能为空'
    return
  }
  addError.value = ''
  try {
    await saveTag({ name: newName.value.trim(), color: newColor.value })
    toast('标签创建成功', 'success')
    newName.value = ''
    loadTags()
    emit('updated')
  } catch (e) { /* handled */ }
}

const removeTag = async (tag) => {
  if (!confirm(`确定删除标签"${tag.name}"吗？关联笔记的标签也会被移除。`)) return
  try {
    await deleteTag(tag.id)
    toast('标签已删除', 'success')
    loadTags()
    emit('updated')
  } catch (e) { /* handled */ }
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
  width: 400px;
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
.dialog-close:hover { color: #374151; }
.dialog-body {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
}
.new-tag-area {
  margin-bottom: 16px;
}
.new-tag-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.form-input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 14px;
  outline: none;
  color: #374151;
}
.form-input:focus { border-color: #3b82f6; }
.color-input {
  width: 36px;
  height: 36px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  cursor: pointer;
  padding: 2px;
}
.add-tag-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: #3b82f6;
  color: #fff;
  border-radius: 6px;
  cursor: pointer;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.add-tag-btn:hover { background: #2563eb; }
.form-error {
  font-size: 12px;
  color: #ef4444;
  margin-top: 4px;
  display: block;
}
.tag-list {
  max-height: 300px;
  overflow-y: auto;
}
.list-empty {
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
  padding: 20px;
}
.tag-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  transition: background 0.15s;
}
.tag-row:hover {
  background: #f9fafb;
}
.tag-color-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex-shrink: 0;
}
.tag-name {
  flex: 1;
  font-size: 13px;
  color: #374151;
}
.tag-count {
  font-size: 11px;
  color: #9ca3af;
}
.tag-delete {
  border: none;
  background: none;
  cursor: pointer;
  color: #9ca3af;
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 4px;
}
.tag-delete:hover {
  color: #ef4444;
  background: #fef2f2;
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid #e5e7eb;
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
.btn-submit:hover { background: #2563eb; }
</style>
