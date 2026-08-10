package com.knowledge.note.module.note.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.note.module.note.entity.Note;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 笔记 Mapper
 */
@Mapper
public interface NoteMapper extends BaseMapper<Note> {

    /**
     * 按关键词搜索笔记标题
     */
    @Select("SELECT * FROM note WHERE title LIKE CONCAT('%', #{keyword}, '%') AND is_deleted = 0")
    List<Note> selectByKeyword(@Param("keyword") String keyword);
}
