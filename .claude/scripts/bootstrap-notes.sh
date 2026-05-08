#!/usr/bin/env bash
# bootstrap-notes.sh
# 在 SessionStart 时自动创建 .claude/notes/ 目录和空白笔记文件
# 设计原则：幂等，已存在则跳过，不覆盖任何现有内容

NOTES_DIR="$(git rev-parse --show-toplevel 2>/dev/null)/.claude/notes"

# 不在 git 仓库中则跳过
[ -z "$NOTES_DIR" ] && exit 0

mkdir -p "$NOTES_DIR"

# 初始化空白 CURRENT.md
if [ ! -f "$NOTES_DIR/CURRENT.md" ]; then
cat > "$NOTES_DIR/CURRENT.md" << 'TMPL'
# 当前工作状态

最后更新：（待 Claude 在首次会话结束时刷新）

## 正在做什么

- 项目刚 clone，等待首个业务任务

## 关键决策

- 暂无

## 下次会话第一件事

- 告知业务需求，进入正式开发

## 风险 / 阻塞

- 暂无
TMPL
fi

# 初始化空白 BACKLOG.md
if [ ! -f "$NOTES_DIR/BACKLOG.md" ]; then
cat > "$NOTES_DIR/BACKLOG.md" << 'TMPL'
# 讨论 Backlog

> 记录识别到但尚未深入展开的话题，下次会话优先处理

## 待展开话题

- （暂无）
TMPL
fi

# 初始化空白 DECISIONS.md
if [ ! -f "$NOTES_DIR/DECISIONS.md" ]; then
cat > "$NOTES_DIR/DECISIONS.md" << 'TMPL'
# 重大决策日志

> 追加模式，永不删除

## 决策记录

- （暂无）
TMPL
fi

# 初始化空白 GOTCHAS.md
if [ ! -f "$NOTES_DIR/GOTCHAS.md" ]; then
cat > "$NOTES_DIR/GOTCHAS.md" << 'TMPL'
# 踩坑记录

> 记录遇到的坑，避免重复犯错

## 踩坑列表

- （暂无）
TMPL
fi

exit 0
