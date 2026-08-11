package com.knowledge.note.module.document.service;

import com.knowledge.note.module.document.vo.OcrPageVO;

import java.util.List;

/**
 * 火山引擎 OCR 服务接口
 */
public interface VolcanoOcrService {

    /**
     * OCR 识别单页图片，返回纯文本
     * @param imageBase64 图片的 base64 编码（不含 data:image 前缀）
     * @return 识别出的文本
     */
    String recognize(String imageBase64);

    /**
     * OCR 识别单页图片，返回带坐标的文本行列表
     * @param imageBase64 图片的 base64 编码（不含 data:image 前缀）
     * @param imageWidth  图片宽度
     * @param imageHeight 图片高度
     * @return 文本行列表（含归一化坐标）
     */
    List<OcrPageVO.TextLine> recognizeLines(String imageBase64, int imageWidth, int imageHeight);
}
