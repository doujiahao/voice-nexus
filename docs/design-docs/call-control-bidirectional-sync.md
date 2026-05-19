# 通话控制双向同步 — 设计文档

## 问题

当前 Linphone（SIP 软电话）和 Web 页面操作不同步：

| 操作 | Linphone → 前端 | 前端 → Linphone |
|------|-----------------|-----------------|
| 接听 | ❌ 缺失 | ✅ bridge + streaming |
| 拒接 | ❌ 缺失 | ✅ hangup(REJECTED) |
| 挂断 | ✅ CALL_ENDED → pushCallState(idle) | ✅ hangup(NORMAL_CLEARING) |

**期望**：
- 在页面点击接听 → Linphone 也要接听
- 在 Linphone 点击接听 → 页面也要进入通话状态
- 在 Linphone 点击拒接/挂断 → 页面也要挂断（已实现）
- 在页面点击拒绝/挂断 → Linphone 也要挂断（已实现）

## 方案：事件驱动双向同步

### 核心原则

1. **后端是唯一状态权威** — 所有状态变更经后端确认后再广播
2. **谁先操作谁生效** — Linphone 接听和 Web 接听，哪个先到以哪个为准，后到的幂等忽略
3. **SIP 侧是通话主控端** — FreeSwitch 驱动真实通话状态，Web 侧是状态镜像+控制入口

### 状态同步模型

```
                    SIP 侧操作                        Web 侧操作
                        │                                 │
                        ▼                                 ▼
                  FreeSwitch 事件                   WS call_response
                        │                                 │
                        ▼                                 ▼
                  Java 后端（唯一状态权威）
                        │
            ┌───────────┼───────────┐
            ▼           ▼           ▼
       更新 DB      推送 WS     通知 FS
      (状态源)    (通知前端)  (控制SIP)
```

## 泳道图

### 场景 A：Linphone 先接听

```
 FreeSwitch(Python)       Java后端              前端(Vue3)          Linphone
       │                    │                     │                    │
       │ ①CHANNEL_ANSWER   │                     │                    │
       │   (坐席B-leg接听)  │                     │                    │
       │──────────────────>│                     │                    │
       │                   │                     │                    │
       │                   │ ②幂等检查            │                    │
       │   ANSWERED事件     │   session已TALKING?  │                    │
       │   含B-leg UUID    │   →跳过(防重复)      │                    │
       │                   │                     │                    │
       │                   │ ③更新session=TALKING │                    │
       │                   │  更新agent=TALKING   │                    │
       │                   │                     │                    │
       │                   │ ④WS: call_state     │                    │
       │                   │  {state:"active"}   │                    │
       │                   │────────────────────>│                    │
       │                   │                     │                    │
       │                   │ ⑤WS: call_session   │                    │
       │                   │  {call_session_id}  │                    │
       │                   │────────────────────>│                    │
       │                   │                     │                    │
       │                   │                     │ ⑥进入通话界面       │
       │                   │                     │ (startAsr+计时器)   │
       │                   │                     │                    │
       │ ⑦ANSWERED上报     │                     │                    │
       │ (webhook_service  │                     │                    │
       │  已有逻辑启动      │                     │                    │
       │  音频处理)         │                     │                    │
```

### 场景 B：前端先接听

```
 FreeSwitch(Python)       Java后端              前端(Vue3)          Linphone
       │                    │                     │                    │
       │                    │                     │ ①点击接听          │
       │                    │<──WS call_response──│  action=accept     │
       │                    │                     │                    │
       │                    │ ②幂等检查            │                    │
       │                    │   session已TALKING?  │                    │
       │                    │   →跳过(防重复)      │                    │
       │                    │                     │                    │
       │                    │ ③更新session=TALKING │                    │
       │                    │  更新agent=TALKING   │                    │
       │                    │                     │                    │
       │                    │ ④FS: bridge          │                    │
       │                    │─────────────────────│──────────────────>│
       │                    │                     │        Linphone接听 │
       │                    │                     │                    │
       │                    │ ⑤FS: start_streaming │                   │
       │                    │─────────────────────│──────────────────>│
       │                    │                     │                    │
       │                    │ ⑥WS: call_state     │                    │
       │                    │  {state:"active"}   │                    │
       │                    │────────────────────>│                    │
       │                    │                     │                    │
       │                    │ ⑦WS: call_session   │                    │
       │                    │────────────────────>│                    │
       │                    │                     │                    │
       │                    │                     │ ⑧进入通话界面       │
```

