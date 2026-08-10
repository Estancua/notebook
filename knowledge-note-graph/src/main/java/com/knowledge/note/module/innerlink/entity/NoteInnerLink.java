package com.knowledge.note.module.innerlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 笔记双向链接实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("note_inner_link")
public class NoteInnerLink {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 源笔记ID（包含[[xxx]]的笔记） */
    private Long sourceNoteId;

    /** 目标笔记ID（被引用的笔记） */
    private Long targetNoteId;

    /** 目标笔记标题快照 */
    private String targetTitle;

    /** 首次引用时间 */
    private LocalDateTime createdAt;
}
