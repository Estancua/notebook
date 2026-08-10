个人知识图谱笔记系统完整方案文档
项目名称：knowledge‑note‑graph
开发模式：多 Agent 流水线迭代，链路：方案设计 Agent → 产品设计 Agent → 自动开发 Agent → 自动测试 Agent
迭代版本：V0.1（基础笔记能力）→ V0.2（图谱 + 向量 + 智能问答，交付版）
定位：类印象笔记笔记管理；图谱为后台自动派生视图；对外提供 API 做私有笔记智能检索问答，回答附带笔记溯源；MySQL 为唯一可信数据源。
目录
项目概述
整体系统架构
多 Agent 迭代执行流程
版本范围划分 V0.1 / V0.2
后端 SpringBoot 工程完整目录结构
前端 Vue3 工程完整目录结构
doc 文档目录产出物
存储设计（含完整 DDL）
核心模块职责说明
核心 API 契约
智能知识问答内部执行流程
非功能约束
交付输出物清单
1. 项目概述
业务目标
实现类印象笔记个人笔记系统：笔记本分类、Markdown 笔记编辑、标签、笔记双向链接[[笔记标题]]、思维导图（放射状可视化，基于笔记父子层级）、收藏、回收站软删除。
知识图谱为派生视图：笔记新增 / 修改 / 删除触发后台异步任务，自动构建笔记‑标签‑实体‑引用关系；图谱页面仅做浏览、节点跳转笔记，图谱不提供编辑能力，不能反向修改笔记源数据，支持一键全量重建图谱。
向量语义检索（ES dense_vector） + 全文关键词搜索 + 图谱关联增强召回；对外提供 HTTP API，输入自然语言问题，基于本地历史笔记给出解答，返回回答 + 溯源笔记列表；无相关资料明确告知知识库无记录。
全部能力对外暴露接口，支持第三方调用智能知识检索能力。
技术栈
后端：SpringBoot 3.2.x、MyBatis‑Plus、Spring Async 异步任务
业务存储：MySQL 8.0（唯一可信源数据）
图存储：Neo4j（派生图谱视图）
向量 + 全文检索：Elasticsearch 8.x（笔记分段向量索引 + 关键词全文搜索）
Embedding 模型：默认 bge-large-zh-v1.5（1024 维），支持通过配置替换其他模型
LLM：支持配置多个大模型 API（OpenAI 兼容协议），运行时通过前端切换选择；支持独立开关
前端：Vue3 + ECharts‑Graph
2. 整体系统架构
plaintext
┌─────────────────────┐
│ 前端Vue3应用        │
│ 笔记编辑｜笔记本树  │
│ 图谱浏览｜智能问答  │
└──────────┬──────────┘
           │HTTP
┌──────────▼──────────────────────────────────┐
│ SpringBoot后端应用                          │
│ ├─基础业务模块：notebook / note / tag / innerlink │
│ ├─graph模块：异步增量更新、全量重建Neo4j图谱      │
│ ├─vector模块：ES向量增量更新、向量召回、全文搜索   │
│ └─knowledgeqa模块【对外API入口】                 │
│    向量召回 + 图谱增强 + 全文搜索 + LLM生成回答   │
└───┬───────────┬────────────┬───────────────┘
    │           │            │
┌───▼───┐   ┌───▼───┐   ┌────▼────────────┐
│ MySQL │   │ Neo4j │   │ Elasticsearch   │
│源数据 │   │派生视图│   │向量索引+全文检索│
└───────┘   └───────┘   └─────────────────┘

