// ============================
// 知识笔记系统 - Neo4j 初始化脚本
// 版本: V0.1 —— 仅创建约束占位
// V0.2 将定义 Note/Tag/Entity 节点与关系
// ============================

// 注意：V0.1 不实际创建节点，仅预留约束定义
// 以下为 V0.2 的约束定义（当前注释状态）:
//
// CREATE CONSTRAINT note_id_unique IF NOT EXISTS FOR (n:Note) REQUIRE n.noteId IS UNIQUE;
// CREATE CONSTRAINT tag_id_unique  IF NOT EXISTS FOR (t:Tag)  REQUIRE t.tagId  IS UNIQUE;
// CREATE CONSTRAINT entity_name_unique IF NOT EXISTS FOR (e:Entity) REQUIRE e.name IS UNIQUE;

RETURN 'Neo4j init script for knowledge-note-graph V0.1 - placeholder only';
