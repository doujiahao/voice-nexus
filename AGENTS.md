# AGENTS.md
# 本文件专为 AI Agent 设计，描述在本项目中的行为协议。
# 人类成员请阅读 CLAUDE.md 和 docs/PIPELINE.md。

## 项目身份

本项目基于 JeecgBoot（Spring Boot + Vue3）的企业级低代码平台二次开发。
前后端分离，后端 Maven 多模块，前端 Vite + Vue3 + Ant Design Vue。

## 六阶段流水线强制规则

接收到用户意图后，必须按以下映射触发对应阶段，不得跳过：

| 用户意图关键词 | 必须执行的阶段 | 对应 Skill |
|---|---|---|
| 新功能 / 新需求 / 新页面 / 实现 XX | 阶段0 需求澄清 | brainstorming |
| 立项 / 提案 / 开始开发 | 阶段1 提案 | openspec-propose |
| 写计划 / 拆任务 | 阶段2 计划 | writing-plans |
| 开始执行 / 动手 / 写代码 | 阶段3 执行 | executing-plans |
| 完成了 / 提交 / 收工 | 阶段4 验收 | verification-before-completion |
| 归档 / 关闭提案 | 阶段5 归档 | openspec-archive-change |

## 页面变动决策树

收到页面相关需求时，先判断类型再决定流程深度：

```
页面变动
├── 只改样式/文案？
│   └── YES → 直接执行，不立提案
├── 改了接口参数/表单字段？
│   └── YES → 中量流程：brainstorming（梳理前后端联动）→ 执行
└── 全新页面/菜单？
    └── YES → 完整六阶段，影响面清单必须包含系统配置
```

## 影响面清单标准模板

每次 brainstorming 产出的 spec 必须包含以下结构：

```markdown
## 影响面清单

### 后端
- Controller:
- Service:
- Mapper/XML:
- DTO/Entity:
- 数据库:

### 前端
- 页面: views/模块名/
- API: api/模块名.js
- 路由: 是否需要新增
- 组件:

### 系统配置（JeecgBoot 特有，最容易漏！）
- [ ] 菜单权限配置（sys_permission 表）
- [ ] 按钮权限编码（查询/新增/编辑/删除/导出）
- [ ] 数据权限（如需）

### 旁路
- 消息通知:
- 日志记录:
- 缓存失效:
```

## 执行计划文件规范

每个执行中的功能在 `docs/exec-plans/active/` 下创建 markdown 文件：

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
- [ ] 权限配置

## 步骤清单（联调验收）
- [ ] 联调测试
- [ ] 验收确认
```

完成后移至 `docs/exec-plans/completed/`。

## 上下文加载顺序（每次会话）

1. `CLAUDE.md` → 项目规范和 Skill 触发规则
2. `AGENTS.md`（本文件）→ AI 行为协议
3. `ARCHITECTURE.md` → 定位代码，不盲目扫描
4. `docs/exec-plans/active/` → 当前进行中的任务
5. `openspec/changes/` → 当前活跃提案
6. `.claude/notes/CURRENT.md` → 上次会话状态

## 代码定位原则

**禁止**：不读 ARCHITECTURE.md 就直接 glob/grep 全量扫描代码。
**要求**：先读 ARCHITECTURE.md 定位到目标模块，再精准 Read 目标文件。

## token 节约规则

- 优先读 `docs/references/` 中的 llms.txt，不重复 WebSearch 已知文档
- 读代码前先确认是否已在 ARCHITECTURE.md 中描述
- 每次会话结束前更新 `.claude/notes/CURRENT.md`，下次会话秒恢复
