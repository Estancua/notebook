import request from './request'

export function saveNote(data) {
  return request.post('/note/save', data)
}

export function getNoteList(params) {
  return request.get('/note/list', { params })
}

export function getNoteDetail(id) {
  return request.get(`/note/${id}`)
}

export function recycleNote(id) {
  return request.delete(`/note/${id}`)
}

export function batchRecycle(ids) {
  return request.post('/note/batch-recycle', ids)
}

export function recoverNote(id) {
  return request.post(`/note/${id}/recover`)
}

export function permanentDelete(id) {
  return request.delete(`/note/${id}/permanent`)
}

export function toggleFavorite(id) {
  return request.post(`/note/${id}/favorite`)
}

// 获取PDF引用列表
export function getPdfRefList(noteId) {
  return request.get(`/note-pdf-ref/list/${noteId}`)
}

// 保存PDF引用
export function savePdfRef(data) {
  return request.post('/note-pdf-ref/save', data)
}

// 删除PDF引用
export function deletePdfRef(id) {
  return request.delete(`/note-pdf-ref/${id}`)
}

// 获取笔记本下的笔记列表
export function listByNotebook(notebookId) {
  return request.get('/note/list', { params: { notebookId, size: 9999 } })
}
