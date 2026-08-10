package com.knowledge.note.module.note.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 笔记列表项 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteListVO {

    private Long id;
    private String title;
    private Long notebookId;
    private String notebookName;
    private Integer wordCount;
    private Integer isFavorite;

    /** 标签列表 */
    private List<TagSimpleVO> tags;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
