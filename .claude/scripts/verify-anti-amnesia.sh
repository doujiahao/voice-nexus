#!/usr/bin/env bash
# verify-anti-amnesia.sh
# 自动化验收"反失忆基础设施"安装是否到位
#
# 用法：
#   cd /path/to/your-project
#   bash .claude/scripts/verify-anti-amnesia.sh
#
# 退出码：
#   0  全部通过
#   1  存在 ❌（必须修复）
#   2  存在 ⚠（建议修复，不阻塞）

set -u
IFS=$'\n\t'
LANG=en_US.UTF-8
LC_ALL=en_US.UTF-8
cd "$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

# === 颜色 ===
G='\033[0;32m'; R='\033[0;31m'; Y='\033[1;33m'; B='\033[0;34m'; C='\033[0;36m'; N='\033[0m'
PASS=0; FAIL=0; WARN=0

pass() { echo -e "  ${G}✅${N} $1"; PASS=$((PASS+1)); }
fail() { echo -e "  ${R}❌${N} $1"; FAIL=$((FAIL+1)); }
warn() { echo -e "  ${Y}⚠ ${N} $1"; WARN=$((WARN+1)); }
title(){ echo ""; echo -e "${C}━━━ $1 ━━━${N}"; }

echo ""
echo -e "${B}════════════════════════════════════════════════${N}"
echo -e "${B}  反失忆基础设施验收 · $(date '+%Y-%m-%d %H:%M')${N}"
echo -e "${B}  项目：$(pwd)${N}"
echo -e "${B}════════════════════════════════════════════════${N}"

# === 验收 1：文件结构 ===
title "1/6  文件结构完整性"
for f in CLAUDE.md .gitignore .claude/settings.json \
         .claude/notes/CURRENT.md .claude/notes/BACKLOG.md \
         .claude/notes/DECISIONS.md .claude/notes/GOTCHAS.md \
         .claude/scripts/auto-commit-notes.sh \
         .claude/scripts/init-anti-amnesia.sh; do
  if [ -e "$f" ]; then pass "$f"; else fail "缺失 $f"; fi
done

# === 验收 2：CLAUDE.md 三段协议 ===
title "2/6  CLAUDE.md 协议段落"
if [ -f CLAUDE.md ]; then
  for seg in "会话启动协议" "会话结束协议" "Skill 强制触发规则"; do
    if grep -q "$seg" CLAUDE.md; then pass "包含「${seg}」"; else fail "缺「${seg}」段落"; fi
  done
  if grep -q "ANTI_AMNESIA_BEGIN" CLAUDE.md; then
    pass "marker（ANTI_AMNESIA_BEGIN）存在"
  else
    warn "无 marker，二次 init 可能重复追加"
  fi
else
  fail "CLAUDE.md 不存在，跳过本节"
fi

# === 验收 3：.gitignore 反向白名单 ===
title "3/6  .gitignore 反向白名单"
if [ -f .gitignore ]; then
  if grep -q "ANTI_AMNESIA_BEGIN" .gitignore; then
    pass "marker 存在"
  else
    warn ".gitignore 无 marker，可能未通过 SOP 注入"
  fi
  # 应被放行
  for f in .claude/notes/CURRENT.md .claude/settings.json; do
    if [ -e "$f" ]; then
      if git check-ignore -q "$f" 2>/dev/null; then
        fail "$f 被忽略（应放行）"
      else
        pass "$f 已放行（团队共享）"
      fi
    fi
  done
  # 应被忽略
  touch .claude/settings.local.json.test 2>/dev/null
  if git check-ignore -q .claude/settings.local.json 2>/dev/null \
     || grep -q "settings.local.json" .gitignore; then
    pass ".claude/settings.local.json 配置为忽略（个人配置）"
  else
    warn "settings.local.json 未在 .gitignore 中显式排除"
  fi
  rm -f .claude/settings.local.json.test
else
  fail ".gitignore 不存在"
fi

# === 验收 4：硬性依赖 ===
title "4/6  硬性依赖检测"
if [ -d "$HOME/.claude/skills/using-superpowers" ]; then
  pass "superpowers 已安装"
