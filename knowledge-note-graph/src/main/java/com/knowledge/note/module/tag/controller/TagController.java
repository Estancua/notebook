package com.knowledge.note.module.tag.controller;

import com.knowledge.note.common.result.Result;
import com.knowledge.note.module.tag.dto.TagListVO;
import com.knowledge.note.module.tag.dto.TagSaveDTO;
import com.knowledge.note.module.tag.entity.Tag;
import com.knowledge.note.module.tag.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标签控制器
 */
@RestController
@RequestMapping("/api/tag")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    /** 新增/修改标签（返回保存后的 Tag 对象，供前端获取 id） */
    @PostMapping("/save")
    public Result<Tag> save(@Valid @RequestBody TagSaveDTO dto) {
        return Result.success(tagService.save(dto));
    }

    /** 标签列表 */
    @GetMapping("/list")
    public Result<List<TagListVO>> list() {
        return Result.success(tagService.list());
    }

    /** 删除标签 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return Result.success();
    }
}
