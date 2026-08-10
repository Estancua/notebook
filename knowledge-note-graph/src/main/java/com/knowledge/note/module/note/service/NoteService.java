package com.knowledge.note.module.note.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.note.module.note.dto.*;

/**
 * 笔记服务接口
 */
public interface NoteService {

    /**
     * 保存笔记（新增/修改）
     */
    NoteDetailVO save(NoteSaveDTO dto);

    /**
     * 分页查询笔记列表
     */
    Page<NoteListVO> list(NoteListDTO dto);

    /**
     * 获取笔记详情
     */
    NoteDetailVO detail(Long id);

    /**
     * 软删除移入回收站
     */
    void recycle(Long id);

    /**
     * 批量软删除移入回收站
     */
    void batchRecycle(BatchRecycleDTO dto);

    /**
     * 从回收站恢复
     */
    void recover(Long id);

    /**
     * 永久删除
     */
    void permanentDelete(Long id);

    /**
     * 切换收藏状态
     */
    void favorite(Long id);
}
