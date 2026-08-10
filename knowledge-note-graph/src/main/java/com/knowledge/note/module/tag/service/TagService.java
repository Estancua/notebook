package com.knowledge.note.module.tag.service;

import com.knowledge.note.module.tag.dto.TagListVO;
import com.knowledge.note.module.tag.dto.TagSaveDTO;
import com.knowledge.note.module.tag.entity.Tag;

import java.util.List;

/**
 * 标签服务接口
 */
public interface TagService {

    /**
     * 新增/修改标签，返回保存后的 Tag 对象
     */
    Tag save(TagSaveDTO dto);

    /**
     * 获取标签列表
     */
    List<TagListVO> list();

    /**
     * 删除标签（同时解除所有关联）
     */
    void delete(Long id);
}
