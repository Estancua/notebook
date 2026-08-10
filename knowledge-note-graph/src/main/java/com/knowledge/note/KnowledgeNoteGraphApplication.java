package com.knowledge.note;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 知识笔记系统 - 启动类
 * V0.1：基础笔记管理能力（笔记本/笔记/标签/双向链接）
 */
@SpringBootApplication
public class KnowledgeNoteGraphApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeNoteGraphApplication.class, args);
    }
}
