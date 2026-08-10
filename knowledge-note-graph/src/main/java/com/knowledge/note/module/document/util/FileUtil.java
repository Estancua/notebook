package com.knowledge.note.module.document.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
public class FileUtil {

    /**
     * 保存上传文件到指定目录
     */
    public static String saveFile(MultipartFile file, String dir) throws IOException {
        Path uploadDir = Paths.get(dir);
        Files.createDirectories(uploadDir);
        String fileName = file.getOriginalFilename();
        Path targetPath = uploadDir.resolve(fileName);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        return targetPath.toString();
    }

    /**
     * 删除文件
     */
    public static boolean deleteFile(String path) {
        try {
            Path filePath = Paths.get(path);
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("删除文件失败: {}", path, e);
            return false;
        }
    }
}
