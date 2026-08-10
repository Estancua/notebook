package com.knowledge.note.module.notebook.controller;

import com.knowledge.note.common.result.Result;
import com.knowledge.note.module.notebook.dto.NotebookSaveDTO;
import com.knowledge.note.module.notebook.dto.NotebookTreeVO;
import com.knowledge.note.module.notebook.service.NotebookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 笔记本控制器
 */
@RestController
@RequestMapping("/api/notebook")
@RequiredArgsConstructor
public class NotebookController {

    private final NotebookService notebookService;

    /** 新增/修改笔记本 */
    @PostMapping("/save")
    public Result<Void> save(@Valid @RequestBody NotebookSaveDTO dto) {
        notebookService.save(dto);
        return Result.success();
    }

    /** 获取笔记本树 */
    @GetMapping("/tree")
    public Result<List<NotebookTreeVO>> tree() {
        return Result.success(notebookService.tree());
    }

    /** 删除笔记本 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        notebookService.delete(id);
        return Result.success();
    }
}
