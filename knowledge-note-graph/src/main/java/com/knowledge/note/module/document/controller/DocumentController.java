package com.knowledge.note.module.document.controller;

import com.knowledge.note.common.exception.BusinessException;
import com.knowledge.note.common.result.Result;
import com.knowledge.note.module.document.dto.DocumentSaveDTO;
import com.knowledge.note.module.document.dto.GenerateMindmapDTO;
import com.knowledge.note.module.document.entity.DocumentChapter;
import com.knowledge.note.module.document.entity.DocumentSection;
import com.knowledge.note.module.document.mapper.DocumentSectionMapper;
import com.knowledge.note.module.document.service.DocumentService;
import com.knowledge.note.module.document.vo.DocumentVO;
import com.knowledge.note.module.document.vo.GenerateMindmapVO;
import com.knowledge.note.module.document.vo.OcrPageVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentSectionMapper documentSectionMapper;

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    @PostMapping("/upload")
    public Result<DocumentVO> upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam("notebookId") Long notebookId) {
        return Result.success(documentService.upload(file, notebookId));
    }

    @GetMapping("/{notebookId}")
    public Result<List<DocumentVO>> listByNotebook(@PathVariable Long notebookId) {
        return Result.success(documentService.listByNotebook(notebookId));
    }

    @PutMapping("/{id}")
    public Result<Void> updateParseResult(@PathVariable Long id,
                                          @RequestBody DocumentSaveDTO dto) {
        documentService.updateParseResult(id, dto.getParseResult());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/generate-mindmap")
    public Result<GenerateMindmapVO> generateMindmap(@PathVariable Long id,
                                                      @RequestBody GenerateMindmapDTO dto) {
        return Result.success(documentService.generateMindmap(id, dto));
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable Long id) {
        try {
            return doPreview(id);
        } catch (BusinessException e) {
            // ⚠️ 预览接口是给 iframe 用的，绝对不能返回 JSON Result（否则浏览器会把 JSON 当文件下载）
            // 业务异常转换为 HTTP 404 + 纯文本说明，iframe 能直接展示文字，不会触发下载
            log.warn("预览业务异常 id={} code={} msg={}", id, e.getCode(), e.getMessage());
            return errorTextResponse(
                    e.getCode() >= 400 && e.getCode() < 600 ? e.getCode() : 400,
                    "预览失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("预览内部错误 id={}", id, e);
            return errorTextResponse(500, "预览失败（服务内部错误）：" + e.getMessage());
        }
    }

    private ResponseEntity<Resource> doPreview(Long id) {
        DocumentSection doc = documentSectionMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(404, "文档不存在 id=" + id);
        }
        String filePath = doc.getFilePath();
        File file = resolveFile(filePath);
        if (file == null || !file.exists() || !file.isFile()) {
            log.error("预览失败：文档id={} 原始路径={} 工作目录={} uploadPath配置={} —— 文件不存在或不是普通文件",
                    id, filePath, System.getProperty("user.dir"), uploadPath);
            throw new BusinessException(404, "文件不存在（原始path=" + filePath + "）");
        }
        long fileSize = file.length();
        String fileName = doc.getFileName() != null ? doc.getFileName() : file.getName();
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0) {
            ext = fileName.substring(dot + 1).toUpperCase();
        }

        MediaType mediaType;
        switch (ext) {
            case "PDF":
                mediaType = MediaType.APPLICATION_PDF;
                break;
            case "DOCX":
            case "DOC":
                mediaType = MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                break;
            case "TXT":
                mediaType = MediaType.TEXT_PLAIN;
                break;
            default:
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        String encodedFilename;
        try {
            encodedFilename = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");
        } catch (Exception e) {
            encodedFilename = fileName;
        }

        log.info("预览文档 id={} file={} size={}B ext={} contentType={} resolvedPath={}",
                id, fileName, fileSize, ext, mediaType, file.getAbsolutePath());

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(fileSize)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename)
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    /**
     * 兼容 filePath 存的是「相对路径」还是「绝对路径」两种情况。
     * V0.1.5 及之前上传的文档 filePath=uploads/2/xxx.pdf（相对 user.dir）
     * V0.1.6+ 上传的文档 filePath 是绝对路径
     */
    private File resolveFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) return null;
        File f = new File(filePath);
        if (f.isAbsolute() && f.exists()) return f;
        // 尝试按相对路径 + user.dir 解析
        if (!f.isAbsolute()) {
            File absByUserDir = f.getAbsoluteFile();
            if (absByUserDir.exists()) return absByUserDir;
        }
        // 尝试按相对路径 + 配置的 uploadPath 解析
        if (uploadPath != null && !uploadPath.isEmpty()) {
            File byUploadDir = new File(uploadPath, filePath.replace("\\", "/").replaceFirst("^uploads[/\\\\]", ""));
            if (byUploadDir.exists()) return byUploadDir;
            // 再试一种：uploadPath + filePath 末尾相对部分
            File byUploadDir2 = Paths.get(uploadPath).resolve(filePath.replace("\\", "/")).toFile();
            if (byUploadDir2.exists()) return byUploadDir2;
        }
        // 都找不到就返回原始的（后面调用方会判断 exists）
        return f;
    }

    /**
     * 预览接口出错时，返回 HTTP 错误码 + 纯文本说明，不要返回 JSON。
     * iframe 收到 text/plain 会直接渲染文字，而不是触发「下载 JSON 文件」。
     */
    private ResponseEntity<Resource> errorTextResponse(int httpStatus, String message) {
        byte[] bytes = (message + "\n\n（文档预览失败，请返回上一页重试）").getBytes(StandardCharsets.UTF_8);
        Resource body = new org.springframework.core.io.ByteArrayResource(bytes);
        return ResponseEntity.status(httpStatus)
                .contentType(MediaType.TEXT_PLAIN)
                .contentLength(bytes.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(body);
    }

    @GetMapping("/{id}/text")
    public Result<String> getText(@PathVariable Long id) {
        return Result.success(documentService.getFullText(id));
    }

    @GetMapping("/chapter/list/{documentId}")
    public Result<List<DocumentChapter>> listChapters(@PathVariable Long documentId) {
        return Result.success(documentService.listChaptersByDocument(documentId));
    }

    @PutMapping("/chapter/{chapterId}/bind-note")
    public Result<Void> bindNote(@PathVariable Long chapterId,
                                  @RequestBody BindNoteRequest request) {
        documentService.bindNote(chapterId, request.getNoteId());
        return Result.success();
    }

    @PutMapping("/chapter/{chapterId}/unbind-note")
    public Result<Void> unbindNote(@PathVariable Long chapterId) {
        documentService.unbindNote(chapterId);
        return Result.success();
    }

    /**
     * 单个章节按字段更新（页码范围/标题/内容），传入非 null 字段才会被更新。
     * 用于用户手动绑定章节 ↔ PDF 页码。
     */
    @PutMapping("/chapter/{chapterId}")
    public Result<DocumentChapter> updateChapter(@PathVariable Long chapterId,
                                                  @RequestBody DocumentChapter update) {
        if (update == null) {
            return Result.success(documentService.updateChapter(chapterId, null));
        }
        // 基本参数校验：页码必须 >=1
        if (update.getPageStart() != null && update.getPageStart() < 1) {
            throw new BusinessException(400, "起始页码必须 >= 1");
        }
        if (update.getPageEnd() != null && update.getPageEnd() < 1) {
            throw new BusinessException(400, "结束页码必须 >= 1");
        }
        if (update.getPageStart() != null && update.getPageEnd() != null
                && update.getPageEnd() < update.getPageStart()) {
            throw new BusinessException(400, "结束页码不能小于起始页码");
        }
        return Result.success(documentService.updateChapter(chapterId, update));
    }

    @GetMapping("/chapter/by-note/{noteId}")
    public Result<DocumentChapter> getChapterByNoteId(@PathVariable Long noteId) {
        return Result.success(documentService.getChapterByNoteId(noteId));
    }

    /** OCR 识别 PDF 指定页（纯文本） */
    @PostMapping("/{id}/ocr-page")
    public Result<String> ocrPage(@PathVariable Long id, @RequestParam int page) {
        return Result.success(documentService.ocrPage(id, page));
    }

    /** OCR 识别并返回图片+坐标（文字层叠加用） */
    @PostMapping("/{id}/ocr-page-with-positions")
    public Result<OcrPageVO> ocrPageWithPositions(@PathVariable Long id, @RequestParam int page) {
        return Result.success(documentService.ocrPageWithPositions(id, page));
    }

    /** 获取 PDF 单页 OCR 结果（带缓存，首次识别后自动缓存） */
    @GetMapping("/{id}/ocr-page-result")
    public Result<OcrPageVO> getPageOcrResult(@PathVariable Long id, @RequestParam int page) {
        return Result.success(documentService.getPageOcrResult(id, page));
    }

    /** 手动创建章节 */
    @PostMapping("/chapter")
    public Result<DocumentChapter> createChapter(@RequestBody DocumentChapter chapter) {
        return Result.success(documentService.createChapter(chapter));
    }

    /** 删除章节（级联删除子章节） */
    @DeleteMapping("/chapter/{id}")
    public Result<Void> deleteChapter(@PathVariable Long id) {
        documentService.deleteChapter(id);
        return Result.success();
    }

    @Data
    public static class BindNoteRequest {
        private Long noteId;
    }
}
