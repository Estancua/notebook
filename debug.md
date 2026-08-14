# Debug：新建导图节点不生成页码信息（badge 不显示）

> 记录日期：2026-08-13
> 涉及模块：knowledge-note-web（NoteEditor / MindMapViewer / PdfPreview）、knowledge-note-graph（NotePdfRef）

## 现象

新建导图节点（PDF 选中文字 → Ctrl+B / 拖拽文字到导图）后，节点上没有 📖 页码角标（badge），点击也无法跳转页码。

## 根因

### 1. 旧构建中节点 uid 与关联 uid 不一致（直接原因）

旧版构建（dist 14:14，3000 端口）中：

- `handleOcrCreateNode` 调 `insertDragNode` 时**不传 uid**，导图节点 uid 由 simple-mind-map 随机生成
- 保存关联时硬编码 `nodeUid: "h2_" + Date.now() + "_" + 随机`

两边 uid 对不上 → `renderBadges` 用 `findNodeElByUid('h2_xxx')` 在节点缓存中找不到 → badge 不渲染。

当前源码已修复（工作区未提交，需 build 后 3000 端口才生效）：

- `NoteEditor.vue` `handleOcrCreateNode`：生成统一 `ocr_` 前缀 uid，创建节点时传入 `insertDragNode({ uid })`
- `MindMapViewer.vue` `insertDragNode`：认 `dragMeta.uid`，节点 uid 与 pdfRef.nodeUid 共用

### 2. 后端 saveOrUpdate 语义陷阱（排查期踩坑，务必注意）

`NotePdfRefServiceImpl.saveOrUpdate` **不是按 id 更新**，而是：

```java
// 按 noteId + nodeUid 查询，命中则只更新 nodeTitle/pageStart/pageEnd/excerpt（nodeUid 不变）
// 未命中则无视传入 id，强制插入新记录
LambdaQueryWrapper<NotePdfRef> wrapper = ...;
wrapper.eq(NotePdfRef::getNoteId, ref.getNoteId());
wrapper.eq(NotePdfRef::getNodeUid, ref.getNodeUid());
```

后果：想用「传 id 改 nodeUid」的脚本更新是无效的——新 uid 会命中已存在的另一条记录，把它的标题等字段覆盖掉；uid 不存在则新增记录。批量更新 nodeUid 只能走直接改库或「删除失效 + 保留有效」两条路。

## 数据修复记录（noteId=24）

- 删除 uid 不在导图 content 中的失效记录：41 条
- 保留有效记录：19 条（nodeUid 与导图节点 uid 全部匹配）
- 修复被误覆盖标题：id=64 恢复为「货币」（原被覆盖为 OCR 噪音文本）

## 隐患提醒（重点，后续需要跟踪）

导图 content 里所有 uid 注释（如 `<!-- uid:h1_1786605592674_xxx -->`）在 2026-08-13 14:39 **同一毫秒**生成，说明当时 content 的标题全部没有 uid 注释，被 `parseMdToTree` 一次性重新赋值。

**只要有操作写出不带 uid 注释的 Markdown，下次解析就会给无 uid 标题生成新 uid，已建的页码关联会再次全部失效。**

已知的无 uid 写入路径：

- `NoteEditor.vue` 编辑/预览模式追加标题：
  ```js
  const heading = `\n\n## ${text.replace(/\n/g, ' ')}`   // 没有 <!-- uid:xxx -->
  content.value = (content.value || '') + heading
  ```
- 章节编辑（inline 编辑标题）等可能重写整个 content 的功能，需逐一确认是否保留 uid 注释

正常链路（保留 uid）参考：

- `MindMapViewer.vue` `parseMdToTree`（119-121 行）：解析行尾 `<!-- uid:xxx -->`，有则复用
- `MindMapViewer.vue` `treeToMarkdown`（201 行）：导出时写回 ` <!-- uid:${node.data.uid} -->`

## 验证方式

1. 用 http://localhost:3001/（新代码 dev server）打开笔记 24，标题节点应显示 📖 badge，点击跳转对应页
2. 新建节点（Ctrl+B / 拖拽）→ 生成 `ocr_` 前缀记录且 badge 立即出现
3. 若页码关联再次丢失：优先检查上述「无 uid 注释写入路径」，确认 content 中标题是否仍有 `<!-- uid:xxx -->`
