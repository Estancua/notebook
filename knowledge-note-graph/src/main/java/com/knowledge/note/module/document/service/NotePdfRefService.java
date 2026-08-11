package com.knowledge.note.module.document.service;

import com.knowledge.note.module.document.entity.NotePdfRef;

import java.util.List;

public interface NotePdfRefService {

    List<NotePdfRef> listByNoteId(Long noteId);

    NotePdfRef saveOrUpdate(NotePdfRef ref);

    void delete(Long id);

    NotePdfRef getById(Long id);
}
