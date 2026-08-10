package com.knowledge.note.module.note.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.note.common.result.Result;
import com.knowledge.note.module.note.dto.*;
import com.knowledge.note.module.note.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 笔记控制器
 */
@RestController
@RequestMapping("/api/note")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    /** 保存笔记（新增/修改） */
    @PostMapping("/save")
    public Result<NoteDetailVO> save(@Valid @RequestBody NoteSaveDTO dto) {
        return Result.success(noteService.save(dto));
    }

    /** 笔记列表 */
    @GetMapping("/list")
    public Result<Page<NoteListVO>> list(NoteListDTO dto) {
        return Result.success(noteService.list(dto));
    }

    /** 获取笔记详情 */
    @GetMapping("/{id}")
    public Result<NoteDetailVO> detail(@PathVariable Long id) {
        return Result.success(noteService.detail(id));
    }

    /** 软删除移入回收站 */
    @DeleteMapping("/{id}")
    public Result<Void> recycle(@PathVariable Long id) {
        noteService.recycle(id);
        return Result.success();
    }

    /** 批量移入回收站 */
    @PostMapping("/batch-recycle")
    public Result<Void> batchRecycle(@RequestBody BatchRecycleDTO dto) {
        noteService.batchRecycle(dto);
        return Result.success();
    }

    /** 从回收站恢复 */
    @PostMapping("/{id}/recover")
    public Result<Void> recover(@PathVariable Long id) {
        noteService.recover(id);
        return Result.success();
    }

    /** 永久删除 */
    @DeleteMapping("/{id}/permanent")
    public Result<Void> permanentDelete(@PathVariable Long id) {
        noteService.permanentDelete(id);
        return Result.success();
    }

    /** 切换收藏状态 */
    @PostMapping("/{id}/favorite")
    public Result<Void> favorite(@PathVariable Long id) {
        noteService.favorite(id);
        return Result.success();
    }
}
