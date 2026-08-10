package com.knowledge.note.module.notebook.service;

import com.knowledge.note.module.notebook.dto.NotebookSaveDTO;
import com.knowledge.note.module.notebook.dto.NotebookTreeVO;

import java.util.List;

/**
 * 笔记本服务接口
 */
public interface NotebookService {

    /**
     * 新增或修改笔记本
     */
    void save(NotebookSaveDTO dto);

    /**
     * 获取笔记本树
     */
    List<NotebookTreeVO> tree();

    /**
     * 删除笔记本
     */
    void delete(Long id);
}
