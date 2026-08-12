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

// 获取章节列表
export function getChapterList(documentId) {
  return request.get(`/document/chapter/list/${documentId}`)
}

// 绑定章节笔记
export function bindChapterNote(chapterId, noteId) {
  return request.put(`/document/chapter/${chapterId}/bind-note`, { noteId })
}

// 解绑章节笔记
export function unbindChapterNote(chapterId) {
  return request.put(`/document/chapter/${chapterId}/unbind-note`)
}

// 根据笔记ID获取章节
export function getChapterByNoteId(noteId) {
  return request.get(`/document/chapter/by-note/${noteId}`)
}

// 更新单个章节（pageStart/pageEnd/title/content，传了哪个更哪个）
export function updateChapter(chapterId, data) {
  return request.put(`/document/chapter/${chapterId}`, data)
}

// 手动创建章节
export function createChapter(data) {
  return request.post('/document/chapter', data)
}

// 删除章节（级联删除子章节）
export function deleteChapter(chapterId) {
  return request.delete(`/document/chapter/${chapterId}`)
}

// OCR 识别 PDF 指定页（返回纯文本）
export function ocrPage(documentId, page) {
  return request.post(`/document/${documentId}/ocr-page`, null, { params: { page } })
}

// OCR 识别 PDF 指定页（返回带坐标的文本行 + 图片）
export function ocrPageWithPositions(documentId, page) {
  return request.post(`/document/${documentId}/ocr-page-with-positions`, null, { params: { page } })
}

// 获取 PDF 单页 OCR 结果（带缓存，首次自动识别并缓存）
export function getPageOcrResult(documentId, page) {
  return request.get(`/document/${documentId}/ocr-page-result`, { params: { page } })
}