### 场景 C：Linphone 拒接（坐席在振铃时点拒接）

```
 FreeSwitch(Python)       Java后端              前端(Vue3)          Linphone
       │                    │                     │                    │
       │ ①CHANNEL_HANGUP   │                     │                    │
       │   (坐席B-leg挂断)  │                     │                    │
       │   hangup_cause=    │                     │                    │
       │   ORIGINATOR_     │                     │                    │
       │   CANCEL / NO_    │                     │                    │
       │   ANSWER          │                     │                    │
       │──────────────────>│                     │                    │
       │                   │                     │                    │
       │                   │ ②判断：会话在RINGING  │                    │
       │                   │   且坐席挂断 = 拒接   │                    │
       │                   │                     │                    │
       │                   │ ③session→QUEUING    │                    │
       │                   │   agent→ONLINE      │                    │
       │                   │                     │                    │
       │                   │ ④WS: incoming_call_ │                    │
       │                   │   cancelled          │                    │
       │                   │────────────────────>│                    │
       │                   │                     │                    │
       │                   │                     │ ⑤关闭来电弹窗       │
       │                   │                     │                    │
       │ ⑤CALL_ENDED      │                     │                    │
       │──────────────────>│                     │                    │
       │                   │  (二次事件，忽略)     │                    │
```

## 各端改动

### Java 后端（haoqi）

#### 1. CallSessionServiceImpl.handleEvent() — ANSWERED 事件增加 WS 推送

当前代码（`CallSessionServiceImpl.java:197-227`）：ANSWERED 事件只更新 DB，不推送 WS。

**改动**：在 ANSWERED 分支末尾，增加推送 `call_state: active` 和 `call_session`：

```java
case "ANSWERED":
    // ...现有逻辑...
    session.setStatus("TALKING");
    session.setAnswerTime(new Date());
    updateById(session);
    agentProfileService.changeStatus(agent.getUserId(), AgentStatusEnum.TALKING, "通话接通");

    // ✅ 新增：通知前端通话已接通（Linphone 接听 → 前端同步）
    CallWebSocket.pushCallState(agent.getUserId(), "active");
    CallWebSocket.pushCallSession(agent.getUserId(), session.getId());
    break;
```

#### 2. CallWsMessageHandler.handleCallResponse() — accept 分支增加幂等检查

当前代码（`CallWsMessageHandler.java:62-89`）：前端点接听无条件执行 bridge。

**改动**：增加幂等检查，已 TALKING 的会话跳过 bridge，只补推 streaming：

```java
if ("accept".equals(action)) {
    CallSession session = callSessionService.getById(callId);
    // ✅ 幂等：如果已经是 TALKING 状态（Linphone 先接听），跳过 bridge
    if (session != null && "TALKING".equals(session.getStatus())) {
        log.info("[CallWS] 会话已接通，跳过 bridge: callId={}", callId);
        CallWebSocket.pushCallState(userId, "active");
        CallWebSocket.pushCallSession(userId, callId);
        // 补推 streaming（如果尚未启动）
        if (session.getFsCallId() != null) {
            AgentProfile agent = agentProfileService.getByUserId(userId);
            if (agent != null && agent.getExtension() != null) {
                String rtmpUrl = buildRtmpUrl(callId);
                FreeSwitchClient.startStreaming(session.getFsCallId(), callId, rtmpUrl);
            }
        }
        return;
    }
    // ... 原有逻辑 ...
}
```

#### 3. CallSessionServiceImpl.handleEvent() — 振铃中坐席挂断识别为拒接

