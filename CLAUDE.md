# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## 项目概述

（请在此处补充项目说明）

---

## 🚀 首次使用说明（新成员必读）

clone 本仓库后，在开始使用 Claude Code 之前，请按以下步骤完成环境初始化：

### 第一步：安装 Claude Code

```bash
npm install -g @anthropic-ai/claude-code
```

### 第二步：安装 superpowers 技能包

superpowers 是本项目强制依赖的工作流 skills 集合，提供 brainstorming / TDD / debugging 等能力：

```bash
# 方式一：通过 Claude Code 安装（推荐）
claude mcp add superpowers

# 方式二：手动克隆
git clone https://github.com/obra/superpowers ~/.claude/skills/using-superpowers
```

### 第三步：安装 openspec CLI

openspec 用于结构化管理变更提案，本项目已初始化：

```bash
npm i -g @fission-ai/openspec
```

### 第四步：验证安装

```bash
ls ~/.claude/skills/using-superpowers   # 应有输出
command -v openspec && echo OK           # 应输出 OK
ls openspec/                             # 应看到 changes/ specs/ 目录
```

### 第五步：开启 Claude Code

```bash
claude
```

首次启动时，`SessionStart` hook 会自动：
- 创建 `.claude/notes/` 本地记忆目录（不提交到 git）
- 触发会话启动协议，输出项目状态摘要

> **注意**：`.claude/notes/` 是你的**本地私有记忆**，不会推送到远程，每个人独立维护。

---

<!-- ANTI_AMNESIA_BEGIN -->
---

## 会话启动协议（强制执行 · 中度自检）

每次新会话开始，Claude 必须按顺序执行以下步骤，未完成前禁止编写任何代码：

0. **环境前置检查**（缺失则中止业务工作并提示用户安装）：
   - Bash `ls ~/.claude/skills/using-superpowers 2>/dev/null` → 验证 superpowers 已装
   - Bash `command -v openspec >/dev/null && echo OK` → 验证 openspec CLI 已装
   - Bash `[ -d openspec ] && echo INITIALIZED` → 验证当前项目已 init
   - 三项任意缺失：在「报告恢复结果」中标红提示，并阻止"新增/修复/重构"类任务
1. **读取当前任务状态**：Read `.claude/notes/CURRENT.md`
2. **读取讨论 backlog**（若用户提到"接着聊 / 讨论 / 那个话题"等）：Read `.claude/notes/BACKLOG.md`
3. **读取活跃提案**（若 openspec 已初始化）：
   - Bash `ls openspec/changes/ 2>/dev/null`
   - 若有未归档目录，Read 各 `proposal.md` 与 `tasks.md`
4. **回顾近期提交**：Bash `git log --oneline -10`
5. **报告恢复结果**：用 5 行内中文摘要输出
   - 上次进展：…
   - 当前任务：…
   - 待办事项：…
   - 风险/阻塞：…
   - 建议下一步：…
6. **等待用户确认**后再继续

## 会话结束协议（强制执行 · 自动落盘）

会话即将结束（用户说"结束/收工/下班/today done/明天再说"，或 Stop hook 触发）时，Claude 必须：

1. **更新 `.claude/notes/CURRENT.md`**，包含：
   - `最后更新`：YYYY-MM-DD HH:MM
   - `正在做什么`：当前任务一句话 + 已完成 / 进行中 / 未开始
   - `关键决策`：本次会话产生的重要决定（追加，不覆盖历史）
   - `下次会话第一件事`：明确到可执行命令或文件
   - `风险/阻塞`：未解决问题
2. **如有重大决策**，追加到 `.claude/notes/DECISIONS.md`（追加模式，永不删除）
3. **如踩坑**，追加到 `.claude/notes/GOTCHAS.md`
4. 最后用一句话告知用户："状态已落盘到 .claude/notes/CURRENT.md"

## Skill 强制触发规则

### 一、硬性前置依赖（不可省略）

本项目**必须**安装并使用以下两套技能体系。会话启动时若检测到缺失，必须立即提示用户安装，不得跳过：

