#!/usr/bin/env bash
# .claude/scripts/auto-commit-notes.sh
# 在会话结束时自动提交 .claude/notes/ 与 openspec/changes/ 下的变更
# 设计原则：
#   - 只提交"笔记类"内容，绝不触碰业务代码
#   - 失败静默（|| true），不阻塞 Claude 退出
#   - commit message 自动带时间戳与简要 diff stat

set -e

cd "$(git rev-parse --show-toplevel 2>/dev/null)" || exit 0

# 仅 stage 笔记目录（白名单严格控制）
PATHS_TO_STAGE=(
  ".claude/notes/CURRENT.md"
  ".claude/notes/BACKLOG.md"
  ".claude/notes/DECISIONS.md"
  ".claude/notes/GOTCHAS.md"
)

CHANGED=0
for p in "${PATHS_TO_STAGE[@]}"; do
  if [ -f "$p" ] && ! git diff --quiet -- "$p" 2>/dev/null; then
    git add -- "$p"
    CHANGED=1
  fi
done

# openspec/changes 目录若存在变化也一起带上（提案是笔记的一种）
if [ -d "openspec/changes" ]; then
  if ! git diff --quiet -- openspec/changes 2>/dev/null \
     || [ -n "$(git ls-files --others --exclude-standard openspec/changes)" ]; then
    git add openspec/changes
    CHANGED=1
  fi
fi

if [ "$CHANGED" -eq 0 ]; then
  exit 0
fi

TS=$(date '+%Y-%m-%d %H:%M')
STAT=$(git diff --cached --shortstat | sed 's/^ *//')

git commit -m "chore(notes): 自动落盘会话状态 ${TS}

${STAT}

Co-Authored-By: Claude Code <noreply@anthropic.com>" \
  --no-verify >/dev/null 2>&1 || true

echo "📝 已自动提交笔记变更：${STAT}"