当前代码：`CALL_ENDED` 事件统一走 `endSession()`，不做拒接判断。

**改动**：如果会话状态是 RINGING 且坐席侧挂断（非客户挂断），走拒接逻辑而非结束逻辑：

```java
case "CALL_ENDED":
    // ✅ 新增：振铃中坐席挂断 = 拒接
    if ("RINGING".equals(session.getStatus())
        && event.getEndedBy() != null
        && !"CUSTOMER".equals(event.getEndedBy())) {
        // 坐席拒接：不结束会话，放回排队
        session.setStatus("QUEUING");
        updateById(session);
        if (session.getAgentId() != null) {
            AgentProfile agent = agentProfileMapper.selectById(session.getAgentId());
            if (agent != null) {
                agentProfileService.changeStatus(agent.getUserId(), AgentStatusEnum.ONLINE, "坐席拒接(Linphone)");
                CallWebSocket.pushCallState(agent.getUserId(), "idle");
                CallWebSocket.pushIncomingCallCancelled(agent.getUserId(), session.getId(), null, session.getFsCallId());
            }
        }
        removeFromQueueIfNeeded(session);
        // 不走 endSession，让排队机制重新分配
        break;
    }
    // ... 原有 endSession 逻辑 ...
```

#### 4. CallWebSocket — 新增 pushIncomingCallCancelled 方法

```java
public static void pushIncomingCallCancelled(String agentUserId, String callId, String phone, String fsCallId) {
    JSONObject msg = new JSONObject();
    msg.put("type", "incoming_call_cancelled");
    msg.put("call_id", callId);
    msg.put("phone", phone);
    msg.put("fs_call_id", fsCallId);
    sendMessage(agentUserId, msg.toJSONString());
}
```

### FreeSwitch (Python)（窦贾浩）

#### 1. ANSWERED 事件上报增加 B-leg 信息

当前 `_report_answered()` 只发送 `eventType: ANSWERED`，没有区分谁接听。

**改动**：增加 `endedBy` / `answeredBy` 字段，让后端区分是坐席接听还是客户接听：

```python
def _report_answered(self, uuid: str):
    url = f"{self.vn_base_url}/api/v1/internal/calls/{uuid}/events"
    session = self.state_machine.get_session(uuid)

    # 判断谁接听：如果有 B-leg 且 B-leg 是坐席分机，则为坐席接听
    answered_by = "AGENT"  # originate 场景下，ANSWERED 一定是坐席先接
    if session and session.direction and session.direction.value == "OUTBOUND":
        answered_by = "AGENT"

    payload = {
        "eventType": "ANSWERED",
        "answeredBy": answered_by,
        "timestamp": datetime.now().isoformat(),
    }
    self._http_post(url, payload, internal_auth=True)
```

#### 2. CALL_ENDED 事件上报增加 endedBy 区分

当前 `_report_call_ended()` 固定 `endedBy: "CUSTOMER"`。

**改动**：根据挂断来源判断 endedBy：

```python
def _report_call_ended(self, uuid: str):
    session = self.state_machine.get_session(uuid)
    # 判断挂断来源
    ended_by = "CUSTOMER"
    if session:
        # 如果是坐席侧先挂断，标记为 AGENT
        # FreeSwitch 的 hangup_cause 可以辅助判断
        hangup_cause = session.hangup_cause or ""
        if hangup_cause in ("NORMAL_CLEARING", "ORIGINATOR_CANCEL", "NO_ANSWER"):
            # originate 场景下，如果坐席没接就挂断，是坐席拒接
            if session.state == CallState.RINGING:
                ended_by = "AGENT"
    # ... 其余逻辑 ...
    payload = {
        "eventType": "CALL_ENDED",
        "endedBy": ended_by,
        # ...
    }
```

### 前端 (Vue3)（张成）

#### 1. useCallState — 监听 call_state: active 自动进入通话

当前 `_onCallState`（`CustomerServiceView.vue:156-172`）已处理 `call_state: active`，会调用 `toActive()`。

