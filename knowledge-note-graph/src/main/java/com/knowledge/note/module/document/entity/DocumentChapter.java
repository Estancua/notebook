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
@TableName("document_chapter")
public class DocumentChapter {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private Long parentId;

    private String title;

    private Integer level;

    private String content;

    private Integer pageStart;

    private Integer pageEnd;

    private Long noteId;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
