package com.knowledge.note.util;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 解析工具类
 * 用于提取 [[笔记标题]] 双向链接语法
 */
public class MarkdownParseUtil {

    /** [[xxx]] 链接匹配正则 */
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[\\[(.+?)\\]\\]");

    /**
     * 从 Markdown 内容中提取所有 [[xxx]] 中的标题文本
     * @param content Markdown 内容
     * @return 去重后的标题集合
     */
    public static Set<String> extractLinks(String content) {
        Set<String> titles = new LinkedHashSet<>();
        if (content == null || content.isEmpty()) {
            return titles;
        }
        Matcher matcher = LINK_PATTERN.matcher(content);
        while (matcher.find()) {
            String title = matcher.group(1).trim();
            if (!title.isEmpty()) {
                titles.add(title);
            }
        }
        return titles;
    }
}
