package com.knowledge.note.module.document.controller;

import com.knowledge.note.common.exception.BusinessException;
import com.knowledge.note.common.result.Result;
import com.knowledge.note.module.document.dto.DocumentSaveDTO;
import com.knowledge.note.module.document.dto.GenerateMindmapDTO;
import com.knowledge.note.module.document.service.DocumentService;
import com.knowledge.note.module.document.vo.DocumentVO;
import com.knowledge.note.module.document.vo.GenerateMindmapVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;

@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

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
        String filePath = documentService.getFilePath(id);
        File file = new File(filePath);
        if (!file.exists()) {
            throw new BusinessException(404, "文件不存在");
        }
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + file.getName() + "\"")
                .body(resource);
    }

    @GetMapping("/{id}/text")
    public Result<String> getText(@PathVariable Long id) {
        return Result.success(documentService.getFullText(id));
    }
}
