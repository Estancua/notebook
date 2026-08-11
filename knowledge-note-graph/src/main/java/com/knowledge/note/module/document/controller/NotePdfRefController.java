package com.knowledge.note.module.document.controller;

import com.knowledge.note.common.result.Result;
import com.knowledge.note.module.document.entity.NotePdfRef;
import com.knowledge.note.module.document.service.NotePdfRefService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/note-pdf-ref")
@RequiredArgsConstructor
public class NotePdfRefController {

    private final NotePdfRefService notePdfRefService;

    @GetMapping("/list/{noteId}")
    public Result<List<NotePdfRef>> listByNoteId(@PathVariable Long noteId) {
        return Result.success(notePdfRefService.listByNoteId(noteId));
    }

    @PostMapping("/save")
    public Result<NotePdfRef> save(@RequestBody NotePdfRef ref) {
        return Result.success(notePdfRefService.saveOrUpdate(ref));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        notePdfRefService.delete(id);
        return Result.success();
    }
}
