## 1. 后端 P0：数据正确性

- [ ] 1.1 endSession 幂等检查：`CallSessionServiceImpl.endSession` 入口加 `if ("ENDED".equals(session.getStatus())) { log.warn; return; }`
- [ ] 1.2 创建 session 分布式锁：在 `CallSessionServiceImpl.handleEvent(RINGING)` 和 `createInboundSession` 中，insert 前用 Redis SETNX（key=`call:session:lock:{fsCallId}`, TTL=30s）加锁，获取锁失败 fallback 为 selectOne
- [ ] 1.3 CallSession 实体加 @Version：`CallSession.java` 新增 `@Version private Integer version;` 字段，数据库 ALTER TABLE 加 `version INT DEFAULT 0`
- [ ] 1.4 ANSWERED 事件中取消 pendingRingingTimeout：`CallSessionServiceImpl.handleEvent` ANSWERED 分支开头加 `pendingRingingTimeouts.remove(fsCallId)` + cancel

## 2. 后端 P0：查询性能与过滤

- [ ] 2.1 CallSessionMapper 新增 `selectCallListPage` XML SQL：LEFT JOIN customer + agent_profile + 子查询 COUNT(call_turn)，返回含 customer_name/agent_name/turn_count 的 DTO
- [ ] 2.2 新增 CallListVO DTO：包含 call_session 基础字段 + customerName + agentName + turnCount
- [ ] 2.3 CallController.listCalls 改用新 XML 查询：替换 stream.map 内的 N+1 循环查询
- [ ] 2.4 时间过滤生效：`listCalls` 的 QueryWrapper 补上 `between(CallSession::getCreateTime, startTime, endTime)`，支持只传 start_time
- [ ] 2.5 count 查询优化：分页 count 只查主表条件，不 JOIN

## 3. 后端 P2：索引与 Recovery

- [ ] 3.1 确认/补充 fs_call_id 索引：`CREATE INDEX idx_call_session_fs_call_id ON call_session(fs_call_id)`
- [ ] 3.2 Recovery 补全：启动恢复时清理 Redis 队列残留（遍历 call:queue:* 清除已 ENDED 的 sessionId），并触发异步摘要生成

## 4. 前端 P1：错误处理与健壮性

- [ ] 4.1 useCallHistory 返回 error ref：新增 `const error = ref<string | null>(null)`，fetchList/fetchMore/fetchDetail 的 catch 中赋值，成功时清空
- [ ] 4.2 CallHistoryList 展示错误状态：接收 error prop，error 非空时显示内联错误提示（"加载失败，请重试"），带重试按钮
- [ ] 4.3 updateRecordNote 回滚：保存旧 note 值，API 失败时还原旧值 + error ref 赋值"备注保存失败"
- [ ] 4.4 fetchList/fetchMore 互斥：fetchList 执行中 fetchMore 直接 return，fetchMore 执行中 fetchList 先设置 abort 标志再执行

## 5. 前端 P2：UI 体验与类型统一

- [ ] 5.1 滚动刷新加阈值：`_handleScroll` 中 scrollTop === 0 时设 debounce 定时器 300ms，定时器到期才 emit refresh，滚动离开时清除定时器
- [ ] 5.2 列表区分通话方向：CallRecord 增加 direction 字段，CallHistoryList 根据 direction 显示"呼入"/"呼出"标签，替换硬编码"用户来电"
- [ ] 5.3 customer_name 渲染：CallHistoryList 在 phone 旁显示 customer_name（非空时）
- [ ] 5.4 删除重复类型定义：grep 确认 `api/call/model/callModel.ts` 无外部 import 后删除该文件；如有 import 则改为指向 `views/call/types/index.ts`
