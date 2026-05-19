## ADDED Requirements

### Requirement: 列表查询使用 JOIN 替代 N+1
`GET /api/v1/calls` 列表接口 SHALL 使用单条 LEFT JOIN SQL（call_session LEFT JOIN customer LEFT JOIN agent_profile，子查询 COUNT call_turn）替代循环内 3 次查询。SQL 数量 SHALL 从 1+3N 降至 2（1 条分页查询 + 1 条 count）。

#### Scenario: page_size=20 的正常查询
- **WHEN** 请求 GET /api/v1/calls?page=1&page_size=20
- **THEN** 后端执行 1 条 JOIN 分页查询 + 1 条 count 查询，共 2 条 SQL（而非原来的 61 条）

#### Scenario: customer_id 为空的记录
- **WHEN** 某条 call_session 的 customer_id 为 null
- **THEN** LEFT JOIN 结果中 customer_name 为 null，前端显示为空而非报错

### Requirement: 时间范围过滤生效
`GET /api/v1/calls` 接口的 `start_time` 和 `end_time` 参数 SHALL 正确应用到 QueryWrapper 的 between 条件（基于 call_session.create_time 字段）。

#### Scenario: 传入 start_time 和 end_time
- **WHEN** 请求 GET /api/v1/calls?start_time=2026-05-01&end_time=2026-05-19
- **THEN** 返回的记录的 create_time 均在 2026-05-01 至 2026-05-19 范围内

#### Scenario: 只传 start_time
- **WHEN** 请求 GET /api/v1/calls?start_time=2026-05-01
- **THEN** 返回 create_time >= 2026-05-01 的记录

#### Scenario: 不传时间参数
- **WHEN** 请求 GET /api/v1/calls（无时间参数）
- **THEN** 不应用时间过滤，返回所有符合条件的记录（与当前行为一致）

### Requirement: fs_call_id 索引保障
call_session 表 SHALL 在 fs_call_id 列上建立普通索引（非唯一，因历史数据可能存在少量重复行），保障 getByFsCallId 查询走索引而非全表扫描。

#### Scenario: getByFsCallId 查询
- **WHEN** 执行 SELECT * FROM call_session WHERE fs_call_id = ?
- **THEN** 查询走 idx_fs_call_id 索引，不产生全表扫描

### Requirement: 列表 UI 区分通话方向
CallHistoryList 组件 SHALL 根据 CallRecord 的 direction 字段显示通话方向标签（"呼入"/"呼出"），不再硬编码"用户来电"。

#### Scenario: 呼入通话记录
- **WHEN** 记录的 direction 为 INBOUND
- **THEN** 列表项显示"呼入"标签

#### Scenario: 呼出通话记录
- **WHEN** 记录的 direction 为 OUTBOUND
- **THEN** 列表项显示"呼出"标签

### Requirement: customer_name 在列表中渲染
CallHistoryList 组件 SHALL 在列表项中渲染 customer_name 字段，当 customer_name 存在时显示在 phone 旁边。

#### Scenario: 记录有 customer_name
- **WHEN** CallRecord 的 customer_name 非空
- **THEN** 列表项在 phone 旁显示 customer_name

#### Scenario: 记录无 customer_name
- **WHEN** CallRecord 的 customer_name 为空
- **THEN** 只显示 phone，不显示空白区域
