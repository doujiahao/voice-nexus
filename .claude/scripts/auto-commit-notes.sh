#!/usr/bin/env bash
# .claude/scripts/auto-commit-notes.sh
# 在会话结束时自动提交 openspec/changes/ 下的变更
# 注意：.claude/notes/ 已被 gitignore，仅为本地记忆，不提交到远程
# 设计原则：
#   - 只提交"提案类"内容，绝不触碰业务代码
#   - 失败静默（|| true），不阻塞 Claude 退出
#   - commit message 自动带时间戳与简要 diff stat

set -e

cd "$(git rev-parse --show-toplevel 2>/dev/null)" || exit 0

CHANGED=0

# openspec/changes 目录若存在变化也一起带上（提案是共享内容）
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

git commit -m "chore(spec): 自动落盘 openspec 变更 ${TS}

${STAT}

Co-Authored-By: Claude Code <noreply@anthropic.com>" \
  --no-verify >/dev/null 2>&1 || true

echo "📝 已自动提交 openspec 变更：${STAT}"
