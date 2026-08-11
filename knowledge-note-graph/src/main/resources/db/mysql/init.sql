-- ============================
-- 知识笔记系统 - MySQL 初始化脚本
-- 数据库名: knowledge_note
-- 版本: V0.1
-- ============================

CREATE DATABASE IF NOT EXISTS knowledge_note DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE knowledge_note;

-- 1. 笔记本表（支持嵌套）
DROP TABLE IF EXISTS notebook;
CREATE TABLE notebook (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    parent_id   BIGINT DEFAULT 0          COMMENT '父笔记本ID，0为根',
    name        VARCHAR(128) NOT NULL     COMMENT '笔记本名称',
    sort_order  INT DEFAULT 0             COMMENT '排序号，越小越靠前',
    created_at  DATETIME DEFAULT NOW()    COMMENT '创建时间',
    updated_at  DATETIME DEFAULT NOW() ON UPDATE NOW() COMMENT '更新时间',
    INDEX idx_parent (parent_id)
) COMMENT '笔记本（支持嵌套）';

-- 2. 笔记表
DROP TABLE IF EXISTS note;
CREATE TABLE note (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    notebook_id   BIGINT NOT NULL            COMMENT '归属笔记本ID',
    title         VARCHAR(256) NOT NULL      COMMENT '笔记标题',
    content       LONGTEXT                   COMMENT 'Markdown正文',
    word_count    INT DEFAULT 0              COMMENT '字数统计',
    is_favorite   TINYINT(1) DEFAULT 0       COMMENT '收藏标记 0-否 1-是',
    is_deleted    TINYINT(1) DEFAULT 0       COMMENT '回收站标记 0-正常 1-已删除',
    deleted_at    DATETIME                   COMMENT '删除时间（软删除时间戳）',
    created_at    DATETIME DEFAULT NOW()     COMMENT '创建时间',
    updated_at    DATETIME DEFAULT NOW() ON UPDATE NOW() COMMENT '更新时间',
    INDEX idx_notebook (notebook_id),
    INDEX idx_parent (parent_note_id),
    INDEX idx_deleted (is_deleted, deleted_at),
    INDEX idx_favorite (is_favorite),
    FULLTEXT idx_title_content (title, content) COMMENT 'MySQL原生全文索引（备用）'
) COMMENT '笔记';

-- 3. 标签表
DROP TABLE IF EXISTS tag;
CREATE TABLE tag (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    name       VARCHAR(64) NOT NULL UNIQUE  COMMENT '标签名称',
    color      VARCHAR(8) DEFAULT ''        COMMENT '标签颜色（hex）',
    created_at DATETIME DEFAULT NOW()       COMMENT '创建时间'
) COMMENT '标签';

-- 4. 笔记-标签关联表
DROP TABLE IF EXISTS note_tag_rel;
CREATE TABLE note_tag_rel (
    id      BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    note_id BIGINT NOT NULL COMMENT '笔记ID',
    tag_id  BIGINT NOT NULL COMMENT '标签ID',
    UNIQUE KEY uk_note_tag (note_id, tag_id),
    INDEX idx_note (note_id),
    INDEX idx_tag (tag_id)
) COMMENT '笔记-标签关联';

-- 5. 笔记双向链接表（基于 noteId）
DROP TABLE IF EXISTS note_inner_link;
CREATE TABLE note_inner_link (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    source_note_id BIGINT NOT NULL COMMENT '源笔记ID（包含[[xxx]]的笔记）',
    target_note_id BIGINT NOT NULL COMMENT '目标笔记ID（被引用的笔记，解析时按标题匹配到id）',
    target_title  VARCHAR(256) NOT NULL     COMMENT '目标笔记标题快照（用于日志和调试）',
    created_at    DATETIME DEFAULT NOW()    COMMENT '首次引用时间',
    UNIQUE KEY uk_source_target (source_note_id, target_note_id),
    INDEX idx_target (target_note_id)
) COMMENT '笔记双向链接（保存时解析[[笔记标题]]，匹配标题找到target_note_id后写入）';

-- 6. 异步任务脏数据标记表（增量更新补偿机制）
DROP TABLE IF EXISTS sync_dirty_flag;
CREATE TABLE sync_dirty_flag (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    note_id     BIGINT NOT NULL UNIQUE         COMMENT '需要重新同步的笔记ID',
    sync_type   VARCHAR(16) NOT NULL           COMMENT 'GRAPH / VECTOR / BOTH',
    retry_count INT DEFAULT 0                  COMMENT '重试次数',
    max_retry   INT DEFAULT 3                  COMMENT '最大重试次数',
    created_at  DATETIME DEFAULT NOW()         COMMENT '标记时间',
    next_retry_at DATETIME                     COMMENT '下次重试时间'
) COMMENT '增量同步失败补偿标记';

