## 1. FreeSwitch 事件上报增强（窦贾浩）

- [ ] 1.1 webhook_service.py `_report_answered()` 增加 `answeredBy` 字段（originate 场景下值为 AGENT）
- [ ] 1.2 webhook_service.py `_report_call_ended()` 根据 hangup_cause 和会话状态判断 `endedBy`（RINGING 时坐席挂断为 AGENT，其余为 CUSTOMER）

## 2. Java 后端 ANSWERED 推送（haoqi）

- [ ] 2.1 CallSessionServiceImpl.handleEvent() ANSWERED 分支末尾增加 `CallWebSocket.pushCallState(agentUserId, "active")` 和 `CallWebSocket.pushCallSession(agentUserId, sessionId)`
- [ ] 2.2 CallSessionServiceImpl.handleEvent() ANSWERED 分支增加幂等检查：session 已 TALKING 则跳过状态更新和推送

## 3. Java 后端接听幂等（haoqi）

- [ ] 3.1 CallWsMessageHandler.handleCallResponse() accept 分支增加幂等检查：session.status=TALKING 时跳过 bridge，仅补推 startStreaming + pushCallState + pushCallSession

## 4. Java 后端振铃中拒接识别（haoqi）

- [ ] 4.1 CallSessionServiceImpl.handleEvent() CALL_ENDED 分支增加判断：session.status=RINGING 且 endedBy≠CUSTOMER 时，走拒接逻辑（session→QUEUING，agent→ONLINE，推送 incoming_call_cancelled + call_state:idle）
- [ ] 4.2 CallWebSocket 新增 `pushIncomingCallCancelled()` 方法

## 5. 前端 call_state:active 完善处理（张成）

- [ ] 5.1 CustomerServiceView.vue `_onCallState` 的 active 分支增加 `startAsr()` + `startCallTimer()` + `showToast('Linphone 已接听')`，并增加幂等检查（已在 active 状态时跳过）

## 6. 联调验证

- [ ] 6.1 验证 Linphone 接听后前端自动进入通话状态
- [ ] 6.2 验证前端接听后 Linphone 自动接听
- [ ] 6.3 验证 Linphone 拒接后前端关闭来电弹窗
- [ ] 6.4 验证两端同时接听的幂等处理
