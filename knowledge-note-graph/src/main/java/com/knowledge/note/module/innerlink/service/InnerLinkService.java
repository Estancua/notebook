package com.knowledge.note.module.innerlink.service;

import com.knowledge.note.module.note.dto.LinkVO;

import java.util.List;

/**
 * 笔记双向链接服务接口
 */
public interface InnerLinkService {

    /**
     * 解析笔记内容中的 [[xxx]] 并保存双向链接
     */
    void parseAndSave(Long noteId, String content);

    /**
     * 获取正向链接（该笔记引用了哪些笔记）
     */
    List<LinkVO> getOutgoingLinks(Long noteId);

    /**
     * 获取反向链接（哪些笔记引用了该笔记）
     */
    List<LinkVO> getIncomingLinks(Long noteId);
}
