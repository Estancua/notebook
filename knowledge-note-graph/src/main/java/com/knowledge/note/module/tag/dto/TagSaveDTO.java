package com.knowledge.note.module.tag.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标签保存 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagSaveDTO {

    /** 标签ID（新增时不传） */
    private Long id;

    /** 标签名称 */
    @NotBlank(message = "标签名称不能为空")
    private String name;

    /** 标签颜色 */
    private String color;
}
