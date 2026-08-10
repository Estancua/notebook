package com.knowledge.note.module.note.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记-标签关联实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("note_tag_rel")
public class NoteTagRel {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 笔记ID */
    private Long noteId;

    /** 标签ID */
    private Long tagId;
}
