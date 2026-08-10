package com.knowledge.note.module.innerlink.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 笔记双向链接控制器
 * V0.1：链接解析由笔记保存时自动触发，无需独立接口，此处保留占位。
 * V0.2 可新增链接查询接口。
 */
@RestController
@RequestMapping("/api/innerlink")
public class InnerLinkController {
    // TODO: V0.2 新增链接查询/补全接口
}
