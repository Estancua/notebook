import request from './request'

export function saveNotebook(data) {
  return request.post('/notebook/save', data)
}

export function getNotebookTree() {
  return request.get('/notebook/tree')
}

export function deleteNotebook(id) {
  return request.delete(`/notebook/${id}`)
}
