## Why

当前 Linphone（SIP 软电话）和 Web 页面的通话操作（接听/拒接/挂断）互相不同步：坐席在 Linphone 上接听后页面不知道通话已开始，坐席在页面点拒接后 Linphone 继续振铃。这导致坐席必须同时在两端操作，体验割裂且容易遗漏来电。

## What Changes

- 后端 ANSWERED 事件处理增加 WS 推送，通知前端通话已接通（Linphone 接听 → 前端同步）
- 后端前端接听逻辑增加幂等检查，已接通的会话跳过 bridge（前端接听 → Linphone 同步的防重复）
- 后端 CALL_ENDED 事件处理区分振铃中坐席挂断（拒接）与正常挂断，推送 incoming_call_cancelled
- FreeSwitch ANSWERED/CALL_ENDED 事件上报增加 answeredBy/endedBy 字段，让后端区分操作来源
- 前端 call_state:active 事件处理补上 ASR 启动和计时器，使 Linphone 接听后页面完整进入通话状态

## Capabilities

### New Capabilities
- `call-action-sync`: 通话操作双向同步——Linphone 接听/拒接与 Web 接听/拒接互相同步，后端幂等处理

### Modified Capabilities


## Impact

- **Java 后端**：CallSessionServiceImpl、CallWsMessageHandler、CallWebSocket（~40 行改动）
- **FreeSwitch (Python)**：webhook_service.py 的 _report_answered 和 _report_call_ended（~15 行改动）
- **前端 (Vue3)**：CustomerServiceView.vue 的 _onCallState（~5 行改动）
- **消息格式**：ANSWERED 事件新增 answeredBy 字段，CALL_ENDED 事件 endedBy 字段语义变更（不再固定 CUSTOMER）
- **兼容性**：新增字段为可选字段，旧版客户端忽略即可，无 **BREAKING** 变更
