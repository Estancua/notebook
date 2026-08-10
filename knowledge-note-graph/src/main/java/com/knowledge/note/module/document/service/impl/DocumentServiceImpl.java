package com.knowledge.note.module.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.note.common.exception.BusinessException;
import com.knowledge.note.module.document.dto.GenerateMindmapDTO;
import com.knowledge.note.module.document.entity.DocumentSection;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentSectionMapper documentSectionMapper;
    private final DeepSeekService deepSeekService;
    private final NoteMapper noteMapper;

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    @Override
    @Transactional
    public DocumentVO upload(MultipartFile file, Long notebookId) {
        // 1. 校验文件类型
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new BusinessException(400, "文件名不能为空");
        }
        String ext = getFileExtension(originalFilename).toUpperCase();
        if (!"PDF".equals(ext) && !"DOCX".equals(ext)) {
            throw new BusinessException(400, "仅支持 PDF 和 DOCX 格式文件，当前文件：" + ext);
        }

        // 2. 保存文件到 uploadDir/notebookId/filename
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

        // 3. 提取全文文本
        String fullText;
        try {
            fullText = extractText(file.getInputStream(), ext);
        } catch (IOException e) {
            throw new BusinessException(500, "文本提取失败: " + e.getMessage());
        }

        // 4. 调用 DeepSeek 解析文档章节结构
        String parseResult;
        try {
            parseResult = deepSeekService.parseDocumentStructure(fullText);
        } catch (Exception e) {
            log.warn("LLM 解析文档结构失败，保存空白解析结果", e);
            parseResult = "[]";
        }

        // 5. 创建 DocumentSection 实体写入数据库
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

        return toVO(entity);
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
    }

    @Override
    @Transactional
    public void delete(Long id) {
        DocumentSection entity = documentSectionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "文档不存在");
        }
        // 删除物理文件
        try {
            Path filePath = Paths.get(entity.getFilePath());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("删除物理文件失败: {}", entity.getFilePath(), e);
        }
        // 删除数据库记录
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

        // 1. 调用 LLM 生成思维导图 Markdown
        String mindmapMarkdown;
        try {
            mindmapMarkdown = deepSeekService.generateMindmap(dto.getSectionTitle(), dto.getSectionContent());
        } catch (Exception e) {
            log.error("生成思维导图失败", e);
            throw new BusinessException(500, "LLM 生成思维导图失败: " + e.getMessage());
        }

        // 2. 创建笔记
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

        // 3. 返回 GenerateMindmapVO
        GenerateMindmapVO vo = new GenerateMindmapVO();
        vo.setNoteId(note.getId());
        vo.setTitle(dto.getSectionTitle());
        vo.setContent(mindmapMarkdown);
        return vo;
    }

    /**
     * 提取 PDF 文本
     */
    private String extractPdfText(InputStream inputStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    /**
     * 提取 Word 文本
     */
    private String extractDocxText(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    /**
     * 提取文档全文文本
     */
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
