package com.knowledge.note.module.notebook.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 笔记本实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("notebook")
public class Notebook {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父笔记本ID，0为根级 */
    private Long parentId;

    /** 笔记本名称 */
    private String name;

    /** 排序号，越小越靠前 */
    private Integer sortOrder;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
