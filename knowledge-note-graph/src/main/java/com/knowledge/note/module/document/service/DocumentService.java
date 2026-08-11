package com.knowledge.note.module.document.service;

import com.knowledge.note.module.document.dto.GenerateMindmapDTO;
import com.knowledge.note.module.document.entity.DocumentChapter;
import com.knowledge.note.module.document.vo.DocumentVO;
import com.knowledge.note.module.document.vo.GenerateMindmapVO;
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

    /** 手动创建章节 */
    DocumentChapter createChapter(DocumentChapter chapter);

    /** 删除章节（级联删除子章节） */
    void deleteChapter(Long chapterId);
}
