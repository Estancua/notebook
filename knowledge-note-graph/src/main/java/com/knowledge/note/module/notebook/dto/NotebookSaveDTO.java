package com.knowledge.note.module.notebook.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记本保存 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotebookSaveDTO {

    /** 笔记本ID（新增时不传，修改时必传） */
    private Long id;

    /** 父笔记本ID，0为根级 */
    private Long parentId;

    /** 笔记本名称 */
    @NotBlank(message = "笔记本名称不能为空")
    private String name;

    /** 排序号 */
    private Integer sortOrder;
}
