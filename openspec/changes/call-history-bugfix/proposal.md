## Why

话务历史记录页前后端共 15 个 bug，涵盖数据正确性（重复摘要/标签写入、并发创建重复行）、查询性能（N+1 导致 61 条 SQL/页）、功能缺失（日期筛选完全失效、错误状态不可见）和边界体验问题。不修复会导致线上数据污染和用户操作不可靠。

## What Changes

- endSession 加幂等检查：status == ENDED 时直接返回，防止重复 NLP 摘要和 call_tag 插入
- 列表查询 N+1 改 JOIN：一条 LEFT JOIN SQL 替代循环内 3 次查询（customer_name / agent_name / turn_count）
- 时间范围过滤生效：QueryWrapper 补上 between(createTime, startTime, endTime)
- 创建 session 加 Redis 分布式锁：以 fsCallId 为 key，防止并发创建重复行
- CallSession 实体加 @Version 乐观锁：防止 ANSWERED/CALL_ENDED 并发覆盖状态
- 前端 useCallHistory 返回 error ref，列表组件展示加载失败提示
- 前端 updateRecordNote 乐观更新加回滚：API 失败时还原旧值并提示
- Recovery 补全 Redis 队列清理 + 触发异步摘要
- pendingRingingTimeout 在 ANSWERED 到达时取消，避免推送过时弹窗
- 前端 fetchList/fetchMore 加互斥标志防竞态
- 前端滚动刷新加下拉阈值，避免意外触发
- 前端去重类型定义，统一到一处
- 列表 UI 区分呼入/呼出方向，不再硬编码"用户来电"
- customer_name 字段在列表中渲染
- 确认/补充 fs_call_id 数据库索引

## Capabilities

### New Capabilities
- `session-idempotency`: 通话会话生命周期关键操作（创建/结束）的幂等保障
- `call-history-query-perf`: 话务历史列表查询性能优化（JOIN 替代 N+1 + 索引保障）
- `call-history-error-handling`: 前端错误状态暴露与乐观更新回滚

### Modified Capabilities

## Impact

- **Java 后端**：CallSessionServiceImpl（幂等+锁）、CallHistoryController/Mapper（JOIN+过滤）、CallSession 实体（@Version）、RecoveryService、pendingRingingTimeout 逻辑
- **前端 (Vue3)**：useCallHistory composable（error ref+回滚+竞态）、CallListItem 组件（方向+customer_name）、类型定义合并、滚动刷新阈值
- **数据库**：确认/补充 fs_call_id 索引
- **兼容性**：无 breaking 变更，API 响应结构不变
