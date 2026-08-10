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
