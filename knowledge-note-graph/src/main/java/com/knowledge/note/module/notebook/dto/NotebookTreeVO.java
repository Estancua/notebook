package com.knowledge.note.module.notebook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 笔记本树节点 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotebookTreeVO {

    private Long id;
    private Long parentId;
    private String name;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 子节点列表 */
    private List<NotebookTreeVO> children;
}
