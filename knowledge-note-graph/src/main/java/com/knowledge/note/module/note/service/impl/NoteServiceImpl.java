package com.knowledge.note.module.note.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.note.common.exception.BusinessException;
import com.knowledge.note.module.document.entity.DocumentChapter;
import com.knowledge.note.module.document.mapper.DocumentChapterMapper;
import com.knowledge.note.module.innerlink.entity.NoteInnerLink;
import com.knowledge.note.module.innerlink.mapper.NoteInnerLinkMapper;
import com.knowledge.note.module.innerlink.service.InnerLinkService;
import com.knowledge.note.module.note.dto.*;
import com.knowledge.note.module.note.entity.Note;
import com.knowledge.note.module.note.entity.NoteTagRel;
import com.knowledge.note.module.note.mapper.NoteMapper;
import com.knowledge.note.module.note.mapper.NoteTagRelMapper;
import com.knowledge.note.module.note.service.NoteService;
import com.knowledge.note.module.notebook.entity.Notebook;
import com.knowledge.note.module.notebook.mapper.NotebookMapper;
import com.knowledge.note.module.tag.entity.Tag;
import com.knowledge.note.module.tag.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 笔记服务实现
 */
@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    private final NoteMapper noteMapper;
    private final NotebookMapper notebookMapper;
    private final NoteTagRelMapper noteTagRelMapper;
    private final TagMapper tagMapper;
    private final InnerLinkService innerLinkService;
    private final NoteInnerLinkMapper noteInnerLinkMapper;
    private final DocumentChapterMapper documentChapterMapper;

    @Override
    @Transactional
    public NoteDetailVO save(NoteSaveDTO dto) {
        Note note = new Note();
        note.setTitle(dto.getTitle());
        note.setContent(dto.getContent());
        note.setNotebookId(dto.getNotebookId());

        // 计算字数
        int wordCount = 0;
        if (StringUtils.hasText(dto.getContent())) {
            // 简单字数统计：去除空格和 Markdown 标记后计数
            String plainText = dto.getContent()
                    .replaceAll("[#*`>\\-\\[\\]()!_~|]", "")
                    .replaceAll("\\s+", "");
            wordCount = plainText.length();
        }
        note.setWordCount(wordCount);

        if (dto.getId() != null) {
            // 修改：先查询确保笔记存在
            Note existing = noteMapper.selectById(dto.getId());
            if (existing == null) {
                throw new BusinessException(404, "笔记不存在");
            }
            note.setId(dto.getId());
            note.setIsFavorite(existing.getIsFavorite());
            note.setUpdatedAt(LocalDateTime.now());
            noteMapper.updateById(note);

            // 删除旧标签关联
            noteTagRelMapper.delete(
                    new LambdaQueryWrapper<NoteTagRel>().eq(NoteTagRel::getNoteId, dto.getId()));
        } else {
            // 新增
            note.setIsFavorite(0);
            note.setCreatedAt(LocalDateTime.now());
            note.setUpdatedAt(LocalDateTime.now());
            noteMapper.insert(note);
        }

        // 保存标签关联
        if (dto.getTagIds() != null && !dto.getTagIds().isEmpty()) {
            List<NoteTagRel> rels = dto.getTagIds().stream().map(tagId -> {
                NoteTagRel rel = new NoteTagRel();
                rel.setNoteId(note.getId());
                rel.setTagId(tagId);
                return rel;
            }).collect(Collectors.toList());
            for (NoteTagRel rel : rels) {
                noteTagRelMapper.insert(rel);
            }
        }

        // 解析 [[xxx]] 双向链接
        if (StringUtils.hasText(note.getContent())) {
            innerLinkService.parseAndSave(note.getId(), note.getContent());
        }

        // TODO: V0.2 触发异步事件（图谱更新、向量更新）

        // 返回详情
        return this.detail(note.getId());
    }

    @Override
    public Page<NoteListVO> list(NoteListDTO dto) {
        LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<>();

        // 默认查正常笔记
        if (dto.getIsDeleted() != null && dto.getIsDeleted() == 1) {
            wrapper.eq(Note::getIsDeleted, 1);
        } else {
            wrapper.eq(Note::getIsDeleted, 0);
        }

        // 条件筛选
        if (dto.getNotebookId() != null) {
            wrapper.eq(Note::getNotebookId, dto.getNotebookId());
        }
        if (dto.getIsFavorite() != null) {
            wrapper.eq(Note::getIsFavorite, dto.getIsFavorite());
        }
        if (StringUtils.hasText(dto.getKeyword())) {
            wrapper.like(Note::getTitle, dto.getKeyword());
        }

        wrapper.orderByDesc(Note::getUpdatedAt);

        // 分页
        int pageNum = (dto.getPage() != null && dto.getPage() > 0) ? dto.getPage() : 1;
        int pageSize = (dto.getSize() != null && dto.getSize() > 0 && dto.getSize() <= 100) ? dto.getSize() : 20;
        Page<Note> page = new Page<>(pageNum, pageSize);
        Page<Note> notePage = noteMapper.selectPage(page, wrapper);

        // 转换为 NoteListVO
        List<NoteListVO> voList = notePage.getRecords().stream().map(note -> {
            NoteListVO vo = new NoteListVO();
            vo.setId(note.getId());
            vo.setTitle(note.getTitle());
            vo.setNotebookId(note.getNotebookId());
            vo.setWordCount(note.getWordCount());
            vo.setIsFavorite(note.getIsFavorite());
            vo.setUpdatedAt(note.getUpdatedAt());
            vo.setCreatedAt(note.getCreatedAt());
            vo.setDeletedAt(note.getDeletedAt());

            // 笔记本名称
            Notebook notebook = notebookMapper.selectById(note.getNotebookId());
            vo.setNotebookName(notebook != null ? notebook.getName() : "");

            // 标签
            List<NoteTagRel> rels = noteTagRelMapper.selectList(
                    new LambdaQueryWrapper<NoteTagRel>().eq(NoteTagRel::getNoteId, note.getId()));
            List<TagSimpleVO> tags = new ArrayList<>();
            for (NoteTagRel rel : rels) {
                Tag tag = tagMapper.selectById(rel.getTagId());
                if (tag != null) {
                    tags.add(new TagSimpleVO(tag.getId(), tag.getName(), tag.getColor()));
                }
            }
            vo.setTags(tags);

            return vo;
        }).collect(Collectors.toList());

        Page<NoteListVO> resultPage = new Page<>(pageNum, pageSize, notePage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    public NoteDetailVO detail(Long id) {
        Note note = noteMapper.selectById(id);
        if (note == null) {
            throw new BusinessException(404, "笔记不存在");
        }

        NoteDetailVO vo = new NoteDetailVO();
        vo.setId(note.getId());
        vo.setTitle(note.getTitle());
        vo.setContent(note.getContent());
        vo.setWordCount(note.getWordCount());
        vo.setIsFavorite(note.getIsFavorite());
        vo.setIsDeleted(note.getIsDeleted());
        vo.setNotebookId(note.getNotebookId());
        vo.setCreatedAt(note.getCreatedAt());
        vo.setUpdatedAt(note.getUpdatedAt());

        // 笔记本名称
        Notebook notebook = notebookMapper.selectById(note.getNotebookId());
        vo.setNotebookName(notebook != null ? notebook.getName() : "");

        // 标签
        List<NoteTagRel> rels = noteTagRelMapper.selectList(
                new LambdaQueryWrapper<NoteTagRel>().eq(NoteTagRel::getNoteId, note.getId()));
        List<TagSimpleVO> tags = new ArrayList<>();
        for (NoteTagRel rel : rels) {
            Tag tag = tagMapper.selectById(rel.getTagId());
            if (tag != null) {
                tags.add(new TagSimpleVO(tag.getId(), tag.getName(), tag.getColor()));
            }
        }
        vo.setTags(tags);

        // 正向链接（我引用了谁）
        List<LinkVO> outgoingLinks = innerLinkService.getOutgoingLinks(note.getId());
        vo.setOutgoingLinks(outgoingLinks != null ? outgoingLinks : Collections.emptyList());

        // 反向链接（谁引用了我）
        List<LinkVO> incomingLinks = innerLinkService.getIncomingLinks(note.getId());
        vo.setIncomingLinks(incomingLinks != null ? incomingLinks : Collections.emptyList());

        // 文档章节绑定信息
        LambdaQueryWrapper<DocumentChapter> chapterWrapper = new LambdaQueryWrapper<>();
        chapterWrapper.eq(DocumentChapter::getNoteId, note.getId());
        chapterWrapper.last("LIMIT 1");
        DocumentChapter chapter = documentChapterMapper.selectOne(chapterWrapper);
        if (chapter != null) {
            NoteBindInfoVO bindInfo = new NoteBindInfoVO();
            bindInfo.setDocumentId(chapter.getDocumentId());
            bindInfo.setChapterId(chapter.getId());
            bindInfo.setChapterTitle(chapter.getTitle());
            bindInfo.setPageStart(chapter.getPageStart());
            bindInfo.setPageEnd(chapter.getPageEnd());
            vo.setBindInfo(bindInfo);
        }

        return vo;
    }

    @Override
    @Transactional
    public void recycle(Long id) {
        Note note = noteMapper.selectById(id);
        if (note == null) {
            throw new BusinessException(404, "笔记不存在");
        }
        note.setIsDeleted(1);
        note.setDeletedAt(LocalDateTime.now());
        noteMapper.updateById(note);
    }

    @Override
    @Transactional
    public void batchRecycle(BatchRecycleDTO dto) {
        if (dto.getIds() == null || dto.getIds().isEmpty()) {
            return;
        }
        for (Long id : dto.getIds()) {
            this.recycle(id);
        }
    }

    @Override
    @Transactional
    public void recover(Long id) {
        Note note = noteMapper.selectById(id);
        if (note == null) {
            throw new BusinessException(404, "笔记不存在");
        }
        note.setIsDeleted(0);
        note.setDeletedAt(null);
        noteMapper.updateById(note);
    }

    @Override
    @Transactional
    public void permanentDelete(Long id) {
        Note note = noteMapper.selectById(id);
        if (note == null) {
            throw new BusinessException(404, "笔记不存在");
        }
        // 删除关联 note_tag_rel
        noteTagRelMapper.delete(
                new LambdaQueryWrapper<NoteTagRel>().eq(NoteTagRel::getNoteId, id));
        // 删除关联 note_inner_link
        noteInnerLinkMapper.delete(
                new LambdaQueryWrapper<NoteInnerLink>().eq(NoteInnerLink::getSourceNoteId, id)
                        .or().eq(NoteInnerLink::getTargetNoteId, id));
        // 物理删除笔记
        noteMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void favorite(Long id) {
        Note note = noteMapper.selectById(id);
        if (note == null) {
            throw new BusinessException(404, "笔记不存在");
        }
        // 切换收藏状态
        note.setIsFavorite(note.getIsFavorite() == 1 ? 0 : 1);
        noteMapper.updateById(note);
    }
}