3. Multi‑Agent 迭代执行流程
每一轮迭代完整执行 4 个阶段 Agent 流水线
方案设计 Agent：技术选型、模块划分、存储方案、API 边界、风险约束、版本范围划定；输出技术方案文档。
产品设计 Agent：业务场景拆解、功能清单、业务流程、输入输出约束；输出 PRD 产品需求。
自动开发 Agent：读取技术方案 + PRD，生成后端工程、实体、mapper、service、controller、前端骨架、数据库脚本、配置文件。
自动测试 Agent：单元测试、接口自动化测试；缺陷分级：严重 / 一般 / 建议；输出测试缺陷报告。
迭代流转：
启动迭代 1 → 输出 V0.1 工程与报告 → 缺陷报告作为迭代 2 输入
启动迭代 2，完整再跑一遍 Agent 流水线，修复 V0.1 缺陷，新增图谱、向量、问答能力 → V0.2 交付版本。
4. 版本范围划分 V0.1 / V0.2
V0.1 迭代一（MVP 基础笔记）
✅ 笔记本文件夹（支持嵌套、排序）、笔记 CRUD、Markdown 存储、标签管理
✅ 笔记思维导图：基于笔记父子层级关系，前端使用放射状可视化布局（simple-mind-map），支持节点拖拽、新增子节点、缩放平移、点击跳转笔记
✅ 笔记双向链接[[笔记标题]]解析存储（基于 noteId，标题修改后链接不丢失）
✅ 笔记收藏、回收站软删除（含 deleted_at 时间戳）恢复永久删除
✅ 笔记列表分页查询、基础条件查询；完整 CRUD 接口
✅ 工程创建 graph /vector/knowledgeqa 空模块占位，不实现业务逻辑
✅ MySQL 建表脚本、Neo4j 初始化脚本
❌ 图谱、向量、ES、智能问答全部推迟 V0.2
❌ 图片上传推迟 V0.2
V0.2 迭代二（增强交付版）
修复 V0.1 全部严重缺陷，优先修复一般缺陷。
graph 模块：实现异步增量更新图谱、LLM 实体抽取（独立开关控制）、全量重建图谱、子图查询接口；前端图谱浏览页面。
vector 模块：ES 集成，笔记变更增量更新向量索引、全量重建向量索引、向量相似度召回 + 关键词全文搜索。
knowledgeqa 模块：实现智能知识检索问答对外 API；向量 + 全文 + 图谱联合召回；多模型 LLM 适配器（支持模型切换）；回答附带笔记溯源。
前端新增图谱视图、智能问答面板（含模型选择下拉）；可开关 LLM，关闭后降级只返回匹配笔记列表。
图片上传接口，Markdown 图片通过 URL 引用。
5. 后端 SpringBoot 工程完整目录结构
plaintext
knowledge‑note‑graph/
├── pom.xml
├── README.md
├── doc/                                 # 迭代配套文档
├── src/
│   ├── main/
│   │   ├── java/com/knowledge/note/
│   │   │   ├── KnowledgeNoteGraphApplication.java
│   │   │   ├── common/
│   │   │   │   ├── constant/
│   │   │   │   ├── exception/
│   │   │   │   │   ├── BusinessException.java
│   │   │   │   │   └── GlobalExceptionHandler.java
│   │   │   │   └── result/
│   │   │   │       └── Result.java
│   │   │   ├── module/
│   │   │   │   ├── notebook/                 # 笔记本模块
│   │   │   │   │   ├── controller/
│   │   │   │   │   ├── dto/
│   │   │   │   │   ├── entity/
│   │   │   │   │   ├── mapper/
│   │   │   │   │   └── service/
│   │   │   │   ├── note/                     # 笔记核心模块
│   │   │   │   │   ├── controller/
│   │   │   │   │   ├── dto/
│   │   │   │   │   ├── entity/
│   │   │   │   │   ├── mapper/
│   │   │   │   │   └── service/
│   │   │   │   ├── tag/                      # 标签模块
│   │   │   │   │   ├── controller/
│   │   │   │   │   ├── dto/
│   │   │   │   │   ├── entity/
│   │   │   │   │   ├── mapper/
│   │   │   │   │   └── service/
│   │   │   │   ├── innerlink/                # 笔记双向链接（基于 noteId）
│   │   │   │   │   ├── controller/
│   │   │   │   │   ├── dto/
│   │   │   │   │   ├── entity/
│   │   │   │   │   ├── mapper/
│   │   │   │   │   └── service/
│   │   │   │   ├── graph/                    # Neo4j图谱模块（派生视图）
│   │   │   │   │   ├── config/
│   │   │   │   │   ├── controller/
│   │   │   │   │   ├── dto/
│   │   │   │   │   ├── entity/
│   │   │   │   │   ├── repository/
│   │   │   │   │   └── service/
│   │   │   │   │       ├── GraphAsyncService.java
│   │   │   │   │       ├── GraphRebuildService.java
│   │   │   │   │       └── EntityExtractService.java  # LLM实体抽取（独立开关）
│   │   │   │   ├── vector/                   # Elasticsearch向量+全文检索模块
│   │   │   │   │   ├── config/
│   │   │   │   │   │   └── ElasticsearchConfig.java
│   │   │   │   │   ├── dto/
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   └── NoteVectorRepository.java  # ES客户端操作
│   │   │   │   │   └── service/
│   │   │   │   │       ├── VectorAsyncService.java
│   │   │   │   │       ├── VectorRebuildService.java
│   │   │   │   │       └── EmbeddingService.java      # 嵌入模型调用
│   │   │   │   ├── knowledgeqa/              # 智能问答对外API模块
│   │   │   │   │   ├── controller/
│   │   │   │   │   ├── dto/
│   │   │   │   │   └── service/
│   │   │   │   │       ├── impl/
│   │   │   │   │       │   └── KnowledgeQaServiceImpl.java
│   │   │   │   │       └── adapter/
│   │   │   │   │           ├── LlmAdapter.java          # LLM适配器接口
│   │   │   │   │           └── OpenAiCompatibleAdapter.java  # OpenAI兼容实现
│   │   │   │   └── file/                     # 文件上传模块（V0.2）
│   │   │   │       ├── controller/
│   │   │   │       └── service/
│   │   │   ├── task/
│   │   │   │   ├── AsyncTaskConfig.java      # Spring异步线程池配置
│   │   │   │   └── RetryableAsyncTask.java   # 异步任务重试封装
│   │   │   └── util/
│   │   │       ├── MarkdownParseUtil.java
│   │   │       └── TextSplitUtil.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application‑dev.yml
│   │       ├── db/
│   │       │   ├── mysql/init.sql
│   │       │   ├── neo4j/init.cypher
│   │       │   └── elasticsearch/init_index.json
│   │       └── mybatis‑plus/
│   └── test/java/com/knowledge/note/         # 单元、接口测试代码

