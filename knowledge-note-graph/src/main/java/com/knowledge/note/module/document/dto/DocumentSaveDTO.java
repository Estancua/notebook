package com.knowledge.note.module.document.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DocumentSaveDTO {
    @NotNull
    private Long notebookId;

    private String parseResult;
}
