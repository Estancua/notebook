package com.knowledge.note.module.document.dto;

import lombok.Data;

@Data
public class GenerateMindmapDTO {
    /** 旧版兼容：章节在 parseResult JSON 数组中的下标 */
    private int sectionIndex;
    private String sectionTitle;
    private String sectionContent;
    /** V0.2 新增：指定章节ID，生成脑图笔记后自动绑定该章节（一对一）；不传则不绑定 */
    private Long chapterId;
}
