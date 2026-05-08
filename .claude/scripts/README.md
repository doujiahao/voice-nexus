# Claude Code 反失忆基础设施 SOP

> 一键初始化 Claude Code 团队共享记忆体系。
> 解决跨会话遗忘、长会话腐烂、精确召回不够、团队知识断层、长期个性化沉淀五大失忆痛点。

---

## 快速开始

### 硬性前置依赖

本 SOP 假设以下两套技能体系已安装，强烈推荐在 init 前先就绪：

```bash
# 1. superpowers（通用工作流 skills 集合）
#    安装方式见上游：https://github.com/obra/superpowers

# 2. openspec CLI
npm i -g @fission-ai/openspec

# 3. 在目标项目根目录初始化 openspec
cd /path/to/your-project
openspec init --tools claude
```

init 脚本会自动检测这两项，缺失时会警告并继续；但 Skill 强制触发规则依赖它们才能生效。

### 在新项目中初始化

```bash
# 1. 把整个 .claude/scripts/ 目录拷到目标项目
cp -r <本项目>/.claude/scripts /path/to/your-project/.claude/

# 2. 进入目标项目根目录
cd /path/to/your-project

# 3. 跑初始化脚本
bash .claude/scripts/init-anti-amnesia.sh

# 4. 检查输出，提交到 git
git add CLAUDE.md .gitignore .claude/
git commit -m "chore: 接入反失忆基础设施"
```

### 二次执行（幂等）

脚本可重复执行，已存在的内容会跳过：
- `.claude/notes/*` 已存在 → 跳过（保护现有数据）
- `CLAUDE.md` 已含 `ANTI_AMNESIA_BEGIN` marker → 跳过追加
- `.gitignore` 已含 `ANTI_AMNESIA_BEGIN` marker → 跳过追加
- `.claude/settings.json` 已存在 → 提示手动 diff（JSON 自动合并不安全）

---

## 验收

### 一键自动验收（静态检查 6 大类 29 项）

```bash
bash .claude/scripts/verify-anti-amnesia.sh
```

退出码：
- `0`：全部通过 ✅
- `1`：存在 ❌（必须修复）
- `2`：存在 ⚠（建议修复，不阻塞）

`init-anti-amnesia.sh` 末尾会自动调用一次 verify，无需手动跑。

### 行为级验收（需开 Claude Code 配合）

静态检查通过后，再人工验四件事：

1. **重开会话** → Claude 应主动报告 5 行恢复摘要（上次进展 / 当前任务 / 待办 / 风险 / 下一步）
2. **说「今天到这」** → Claude 应自动落盘 CURRENT.md 并 commit
3. **说「修 bug」「实现 xx 功能」** → 应触发对应 skill（systematic-debugging / brainstorming）
4. **跨天恢复** → 第二天开会话只说"你好"，应精准报告昨天的状态

---

## 安装内容清单

| 文件 | 作用 |
|---|---|
| `CLAUDE.md`（追加） | 启动协议 + 结束协议 + Skill 强制触发规则 + 记忆架构图 |
| `.gitignore`（追加） | 反向白名单：团队共享 `.claude/` 部分内容，排除个人 `settings.local.json` |
| `.claude/settings.json` | 三套 hooks：SessionStart 提示自检 / UserPromptSubmit 检测结束词 / Stop 自动 commit |
| `.claude/notes/CURRENT.md` | 当前任务状态，每次会话结束自动更新 |
| `.claude/notes/DECISIONS.md` | 重大决策日志，追加模式永不删除 |
| `.claude/notes/GOTCHAS.md` | 踩坑记录，避免重复犯错 |
| `.claude/scripts/auto-commit-notes.sh` | Stop hook 调用，自动提交笔记变更 |

---

## 设计原理

### 三层联动机制

```
┌─────────────────────────────────────────────────────────┐
│ 1. CLAUDE.md 启动协议                                   │
│    每次新会话自动加载 → 提示 Claude 执行 5 步开机自检    │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ 2. .claude/notes/CURRENT.md                             │
│    充当"交接班日志"，会话间无缝续接                      │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ 3. .claude/scripts/auto-commit-notes.sh + Stop hook    │
│    会话结束自动提交 → 跨设备同步 → 历史可追溯           │
└─────────────────────────────────────────────────────────┘
```

### 团队共享 vs 个人配置隔离

`.gitignore` 用反向白名单规则精确控制：

```gitignore
.claude            # ① 整体忽略
!.claude/          # ② 但放行目录本身
.claude/*          # ③ 默认忽略所有内容
!.claude/notes/    # ④ 反向放行 notes（团队共享）
!.claude/scripts/  # ④ 反向放行 scripts（团队共享）
!.claude/settings.json   # ④ 放行团队 hooks 配置
.claude/settings.local.json  # ⑤ 个人权限白名单永不入库
```

**为什么需要这么复杂**：直接 `.claude/notes/`（不带前置 `.claude` 整体忽略）会受到全局规则干扰；先整体忽略再反向白名单是 Git 推荐的精确控制模式。

### 幂等性保证

所有合并操作通过 marker（`ANTI_AMNESIA_BEGIN` / `ANTI_AMNESIA_END`）检测：
- 已合并 → 跳过
- 未合并且文件存在 → 备份 `.bak` 后追加
- 文件不存在 → 直接创建

---

## 抽取到独立仓库（未来）

当前 SOP 安于 `<project>/.claude/scripts/`，便于"克隆即用"。当稳定后可整体抽取：

```bash
# 1. 创建独立仓库
git init claude-anti-amnesia
cd claude-anti-amnesia

# 2. 拷贝整个 scripts 目录的内容（不含 .claude/scripts 前缀）
cp -r /path/to/old-project/.claude/scripts/* .

# 3. 团队成员使用方式
git clone <repo-url> /tmp/claude-aa
cd /path/to/new-project
bash /tmp/claude-aa/init-anti-amnesia.sh
```

或包装成 npm 包 / Homebrew formula 后 `npx` / `brew` 一键调用。

---

## 故障排查

### 启动协议没生效

1. 检查 `~/.claude/settings.json` 是否禁用了 hooks
2. 重启 Claude Code 让 SessionStart hook 重新注册
3. 直接 Read 一次 CLAUDE.md 看是否包含"会话启动协议"段

### auto-commit 没触发

1. 确认 `.gitignore` 反向规则正确（跑 `git check-ignore -v .claude/notes/CURRENT.md` 应返回放行规则）
2. 检查脚本可执行：`ls -la .claude/scripts/auto-commit-notes.sh`
3. 手动跑一次：`bash .claude/scripts/auto-commit-notes.sh`

### settings.json 已存在，跳过了合并

`init-anti-amnesia.sh` 不会自动合并 JSON。需手动 diff：
```bash
diff .claude/settings.json .claude/scripts/anti-amnesia-template/settings.json
```
按需把 hooks 段并入现有配置。

---

## 维护

### 升级模板

修改 `.claude/scripts/anti-amnesia-template/` 下的文件，再跑一次 init 脚本即可（marker 检测保证幂等）。

如果需要强制更新已合并的段落：手动删除项目中带 `ANTI_AMNESIA_BEGIN`/`END` 的段落，再跑 init。

### 版本历史

- v1.0 (2026-05-08)：初版，含 CLAUDE.md / settings.json / notes / auto-commit / gitignore 五件套
