package com.knowledge.note.module.note.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.note.module.note.entity.NoteTagRel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 笔记-标签关联 Mapper
 */
@Mapper
public interface NoteTagRelMapper extends BaseMapper<NoteTagRel> {
}
