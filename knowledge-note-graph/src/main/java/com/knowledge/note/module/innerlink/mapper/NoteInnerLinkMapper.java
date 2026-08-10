package com.knowledge.note.module.innerlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.note.module.innerlink.entity.NoteInnerLink;
import org.apache.ibatis.annotations.Mapper;

/**
 * 笔记双向链接 Mapper
 */
@Mapper
public interface NoteInnerLinkMapper extends BaseMapper<NoteInnerLink> {
}