-- 7. 文档解析结果表（V0.1.5）
DROP TABLE IF EXISTS document_section;
CREATE TABLE document_section (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    notebook_id   BIGINT NOT NULL COMMENT '绑定笔记本ID',
    file_name     VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_path     VARCHAR(500) NOT NULL COMMENT '服务器存储路径',
    file_type     VARCHAR(10) NOT NULL COMMENT 'PDF / DOCX',
    parse_result  LONGTEXT COMMENT '解析结果JSON（章节树结构，保留用于向后兼容）',
    full_text     LONGTEXT COMMENT '全文文本（供LLM分析用）',
    created_at    DATETIME DEFAULT NOW() COMMENT '创建时间',
    updated_at    DATETIME DEFAULT NOW() ON UPDATE NOW() COMMENT '更新时间',
    INDEX idx_notebook (notebook_id)
) COMMENT '文档解析结果（绑定笔记本）';

-- 8. 文档章节表（V0.2 - 每个章节一条记录，支持绑定笔记 + 页码）
DROP TABLE IF EXISTS document_chapter;
CREATE TABLE document_chapter (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    document_id   BIGINT NOT NULL COMMENT '所属文档ID（FK document_section.id）',
    parent_id     BIGINT DEFAULT 0 COMMENT '父章节ID，0=顶级章节',
    title         VARCHAR(500) NOT NULL COMMENT '章节标题',
    level         TINYINT NOT NULL DEFAULT 1 COMMENT '层级 1-6（对应Markdown标题级别）',
    content       LONGTEXT COMMENT '章节正文内容',
    page_start    INT COMMENT '起始页码（null表示未知）',
    page_end      INT COMMENT '结束页码（null表示未知或单页）',
    note_id       BIGINT UNIQUE COMMENT '绑定的笔记ID（一对一，唯一约束；空=未绑定）',
    sort_order    INT DEFAULT 0 COMMENT '同级排序序号',
    created_at    DATETIME DEFAULT NOW() COMMENT '创建时间',
    updated_at    DATETIME DEFAULT NOW() ON UPDATE NOW() COMMENT '更新时间',
    INDEX idx_document (document_id),
    INDEX idx_parent (parent_id),
    INDEX idx_note (note_id)
) COMMENT '文档章节（拆分自document_section.parse_result，支持一对一绑定笔记+页码导航）';

-- 9. 笔记知识点-PDF页码关联表（V0.2 - 脑图节点 ↔ PDF页码 映射）
DROP TABLE IF EXISTS note_pdf_ref;
CREATE TABLE note_pdf_ref (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    note_id       BIGINT NOT NULL COMMENT '所属笔记ID',
    node_uid      VARCHAR(100) NOT NULL COMMENT 'simple-mind-map 节点唯一ID（库自带uid）',
    node_title    VARCHAR(500) COMMENT '节点标题快照（冗余，用于导航面板展示）',
    page_start    INT NOT NULL COMMENT '关联起始页码',
    page_end      INT COMMENT '关联结束页码（null=仅单页）',
    excerpt       VARCHAR(1000) COMMENT '原文摘录（可选，用户手填）',
    created_at    DATETIME DEFAULT NOW() COMMENT '关联时间',
    UNIQUE KEY uk_note_node (note_id, node_uid) COMMENT '同一笔记同一节点只允许一条关联',
    INDEX idx_note (note_id)
) COMMENT '脑图知识点节点 ↔ PDF页码 映射（知识点导航核心表）';

-- 10. OCR 页面缓存表（V0.2.5 - 避免重复OCR识别）
DROP TABLE IF EXISTS page_ocr_cache;
CREATE TABLE page_ocr_cache (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    document_id   BIGINT NOT NULL COMMENT '所属文档ID',
    page_number   INT NOT NULL COMMENT '页码（从1开始）',
    image_base64  LONGTEXT COMMENT '页面渲染图片base64（JPEG）',
    ocr_text      LONGTEXT COMMENT 'OCR识别全文（纯文本，换行分隔）',
    text_lines    LONGTEXT COMMENT 'OCR文本行JSON [{text, x, y, width, height}]，坐标为百分比',
    image_width   INT COMMENT '渲染图片宽度（像素）',
    image_height  INT COMMENT '渲染图片高度（像素）',
    created_at    DATETIME DEFAULT NOW() COMMENT '创建时间',
    UNIQUE KEY uk_doc_page (document_id, page_number)
) COMMENT 'OCR 页面识别结果缓存（按页缓存，避免重复调用火山引擎API）';
