package com.knowledge.note.module.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("note_pdf_ref")
public class NotePdfRef {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long noteId;

    private String nodeUid;

    private String nodeTitle;

    private Integer pageStart;

    private Integer pageEnd;

    private String excerpt;

    private LocalDateTime createdAt;
}
