package com.knowledge.note.module.document.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.note.module.document.service.VolcanoOcrService;
import com.knowledge.note.module.document.vo.OcrPageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 火山引擎 OCR 服务实现 — 通过 Python 子进程调用 volcengine SDK。
 * 用户已验证 Python SDK 方式可正常工作，Java 只负责进程管理。
 */
@Slf4j
@Service
public class VolcanoOcrServiceImpl implements VolcanoOcrService {

    @Value("${volcano.ak:}")
    private String volcanoAk;

    @Value("${volcano.sk:}")
    private String volcanoSk;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Python 解释器路径 */
    private static final String PYTHON = "python";

    @Override
    public String recognize(String imageBase64) {
        List<OcrPageVO.TextLine> lines = recognizeLines(imageBase64, 0, 0);
        StringBuilder sb = new StringBuilder();
        for (OcrPageVO.TextLine line : lines) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(line.getText());
        }
        return sb.toString();
    }

    @Override
    public List<OcrPageVO.TextLine> recognizeLines(String imageBase64, int imageWidth, int imageHeight) {
        try {
            String inputJson = objectMapper.writeValueAsString(
                    Map.of("image_base64", imageBase64));

            String resultJson = callPythonScript(inputJson);
            JsonNode result = objectMapper.readTree(resultJson);

            if (!result.path("success").asBoolean()) {
                String error = result.path("error").asText("Unknown error");
                log.error("OCR Python script failed: {}", error);
                throw new RuntimeException("OCR 识别失败: " + error);
            }

            JsonNode lineTexts = result.path("line_texts");
            JsonNode lineRects = result.path("line_rects");

            List<OcrPageVO.TextLine> lines = new ArrayList<>();
            if (lineTexts.isArray()) {
                for (int i = 0; i < lineTexts.size(); i++) {
                    String text = lineTexts.get(i).asText();
                    if (text == null || text.trim().isEmpty()) continue;

                    OcrPageVO.TextLine line = new OcrPageVO.TextLine();
                    line.setText(text);

                    if (lineRects.isArray() && i < lineRects.size()) {
                        JsonNode rect = lineRects.get(i);
                        double x = rect.path("x").asDouble();
                        double y = rect.path("y").asDouble();
                        double w = rect.path("width").asDouble();
                        double h = rect.path("height").asDouble();
                        if (imageWidth > 0 && imageHeight > 0) {
                            line.setX((x / imageWidth) * 100.0);
                            line.setY((y / imageHeight) * 100.0);
                            line.setWidth((w / imageWidth) * 100.0);
                            line.setHeight((h / imageHeight) * 100.0);
                        } else {
                            line.setX(x); line.setY(y);
                            line.setWidth(w); line.setHeight(h);
                        }
                    } else {
                        line.setX(0); line.setY(i * 2.5);
                        line.setWidth(100); line.setHeight(2.5);
                    }
                    lines.add(line);
                }
            }
            log.info("OCR result: {} lines", lines.size());
            return lines;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Volcano OCR error", e);
            throw new RuntimeException("OCR 识别失败: " + e.getMessage(), e);
        }
    }

    private String callPythonScript(String inputJson) throws IOException, InterruptedException {
        String scriptPath = findScriptPath();

        ProcessBuilder pb = new ProcessBuilder(PYTHON, scriptPath);
        pb.redirectErrorStream(true);
        // 传入 AK/SK 环境变量（从 application.yml 读取）
        Map<String, String> env = pb.environment();
        if (volcanoAk != null && !volcanoAk.isEmpty()) {
            env.put("VOLCANO_AK", volcanoAk);
        }
        if (volcanoSk != null && !volcanoSk.isEmpty()) {
            env.put("VOLCANO_SK", volcanoSk);
        }
        log.debug("Python OCR env: AK={}, SK={}",
                volcanoAk != null && !volcanoAk.isEmpty() ? "set" : "missing",
                volcanoSk != null && !volcanoSk.isEmpty() ? "set" : "missing");
        Process process = pb.start();

        try (OutputStream os = process.getOutputStream()) {
            os.write(inputJson.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }

        int exitCode;
        try {
            boolean finished = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Python OCR script timed out after 60s");
            }
            exitCode = process.exitValue();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new RuntimeException("Python OCR script interrupted", e);
        }
        String outputStr = output.toString();

        if (exitCode != 0) {
            log.error("Python script exit code={} output={}", exitCode, outputStr);
            throw new RuntimeException("Python OCR script failed (exit " + exitCode + "): " + outputStr);
        }

        return outputStr;
    }

    private String findScriptPath() {
        String devPath = "src/main/resources/scripts/volcano_ocr.py";
        if (new File(devPath).exists()) {
            return new File(devPath).getAbsolutePath();
        }
        var classLoader = getClass().getClassLoader();
        var resource = classLoader.getResource("scripts/volcano_ocr.py");
        if (resource != null) {
            String path = resource.getPath();
            if (new File(path).exists()) return path;
            try (InputStream is = resource.openStream()) {
                File tempFile = File.createTempFile("volcano_ocr_", ".py");
                tempFile.deleteOnExit();
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    is.transferTo(fos);
                }
                log.info("Extracted OCR script to temp: {}", tempFile.getAbsolutePath());
                return tempFile.getAbsolutePath();
            } catch (IOException e) {
                throw new RuntimeException("无法提取 volcano_ocr.py", e);
            }
        }
        throw new RuntimeException("找不到 volcano_ocr.py");
    }
}