**需确认**：收到 `call_state: active` 后，是否也需要 `startAsr()` + `startCallTimer()`？

当前代码中，这些逻辑在 `acceptCall()` 里触发，而 `acceptCall()` 只在 `acceptedCall` watch 里调用。`_onCallState` 只调了 `toActive()` 但没启动 ASR 和计时器。

**改动**：在 `_onCallState` 的 `active` 分支中，也触发 ASR 启动和计时器：

```typescript
function _onCallState(payload: any): void {
  if (payload.state === 'active') {
    hadRealCall = true
    toActive()
    resetAnalysis()
    // ✅ 新增：Linphone 接听后前端也需要启动 ASR 和计时器
    startAsr()
    startCallTimer()
    showToast('Linphone 已接听')
  }
  // ...
}
```

#### 2. incoming_call_cancelled 订阅

`useCallNotify.ts` 已订阅 `incoming_call_cancelled`，已有处理逻辑。无需额外改动。

## 消息格式

### call_state（后端 → 前端，已有，扩展使用）

```json
{ "type": "call_state", "state": "active" }   // 通话接通（无论谁接的）
{ "type": "call_state", "state": "idle" }     // 通话结束/拒接
```

### incoming_call_cancelled（后端 → 前端，已有）

```json
{ "type": "incoming_call_cancelled", "call_id": "xxx", "fs_call_id": "abc" }
```

### ANSWERED 事件（FreeSwitch → Java 后端，扩展字段）

```json
{ "eventType": "ANSWERED", "answeredBy": "AGENT", "timestamp": "2026-05-19T10:00:00" }
```

### CALL_ENDED 事件（FreeSwitch → Java 后端，扩展字段）

```json
{ "eventType": "CALL_ENDED", "endedBy": "AGENT", "durationSec": 0, "timestamp": "2026-05-19T10:00:00", "metadata": { "hangup_cause": "NO_ANSWER" } }
```

## 异常处理

| 场景 | 策略 |
|------|------|
| 两端几乎同时接听 | 幂等检查：后端先处理到的事件生效，第二个被忽略 |
| 前端点接听时 Linphone 已接听 | 幂等跳过 bridge，补推 streaming |
| Linphone 接听但 WS 断线 | WS 重连后前端通过 `call_state` 消息恢复状态；极端情况走超时兜底 |
| 坐席在 Linphone 拒接 | 后端识别为拒接，推送 `incoming_call_cancelled`，会话放回排队 |
| FreeSwitch bridge 失败（坐席已自行接听） | `uuid_bridge` 对已 bridge 的通话返回错误，后端忽略该错误即可 |

## 人员分工

### 窦贾浩 — FreeSwitch (Python)

- `_report_answered()`: 增加 `answeredBy` 字段
- `_report_call_ended()`: 根据 hangup_cause 和会话状态判断 `endedBy`（AGENT / CUSTOMER）

### 张成 — 前端 (Vue3)

- `CustomerServiceView.vue` `_onCallState`: `active` 分支增加 `startAsr()` + `startCallTimer()` + `showToast`

### haoqi — Java 后端

- `CallSessionServiceImpl.handleEvent()`: ANSWERED 分支增加 `pushCallState("active")` + `pushCallSession()`
- `CallWsMessageHandler.handleCallResponse()`: accept 分支增加幂等检查（已 TALKING 则跳过 bridge）
- `CallSessionServiceImpl.handleEvent()`: CALL_ENDED 分支增加振铃中坐席拒接判断
- `CallWebSocket`: 新增 `pushIncomingCallCancelled()` 方法

## 改动量评估

| 端 | 改动文件 | 改动行数(估) | 风险 |
|----|---------|------------|------|
| Java 后端 | CallSessionServiceImpl / CallWsMessageHandler / CallWebSocket | ~40 行 | 中（幂等逻辑需仔细测试） |
| FreeSwitch | webhook_service.py | ~15 行 | 低（只加字段） |
| 前端 | CustomerServiceView.vue | ~5 行 | 低 |
