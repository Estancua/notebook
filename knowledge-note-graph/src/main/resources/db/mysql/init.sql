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
