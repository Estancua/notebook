package com.knowledge.note.module.innerlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.note.module.innerlink.entity.NoteInnerLink;
import com.knowledge.note.module.innerlink.mapper.NoteInnerLinkMapper;
import com.knowledge.note.module.innerlink.service.InnerLinkService;
import com.knowledge.note.module.note.dto.LinkVO;
import com.knowledge.note.module.note.entity.Note;
import com.knowledge.note.module.note.mapper.NoteMapper;
import com.knowledge.note.util.MarkdownParseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 笔记双向链接服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InnerLinkServiceImpl implements InnerLinkService {

    private final NoteInnerLinkMapper noteInnerLinkMapper;
    private final NoteMapper noteMapper;

    @Override
    @Transactional
    public void parseAndSave(Long noteId, String content) {
        // 提取 [[xxx]] 中的标题
        Set<String> titles = MarkdownParseUtil.extractLinks(content);
        if (titles.isEmpty()) {
            return;
        }

        // 删除该笔记旧的链接记录
        noteInnerLinkMapper.delete(
                new LambdaQueryWrapper<NoteInnerLink>().eq(NoteInnerLink::getSourceNoteId, noteId));

        // 逐个匹配标题找到目标笔记
        for (String title : titles) {
            Note targetNote = noteMapper.selectOne(
                    new LambdaQueryWrapper<Note>()
                            .eq(Note::getTitle, title)
                            .eq(Note::getIsDeleted, 0));
            if (targetNote == null) {
                log.debug("链接目标笔记不存在: title={}", title);
                continue;
            }

            // 跳过自引用
            if (targetNote.getId().equals(noteId)) {
                continue;
            }

            // 插入链接记录
            NoteInnerLink link = new NoteInnerLink();
            link.setSourceNoteId(noteId);
            link.setTargetNoteId(targetNote.getId());
            link.setTargetTitle(title);
            link.setCreatedAt(LocalDateTime.now());

            // 使用 INSERT IGNORE 逻辑：先查是否存在
            Long existCount = noteInnerLinkMapper.selectCount(
                    new LambdaQueryWrapper<NoteInnerLink>()
                            .eq(NoteInnerLink::getSourceNoteId, noteId)
                            .eq(NoteInnerLink::getTargetNoteId, targetNote.getId()));
            if (existCount == 0) {
                noteInnerLinkMapper.insert(link);
            }
        }
    }

    @Override
    public List<LinkVO> getOutgoingLinks(Long noteId) {
        List<NoteInnerLink> links = noteInnerLinkMapper.selectList(
                new LambdaQueryWrapper<NoteInnerLink>().eq(NoteInnerLink::getSourceNoteId, noteId));

        List<LinkVO> result = new ArrayList<>();
        for (NoteInnerLink link : links) {
            Note targetNote = noteMapper.selectById(link.getTargetNoteId());
            if (targetNote != null) {
                result.add(new LinkVO(targetNote.getId(), targetNote.getTitle()));
            }
        }
        return result;
    }

    @Override
    public List<LinkVO> getIncomingLinks(Long noteId) {
        List<NoteInnerLink> links = noteInnerLinkMapper.selectList(
                new LambdaQueryWrapper<NoteInnerLink>().eq(NoteInnerLink::getTargetNoteId, noteId));

        List<LinkVO> result = new ArrayList<>();
        for (NoteInnerLink link : links) {
            Note sourceNote = noteMapper.selectById(link.getSourceNoteId());
            if (sourceNote != null) {
                result.add(new LinkVO(sourceNote.getId(), sourceNote.getTitle()));
            }
        }
        return result;
    }
}