else
  fail "缺 superpowers（~/.claude/skills/using-superpowers）"
  echo -e "     ${Y}→${N} 安装方式见 https://github.com/obra/superpowers"
fi

if command -v openspec >/dev/null 2>&1; then
  pass "openspec CLI 已安装（$(openspec --version 2>/dev/null || echo "version unknown")）"
else
  fail "缺 openspec CLI"
  echo -e "     ${Y}→${N} npm i -g @fission-ai/openspec"
fi

if [ -d openspec ]; then
  pass "项目已 openspec init"
else
  fail "项目未 openspec init"
  echo -e "     ${Y}→${N} openspec init --tools claude"
fi

# === 验收 5：脚本可执行性 ===
title "5/6  脚本可执行性"
for s in .claude/scripts/auto-commit-notes.sh .claude/scripts/init-anti-amnesia.sh; do
  if [ -x "$s" ]; then
    pass "$s 可执行"
  elif [ -f "$s" ]; then
    warn "$s 未加 +x，运行 chmod +x 修复"
  else
    fail "$s 不存在"
  fi
done

# 语法检查
for s in .claude/scripts/auto-commit-notes.sh .claude/scripts/init-anti-amnesia.sh; do
  if [ -f "$s" ]; then
    if bash -n "$s" 2>/dev/null; then
      pass "$(basename $s) 语法正确"
    else
      fail "$(basename $s) 语法错误"
    fi
  fi
done

# 干跑 auto-commit（笔记无变更时应静默退出 0）
if [ -x .claude/scripts/auto-commit-notes.sh ]; then
  if bash .claude/scripts/auto-commit-notes.sh >/dev/null 2>&1; then
    pass "auto-commit-notes.sh 干跑退出码 0"
  else
    warn "auto-commit-notes.sh 干跑非 0 退出，检查 git 状态"
  fi
fi

# === 验收 6：JSON 配置 ===
title "6/6  配置文件合法性"
if [ -f .claude/settings.json ]; then
  if python3 -m json.tool .claude/settings.json >/dev/null 2>&1; then
    pass ".claude/settings.json 合法"
  else
    fail ".claude/settings.json JSON 损坏"
  fi
  # 检查关键 hook 是否齐全
  for hook in SessionStart UserPromptSubmit Stop; do
    if grep -q "\"$hook\"" .claude/settings.json; then
      pass "hook「${hook}」已配置"
    else
      warn "hook「${hook}」未配置，部分自动化失效"
    fi
  done
else
  fail ".claude/settings.json 不存在"
fi

# === 总结 ===
echo ""
echo -e "${B}════════════════════════════════════════════════${N}"
echo -e "  通过：${G}${PASS}${N}   警告：${Y}${WARN}${N}   失败：${R}${FAIL}${N}"
echo -e "${B}════════════════════════════════════════════════${N}"

if [ "$FAIL" -gt 0 ]; then
  echo -e "${R}❌ 验收未通过，请按上述提示修复后重跑。${N}"
  echo ""
  echo "下一步行为级验证（需人工 + Claude Code 配合）："
  echo "  - 重开会话，看是否主动报告恢复结果"
  echo "  - 说「今天到这」，看是否触发结束协议"
  echo "  - 说「修 bug」「实现 xx 功能」，看是否调用对应 skill"
  exit 1
elif [ "$WARN" -gt 0 ]; then
  echo -e "${Y}⚠  全部硬性检查通过，但存在建议修复项。${N}"
  echo ""
  echo "可继续行为级验证（需人工 + Claude Code 配合）："
  echo "  1. 重开会话验证启动协议"
  echo "  2. 说「今天到这」验证结束协议"
  echo "  3. 说「修 bug」验证 skill 触发"
  exit 2
else
  echo -e "${G}✅ 全部静态检查通过！${N}"
  echo ""
  echo "请继续行为级验证（需人工 + Claude Code 配合）："
  echo "  1. 重开会话 → 应主动报告 5 行恢复摘要"
  echo "  2. 说「今天到这」→ 应自动落盘并 commit"
  echo "  3. 说「修 bug」→ 应触发 systematic-debugging skill"
  echo "  4. 跨天恢复 → 应精准报告昨天的状态"
  exit 0
fi
