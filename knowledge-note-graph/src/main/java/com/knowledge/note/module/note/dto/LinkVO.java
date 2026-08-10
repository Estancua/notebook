package com.knowledge.note.module.note.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 链接 VO（用于正反向链接展示）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkVO {

    private Long noteId;
    private String title;
}
