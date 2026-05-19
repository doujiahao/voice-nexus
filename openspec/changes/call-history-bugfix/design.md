## Context

话务历史页面（CallHistoryList + useCallHistory）存在 15 个前后端 bug，核心问题集中在：
- **数据正确性**：`endSession` 无幂等检查，WS hangup 和 FS CALL_ENDED 两条路径可重复触发 NLP 摘要 + 标签插入；`createInboundSession` 和 `handleEvent(RINGING)` 两处 selectOne+insert 无锁，并发可创建重复行
- **查询性能**：`CallController.listCalls` 在 stream.map 内循环调用 `customerMapper.selectById` / `agentProfileMapper.selectById` / `callTurnMapper.selectCount`，page_size=20 时产生 1+60=61 条 SQL
- **功能缺失**：`startTime`/`endTime` 参数接收了但未应用到 QueryWrapper
- **前端健壮性**：所有 catch 只 console.error，不暴露 error ref；乐观更新失败不回滚；fetchList/fetchMore 无互斥

当前代码路径：
- 后端：`CallController.java:61-116`（listCalls）、`CallSessionServiceImpl.java:285-317`（endSession）、`CallEndProcessor.java:33-57`（processCallEnd）
- 前端：`useCallHistory.ts`（composable）、`CallHistoryList.vue`（组件）、`types/index.ts` + `api/call/model/callModel.ts`（类型定义重复）

## Goals / Non-Goals

**Goals:**
- endSession 幂等：status == ENDED 时直接返回，消除重复摘要/标签
- 列表查询从 N+1 改为单条 JOIN SQL，page_size=20 时 SQL 从 61 降至 1
- 时间范围过滤生效
- 创建 session 加 Redis 分布式锁防并发重复行
- CallSession 加 @Version 乐观锁防状态覆盖
- 前端错误状态可见、乐观更新可回滚、请求互斥

**Non-Goals:**
- 不重构 API 响应格式（保持现有 JSON 结构）
- 不引入新的前端状态管理库（继续用 composable + ref）
- 不做分库分表或 ES 搜索优化（当前 ≤100 并发坐席无需）
- 不修改 FreeSWITCH/freeswichService 侧代码

## Decisions

### D1: endSession 幂等 — 入口检查 vs Redis 锁

**选择**：入口检查 `if ("ENDED".equals(session.getStatus())) return`

理由：
- session 对象已在 handleEvent 中查出，无额外查询开销
- 重复调用场景是 WS hangup 和 FS CALL_ENDED 几乎同时到达，两次在同一个 JVM 进程内，进程内检查即可
- 不需要 Redis 锁，因为不是跨进程竞态

替代方案（否决）：Redis SETNX 锁 — 杀鸡用牛刀，增加外部依赖和超时处理复杂度

### D2: N+1 改 JOIN — MyBatis XML vs 注解

**选择**：MyBatis XML 自定义 SQL

理由：
- 需 LEFT JOIN customer + agent_profile + 子查询 COUNT(turn)，LambdaQueryWrapper 无法表达
- XML 可读性更好，后续加字段/索引也方便
- 在 `CallSessionMapper.xml` 中新增 `selectCallListPage` 方法

JOIN 结构：
```sql
SELECT cs.*,
       c.name AS customer_name,
       ap.agent_no AS agent_name,
       (SELECT COUNT(*) FROM call_turn ct WHERE ct.session_id = cs.id) AS turn_count
FROM call_session cs
LEFT JOIN customer c ON c.id = cs.customer_id
LEFT JOIN agent_profile ap ON ap.id = cs.agent_id
WHERE ...
ORDER BY cs.create_time DESC
```

### D3: 时间过滤 — 字段选择

**选择**：用 `create_time` 做 between 过滤

理由：
- `create_time` 是记录创建时间，对应通话发生时间
- `start_time` 字段在 CallSession 实体中不存在（有 queueEnterTime/ringTime/answerTime，语义不同）
- 前端传 `start_time`/`end_time` 映射到 `create_time` 的 between 范围

### D4: 创建 session 分布式锁 — key 设计

**选择**：Redis SETNX，key = `call:session:lock:{fsCallId}`，TTL 30s

理由：
- createInboundSession 和 handleEvent(RINGING) 两处可能并发调用
- 获取锁失败说明同 fsCallId 正在创建，等待后重试查询即可
- 30s TTL 兜底防止死锁（正常创建 < 1s）

### D5: 前端错误处理 — error ref vs 通知

**选择**：composable 返回 `error` ref，组件展示内联错误提示

理由：
- 通知（message.error）是临时的，用户可能错过
- 内联提示在列表区域内持续显示，直到用户刷新
- error ref 类型为 `Ref<string | null>`，catch 时赋值，fetchList 成功时清空

### D6: 前端类型定义统一 — 保留哪个

**选择**：保留 `views/call/types/index.ts`，删除 `api/call/model/callModel.ts` 中的重复定义

理由：
- `types/index.ts` 是组件实际 import 的来源
- `callModel.ts` 未被任何文件 import（仅定义未使用）
- 统一后只需维护一处

### D7: pendingRingingTimeout 取消时机

**选择**：在 ANSWERED 事件处理中增加 `pendingRingingTimeouts.remove(fsCallId)` + cancel

理由：
- 当前只在 RINGING_CONFIRMED 和 CALL_ENDED 中清理，ANSWERED 先于 RINGING_CONFIRMED 到达时遗漏
- ANSWERED 表示通话已接通，超时弹窗此时已无意义

## Risks / Trade-offs

- **JOIN 查询 + 分页**：MyBatis-Plus Page 配合自定义 XML SQL 需手动处理 count 查询。风险：count 查询如果也带 JOIN 会慢。缓解：count 查询只查主表，不 JOIN。
- **Redis 分布式锁**：引入 Redis 依赖，但项目已使用 Redis（db4 做队列），无新增基础设施。风险：Redis 不可用时锁获取失败。缓解：锁获取失败时 fallback 为 selectOne 检查，不阻塞业务。
- **乐观锁 @Version**：updateById 失败时抛 OptimisticLockerException。缓解：在 ANSWERED/CALL_ENDED 处理中 catch 并 log.warn，不回滚事务。
- **前端类型文件删除**：`callModel.ts` 如有其他模块 import 会编译失败。缓解：全局 grep 确认无 import 后再删。
