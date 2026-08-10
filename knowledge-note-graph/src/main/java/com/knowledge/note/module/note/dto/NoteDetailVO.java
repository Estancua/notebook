package com.knowledge.note.module.note.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 笔记详情 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteDetailVO {

    private Long id;
    private String title;
    private String content;
    private Integer wordCount;
    private Integer isFavorite;
    private Integer isDeleted;
    private Long notebookId;
    private String notebookName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 标签列表 */
    private List<TagSimpleVO> tags;

    /** 正向链接（我引用了哪些笔记） */
    private List<LinkVO> outgoingLinks;

    /** 反向链接（哪些笔记引用了我） */
    private List<LinkVO> incomingLinks;
}
