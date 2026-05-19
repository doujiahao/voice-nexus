## ADDED Requirements

### Requirement: Linphone 接听后前端同步进入通话状态
当坐席在 Linphone 接听来电时，Java 后端 SHALL 在处理 ANSWERED 事件后通过 WebSocket 推送 call_state:active 和 call_session 消息给对应坐席，前端 SHALL 在收到 call_state:active 后自动启动 ASR、计时器并进入通话界面。

#### Scenario: Linphone 接听后前端收到通话状态
- **WHEN** FreeSwitch 上报 ANSWERED 事件（坐席在 Linphone 接听）
- **THEN** Java 后端更新 session 状态为 TALKING，并通过 WS 推送 call_state:active 和 call_session 给坐席

#### Scenario: 前端收到 call_state:active 后启动通话
- **WHEN** 前端收到 WS 消息 call_state:active
- **THEN** 前端自动启动 ASR 接收、开始计时器、显示通话界面

### Requirement: 前端接听后 Linphone 同步接听
当坐席在 Web 页面点击接听时，Java 后端 SHALL 通知 FreeSwitch 执行 bridge，使坐席 SIP 分机自动接听。

#### Scenario: 前端接听触发 FreeSwitch bridge
- **WHEN** 前端发送 WS 消息 call_response action=accept
- **THEN** Java 后端调用 FreeSwitchClient.bridge() 和 FreeSwitchClient.startStreaming()，并推送 call_state:active 给前端

#### Scenario: 前端接听时坐席已在 Linphone 接听
- **WHEN** 前端发送 WS 消息 call_response action=accept，且 session 状态已经是 TALKING
- **THEN** Java 后端跳过 bridge 调用，仅补推 startStreaming（如尚未启动），并推送 call_state:active 和 call_session 给前端

### Requirement: Linphone 拒接后前端关闭来电弹窗
当坐席在 Linphone 上拒接来电（振铃时挂断）时，Java 后端 SHALL 识别为拒接，推送 incoming_call_cancelled 消息，前端 SHALL 关闭来电弹窗，会话放回排队。

#### Scenario: 振铃中坐席在 Linphone 挂断
- **WHEN** FreeSwitch 上报 CALL_ENDED 事件，且 session 状态为 RINGING，且 endedBy 不为 CUSTOMER
- **THEN** Java 后端将 session 状态更新为 QUEUING，坐席状态恢复为 ONLINE，推送 incoming_call_cancelled 和 call_state:idle 给坐席

#### Scenario: 前端收到 incoming_call_cancelled 后关闭弹窗
- **WHEN** 前端收到 WS 消息 incoming_call_cancelled
- **THEN** 前端关闭来电弹窗，坐席状态恢复空闲

### Requirement: 接听操作幂等
当两端几乎同时操作接听时，后端 SHALL 保证只处理第一个到达的请求，第二个 SHALL 被幂等跳过。

#### Scenario: Linphone 先接听后前端再点接听
- **WHEN** Linphone 接听触发 ANSWERED 事件，session 已变为 TALKING，然后前端又发送 call_response action=accept
- **THEN** 后端检测到 session 已 TALKING，跳过 bridge 调用，仅补推 streaming 和 call_state:active

#### Scenario: 前端先接听后 Linphone 再接听
- **WHEN** 前端接听触发 bridge，session 已变为 TALKING，然后 FreeSwitch 又上报 ANSWERED 事件
- **THEN** 后端检测到 session 已 TALKING，跳过状态更新和 WS 推送

### Requirement: FreeSwitch 事件上报包含操作来源
FreeSwitch 上报 ANSWERED 和 CALL_ENDED 事件时 SHALL 包含 answeredBy/endedBy 字段，标识操作来源为 AGENT 或 CUSTOMER。

#### Scenario: ANSWERED 事件包含 answeredBy
- **WHEN** FreeSwitch 上报 ANSWERED 事件
- **THEN** 事件体包含 answeredBy 字段，值为 AGENT 或 CUSTOMER

#### Scenario: CALL_ENDED 事件包含 endedBy
- **WHEN** FreeSwitch 上报 CALL_ENDED 事件
- **THEN** 事件体包含 endedBy 字段，值根据挂断来源为 AGENT 或 CUSTOMER，而非固定 CUSTOMER
