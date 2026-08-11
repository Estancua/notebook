package com.knowledge.note.module.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OcrPageVO {

    /** 页面图片 base64 */
    private String imageBase64;

    /** 页面图片宽度 */
    private int imageWidth;

    /** 页面图片高度 */
    private int imageHeight;

    /** 识别出的文本行列表（含位置坐标） */
    private List<TextLine> textLines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TextLine {
        /** 文本内容 */
        private String text;
        /** 左上角 x 坐标（百分比，0-100） */
        private double x;
        /** 左上角 y 坐标（百分比，0-100） */
        private double y;
        /** 宽度（百分比，0-100） */
        private double width;
        /** 高度（百分比，0-100） */
        private double height;
    }
}
