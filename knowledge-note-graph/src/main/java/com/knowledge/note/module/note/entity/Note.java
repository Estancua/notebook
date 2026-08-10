package com.knowledge.note.module.note.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 笔记实体
 * 注意：V0.1 使用手动软删除（isDeleted/deletedAt），不使用 @TableLogic，
 * 以确保回收站查询、恢复、永久删除等操作正常可用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("note")
public class Note {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属笔记本ID */
    private Long notebookId;

    /** 笔记标题 */
    private String title;

    /** Markdown正文 */
    private String content;

    /** 字数统计 */
    private Integer wordCount;

    /** 收藏标记 0-否 1-是 */
    private Integer isFavorite;

    /** 回收站标记 0-正常 1-已删除 */
    private Integer isDeleted;

    /** 删除时间（软删除时间戳） */
    private LocalDateTime deletedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
