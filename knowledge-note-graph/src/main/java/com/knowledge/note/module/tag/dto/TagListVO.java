package com.knowledge.note.module.tag.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 标签列表 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagListVO {

    private Long id;
    private String name;
    private String color;

    /** 关联笔记数量 */
    private Long noteCount;

    private LocalDateTime createdAt;
}
