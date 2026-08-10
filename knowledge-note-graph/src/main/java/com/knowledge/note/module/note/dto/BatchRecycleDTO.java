package com.knowledge.note.module.note.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量回收站 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchRecycleDTO {

    /** 笔记ID列表 */
    private List<Long> ids;
}
