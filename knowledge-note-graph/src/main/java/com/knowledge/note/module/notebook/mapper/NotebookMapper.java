package com.knowledge.note.module.notebook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.note.module.notebook.entity.Notebook;
import org.apache.ibatis.annotations.Mapper;

/**
 * 笔记本 Mapper
 */
@Mapper
public interface NotebookMapper extends BaseMapper<Notebook> {
}
