# 来电弹窗等待 Linphone 振铃 — 设计文档

> **状态：已完成** — 2026-05-19 联调通过

## 问题

Linphone 振铃弹窗比 Web 来电弹窗晚 ~15s，坐席先看到 Web 弹窗、后看到 Linphone 弹窗。期望：**Linphone 响铃后页面再弹框**。

## 方案

FreeSwitch originate 成功后，坐席 SIP 分机一定已开始振铃。FreeSwitch 状态机在收到 B-leg `CHANNEL_CALLSTATE RINGING` 事件时已经确认了这一点。利用这个事件，让 FreeSwitch 分两步通知 Java 后端：

1. **RINGING** — 创建 CallSession、更新坐席状态，**不弹窗**
2. **RINGING_CONFIRMED** — 坐席端确实在振铃了，Java 后端此时才推送 `incoming_call` 弹窗

## 泳道图

```
 FreeSwitch(Python)       Java后端              前端(Vue3)          Linphone
       │                    │                     │                    │
       │ ①CHANNEL_CREATE   │                     │                    │
       │──────────────────>│                     │                    │
       │                   │                     │                    │
       │ ②_ on_session_created                    │                    │
       │   POST /route      │                     │                    │
       │──────────────────>│                     │                    │
       │                   │                     │                    │
       │   route响应(RING) │                     │                    │
       │<──────────────────│                     │                    │
       │                   │                     │                    │
       │ ③originate user/{ext}                   │                    │
       │─────────────────────────────────────────────────────────────>│
       │                   │                     │                    │
       │ ④CHANNEL_CALLSTATE RINGING(B-leg)       │                    │
       │──────────────────>│                     │                    │
       │                   │                     │                    │
       │ ⑤_ handle_ringing │                     │                    │
       │   POST /events    │                     │                    │
       │   (RINGING)       │                     │                    │
       │──────────────────>│                     │                    │
       │                   │                     │                    │
       │                   │ ⑥创建CallSession    │                    │
       │                   │   更新坐席RINGING    │                    │
       │                   │   ❌不弹窗，只推送预告│                    │
       │                   │                     │                    │
       │                   │ WS:incoming_call_   │                    │
       │                   │ pending             │                    │
       │                   │────────────────────>│                    │
       │                   │                     │                    │
       │                   │                     │ 缓存来电数据       │
       │                   │                     │ 不弹窗             │
       │                   │                     │                    │
       │                   │                     │     ⑦Linphone开始  │
       │                   │                     │     振铃           │
       │                   │                     │<───────────────────│
       │                   │                     │                    │
       │ ⑧_ on_state_change(RINGING)             │                    │
       │   POST /events    │                     │                    │
       │   (RINGING_       │                     │                    │
       │    CONFIRMED)     │                     │                    │
       │──────────────────>│                     │                    │
       │                   │                     │                    │
       │                   │ ⑨handleEvent        │                    │
       │                   │   (RINGING_CONFIRMED)│                    │
       │                   │   pushIncomingCall  │                    │
       │                   │                     │                    │
       │                   │ WS:incoming_call    │                    │
       │                   │────────────────────>│                    │
       │                   │                     │                    │
       │                   │                     │ ⑩弹出来电弹窗!    │
       │                   │                     │                    │
```

## 各端改动

### FreeSwitch (Python)

| 改动 | 文件 | 说明 |
|------|------|------|
| RINGING 状态回调中，在上报 RINGING 之后延迟上报 RINGING_CONFIRMED | `webhook_service.py` `_handle_ringing()` | originate 是同步命令，执行返回即表示 B-leg 已创建、坐席已振铃。在 `_handle_ringing()` 末尾追加一次 `POST /events` 上报 `RINGING_CONFIRMED` |

### Java 后端

| 改动 | 文件 | 说明 |
|------|------|------|
| RINGING 不再推送 `incoming_call` | `CallSessionServiceImpl.handleEvent()` | 移除 `CallWebSocket.pushIncomingCall()` 调用，改为推送 `incoming_call_pending`（携带 callId/phone/callerName/fsCallId） |
| 新增 `pushIncomingCallPending` | `CallWebSocket` | 推送 `type: "incoming_call_pending"` |
| 新增 RINGING_CONFIRMED 事件处理 | `CallSessionServiceImpl.handleEvent()` | 收到后调用 `CallWebSocket.pushIncomingCall()` 推送弹窗 |
| 超时兜底 | `CallSessionServiceImpl` | RINGING 后 20s 未收到 RINGING_CONFIRMED，自动推送 `incoming_call`（防坐席永远等不到弹窗） |

### 前端 (Vue3)

| 改动 | 文件 | 说明 |
|------|------|------|
| 订阅 `incoming_call_pending` | `useCallNotify.ts` | 收到后缓存来电数据到 `_pendingCall`，不弹窗 |
| `incoming_call` 保持原有弹窗逻辑 | `useCallNotify.ts` | 无需改动，收到即弹窗 |
| 超时兜底 | `useCallNotify.ts` | 收到 `incoming_call_pending` 25s 后若仍未收到 `incoming_call`，自动弹窗 |

## 消息格式

### incoming_call_pending（后端 → 前端，预告，不弹窗）

```json
{ "type": "incoming_call_pending", "call_id": "xxx", "phone": "138xxx", "caller_name": "张三", "fs_call_id": "abc" }
```

### incoming_call（后端 → 前端，弹窗，现有格式不变）

```json
{ "type": "incoming_call", "call_id": "xxx", "phone": "138xxx", "caller_name": "张三", "fs_call_id": "abc" }
```

### RINGING_CONFIRMED（FreeSwitch → Java 后端）

```json
{ "eventType": "RINGING_CONFIRMED", "timestamp": "2026-05-19T10:00:00" }
```

## 异常处理

| 场景 | 策略 |
|------|------|
| originate 失败（坐席离线） | FreeSwitch 触发 HANGUP → Java 清理缓存，推送取消 |
| RINGING_CONFIRMED 丢失 | 后端 20s 超时自动推送 `incoming_call`；前端 25s 超时自动弹窗 |
| 多通来电 | `incoming_call_pending` 入队列，`incoming_call` 按现有 `_queue` 机制顺序弹出 |

## 人员分工

### 窦贾浩 — FreeSwitch (Python)

- `webhook_service.py` `_handle_ringing()` 末尾追加：上报 `RINGING_CONFIRMED` 事件到 Java 后端（`POST /api/v1/internal/calls/{fsCallId}/events`，eventType=`RINGING_CONFIRMED`）

### 张成 — 前端 (Vue3)

- `useCallNotify.ts`：新增订阅 `incoming_call_pending`，收到后缓存到 `_pendingCall`，不弹窗
- `useCallNotify.ts`：新增 25s 超时兜底，超时未收到 `incoming_call` 则自动弹窗
- `index.vue`：`IncomingCallOverlay` 的显示条件改为由 `incomingCall` 驱动（现有逻辑不变，`incoming_call_pending` 不触发弹窗即可）

### haoqi — Java 后端

- `CallWebSocket`：新增 `pushIncomingCallPending()` 方法
- `CallSessionServiceImpl.handleEvent()`：RINGING 分支移除 `pushIncomingCall()`，改为 `pushIncomingCallPending()`；新增 RINGING_CONFIRMED 事件处理，收到后调用 `pushIncomingCall()`
- `CallSessionServiceImpl`：新增 20s 超时兜底，RINGING 后未收到 RINGING_CONFIRMED 则自动推送 `incoming_call`
