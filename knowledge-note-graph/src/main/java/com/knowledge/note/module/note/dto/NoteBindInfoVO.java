package com.knowledge.note.module.note.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteBindInfoVO {

    private Long documentId;

    private Long chapterId;

    private String chapterTitle;

    private Integer pageStart;

    private Integer pageEnd;
}
