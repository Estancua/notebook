package com.knowledge.note.module.document.dto;

import lombok.Data;

@Data
public class GenerateMindmapDTO {
    private int sectionIndex;
    private String sectionTitle;
    private String sectionContent;
}
