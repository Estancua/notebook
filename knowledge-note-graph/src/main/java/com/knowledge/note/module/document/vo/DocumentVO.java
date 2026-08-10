package com.knowledge.note.module.document.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentVO {
    private Long id;
    private Long notebookId;
    private String fileName;
    private String fileType;
    private String parseResult;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
