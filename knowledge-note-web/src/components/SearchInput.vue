<template>
  <div class="search-input-wrapper">
    <span class="search-icon">🔍</span>
    <input
      ref="inputRef"
      class="search-input"
      type="text"
      :value="modelValue"
      placeholder="搜索笔记..."
      @input="onInput"
      @keydown.enter="onEnter"
    />
    <span v-if="modelValue" class="clear-btn" @click="clear">✕</span>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'search'])

const inputRef = ref(null)

const onInput = (e) => {
  emit('update:modelValue', e.target.value)
}

const onEnter = () => {
  emit('search', props.modelValue)
}

const clear = () => {
  emit('update:modelValue', '')
  inputRef.value?.focus()
}
</script>

<style scoped>
.search-input-wrapper {
  display: flex;
  align-items: center;
  background: #f3f4f6;
  border: 1px solid #e5e7eb;
  border-radius: 20px;
  padding: 0 12px;
  width: 280px;
  height: 32px;
  transition: border-color 0.2s;
}
.search-input-wrapper:focus-within {
  border-color: #3b82f6;
  background: #fff;
}
.search-icon {
  font-size: 14px;
  margin-right: 6px;
  opacity: 0.5;
}
.search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 13px;
  color: #374151;
}
.search-input::placeholder {
  color: #9ca3af;
}
.clear-btn {
  cursor: pointer;
  font-size: 12px;
  color: #9ca3af;
  padding: 2px;
  border-radius: 50%;
}
.clear-btn:hover {
  color: #374151;
  background: #e5e7eb;
}
</style>
