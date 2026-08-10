import request from './request'

// 上传文档
export function uploadDocument(formData) {
  return request.post('/document/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 获取笔记本下的文档列表
export function getDocumentsByNotebook(notebookId) {
  return request.get(`/document/${notebookId}`)
}

// 更新章节解析结果
export function updateParseResult(id, parseResult) {
  return request.put(`/document/${id}`, { parseResult })
}

// 删除文档
export function deleteDocument(id) {
  return request.delete(`/document/${id}`)
}

// 一键生成思维导图
export function generateMindmap(id, data) {
  return request.post(`/document/${id}/generate-mindmap`, data)
}

// 获取文档全文
export function getDocumentText(id) {
  return request.get(`/document/${id}/text`)
}

// 文档预览URL
export function getDocumentPreviewUrl(id) {
  return `/api/document/${id}/preview`
}
