package com.knowledge.note.module.notebook.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.note.common.exception.BusinessException;
import com.knowledge.note.module.notebook.dto.NotebookSaveDTO;
import com.knowledge.note.module.notebook.dto.NotebookTreeVO;
import com.knowledge.note.module.notebook.entity.Notebook;
import com.knowledge.note.module.notebook.mapper.NotebookMapper;
import com.knowledge.note.module.notebook.service.NotebookService;
import com.knowledge.note.module.note.entity.Note;
import com.knowledge.note.module.note.mapper.NoteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 笔记本服务实现
 */
@Service
@RequiredArgsConstructor
public class NotebookServiceImpl implements NotebookService {

    private final NotebookMapper notebookMapper;
    private final NoteMapper noteMapper;

    @Override
    @Transactional
    public void save(NotebookSaveDTO dto) {
        Notebook notebook = new Notebook();
        notebook.setName(dto.getName());
        notebook.setParentId(dto.getParentId() != null ? dto.getParentId() : 0L);
        notebook.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);

        if (dto.getId() != null) {
            // 修改
            notebook.setId(dto.getId());
            notebook.setUpdatedAt(LocalDateTime.now());
            notebookMapper.updateById(notebook);
        } else {
            // 新增
            notebook.setCreatedAt(LocalDateTime.now());
            notebook.setUpdatedAt(LocalDateTime.now());
            notebookMapper.insert(notebook);
        }
    }

    @Override
    public List<NotebookTreeVO> tree() {
        // 查询全部笔记本
        List<Notebook> all = notebookMapper.selectList(
                new LambdaQueryWrapper<Notebook>().orderByAsc(Notebook::getSortOrder));

        // 转换为 VO
        List<NotebookTreeVO> voList = all.stream().map(nb -> {
            NotebookTreeVO vo = new NotebookTreeVO();
            vo.setId(nb.getId());
            vo.setParentId(nb.getParentId());
            vo.setName(nb.getName());
            vo.setSortOrder(nb.getSortOrder());
            vo.setCreatedAt(nb.getCreatedAt());
            vo.setUpdatedAt(nb.getUpdatedAt());
            vo.setChildren(new ArrayList<>());
            return vo;
        }).collect(Collectors.toList());

        // 按 parentId 分组
        Map<Long, List<NotebookTreeVO>> groupMap = voList.stream()
                .collect(Collectors.groupingBy(NotebookTreeVO::getParentId));

        // 构建树：从根节点（parentId=0）开始递归组装
        return buildTree(0L, groupMap);
    }

    /**
     * 递归构建树结构
     */
    private List<NotebookTreeVO> buildTree(Long parentId, Map<Long, List<NotebookTreeVO>> groupMap) {
        List<NotebookTreeVO> children = groupMap.getOrDefault(parentId, new ArrayList<>());
        for (NotebookTreeVO vo : children) {
            vo.setChildren(buildTree(vo.getId(), groupMap));
        }
        return children;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // 校验是否有子笔记本
        Long childCount = notebookMapper.selectCount(
                new LambdaQueryWrapper<Notebook>().eq(Notebook::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException(409, "笔记本非空，无法删除");
        }

        // 校验是否有笔记
        Long noteCount = noteMapper.selectCount(
                new LambdaQueryWrapper<Note>().eq(Note::getNotebookId, id));
        if (noteCount > 0) {
            throw new BusinessException(409, "笔记本非空，无法删除");
        }

        notebookMapper.deleteById(id);
    }
}