| 技能体系 | 用途 | 安装/检测命令 | 缺失时的动作 |
|---|---|---|---|
| **superpowers** | 通用工作流 skills 集合（brainstorming / TDD / planning / debugging 等） | `ls ~/.claude/skills/using-superpowers 2>/dev/null` | 提示用户：「未检测到 superpowers，请先安装」并停止业务工作 |
| **openspec** | 结构化变更提案与 spec delta 管理 | `command -v openspec && ls openspec/ 2>/dev/null` | 若工具未装：`npm i -g @fission-ai/openspec`；若仅项目未 init：`openspec init --tools claude` |

**铁律**：未通过上述检测前，禁止开展任何"新增功能 / 修复 bug / 重构"类业务任务。
回答纯咨询、读文档、查代码不受此限。

### 二、按意图强制调用的 skill

为避免"忘记调用 skill"，按下表强制触发，不再依赖 Claude 临场判断：

| 用户意图关键词 | 必须调用的 Skill | 来源 |
|---|---|---|
| 继续 / 接着昨天 / 恢复 / 还在做 | openspec-context-loading | openspec |
| 实现 / 开发 / 加功能 / 新需求 | brainstorming → openspec-propose | superpowers + openspec |
| 修 bug / 报错 / 不对 / 异常 | systematic-debugging | superpowers |
| 重构 / 优化 / 整理 | simplify | superpowers |
| 写测试 / TDD | test-driven-development | superpowers |
| 完成 / 提交 / 收工 | verification-before-completion | superpowers |
| 多个独立子任务 | dispatching-parallel-agents | superpowers |
| 写规划 / 列计划 | writing-plans → executing-plans | superpowers |
| 提交前 review | requesting-code-review | superpowers |
| 归档已完成的 openspec 变更 | openspec-archive-change | openspec |

## 文件分层记忆架构

```
~/.claude/CLAUDE.md              个人全局偏好（语言、风格、硬规则）
<project>/CLAUDE.md              本文件 — 项目规范 + 协议 + Skill 规则
<project>/.claude/notes/
  ├── CURRENT.md                 当前任务状态（每次会话末更新）
  ├── BACKLOG.md                 待展开话题清单（识别但未深入的讨论）
  ├── DECISIONS.md               重大决策日志（追加，永不删）
  └── GOTCHAS.md                 踩坑记录（避免重复犯错）
<project>/openspec/changes/      结构化变更提案（按 openspec skill 管理）
```

## 团队决策分级与同步规则

### 判断框架：两问定级

遇到任何决策时，先问两个问题：

> ① 这个决策影响的人数 ≥ 2 人吗？  
> ② 这个决策一旦反悔，要改动 ≥ 2 个文件 / 流程吗？

| 两问结果 | 级别 | 落在哪里 |
|---|---|---|
| 两问都是「否」 | 个人临时想法 | 本地 `.claude/notes/DECISIONS.md`（私有，不入库） |
| 任意一问是「是」 | 小决策 | `openspec/changes/` proposal.md（入库，团队共享） |
| 两问都是「是」 | 重大决策 | 本文件（CLAUDE.md）专属 section（入库，Claude 每次会话自动感知） |

### 执行时机辅助规则

- 写代码**之前**产生的想法 → 先放私有 `DECISIONS.md` 草稿
- 写代码**完成后**还站得住脚 → 升级到 openspec proposal 或本文件
- **被团队 review 过**的决策 → 一定写入本文件

### 已落定的重大决策

| 时间 | 决策内容 | 原因 |
|---|---|---|
| 2026-05-08 | `.claude/notes/` 本地私有，不入库 | 个人笔记避免污染公共仓库 |
| 2026-05-08 | `openspec/changes/` 入库共享 | 功能提案需团队可见 |
| 2026-05-09 | 记忆基础设施放在 `feat/claude-memory-infra` 分支实验，不合入 master | 方案尚未确定为最佳路径，保持 master 干净 |
<!-- ANTI_AMNESIA_END -->
