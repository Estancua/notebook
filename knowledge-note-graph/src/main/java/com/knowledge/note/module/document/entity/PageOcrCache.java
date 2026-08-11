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
@TableName("page_ocr_cache")
public class PageOcrCache {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private Integer pageNumber;

    private String imageBase64;

    private String ocrText;

    private String textLines;

    private Integer imageWidth;

    private Integer imageHeight;

    private LocalDateTime createdAt;
}
