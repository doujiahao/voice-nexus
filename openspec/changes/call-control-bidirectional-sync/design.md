## Context

当前呼入链路中，Linphone（SIP 软电话）和 Web 页面各自独立操作通话：
- Linphone 接听后，FreeSwitch 上报 ANSWERED 事件，Java 后端更新 DB 状态为 TALKING，但不推送 WS 消息通知前端
- 前端点接听时，后端无条件调用 FreeSwitchClient.bridge()，若坐席已在 Linphone 接听则 bridge 会冲突
- 坐席在 Linphone 拒接（振铃时挂断），FreeSwitch 上报 CALL_ENDED，后端走正常结束逻辑而非拒接逻辑

已有基础：
- 前端挂断 → Linphone 同步（已实现：CallWsMessageHandler.hangup → FreeSwitchClient.hangup）
- Linphone 挂断 → 前端同步（已实现：CALL_ENDED → pushCallState(idle)）
- 前端来电弹窗等 Linphone 振铃（已实现：RINGING_CONFIRMED 机制）

## Goals / Non-Goals

**Goals:**
- Linphone 接听后，Web 页面自动进入通话状态（ASR 启动 + 计时器 + 通话界面）
- 前端接听后，Linphone 自动接听（FreeSwitch bridge 触发 SIP 端接听）
- Linphone 拒接后，Web 页面关闭来电弹窗，会话放回排队
- 前端拒接后，Linphone 停止振铃（已实现，无需改动）
- 两端几乎同时操作时幂等处理，不产生重复状态

**Non-Goals:**
- 不改变现有 originate + bridge 的呼叫模型
- 不引入新的 WebSocket 消息通道
- 不处理呼出场景（本期仅呼入）
- 不做坐席状态冲突检测（如 Linphone 和 Web 同时操作不同通话）

## Decisions

### 1. 后端作为唯一状态权威

**选择**：所有通话状态变更经后端确认后再广播给两端。

**替代方案**：前端直连 FreeSwitch ESL（安全风险高，复杂度大）。

**理由**：后端已有完整的事件处理链路（FreeSwitch → Java → WS → 前端），在这个链路上增加推送最简单。前端不应直接访问 FreeSwitch。

### 2. 幂等检查基于 session.status

**选择**：accept 操作先查 session.status，已 TALKING 则跳过 bridge，只补推 streaming。

**替代方案**：用 Redis 分布式锁防重复（过重，单坐席同一时刻只有一个来电）。

**理由**：session.status 是 DB 级别的真实状态，比内存锁更可靠。且坐席场景下并发度极低，不需要分布式锁。

### 3. 振铃中坐席挂断 = 拒接

**选择**：CALL_ENDED 事件中，如果 session.status=RINGING 且 endedBy≠CUSTOMER，识别为拒接，推送 incoming_call_cancelled。

**替代方案**：FreeSwitch 侧判断 hangup_cause 后发送独立 REJECTED 事件（需新增事件类型，改动更大）。

**理由**：复用现有 CALL_ENDED 事件和 incoming_call_cancelled 消息，前端无需新增订阅。endedBy 字段区分挂断来源即可。

### 4. ANSWERED/CALL_ENDED 事件增加来源字段

**选择**：FreeSwitch 上报时增加 answeredBy/endedBy 字段（AGENT/CUSTOMER）。

**替代方案**：后端根据 metadata 推断（不可靠，坐席分机号可能变化）。

**理由**：FreeSwitch 是唯一知道谁操作的来源，由它上报最准确。

## Risks / Trade-offs

- **[bridge 冲突]** 前端点接听时如果坐席已在 Linphone 接听，FreeSwitchClient.bridge() 会返回错误 → 幂等检查先查状态，已 TALKING 则跳过 bridge
- **[endedBy 判断不准]** FreeSwitch 的 hangup_cause 不总能准确区分坐席/客户挂断 → originate 场景下，RINGING 状态的 HANGUP 必然是坐席侧（客户侧不会在 RINGING 时挂断，因为 originate 是先振铃坐席再 bridge 客户），判断逻辑足够可靠
- **[ANSWERED 事件与前端 accept 竞争]** 两者可能几乎同时到达后端 → 幂等检查保证第一个生效，第二个跳过
- **[前端 _onCallState 重复启动 ASR]** 如果前端先 accept 触发了 startAsr，又收到 call_state:active 再触发一次 → 需要在 startAsr 中增加幂等检查（已连接则跳过），或在 _onCallState 中判断是否已在 active 状态
