package com.knowledge.note.module.document.service;

import com.knowledge.note.module.document.dto.GenerateMindmapDTO;
import com.knowledge.note.module.document.vo.DocumentVO;
import com.knowledge.note.module.document.vo.GenerateMindmapVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {

    DocumentVO upload(MultipartFile file, Long notebookId);

    List<DocumentVO> listByNotebook(Long notebookId);

    void updateParseResult(Long id, String parseResult);

    void delete(Long id);

    String getFullText(Long id);

    String getFilePath(Long id);

    GenerateMindmapVO generateMindmap(Long id, GenerateMindmapDTO dto);
}
