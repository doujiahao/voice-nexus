## ADDED Requirements

### Requirement: 前端错误状态暴露
useCallHistory composable SHALL 返回 `error` ref（类型 `Ref<string | null>`），在 fetchList/fetchMore/fetchDetail 失败时赋值错误消息，成功时清空为 null。CallHistoryList 组件 SHALL 在 error 非空时展示内联错误提示。

#### Scenario: 列表加载失败
- **WHEN** fetchList 请求失败（网络错误或服务端 500）
- **THEN** error ref 赋值为错误消息，列表区域显示"加载失败，请重试"提示

#### Scenario: 加载失败后重试成功
- **WHEN** 用户触发 refresh 后 fetchList 成功
- **THEN** error ref 清空为 null，错误提示消失，正常显示列表

#### Scenario: 追加加载失败
- **WHEN** fetchMore 请求失败
- **THEN** error ref 赋值，列表底部显示"加载更多失败"提示，已有数据不受影响

### Requirement: 乐观更新回滚
updateRecordNote 在 API 调用失败时 SHALL 还原 note 为修改前的旧值，并通过 error ref 或 message 提示用户保存失败。

#### Scenario: 备注保存成功
- **WHEN** 用户修改 note 后 updateCallRemark API 返回成功
- **THEN** 本地 note 保持新值，无错误提示

#### Scenario: 备注保存失败
- **WHEN** 用户修改 note 后 updateCallRemark API 失败
- **THEN** 本地 note 还原为修改前的旧值，error ref 显示"备注保存失败"

### Requirement: fetchList 与 fetchMore 请求互斥
useCallHistory SHALL 使用互斥标志确保 fetchList 和 fetchMore 不会并发执行，避免竞态导致的重复数据。

#### Scenario: fetchList 执行中触发 fetchMore
- **WHEN** fetchList 正在请求中，用户滚动触发 fetchMore
- **THEN** fetchMore 因 isLoading 为 true 直接返回，不发起新请求

#### Scenario: fetchMore 执行中触发 fetchList
- **WHEN** fetchMore 正在请求中，用户下拉刷新触发 fetchList
- **THEN** fetchList 等待 fetchMore 完成后再执行（或直接中断 fetchMore 后执行 fetchList）

### Requirement: 滚动刷新加下拉阈值
CallHistoryList 的滚动到顶刷新 SHALL 要求 scrollTop 在 0 位置停留超过 300ms 或下拉距离超过 50px 才触发 refresh，避免轻微滚动意外触发。

#### Scenario: 快速滑过顶部
- **WHEN** 用户快速滑动列表，scrollTop 瞬间经过 0 但立即离开
- **THEN** 不触发 refresh

#### Scenario: 停留在顶部
- **WHEN** 用户滚动到 scrollTop === 0 并停留超过 300ms
- **THEN** 触发 refresh

### Requirement: 前端类型定义统一
前端 SHALL 删除 `api/call/model/callModel.ts` 中的重复类型定义（CallListItem/CallTurnItem/CallDetail），所有组件统一从 `views/call/types/index.ts` 导入。

#### Scenario: callModel.ts 中有其他模块 import
- **WHEN** grep 发现 callModel.ts 被其他文件 import
- **THEN** 将这些 import 路径改为指向 types/index.ts，再删除 callModel.ts

#### Scenario: callModel.ts 无其他 import
- **WHEN** grep 确认 callModel.ts 未被任何文件 import
- **THEN** 直接删除 callModel.ts
