#!/usr/bin/env bash
# init-anti-amnesia.sh
# 在当前项目搭建"反失忆基础设施"
#
# 用法：
#   cd /path/to/your-project
#   bash <path-to>/init-anti-amnesia.sh
#
# 设计原则：
#   - 幂等：可重复执行，二次运行不会破坏已有内容
#   - 智能合并：已有文件用 marker 检测、增量 patch
#   - 零依赖：纯 bash + grep/sed
#   - 安全：所有覆盖前自动 .bak 备份

set -e

# === 路径解析 ===
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TPL_DIR="$SCRIPT_DIR/anti-amnesia-template"
PROJECT_ROOT="$(pwd)"

# === 颜色输出 ===
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

info()  { echo -e "${BLUE}[INFO]${NC} $1"; }
ok()    { echo -e "${GREEN}[OK]${NC}   $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
err()   { echo -e "${RED}[ERR]${NC}  $1" >&2; }

# === 前置检查 ===
[ -d "$TPL_DIR" ] || { err "模板目录不存在：$TPL_DIR"; exit 1; }
[ -d "$PROJECT_ROOT/.git" ] || warn "当前目录不是 git 仓库，auto-commit 钩子将无效"

# === 依赖技能体系检测（硬性要求） ===
DEPS_MISSING=0
if [ ! -d "$HOME/.claude/skills/using-superpowers" ]; then
  warn "未检测到 superpowers（~/.claude/skills/using-superpowers 不存在）"
  warn "  → 安装方式见 https://github.com/obra/superpowers"
  DEPS_MISSING=1
fi
if ! command -v openspec >/dev/null 2>&1; then
  warn "未检测到 openspec CLI"
  warn "  → 运行：npm i -g @fission-ai/openspec"
  DEPS_MISSING=1
fi
if [ ! -d "$PROJECT_ROOT/openspec" ]; then
  warn "当前项目尚未 openspec 初始化"
  warn "  → 在项目根目录运行：openspec init --tools claude"
  DEPS_MISSING=1
fi
if [ "$DEPS_MISSING" -eq 1 ]; then
  echo ""
  warn "==================================================="
  warn "项目硬性要求 superpowers + openspec 已就位，强烈建议先解决上述缺失项"
  warn "继续运行将完成基础设施搭建，但 Skill 强制触发规则需依赖技能体系才能生效"
  warn "==================================================="
  echo ""
fi

echo ""
info "目标项目：$PROJECT_ROOT"
info "模板来源：$TPL_DIR"
echo ""

# === 步骤 1：创建 .claude/ 目录骨架 ===
info "步骤 1/5  创建 .claude/ 目录骨架"
mkdir -p "$PROJECT_ROOT/.claude/notes" "$PROJECT_ROOT/.claude/scripts"
ok "目录已就绪"

# === 步骤 2：拷贝 notes 三件套（仅当不存在时）===
info "步骤 2/5  初始化 .claude/notes/"
for f in CURRENT.md BACKLOG.md DECISIONS.md GOTCHAS.md; do
  src="$TPL_DIR/notes/$f"
  dst="$PROJECT_ROOT/.claude/notes/$f"
  if [ -f "$dst" ]; then
    warn "已存在 $dst —— 跳过（避免覆盖）"
  else
    cp "$src" "$dst"
    ok "已创建 .claude/notes/$f"
  fi
done

# === 步骤 3：拷贝 auto-commit-notes.sh 与 verify-anti-amnesia.sh ===
info "步骤 3/5  部署脚本"
cp "$TPL_DIR/auto-commit-notes.sh" "$PROJECT_ROOT/.claude/scripts/auto-commit-notes.sh"
chmod +x "$PROJECT_ROOT/.claude/scripts/auto-commit-notes.sh"
ok "已部署 .claude/scripts/auto-commit-notes.sh"
if [ -f "$TPL_DIR/verify-anti-amnesia.sh" ]; then
  cp "$TPL_DIR/verify-anti-amnesia.sh" "$PROJECT_ROOT/.claude/scripts/verify-anti-amnesia.sh"
  chmod +x "$PROJECT_ROOT/.claude/scripts/verify-anti-amnesia.sh"
  ok "已部署 .claude/scripts/verify-anti-amnesia.sh（验收脚本）"
fi
# 把 init 脚本自身也部署进去，便于二次执行 / 团队成员复用
if [ "$SCRIPT_DIR" != "$PROJECT_ROOT/.claude/scripts" ]; then
  cp "${BASH_SOURCE[0]}" "$PROJECT_ROOT/.claude/scripts/init-anti-amnesia.sh"
  chmod +x "$PROJECT_ROOT/.claude/scripts/init-anti-amnesia.sh"
  # 同时拷贝模板目录，让项目自包含、可作为下个项目的源
  cp -r "$TPL_DIR" "$PROJECT_ROOT/.claude/scripts/anti-amnesia-template"
  ok "已部署 .claude/scripts/init-anti-amnesia.sh + 模板目录（项目自包含）"
fi

# === 步骤 4：合并 settings.json ===
info "步骤 4/5  配置 .claude/settings.json"
SETTINGS_DST="$PROJECT_ROOT/.claude/settings.json"
if [ -f "$SETTINGS_DST" ]; then
  warn "已存在 $SETTINGS_DST"
  warn "  请手动 diff 模板与现有配置：$TPL_DIR/settings.json"
  warn "  自动合并 JSON 不安全，已跳过"
else
  cp "$TPL_DIR/settings.json" "$SETTINGS_DST"
  ok "已创建 .claude/settings.json"
fi

# === 步骤 5a：合并 CLAUDE.md ===
info "步骤 5/5  合并 CLAUDE.md 与 .gitignore"
CLAUDE_MD="$PROJECT_ROOT/CLAUDE.md"
APPEND_SRC="$TPL_DIR/CLAUDE.md.append"

if [ -f "$CLAUDE_MD" ] && grep -q "ANTI_AMNESIA_BEGIN" "$CLAUDE_MD"; then
  warn "CLAUDE.md 已包含反失忆协议段（ANTI_AMNESIA_BEGIN marker）—— 跳过"
elif [ -f "$CLAUDE_MD" ]; then
  cp "$CLAUDE_MD" "$CLAUDE_MD.bak"
  cat "$APPEND_SRC" >> "$CLAUDE_MD"
  ok "已追加反失忆协议到 CLAUDE.md（备份在 CLAUDE.md.bak）"
else
  # 项目无 CLAUDE.md，创建一个最小骨架 + 协议段
  {
    echo "# CLAUDE.md"
    echo ""
    echo "This file provides guidance to Claude Code when working with code in this repository."
    echo ""
    echo "## 项目概述"
    echo ""
    echo "（请在此处补充项目说明）"
    echo ""
    cat "$APPEND_SRC"
  } > "$CLAUDE_MD"
  ok "已创建 CLAUDE.md（含反失忆协议）"
fi

# === 步骤 5b：合并 .gitignore ===
GITIGNORE="$PROJECT_ROOT/.gitignore"
GITIGNORE_SRC="$TPL_DIR/gitignore.snippet"

if [ -f "$GITIGNORE" ] && grep -q "ANTI_AMNESIA_BEGIN" "$GITIGNORE"; then
  warn ".gitignore 已包含反失忆白名单段 —— 跳过"
elif [ -f "$GITIGNORE" ]; then
  cp "$GITIGNORE" "$GITIGNORE.bak"
  # 若已有"裸 .claude"忽略规则，保留它（反向规则要求先有忽略再放行）
  echo "" >> "$GITIGNORE"
  cat "$GITIGNORE_SRC" >> "$GITIGNORE"
  ok "已追加反失忆白名单到 .gitignore（备份在 .gitignore.bak）"
else
  cp "$GITIGNORE_SRC" "$GITIGNORE"
  ok "已创建 .gitignore（含反失忆白名单）"
fi

# === 完成报告 ===
echo ""
echo "════════════════════════════════════════════════"
ok "反失忆基础设施初始化完成 ✨"
echo "════════════════════════════════════════════════"
echo ""
echo "已创建/更新："
echo "  ├─ CLAUDE.md                          （启动/结束协议、Skill 触发规则）"
echo "  ├─ .gitignore                         （团队共享 vs 个人配置白名单）"
echo "  ├─ .claude/settings.json              （SessionStart/Stop/UserPromptSubmit hooks）"
echo "  ├─ .claude/notes/CURRENT.md           （当前任务状态，每次会话末更新）"
echo "  ├─ .claude/notes/BACKLOG.md           （待展开话题清单）"
echo "  ├─ .claude/notes/DECISIONS.md         （决策日志，追加永不删）"
echo "  ├─ .claude/notes/GOTCHAS.md           （踩坑记录）"
echo "  └─ .claude/scripts/auto-commit-notes.sh （Stop hook 自动提交脚本）"
echo ""
echo "下一步："
echo "  1. 检查 CLAUDE.md / .gitignore 的 .bak 备份是否需要保留"
echo "  2. git add 上述文件并提交"
echo "  3. 重开一次 Claude Code 会话，验证启动协议生效"
echo ""
echo "════════════════════════════════════════════════"
echo "自动运行验收脚本..."
echo "════════════════════════════════════════════════"
if [ -x "$PROJECT_ROOT/.claude/scripts/verify-anti-amnesia.sh" ]; then
  bash "$PROJECT_ROOT/.claude/scripts/verify-anti-amnesia.sh" || true
fi
