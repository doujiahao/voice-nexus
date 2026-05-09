# 开发流水线使用手册

> 本文档说明本项目的六阶段开发流水线，适用于所有新需求、功能变更、页面开发。
> AI 和人类成员均应遵守本流程。

---

## 一、六阶段全景

```
阶段0        阶段1        阶段2        阶段3        阶段4        阶段5
需求澄清  →  立项提案  →  拆解计划  →  执行代码  →  验收确认  →  归档沉淀
brainstorm  openspec    writing     executing   verificat   openspec
            -propose    -plans      -plans      -before-    -archive
                                                completion  -change
```

---

## 二、每个阶段详解

### 阶段 0：需求澄清（brainstorming）

**触发词**：新功能 / 新需求 / 新页面 / 实现 XX / 加一个

**产出物**：`docs/product-specs/YYYY-MM-DD-<功能名>.md`

产出内容必须包含：
- 背景与目标
- 影响面清单（含后端 / 前端 / 系统配置 / 旁路）
- 不在范围内
- 验收标准

**此阶段不写任何代码。**

---

### 阶段 1：立项提案（openspec-propose）

**触发词**：立项 / 开始开发 / 提案

**产出物**：`openspec/changes/<功能名>/proposal.md` + `tasks.md`

- proposal.md：变更意图（做什么、为什么）
- tasks.md：初步任务拆分（粗粒度）

---

### 阶段 2：拆解计划（writing-plans）

**触发词**：写计划 / 拆任务 / 怎么做

**产出物**：`docs/exec-plans/active/<功能名>.md`

计划文件格式：
```markdown
# 执行计划：<功能名>

**状态**：进行中
**关联提案**：openspec/changes/<提案目录>/
**关联 spec**：docs/product-specs/<spec文件>
**开始日期**：YYYY-MM-DD

## 步骤清单（后端）
- [ ] 步骤1
- [ ] 步骤2

## 步骤清单（前端）
- [ ] 步骤1
- [ ] 步骤2

## 步骤清单（系统配置）
- [ ] 菜单配置
- [ ] 权限编码配置

## 步骤清单（联调验收）
- [ ] 联调测试
- [ ] 验收确认
```

---

### 阶段 3：执行代码（executing-plans）

**触发词**：开始执行 / 动手 / 写代码 / 按计划做

**规则**：
- 按 exec-plans/active/ 的 checklist 逐步执行
- 每步完成立即打勾（✅）
- 每个 checkpoint 暂停等用户确认
- 遇到 bug → 触发 systematic-debugging
- 会话结束前同步更新 `.claude/notes/CURRENT.md`

---

### 阶段 4：验收确认（verification-before-completion）

**触发词**：完成了 / 提交 / 收工 / 验收

**核查内容**：
- 对照 product-specs 的验收标准逐项核查
- 对照影响面清单确认无遗漏
- 未解决问题记入 `docs/exec-plans/tech-debt-tracker.md`

---

### 阶段 5：归档沉淀（openspec-archive-change）

**触发词**：归档 / 关闭提案 / 合并到主分支

**执行动作**：
- `openspec/changes/<功能>/` 移入 `archive/`
- `docs/exec-plans/active/<功能>.md` 移入 `completed/`
- spec delta 合并进 `openspec/specs/`

---

## 三、三类页面变动的流程差异

| 类型 | 描述 | 流程深度 |
|------|------|---------|
| 纯 UI 调整 | 只改样式/文案，不碰接口 | 直接执行，跳过阶段0-1 |
| 功能变更 | 表单字段/接口参数变化 | 阶段0（重点梳理前后端联动）→ 阶段2-4 |
| 全新页面 | 新菜单、新 CRUD 功能 | 完整六阶段，系统配置必须在影响面清单内 |

---

## 四、进度追踪说明

**如何知道当前在做什么？**

```bash
# 查看所有进行中的执行计划
ls docs/exec-plans/active/

# 查看当前会话状态
cat .claude/notes/CURRENT.md

# 查看活跃的 openspec 提案
ls openspec/changes/
```

**每次会话开始，AI 自动读取以上三处，秒恢复上下文。**

---

## 五、常见问题

**Q：brainstorming 和 product-specs 有什么区别？**
A：brainstorming 是过程（对话澄清），product-specs 是结果（结构化文档）。每次 brainstorming 结束后保存到 product-specs。

**Q：openspec/changes 和 exec-plans 重复了吗？**
A：不重复。openspec 管"提案意图"，exec-plans 管"执行进度"。前者是决策记录，后者是 checklist。

**Q：新会话怎么快速恢复进度？**
A：读 `.claude/notes/CURRENT.md`（上次进展）+ `docs/exec-plans/active/`（具体 checklist），30 秒内定位到下一步。
