<template>
  <header class="topbar">
    <div class="topbar-left">
      <span class="logo">知识笔记</span>
    </div>
    <div class="topbar-center">
      <SearchInput @search="handleSearch" />
    </div>
    <div class="topbar-right">
    </div>
  </header>
</template>

<script setup>
import SearchInput from './SearchInput.vue'

const emit = defineEmits(['search'])

const handleSearch = (keyword) => {
  emit('search', keyword)
}
</script>

<style scoped>
.topbar {
  height: 48px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  padding: 0 16px;
  flex-shrink: 0;
  z-index: 100;
}
.topbar-left {
  display: flex;
  align-items: center;
}
.logo {
  font-size: 18px;
  font-weight: 700;
  color: #1f2937;
  letter-spacing: 1px;
}
.topbar-center {
  flex: 1;
  display: flex;
  justify-content: center;
}
.topbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