6. 前端 Vue3 工程完整目录结构
plaintext
knowledge‑note‑web/
├── package.json
├── README.md
├── src/
│   ├── api/
│   │   ├── noteApi.js
│   │   ├── notebookApi.js
│   │   ├── tagApi.js
│   │   ├── graphApi.js
│   │   ├── knowledgeQaApi.js
│   │   └── fileApi.js
│   ├── assets/
│   ├── components/
│   │   ├── NoteEditor.vue
│   │   ├── NotebookTree.vue
│   │   ├── TagSelect.vue
│   │   ├── GraphViewer.vue
│   │   ├── KnowledgeQaPanel.vue
│   │   ├── MindMapViewer.vue       # 思维导图可视化组件（V0.1）
│   │   ├── LlmModelSelect.vue        # 大模型选择器
│   │   └── ImageUpload.vue           # 图片上传组件（V0.2）
│   ├── views/
│   │   ├── Home.vue
│   │   ├── NoteEdit.vue
│   │   ├── RecycleBin.vue
│   │   ├── GraphView.vue
│   │   └── KnowledgeQa.vue
│   ├── router/index.js
│   └── main.js

7. doc 文档目录产出物
plaintext
knowledge‑note‑graph/doc/
├── iteration_v01/
│   ├── 01_技术方案.md
│   ├── 02_PRD产品设计.md
│   └── 03_测试缺陷报告.md
├── iteration_v02/
│   ├── 01_技术方案.md
│   ├── 02_PRD产品设计.md
│   └── 03_测试缺陷报告.md
└── api_doc.md

