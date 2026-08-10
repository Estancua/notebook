package com.knowledge.note.module.tag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.note.common.exception.BusinessException;
import com.knowledge.note.module.note.entity.NoteTagRel;
import com.knowledge.note.module.note.mapper.NoteTagRelMapper;
import com.knowledge.note.module.tag.dto.TagListVO;
import com.knowledge.note.module.tag.dto.TagSaveDTO;
import com.knowledge.note.module.tag.entity.Tag;
import com.knowledge.note.module.tag.mapper.TagMapper;
import com.knowledge.note.module.tag.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 标签服务实现
 */
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;
    private final NoteTagRelMapper noteTagRelMapper;

    @Override
    @Transactional
    public Tag save(TagSaveDTO dto) {
        Tag tag = new Tag();
        tag.setName(dto.getName());
        tag.setColor(dto.getColor() != null ? dto.getColor() : "");

        if (dto.getId() != null) {
            // 修改：检查名称是否与其他标签重复
            Tag existByName = tagMapper.selectOne(
                    new LambdaQueryWrapper<Tag>().eq(Tag::getName, dto.getName())
                            .ne(Tag::getId, dto.getId()));
            if (existByName != null) {
                throw new BusinessException(409, "标签名已存在");
            }
            tag.setId(dto.getId());
            tagMapper.updateById(tag);
            return tagMapper.selectById(dto.getId());
        } else {
            // 新增：检查名称唯一
            Tag existByName = tagMapper.selectOne(
                    new LambdaQueryWrapper<Tag>().eq(Tag::getName, dto.getName()));
            if (existByName != null) {
                throw new BusinessException(409, "标签名已存在");
            }
            tag.setCreatedAt(LocalDateTime.now());
            tagMapper.insert(tag);
            return tag;
        }
    }

    @Override
    public List<TagListVO> list() {
        List<Tag> tags = tagMapper.selectList(
                new LambdaQueryWrapper<Tag>().orderByAsc(Tag::getCreatedAt));

        return tags.stream().map(tag -> {
            TagListVO vo = new TagListVO();
            vo.setId(tag.getId());
            vo.setName(tag.getName());
            vo.setColor(tag.getColor());
            vo.setCreatedAt(tag.getCreatedAt());

            // 统计关联笔记数量
            Long noteCount = noteTagRelMapper.selectCount(
                    new LambdaQueryWrapper<NoteTagRel>().eq(NoteTagRel::getTagId, tag.getId()));
            vo.setNoteCount(noteCount);

            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw new BusinessException(404, "标签不存在");
        }
        // 删除标签关联
        noteTagRelMapper.delete(
                new LambdaQueryWrapper<NoteTagRel>().eq(NoteTagRel::getTagId, id));
        // 删除标签
        tagMapper.deleteById(id);
    }
}
