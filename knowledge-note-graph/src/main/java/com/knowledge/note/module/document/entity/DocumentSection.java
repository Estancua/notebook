package com.knowledge.note.module.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("document_section")
public class DocumentSection {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long notebookId;

    private String fileName;

    private String filePath;

    private String fileType;

    private String parseResult;

    private String fullText;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
