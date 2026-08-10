package com.knowledge.note.module.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 笔记保存 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteSaveDTO {

    /** 笔记ID（新增时不传，修改时必传） */
    private Long id;

    /** 笔记标题 */
    @NotBlank(message = "笔记标题不能为空")
    private String title;

    /** Markdown内容 */
    private String content;

    /** 归属笔记本ID */
    @NotNull(message = "笔记本不能为空")
    private Long notebookId;

    /** 标签ID列表 */
    private List<Long> tagIds;
}
