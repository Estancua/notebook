<template>
  <Teleport to="body">
    <div class="dialog-overlay" @click.self="$emit('close')" v-if="visible">
      <div class="dialog">
        <div class="dialog-header">
          <h3>新建笔记本</h3>
          <button class="dialog-close" @click="$emit('close')">✕</button>
        </div>
        <div class="dialog-body">
          <div class="form-group">
            <label class="form-label">名称</label>
            <input
              class="form-input"
              v-model="form.name"
              placeholder="请输入笔记本名称"
              @keydown.enter="submit"
              ref="nameInput"
            />
            <span v-if="formError" class="form-error">{{ formError }}</span>
          </div>
          <div class="form-group">
            <label class="form-label">父目录</label>
            <select class="form-input" v-model="form.parentId">
              <option :value="0">(根目录)</option>
              <option v-for="nb in flatNotebooks" :key="nb.id" :value="nb.id">
                {{ '  '.repeat(nb._depth || 0) }}{{ nb.name }}
              </option>
            </select>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="$emit('close')">取消</button>
          <button class="btn-submit" @click="submit" :disabled="submitting">
            {{ submitting ? '创建中...' : '创建' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, reactive, watch, nextTick, inject } from 'vue'
import { saveNotebook, getNotebookTree } from '../api/notebookApi'

const props = defineProps({
  visible: { type: Boolean, default: false },
  defaultParentId: { type: [Number, String], default: 0 }
})
const emit = defineEmits(['close', 'created'])
const toast = inject('showToast', () => {})

const nameInput = ref(null)
const form = reactive({ name: '', parentId: 0 })
const formError = ref('')
const submitting = ref(false)
const flatNotebooks = ref([])

const flattenTree = (nodes, depth = 0) => {
  const result = []
  for (const node of nodes) {
    result.push({ ...node, _depth: depth })
    if (node.children && node.children.length) {
      result.push(...flattenTree(node.children, depth + 1))
    }
  }
  return result
}

watch(() => props.visible, async (v) => {
  if (v) {
    form.name = ''
    form.parentId = props.defaultParentId || 0
    formError.value = ''
    try {
      const tree = await getNotebookTree()
      flatNotebooks.value = flattenTree(tree || [])
    } catch (e) { /* handled */ }
    nextTick(() => nameInput.value?.focus())
  }
})

const submit = async () => {
  if (!form.name.trim()) {
    formError.value = '名称不能为空'
    return
  }
  formError.value = ''
  submitting.value = true
  try {
    await saveNotebook({ name: form.name.trim(), parentId: form.parentId })
    toast('笔记本创建成功', 'success')
    emit('created')
    emit('close')
  } catch (e) { /* handled */ } finally {
    submitting.value = false
  }
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
.form-group {
  margin-bottom: 16px;
}
.form-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 6px;
}
.form-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 14px;
  outline: none;
  color: #374151;
  transition: border-color 0.2s;
}
.form-input:focus { border-color: #3b82f6; }
select.form-input { cursor: pointer; }
.form-error {
  font-size: 12px;
  color: #ef4444;
  margin-top: 4px;
  display: block;
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid #e5e7eb;
}
.btn-cancel {
  padding: 8px 20px;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: #374151;
}
.btn-cancel:hover { background: #f3f4f6; }
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
.btn-submit:disabled { opacity: 0.6; cursor: not-allowed; }
</style>
