package com.knowledge.note.module.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.note.common.exception.BusinessException;
import com.knowledge.note.module.document.entity.NotePdfRef;
import com.knowledge.note.module.document.mapper.NotePdfRefMapper;
import com.knowledge.note.module.document.service.NotePdfRefService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotePdfRefServiceImpl implements NotePdfRefService {

    private final NotePdfRefMapper notePdfRefMapper;

    @Override
    public List<NotePdfRef> listByNoteId(Long noteId) {
        LambdaQueryWrapper<NotePdfRef> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotePdfRef::getNoteId, noteId);
        wrapper.orderByAsc(NotePdfRef::getId);
        return notePdfRefMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public NotePdfRef saveOrUpdate(NotePdfRef ref) {
        if (ref.getNoteId() == null) {
            throw new BusinessException(400, "noteId 不能为空");
        }
        if (ref.getNodeUid() == null || ref.getNodeUid().trim().isEmpty()) {
            throw new BusinessException(400, "nodeUid 不能为空");
        }
        LambdaQueryWrapper<NotePdfRef> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotePdfRef::getNoteId, ref.getNoteId());
        wrapper.eq(NotePdfRef::getNodeUid, ref.getNodeUid());
        NotePdfRef existing = notePdfRefMapper.selectOne(wrapper);
        if (existing != null) {
            existing.setNodeTitle(ref.getNodeTitle());
            existing.setPageStart(ref.getPageStart());
            existing.setPageEnd(ref.getPageEnd());
            existing.setExcerpt(ref.getExcerpt());
            notePdfRefMapper.updateById(existing);
            return existing;
        } else {
            ref.setId(null);
            ref.setCreatedAt(LocalDateTime.now());
            notePdfRefMapper.insert(ref);
            return ref;
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        NotePdfRef ref = notePdfRefMapper.selectById(id);
        if (ref == null) {
            throw new BusinessException(404, "记录不存在");
        }
        notePdfRefMapper.deleteById(id);
    }

    @Override
    public NotePdfRef getById(Long id) {
        return notePdfRefMapper.selectById(id);
    }
}
