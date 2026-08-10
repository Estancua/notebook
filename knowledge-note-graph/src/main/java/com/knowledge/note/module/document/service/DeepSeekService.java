package com.knowledge.note.module.document.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DeepSeekService {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url}")
    private String baseUrl;

    @Value("${deepseek.model}")
    private String model;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 调用 DeepSeek API 的通用方法
     */
    private String callApi(String systemPrompt, String userMessage) {
        try {
            String url = baseUrl + "/chat/completions";

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userMessage)
                    ),
                    "temperature", 0.3,
                    "max_tokens", 4096
            );

            String json = objectMapper.writeValueAsString(requestBody);

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("DeepSeek API 返回错误: {} {}", response.code(), response.message());
                    throw new RuntimeException("DeepSeek API 调用失败: " + response.code());
                }
                String responseBody = response.body() != null ? response.body().string() : "";
                JsonNode root = objectMapper.readTree(responseBody);
                return root.path("choices").get(0).path("message").path("content").asText();
            }
        } catch (Exception e) {
            log.error("DeepSeek API 调用失败", e);
            throw new RuntimeException("LLM 调用失败: " + e.getMessage());
        }
    }

    /**
     * 解析文档结构为章节 JSON
     * 输入：文档全文文本
     * 输出：JSON 数组 [{title: "第1章 xxx", level: 1, content: "...", children: [{title: "1.1 xxx", level: 2, content: "..."}]}]
     */
    public String parseDocumentStructure(String fullText) {
        String systemPrompt = "你是一个专业的文档结构分析助手。请分析以下文档内容，提取章节结构。对于每个章节/小节，请提供：章节标题、层级(1-6，对应Markdown标题级别)、该章节的全文内容。请以JSON格式返回，格式为：[{\"title\": \"章节标题\", \"level\": 1, \"content\": \"章节内容\", \"children\": [...]}]。只返回JSON，不要其他说明。";

        // 截取前 8000 字符避免 token 超限
        String truncated = fullText.length() > 8000 ? fullText.substring(0, 8000) + "..." : fullText;
        return callApi(systemPrompt, truncated);
    }

    /**
     * 为一小节内容生成思维导图 Markdown
     */
    public String generateMindmap(String sectionTitle, String sectionContent) {
        String systemPrompt = "你是一个专业的思维导图生成助手。请根据提供的文档小节内容，生成一个结构化的 Markdown 思维导图。使用 # ## ### 表示层级关系。提取核心概念、关键点和逻辑关系。内容要精炼有条理。";

        String truncated = sectionContent.length() > 5000 ? sectionContent.substring(0, 5000) + "..." : sectionContent;
        String userMessage = "标题：" + sectionTitle + "\n\n内容：\n" + truncated;
        return callApi(systemPrompt, userMessage);
    }
}
