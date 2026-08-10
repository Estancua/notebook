package com.knowledge.note.module.tag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.note.module.tag.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标签 Mapper
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {
}
