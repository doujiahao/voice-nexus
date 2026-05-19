## ADDED Requirements

### Requirement: endSession 幂等保护
endSession 方法 SHALL 在执行任何业务逻辑前检查 session 状态，若 status 已为 ENDED 则直接返回，不执行状态更新、NLP 摘要生成、标签插入和队列清理。

#### Scenario: WS hangup 和 FS CALL_ENDED 同时触发
- **WHEN** WS hangup 先调用 endSession 将 status 设为 ENDED，FS CALL_ENDED 随后再次调用 endSession
- **THEN** 第二次调用检测到 status == ENDED，直接返回，不重复生成摘要和插入标签

#### Scenario: 正常单次 endSession 调用
- **WHEN** session 状态为 TALKING，收到 CALL_ENDED 事件调用 endSession
- **THEN** 正常执行状态更新为 ENDED、NLP 摘要、标签插入和队列清理

### Requirement: 创建 session 分布式锁
创建 CallSession 时 SHALL 使用 Redis 分布式锁（key = `call:session:lock:{fsCallId}`，TTL 30s），防止并发创建重复行。获取锁失败时 SHALL fallback 为 selectOne 检查已有记录。

#### Scenario: 同一 fsCallId 并发创建
- **WHEN** createInboundSession 和 handleEvent(RINGING) 同时收到相同 fsCallId
- **THEN** 只有一个请求获取锁并创建 session，另一个等待后查询已有记录返回

#### Scenario: Redis 不可用
- **WHEN** Redis 连接失败导致锁获取异常
- **THEN** fallback 为 selectOne 查询，若已存在则返回已有记录，不阻塞业务

### Requirement: CallSession 乐观锁
CallSession 实体 SHALL 使用 MyBatis-Plus @Version 注解防止并发状态覆盖。updateById 因版本冲突失败时 SHALL 记录 warn 日志，不抛出未捕获异常。

#### Scenario: ANSWERED 和 CALL_ENDED 几乎同时到达
- **WHEN** ANSWERED 先更新 status 为 TALKING（version=1→2），CALL_ENDED 随后用旧 version=1 更新
- **THEN** CALL_ENDED 的 updateById 因版本冲突失败，记录 warn 日志，不抛异常；CALL_ENDED 将在下次重试时使用最新 version

### Requirement: pendingRingingTimeout 在 ANSWERED 时取消
处理 ANSWERED 事件时 SHALL 清理并取消该 fsCallId 对应的 pendingRingingTimeout，防止通话已接通后仍推送过时的来电弹窗。

#### Scenario: ANSWERED 先于 RINGING_CONFIRMED 到达
- **WHEN** ANSWERED 事件到达时 pendingRingingTimeouts 中仍有该 fsCallId 的超时任务
- **THEN** 取消该超时任务并从 map 中移除，不推送过时弹窗

#### Scenario: RINGING_CONFIRMED 正常先于 ANSWERED 到达
- **WHEN** RINGING_CONFIRMED 已取消超时任务，ANSWERED 到达时 map 中无该 fsCallId
- **THEN** 正常处理 ANSWERED，不受影响
