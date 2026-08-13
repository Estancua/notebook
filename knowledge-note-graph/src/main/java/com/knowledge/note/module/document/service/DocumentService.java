package com.knowledge.note.module.document.service;

import com.knowledge.note.module.document.dto.GenerateMindmapDTO;
import com.knowledge.note.module.document.entity.DocumentChapter;
import com.knowledge.note.module.document.vo.DocumentVO;
import com.knowledge.note.module.document.vo.GenerateMindmapVO;
import com.knowledge.note.module.document.vo.OcrPageVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {

    DocumentVO upload(MultipartFile file, Long notebookId);

    List<DocumentVO> listByNotebook(Long notebookId);

    void updateParseResult(Long id, String parseResult);

    void delete(Long id);

    String getFullText(Long id);

    String getFilePath(Long id);

    GenerateMindmapVO generateMindmap(Long id, GenerateMindmapDTO dto);

    List<DocumentChapter> listChaptersByDocument(Long documentId);

    void bindNote(Long chapterId, Long noteId);

    void unbindNote(Long chapterId);

    DocumentChapter getChapterByNoteId(Long noteId);

    /**
     * 按字段更新单个章节信息（pageStart / pageEnd / title / content）
     * 传入的非 null 字段才会被更新，避免误覆盖 noteId / sortOrder 等其他字段
     */
    DocumentChapter updateChapter(Long chapterId, DocumentChapter update);

    /**
     * OCR 识别 PDF 单页文本
     */
    String ocrPage(Long documentId, int page);

    /**
     * OCR 识别 PDF 单页文本（带坐标），用于可视化选区
     */
    OcrPageVO ocrPageWithPositions(Long documentId, int page);

    /**
     * 手动创建文档章节
     */
    DocumentChapter createChapter(DocumentChapter chapter);

    /**
     * 删除章节（级联删除子章节）
     */
    void deleteChapter(Long chapterId);

    /**
     * 获取 PDF 单页 OCR 结果（带缓存，避免重复调用火山引擎API）
     * @return OcrPageVO 包含页面图片 + 文字行坐标
     */
    OcrPageVO getPageOcrResult(Long documentId, int page, boolean force);

    /**
     * 渲染 PDF 单页为 JPEG 图片（前端 OCR 模式直接展示，替代 PDF.js 全量下载）
     * @return JPEG 图片字节
     */
    byte[] getPageImage(Long documentId, int page);
}
