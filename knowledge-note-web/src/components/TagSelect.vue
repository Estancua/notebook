<template>
  <div class="tag-select">
    <div class="tag-header">
      <span class="tag-label">标签</span>
      <button class="edit-btn" @click="editing = !editing">
        {{ editing ? '完成' : '编辑' }}
      </button>
    </div>

    <!-- 展示模式 -->
    <div v-if="!editing" class="tag-display">
      <span v-if="tags.length === 0" class="no-tags">无标签</span>
      <span
        v-for="tag in tags"
        :key="tag.id"
        class="tag-capsule"
        :style="tagStyle(tag)"
      >🏷 {{ tag.name }}</span>
    </div>

    <!-- 编辑模式 -->
    <div v-else class="tag-edit">
      <div class="selected-tags">
        <span
          v-for="tag in selectedTags"
          :key="tag.id"
          class="tag-capsule selected"
          :style="tagStyle(tag)"
        >
          🏷 {{ tag.name }}
          <span class="tag-remove" @click="removeTag(tag)">✕</span>
        </span>
      </div>
      <div class="tag-input-wrap">
        <input
          class="tag-input"
          v-model="newTagName"
          placeholder="输入标签名或选择已有标签"
          @keydown.enter.prevent="addNewTag"
        />
        <div v-if="newTagName || tagDropdownVisible" class="tag-dropdown">
          <div
            v-for="tag in filteredAvailableTags"
            :key="tag.id"
            class="tag-drop-item"
            @click="selectTag(tag)"
          >
            <span class="tag-color-dot" :style="{ background: tag.color }"></span>
            {{ tag.name }}
          </div>
          <div
            v-if="newTagName && !filteredAvailableTags.length"
            class="tag-drop-item new"
            @click="addNewTag"
          >
            + 新建标签 "{{ newTagName }}"
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, inject } from 'vue'
import { getTagList, saveTag } from '../api/tagApi'

const props = defineProps({
  tags: { type: Array, default: () => [] }            // 当前笔记的标签
})

const emit = defineEmits(['update:tags'])
const toast = inject('showToast', () => {})

const editing = ref(false)
const allTags = ref([])
const selectedTags = ref([])
const newTagName = ref('')
const tagDropdownVisible = ref(false)

const filteredAvailableTags = computed(() => {
  const selectedIds = selectedTags.value.map(t => t.id)
  const available = allTags.value.filter(t => !selectedIds.includes(t.id))
  if (newTagName.value) {
    return available.filter(t => t.name.includes(newTagName.value))
  }
  return available
})

const tagStyle = (tag) => ({
  background: (tag.color || '#3b82f6') + '20',
  color: tag.color || '#3b82f6',
  borderColor: (tag.color || '#3b82f6') + '40'
})

watch(editing, async (val) => {
  if (val) {
    selectedTags.value = [...props.tags]
    try {
      allTags.value = await getTagList() || []
    } catch (e) { /* handled */ }
    tagDropdownVisible.value = true
  } else {
    // 保存标签变更
    const tagIds = selectedTags.value.map(t => t.id)
    emit('update:tags', tagIds)
  }
})

watch(newTagName, () => {
  tagDropdownVisible.value = true
})

const selectTag = (tag) => {
  if (!selectedTags.value.find(t => t.id === tag.id)) {
    selectedTags.value.push(tag)
  }
  newTagName.value = ''
}

const removeTag = (tag) => {
  selectedTags.value = selectedTags.value.filter(t => t.id !== tag.id)
}

const addNewTag = async () => {
  const name = newTagName.value.trim()
  if (!name) return
  const existing = allTags.value.find(t => t.name === name)
  if (existing) {
    selectTag(existing)
    return
  }
  try {
    const randomColor = '#' + Math.floor(Math.random() * 16777215).toString(16).padStart(6, '0')
    const saved = await saveTag({ name, color: randomColor })
    const newTag = { id: saved.id || saved, name, color: randomColor }
    allTags.value.push(newTag)
    selectedTags.value.push(newTag)
    newTagName.value = ''
    toast('标签创建成功', 'success')
  } catch (e) { /* handled */ }
}
</script>

<style scoped>
.tag-select {
  padding: 12px 0;
}
.tag-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.tag-label {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
}
.edit-btn {
  font-size: 11px;
  padding: 2px 8px;
  border: 1px solid #d1d5db;
  background: #fff;
  border-radius: 4px;
  cursor: pointer;
  color: #6b7280;
}
.edit-btn:hover {
  background: #f3f4f6;
}
.tag-display {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.no-tags {
  font-size: 12px;
  color: #9ca3af;
}
.tag-capsule {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
  border: 1px solid;
}
.tag-capsule.selected {
  margin: 2px;
}
.tag-remove {
  cursor: pointer;
  font-size: 10px;
  margin-left: 2px;
  opacity: 0.6;
}
.tag-remove:hover {
  opacity: 1;
}
.tag-edit {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.selected-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.tag-input-wrap {
  position: relative;
}
.tag-input {
  width: 100%;
  padding: 6px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 12px;
  outline: none;
  color: #374151;
}
.tag-input:focus {
  border-color: #3b82f6;
}
.tag-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  z-index: 50;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
  max-height: 140px;
  overflow-y: auto;
}
.tag-drop-item {
  padding: 6px 10px;
  font-size: 12px;
  color: #374151;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
}
.tag-drop-item:hover {
  background: #f3f4f6;
}
.tag-drop-item.new {
  color: #3b82f6;
}
.tag-color-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
</style>
