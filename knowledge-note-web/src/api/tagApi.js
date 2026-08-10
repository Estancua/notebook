import request from './request'

export function saveTag(data) {
  return request.post('/tag/save', data)
}

export function getTagList() {
  return request.get('/tag/list')
}

export function deleteTag(id) {
  return request.delete(`/tag/${id}`)
}
