package com.knowledge.note.module.note.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标签简略 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagSimpleVO {

    private Long id;
    private String name;
    private String color;
}