8. 存储设计（含完整 DDL）
8.1 MySQL（唯一源数据）
8.1.1 notebook 笔记本表
sql
CREATE TABLE notebook (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    parent_id   BIGINT DEFAULT 0          COMMENT '父笔记本ID，0为根',
    name        VARCHAR(128) NOT NULL     COMMENT '笔记本名称',
    sort_order  INT DEFAULT 0             COMMENT '排序号，越小越靠前',
    created_at  DATETIME DEFAULT NOW()    COMMENT '创建时间',
    updated_at  DATETIME DEFAULT NOW() ON UPDATE NOW() COMMENT '更新时间',
    INDEX idx_parent (parent_id)
) COMMENT '笔记本（支持嵌套）';

8.1.2 note 笔记表
sql
CREATE TABLE note (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    notebook_id   BIGINT NOT NULL            COMMENT '归属笔记本ID',
    parent_note_id BIGINT DEFAULT NULL       COMMENT '父笔记ID（思维导图层级，NULL=根/中心节点）',
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

8.1.3 tag 标签表
sql
CREATE TABLE tag (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    name       VARCHAR(64) NOT NULL UNIQUE  COMMENT '标签名称',
    color      VARCHAR(8) DEFAULT ''        COMMENT '标签颜色（hex）',
    created_at DATETIME DEFAULT NOW()       COMMENT '创建时间'
) COMMENT '标签';

8.1.4 note_tag_rel 笔记-标签关联表
sql
CREATE TABLE note_tag_rel (
    id      BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    note_id BIGINT NOT NULL COMMENT '笔记ID',
    tag_id  BIGINT NOT NULL COMMENT '标签ID',
    UNIQUE KEY uk_note_tag (note_id, tag_id),
    INDEX idx_note (note_id),
    INDEX idx_tag (tag_id)
) COMMENT '笔记-标签关联';

8.1.5 note_inner_link 笔记双向链接表（基于 noteId）
sql
CREATE TABLE note_inner_link (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    source_note_id BIGINT NOT NULL COMMENT '源笔记ID（包含[[xxx]]的笔记）',
    target_note_id BIGINT NOT NULL COMMENT '目标笔记ID（被引用的笔记，解析时按标题匹配到id）',
    target_title  VARCHAR(256) NOT NULL     COMMENT '目标笔记标题快照（用于日志和调试）',
    created_at    DATETIME DEFAULT NOW()    COMMENT '首次引用时间',
    UNIQUE KEY uk_source_target (source_note_id, target_note_id),
    INDEX idx_target (target_note_id)
) COMMENT '笔记双向链接（保存时解析[[笔记标题]]，匹配标题找到target_note_id后写入）';

设计要点：笔记保存时，MarkdownParseUtil 扫描 content 中的 [[xxx]] 模式，按 xxx 在 note 表中匹配标题，找到对应 noteId，写入 source_note_id → target_note_id。标题修改时，依赖此 noteId 的链接不丢失。

8.1.6 异步任务脏数据标记表（增量更新补偿机制）
sql
CREATE TABLE sync_dirty_flag (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    note_id     BIGINT NOT NULL UNIQUE         COMMENT '需要重新同步的笔记ID',
    sync_type   VARCHAR(16) NOT NULL           COMMENT 'GRAPH / VECTOR / BOTH',
    retry_count INT DEFAULT 0                  COMMENT '重试次数',
    max_retry   INT DEFAULT 3                  COMMENT '最大重试次数',
    created_at  DATETIME DEFAULT NOW()         COMMENT '标记时间',
    next_retry_at DATETIME                     COMMENT '下次重试时间'
) COMMENT '增量同步失败补偿标记';

8.2 Neo4j（派生视图，可全量重建）
节点标签：
Note：noteId、title
Tag：tagId、name
Entity：LLM 抽取概念实体，name（实体抽取由独立开关控制）
关系：
REFERENCE：Note → Note 笔记互相引用（基于 note_inner_link 表）
HAS_TAG：Note → Tag 笔记打标签
MENTION：Note → Entity 笔记提及实体（可选，开关控制）

8.3 Elasticsearch（派生索引，可全量重建）
索引名：note_index
mapping 核心字段：
json
{
  "mappings": {
    "properties": {
      "note_id":    { "type": "long" },
      "title":      { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
      "chunk_text": { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
      "chunk_index":{ "type": "integer" },
      "vector":     { "type": "dense_vector", "dims": 1024, "index": true, "similarity": "cosine" }
    }
  }
}

文本分段策略：chunk_size=512 字符，overlap=64 字符。
向量维度：1024（bge-large-zh-v1.5 默认维度），通过配置可调整。
一条笔记可能对应多条 ES 文档（按 chunk 分段）。

9. 核心模块职责说明
notebook：笔记本文件夹 CRUD，树形结构管理，支持排序。
note：笔记增删改查、收藏、回收站软删除恢复；保存时同步解析 [[笔记标题]] 生成 noteId 双向链接；保存完成触发异步事件（图谱更新、向量更新）；图片通过独立上传接口获取 URL，Markdown 中以 ![alt](url) 引用。
tag：标签维护，笔记多标签绑定，支持颜色标记。
innerlink：markdown 解析，提取 [[笔记标题]]，按标题匹配 note 表获取 noteId，持久化双向链接（source_note_id → target_note_id）。
mindmap：思维导图模块。后端维护笔记父子层级关系（parent_note_id），提供完整树/子树/移动 API。前端使用 simple-mind-map 库渲染放射状思维导图，支持节点拖拽重排、新增子节点、编辑标题、缩放平移、点击跳转笔记。
graph：无图谱编辑接口；监听笔记变更，异步增量更新 Neo4j；LLM 实体抽取由独立开关控制；提供全量重建、子图查询、全图查询接口。
vector：笔记文本分段（chunk_size=512, overlap=64）、调用 Embedding 服务向量化；增量更新 ES 索引；全量重建；向量相似度召回 + 关键词 BM25 全文搜索。
knowledgeqa：对外智能问答入口。联合向量召回 + 全文搜索 + 图谱扩展，去重排序后送入 LLM 生成回答。
LlmAdapter：封装多模型调用，支持运行时切换（通过前端选择模型名称，后端按名称路由到对应 API 配置）。
EmbeddingService：封装嵌入模型调用，支持配置替换不同 Embedding 模型。
task：Spring Async 异步配置 + 重试机制，处理不阻塞主请求的图谱、向量更新任务；失败任务写入 sync_dirty_flag 表，定时任务补偿重试。
重要约束：图谱与向量全部是派生数据；源数据只存在 MySQL；提供重建接口，异常时一键恢复。

10. 核心 API 契约
统一返回体
json
{
  "code": 200,
  "msg": "success",
  "data": {}
}

分页请求参数（所有列表接口统一）
| 参数   | 类型 | 默认值 | 说明       |
|--------|------|--------|------------|
| page   | int  | 1      | 页码       |
| size   | int  | 20     | 每页条数   |
| keyword| str  | -      | 关键词搜索 |

笔记基础接口（V0.1）
POST   /api/notebook/save              笔记本新增 / 修改
GET    /api/notebook/tree              获取笔记本树
DELETE /api/notebook/{id}              删除笔记本（需下方无笔记）
POST   /api/note/save                  保存笔记（触发异步更新图谱、ES）
GET    /api/note/list                  笔记列表（支持 notebookId 筛选、收藏筛选、关键词搜索）
GET    /api/note/{id}                  获取笔记详情（含标签、正反向链接笔记列表）
DELETE /api/note/{id}                  软删除移入回收站（设置 is_deleted=1, deleted_at=now）
POST   /api/note/batch-recycle         批量移入回收站
POST   /api/note/{id}/recover          从回收站恢复
DELETE /api/note/{id}/permanent        永久删除（同时清理图谱节点、ES索引）
POST   /api/note/{id}/favorite         切换收藏状态
POST   /api/tag/save                   标签保存
GET    /api/tag/list                   标签列表
DELETE /api/tag/{id}                   删除标签

思维导图接口（V0.1，后端通过 knowledge-tree 路径暴露，供前端思维导图组件消费）
GET    /api/knowledge-tree                 获取完整层级数据（用于构建思维导图）
GET    /api/knowledge-tree/roots           获取根节点列表（parent_note_id IS NULL）
POST   /api/knowledge-tree/move            移动节点（id + newParentId，校验防止循环引用）
GET    /api/knowledge-tree/{noteId}        获取子树

全文搜索接口（V0.2）
GET    /api/search/notes              关键词全文搜索笔记（ES BM25 + 向量混合）

图谱查询接口（仅查询，无编辑）（V0.2）
GET    /api/graph/fullGraph            获取全部图谱节点和边，供前端可视化
GET    /api/graph/subGraph/{noteId}    获取以某笔记为中心的子图（关联笔记、标签、实体）
POST   /api/graph/rebuild              全量重建图谱
GET    /api/graph/entity/list          获取实体列表

ES 重建接口（V0.2）
POST   /api/vector/rebuild             全量重建向量索引

文件上传接口（V0.2）
POST   /api/file/upload                上传图片，返回访问 URL

LLM 模型配置接口（V0.2）
GET    /api/llm/models                 获取已配置的模型列表
POST   /api/llm/switch                 切换当前使用的模型

【核心对外智能问答 API】（V0.2）
POST /api/knowledge/qa
请求体
json
{
  "query": "需要提问的自然语言问题",
  "modelName": "gpt-4"  // 可选，不传使用默认模型
}

响应 data
json
{
  "answer": "基于自有笔记生成的回答，关闭LLM此字段为空",
  "referenceNotes": [
    {"noteId": 1, "title": "笔记标题", "snippet": "匹配片段摘要"}
  ]
}

11. 智能知识问答内部执行流程
接收用户 query 问题及可选 modelName。
问题向量化（调用 EmbeddingService），ES 做向量相似度召回（k-NN），同时执行 BM25 关键词全文搜索，合并去重得到候选笔记 noteId 集合。
使用候选 noteId 去 Neo4j 图谱查询，扩展召回关联笔记（REFERENCE 关系）、关联标签（HAS_TAG）、关联实体（MENTION），扩大候选集合。
综合去重、相关性排序（向量相似度 + BM25 得分 + 图谱关系权重），截断取 Top-N，拿到参考笔记完整内容。
判断 LLM 开关：
开启：根据 modelName 选择对应 LLM 适配器，将检索到的笔记片段作为上下文送入 LLM，约束模型只能基于提供笔记素材作答，禁止模型通用知识编造；整理 answer + referenceNotes 返回；无相关内容时明确提示"知识库暂无相关记录"。
关闭：不调用大模型，仅返回 referenceNotes 笔记列表 + 片段摘要，answer 为空。
12. 非功能约束
单用户本地部署，无需登录鉴权，所有接口无认证直接访问。
MySQL 为唯一可信源；Neo4j、ES 索引允许随时清空全量重建。
笔记保存接口同步只做数据库落盘 + 双向链接解析；图谱更新、向量更新走异步，不阻塞前端保存响应。
异步任务失败写入 sync_dirty_flag 表，定时任务（每5分钟）扫描重试，超过 max_retry 次后告警日志记录。
问答输出必须溯源；知识库没有记录则明确提示，禁止 LLM 编造答案。
图谱模块不提供任何新增、修改、删除节点关系的 HTTP 接口，全部由笔记业务侧驱动。
LLM 实体抽取功能独立开关控制，关闭后图谱不生成 Entity 节点和 MENTION 关系。
13. 交付输出物清单
SpringBoot 完整后端工程源码
Vue3 前端完整源码
MySQL 建表初始化脚本 db/mysql/init.sql
Neo4j 初始化脚本 db/neo4j/init.cypher
Elasticsearch 索引映射脚本 db/elasticsearch/init_index.json
application.yml 配置模板（含 ES、Neo4j、多 LLM 配置示例）
工程根目录 README.md 部署运行文档
doc 全套迭代文档：V0.1、V0.2 技术方案、PRD、测试缺陷报告
api_doc.md 接口文档
到此完整文档结束，可直接给到多 Agent 流水线作为输入，执行 V0.1、V0.2 两轮迭代开发。

执行的每一步及其结果都记录到"D:\codefile\新建文件夹\biji\log.md"中