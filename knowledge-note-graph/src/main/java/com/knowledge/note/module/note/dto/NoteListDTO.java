package com.knowledge.note.module.note.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记列表查询 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteListDTO {

    /** 页码 */
    private Integer page;

    /** 每页条数 */
    private Integer size;

    /** 关键词搜索（按标题） */
    private String keyword;

    /** 笔记本筛选 */
    private Long notebookId;

    /** 收藏筛选 */
    private Integer isFavorite;

    /** 回收站筛选（0=正常, 1=已删除） */
    private Integer isDeleted;
}
