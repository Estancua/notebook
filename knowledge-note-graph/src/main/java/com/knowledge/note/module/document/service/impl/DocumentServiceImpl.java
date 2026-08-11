package com.knowledge.note.module.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.note.common.exception.BusinessException;
import com.knowledge.note.module.document.dto.GenerateMindmapDTO;
import com.knowledge.note.module.document.entity.DocumentChapter;
import com.knowledge.note.module.document.entity.DocumentSection;
import com.knowledge.note.module.document.mapper.DocumentChapterMapper;
import com.knowledge.note.module.document.mapper.DocumentSectionMapper;
import com.knowledge.note.module.document.service.DeepSeekService;
import com.knowledge.note.module.document.service.DocumentService;
import com.knowledge.note.module.document.vo.DocumentVO;
import com.knowledge.note.module.document.vo.GenerateMindmapVO;
import com.knowledge.note.module.note.entity.Note;
import com.knowledge.note.module.note.mapper.NoteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentSectionMapper documentSectionMapper;
    private final DocumentChapterMapper documentChapterMapper;
    private final DeepSeekService deepSeekService;
    private final NoteMapper noteMapper;
    private final ObjectMapper objectMapper;

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    @Override
    @Transactional
    public DocumentVO upload(MultipartFile file, Long notebookId) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new BusinessException(400, "文件名不能为空");
        }
        String ext = getFileExtension(originalFilename).toUpperCase();
        if (!"PDF".equals(ext) && !"DOCX".equals(ext)) {
            throw new BusinessException(400, "仅支持 PDF 和 DOCX 格式文件，当前文件：" + ext);
        }
        long originSize = file.getSize();
        log.info("========== 文档上传 ==========");
        log.info("上传文档: {} | 格式: {} | 大小: {} bytes ({}MB) | notebookId: {}",
                originalFilename, ext, originSize, String.format("%.2f", originSize / 1024.0 / 1024.0), notebookId);

        String storedName = UUID.randomUUID().toString() + "." + ext.toLowerCase();
        Path uploadDir = Paths.get(uploadPath, notebookId.toString());
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new BusinessException(500, "创建上传目录失败");
        }
        Path targetPath = uploadDir.resolve(storedName);
        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException(500, "文件保存失败: " + e.getMessage());
        }
        Path absolutePath = targetPath.toAbsolutePath();
        log.info("文件落盘路径: {} | 绝对路径: {}", targetPath, absolutePath);

        // 提取全文（仅用于后续手动复制文本等用途，不再自动调用 LLM 解析章节）
        String fullText = "";
        try (InputStream fileStream = Files.newInputStream(absolutePath)) {
            fullText = extractText(fileStream, ext);
        } catch (IOException e) {
            log.warn("文本提取失败（文件仍会保存）| file={}", absolutePath, e);
        }
        int rawLength = fullText == null ? 0 : fullText.length();
        String cleanText = fullText == null ? "" : fullText.replaceAll("\\s", "");
        int cleanLen = cleanText.length();
        log.info("文本提取完成 | 原始字符数: {} | 有效字符数: {}", rawLength, cleanLen);
        if (cleanLen < 50) {
            log.warn("【提示】有效文字较少({}字符)，可能是扫描版/纯图片PDF，需用户手动创建章节", cleanLen);
        }

        DocumentSection docSection = saveDocumentSection(originalFilename, ext, notebookId, absolutePath,
                fullText, "[]");
        log.info("========== 文档上传完成，documentId={}，请手动创建章节 ==========", docSection.getId());
        return toVO(docSection);
    }

    private DocumentSection saveDocumentSection(String originalFilename, String ext, Long notebookId,
                                                Path targetPath, String fullText, String parseResult) {
        DocumentSection entity = new DocumentSection();
        entity.setNotebookId(notebookId);
        entity.setFileName(originalFilename);
        entity.setFilePath(targetPath.toString());
        entity.setFileType(ext);
        entity.setParseResult(parseResult);
        entity.setFullText(fullText);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        documentSectionMapper.insert(entity);
        return entity;
    }

    private void saveChaptersFromParseResult(Long documentId, String parseResult) {
        if (parseResult == null || parseResult.trim().isEmpty()) {
            return;
        }
        List<Map<String, Object>> nodes;
        try {
            nodes = objectMapper.readValue(parseResult, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("解析 parseResult JSON 失败，跳过章节保存: {}", parseResult, e);
            return;
        }
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        AtomicInteger sortCounter = new AtomicInteger(0);
        for (Map<String, Object> node : nodes) {
            saveChapterTree(documentId, 0L, node, sortCounter);
        }
    }

    private void saveChapterTree(Long documentId, Long parentId, Map<String, Object> node, AtomicInteger sortCounter) {
        DocumentChapter chapter = new DocumentChapter();
        chapter.setDocumentId(documentId);
        chapter.setParentId(parentId);
        chapter.setTitle(getString(node, "title"));
        chapter.setLevel(getInteger(node, "level"));
        chapter.setContent(getString(node, "content"));
        chapter.setPageStart(getInteger(node, "pageStart"));
        chapter.setPageEnd(getInteger(node, "pageEnd"));
        chapter.setNoteId(null);
        chapter.setSortOrder(sortCounter.getAndIncrement());
        chapter.setCreatedAt(LocalDateTime.now());
        chapter.setUpdatedAt(LocalDateTime.now());
        documentChapterMapper.insert(chapter);

        Object childrenObj = node.get("children");
        if (childrenObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) childrenObj;
            for (Map<String, Object> child : children) {
                saveChapterTree(documentId, chapter.getId(), child, sortCounter);
            }
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<DocumentVO> listByNotebook(Long notebookId) {
        LambdaQueryWrapper<DocumentSection> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentSection::getNotebookId, notebookId);
        wrapper.orderByDesc(DocumentSection::getCreatedAt);
        List<DocumentSection> list = documentSectionMapper.selectList(wrapper);
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public void updateParseResult(Long id, String parseResult) {
        DocumentSection entity = documentSectionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "文档不存在");
        }
        entity.setParseResult(parseResult);
        entity.setUpdatedAt(LocalDateTime.now());
        documentSectionMapper.updateById(entity);

        documentChapterMapper.delete(new LambdaQueryWrapper<DocumentChapter>().eq(DocumentChapter::getDocumentId, id));
        saveChaptersFromParseResult(id, parseResult);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        DocumentSection entity = documentSectionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "文档不存在");
        }
        try {
            Path filePath = Paths.get(entity.getFilePath());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("删除物理文件失败: {}", entity.getFilePath(), e);
        }
        documentChapterMapper.delete(new LambdaQueryWrapper<DocumentChapter>().eq(DocumentChapter::getDocumentId, id));
        documentSectionMapper.deleteById(id);
    }

    @Override
    public String getFullText(Long id) {
        DocumentSection entity = documentSectionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "文档不存在");
        }
        return entity.getFullText();
    }

    @Override
    public String getFilePath(Long id) {
        DocumentSection entity = documentSectionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "文档不存在");
        }
        return entity.getFilePath();
    }

    @Override
    @Transactional
    public GenerateMindmapVO generateMindmap(Long id, GenerateMindmapDTO dto) {
        DocumentSection doc = documentSectionMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(404, "文档不存在");
        }

        String mindmapMarkdown;
        try {
            mindmapMarkdown = deepSeekService.generateMindmap(dto.getSectionTitle(), dto.getSectionContent());
        } catch (Exception e) {
            log.error("生成思维导图失败", e);
            throw new BusinessException(500, "LLM 生成思维导图失败: " + e.getMessage());
        }

        Note note = new Note();
        note.setNotebookId(doc.getNotebookId());
        note.setTitle(dto.getSectionTitle());
        note.setContent(mindmapMarkdown);
        note.setWordCount(mindmapMarkdown.length());
        note.setIsFavorite(0);
        note.setIsDeleted(0);
        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());
        noteMapper.insert(note);

        // V0.2 新增：如果传了 chapterId，则创建笔记后自动一对一绑定到该章节
        if (dto.getChapterId() != null) {
            try {
                this.bindNote(dto.getChapterId(), note.getId());
            } catch (Exception e) {
                log.warn("脑图笔记生成后自动绑定章节失败，chapterId={} noteId={}", dto.getChapterId(), note.getId(), e);
                // 绑定失败不影响脑图生成的主流程
            }
        }

        GenerateMindmapVO vo = new GenerateMindmapVO();
        vo.setNoteId(note.getId());
        vo.setTitle(dto.getSectionTitle());
        vo.setContent(mindmapMarkdown);
        return vo;
    }

    @Override
    public List<DocumentChapter> listChaptersByDocument(Long documentId) {
        LambdaQueryWrapper<DocumentChapter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChapter::getDocumentId, documentId);
        wrapper.orderByAsc(DocumentChapter::getSortOrder);
        List<DocumentChapter> chapters = documentChapterMapper.selectList(wrapper);
        if (chapters != null && !chapters.isEmpty()) {
            return chapters;
        }
        // 向后兼容：V0.1.5上传的旧文档，parse_result存JSON，document_chapter为空
        // 自动迁移：把JSON parse_result拆解写入document_chapter表，然后返回
        DocumentSection doc = documentSectionMapper.selectById(documentId);
        if (doc == null || !StringUtils.hasText(doc.getParseResult())) {
            return new ArrayList<>();
        }
        log.info("检测到旧文档 documentId={} 未拆分章节，执行迁移拆分 parse_result → document_chapter", documentId);
        try {
            saveChaptersFromParseResult(documentId, doc.getParseResult());
        } catch (Exception e) {
            log.warn("旧文档章节迁移拆分失败: {}", documentId, e);
            return new ArrayList<>();
        }
        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChapter::getDocumentId, documentId);
        wrapper.orderByAsc(DocumentChapter::getSortOrder);
        return documentChapterMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public void bindNote(Long chapterId, Long noteId) {
        DocumentChapter chapter = documentChapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException(404, "章节不存在");
        }
        Note note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BusinessException(404, "笔记不存在");
        }
        LambdaQueryWrapper<DocumentChapter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChapter::getNoteId, noteId);
        wrapper.ne(chapterId != null, DocumentChapter::getId, chapterId);
        DocumentChapter existing = documentChapterMapper.selectOne(wrapper);
        if (existing != null) {
            throw new BusinessException("该笔记已被其他章节绑定，无法重复绑定");
        }
        chapter.setNoteId(noteId);
        chapter.setUpdatedAt(LocalDateTime.now());
        documentChapterMapper.updateById(chapter);
    }

    @Override
    @Transactional
    public void unbindNote(Long chapterId) {
        DocumentChapter chapter = documentChapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException(404, "章节不存在");
        }
        chapter.setNoteId(null);
        chapter.setUpdatedAt(LocalDateTime.now());
        documentChapterMapper.updateById(chapter);
    }

    @Override
    public DocumentChapter getChapterByNoteId(Long noteId) {
        LambdaQueryWrapper<DocumentChapter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChapter::getNoteId, noteId);
        wrapper.last("LIMIT 1");
        return documentChapterMapper.selectOne(wrapper);
    }

    @Override
    public DocumentChapter updateChapter(Long chapterId, DocumentChapter update) {
        DocumentChapter chapter = documentChapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException(404, "章节不存在 id=" + chapterId);
        }
        // 只更新传入的非 null 字段，其他字段原样保留（避免误覆盖 noteId / sortOrder 等）
        boolean changed = false;
        if (update != null) {
            if (update.getPageStart() != null) { chapter.setPageStart(update.getPageStart()); changed = true; }
            if (update.getPageEnd() != null) { chapter.setPageEnd(update.getPageEnd()); changed = true; }
            if (update.getTitle() != null) { chapter.setTitle(update.getTitle()); changed = true; }
            if (update.getContent() != null) { chapter.setContent(update.getContent()); changed = true; }
        }
        if (changed) {
            chapter.setUpdatedAt(LocalDateTime.now());
            documentChapterMapper.updateById(chapter);
            log.info("章节已更新 id={} pageStart={} pageEnd={} title={}", chapter.getId(),
                    chapter.getPageStart(), chapter.getPageEnd(), chapter.getTitle());
        }
        return chapter;
    }

    @Override
    @Transactional
    public DocumentChapter createChapter(DocumentChapter chapter) {
        if (chapter.getLevel() == null) {
            chapter.setLevel(1);
        }
        if (chapter.getParentId() == null) {
            chapter.setParentId(0L);
        }
        // 计算同级排序序号：同级最大 sortOrder + 1
        LambdaQueryWrapper<DocumentChapter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChapter::getDocumentId, chapter.getDocumentId());
        wrapper.eq(DocumentChapter::getParentId, chapter.getParentId());
        wrapper.orderByDesc(DocumentChapter::getSortOrder);
        wrapper.last("LIMIT 1");
        DocumentChapter lastSibling = documentChapterMapper.selectOne(wrapper);
        chapter.setSortOrder(lastSibling != null && lastSibling.getSortOrder() != null
                ? lastSibling.getSortOrder() + 1 : 0);
        chapter.setCreatedAt(LocalDateTime.now());
        chapter.setUpdatedAt(LocalDateTime.now());
        documentChapterMapper.insert(chapter);
        log.info("手动创建章节 id={} title={} documentId={}", chapter.getId(), chapter.getTitle(), chapter.getDocumentId());
        return chapter;
    }

    @Override
    @Transactional
    public void deleteChapter(Long chapterId) {
        DocumentChapter chapter = documentChapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException(404, "章节不存在");
        }
        // 级联删除所有子章节
        List<Long> idsToDelete = new ArrayList<>();
        collectChildIds(chapterId, idsToDelete);
        idsToDelete.add(chapterId);
        for (Long id : idsToDelete) {
            documentChapterMapper.deleteById(id);
        }
        log.info("删除章节 id={}，级联删除 {} 个子章节", chapterId, idsToDelete.size() - 1);
    }

    private void collectChildIds(Long parentId, List<Long> result) {
        LambdaQueryWrapper<DocumentChapter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChapter::getParentId, parentId);
        List<DocumentChapter> children = documentChapterMapper.selectList(wrapper);
        for (DocumentChapter child : children) {
            result.add(child.getId());
            collectChildIds(child.getId(), result);
        }
    }

    private String extractPdfText(InputStream inputStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private String extractDocxText(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractText(InputStream inputStream, String fileType) throws IOException {
        if ("PDF".equals(fileType)) {
            return extractPdfText(inputStream);
        } else if ("DOCX".equals(fileType)) {
            return extractDocxText(inputStream);
        }
        throw new BusinessException(400, "不支持的文件类型: " + fileType);
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1) {
            return "";
        }
        return filename.substring(dotIndex + 1);
    }

    private DocumentVO toVO(DocumentSection entity) {
        DocumentVO vo = new DocumentVO();
        vo.setId(entity.getId());
        vo.setNotebookId(entity.getNotebookId());
        vo.setFileName(entity.getFileName());
        vo.setFileType(entity.getFileType());
        vo.setParseResult(entity.getParseResult());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
