# freeswichService 对接改造指南

> 目标：将音频传输方向从「FS 启动 WS Server 等 Java 来连」改为「FS 作为 WS Client 主动连 Java 的 WS Server」，与统一服务端架构设计对齐。

## 1. 改造背景

按照《呼入链路工程化架构设计》，统一服务端（Java）是唯一状态中心和 WS Server，freeswichService 是无状态控制面，负责音频处理后主动上行。

当前实现与设计不符：

| 维度 | 设计文档 | 当前实现 |
|------|---------|---------|
| WS 方向 | FS → Java（FS 是 Client） | Java → FS（Java 是 Client） |
| 触发机制 | FS 调完 /route 后主动连 Java WS | FS 回调通知 Java，Java 再去连 |
| 端口管理 | Java 一个 WS 端口接所有通话 | FS 为每通电话分配独立 WS 端口 |
| 状态归属 | Java 唯一状态中心 | FS 有自己的状态机 + relay 管理 |

## 2. 需要删除的内容

| 删除项 | 文件/模块 | 原因 |
|--------|----------|------|
| WS Server 功能 | `rtmp_web_relay.py` 中的 WebSocket 服务端 | 不再对外暴露 WS 端口 |
| notify_url 回调 | `WebhookService._notify_unified_service()` 中的旧回调 | 被 WS 上行替代 |
| webhook 注册接口 | `POST /api/webhook/register` | Java 不再需要注册回调 |
| WS 端口池管理 | `WebhookService` 中 41600-41800 端口分配逻辑 | 不再为每通电话分配端口 |
| RTMP 端口池管理 | `WebhookService` 中 41300-41500 端口分配逻辑 | 推流地址由 Java 下发 |
| call_answered/call_ended 回调 | `WebhookService._notify_unified_service()` | 不再需要通知 Java 连哪个端口 |
| mock_unified_service.py | 整个文件 | 模拟的是旧回调模式 |

## 3. 需要保留的内容

| 保留项 | 说明 |
|--------|------|
| ESL 事件监听 | 仍需感知 CHANNEL_ANSWER / CHANNEL_HANGUP |
| RTMP 接收 + FFmpeg 解码 | 仍从 FreeSWITCH 接收 RTMP 音频流 |
| 声道分离（左=客户，右=坐席） | 仍由 FS 端完成 |
| VAD 断句 + WAV 封装 | 仍由 FS 端完成 |
| Protobuf AudioFrame 序列化 | 格式不变，传输方向变了 |
| `POST /internal/calls/{fsCallId}/route` 调用 | 通话接通时调 Java 拿路由结果 |
| `POST /internal/calls/{fsCallId}/events` 调用 | 通话结束时上报事件 |
| FreeSWITCH API Server（8080） | 保留健康检查和管理接口 |
| 通话状态机 | 仍需追踪本地通话生命周期 |

## 4. 新增/改造内容

### 4.1 WS Client：主动连接 Java 服务端

**连接地址**：

```
ws://{java_host}:{java_port}/jeecg-boot/call/audio/{callSessionId}
```

**认证方式**：

连接时通过 query 参数传递内部认证密钥：

```
ws://192.168.1.21:19157/jeecg-boot/call/audio/{callSessionId}?key={internal_key}
```

**触发时机**：

调完 `POST /internal/calls/{fsCallId}/route` 拿到 `call_session_id` 后，立即建立 WS 连接。

**生命周期**：

```
通话接通 → 调 /route 拿到 call_session_id
         → 建立 WS 连接
         → 持续发送 AudioFrame（通话中）
         → 通话结束 → 关闭 WS 连接
         → 上报 /events {CALL_ENDED}
```

**断线重连策略**：

- 初始延迟：3 秒
- 退避策略：指数退避 3s → 6s → 12s → 24s → 30s（封顶）
- 停止条件：收到 CHANNEL_HANGUP 事件后停止重连
- 断线期间音频帧丢弃（不缓存，重连后从最新帧继续）

**Python 伪代码参考**：

```python
import asyncio
import websockets

class AudioUplink:
    """WS Client，主动连接 Java 服务端上行音频"""

    def __init__(self, java_ws_base_url: str, internal_key: str):
        self.base_url = java_ws_base_url  # ws://192.168.1.21:19157/jeecg-boot/call/audio
        self.internal_key = internal_key
        self._connections: dict[str, websockets.WebSocketClientProtocol] = {}
        self._stop_flags: dict[str, bool] = {}

    async def connect(self, call_session_id: str):
        """建立 WS 连接"""
        url = f"{self.base_url}/{call_session_id}?key={self.internal_key}"
        self._stop_flags[call_session_id] = False
        retry_count = 0

        while not self._stop_flags.get(call_session_id, True):
            try:
                ws = await websockets.connect(url, ping_interval=20)
                self._connections[call_session_id] = ws
                retry_count = 0
                # 连接成功，等待关闭
                await ws.wait_closed()
            except Exception:
                pass

            if self._stop_flags.get(call_session_id, True):
                break

            delay = min(3 * (2 ** retry_count), 30)
            retry_count += 1
            await asyncio.sleep(delay)

    async def send_frame(self, call_session_id: str, frame_bytes: bytes):
        """发送 Protobuf AudioFrame"""
        ws = self._connections.get(call_session_id)
        if ws and ws.open:
            await ws.send(frame_bytes)

    async def disconnect(self, call_session_id: str):
        """关闭连接，停止重连"""
        self._stop_flags[call_session_id] = True
        ws = self._connections.pop(call_session_id, None)
        if ws:
            await ws.close()
```

### 4.2 音频上行流程改造

当前流程：
```
RTMP → 解码 → 声道分离 → VAD → WAV → 写入本地 WS Server 队列 → 等 Java 来取
```

改为：
```
RTMP → 解码 → 声道分离 → VAD → WAV → Protobuf 序列化 → 通过 WS Client 发给 Java
```

AudioFrame Protobuf 格式不变：

```protobuf
message AudioFrame {
  bytes  data            = 1;  // 完整 WAV 文件字节（44 字节 RIFF 头 + PCM）
  string speaker_id      = 2;  // 电话号码，如 "13800138000"
  string speaker_name    = 3;  // 说话人显示名
  string speaker_role    = 4;  // "customer"（左声道）| "agent"（右声道）
  string language        = 6;  // "zh"
  bool   is_last         = 7;  // true = 一个完整语句
  uint32 sequence_number = 8;  // 单调递增，从 1 开始
}
```

音频参数不变：

| 属性 | 值 |
|------|------|
| 采样率 | 16000 Hz |
| 位深 | 16-bit signed little-endian |
| 声道 | 单声道（每个 WAV 只含一个说话人） |
| 格式 | 完整 WAV 文件 |
| 最短时长 | 400 ms |
| 最长时长 | 10 秒 |

### 4.3 新增统一控制接口

Java 服务端需要主动调 freeswichService 执行 FreeSWITCH 控制命令。请提供以下接口：

```
POST /api/v1/calls/action
Header: X-Internal-Key: {key}
Content-Type: application/json
```

**请求体**：

```json
{
  "action": "bridge | start_streaming | hangup",
  "fs_call_id": "uuid-from-freeswitch",
  "call_session_id": "java-session-id",
  
  // bridge 时必填
  "target_extension": "8001",
  
  // start_streaming 时必填
  "rtmp_url": "rtmp://...",
  "stream_options": {
    "codec": "aac",
    "sample_rate": 44100,
    "channels": 2
  },
  
  // hangup 时可选
  "hangup_cause": "NORMAL_CLEARING"
}
```

**响应体**：

```json
{
  "success": true,
  "fs_call_id": "uuid-from-freeswitch",
  "command": "bridge | uuid_record | uuid_kill",
  "command_result": "FreeSWITCH 返回的原始输出"
}
```

**三种 action 说明**：

| action | 对应 FS 命令 | 触发时机 |
|--------|-------------|---------|
| `bridge` | `uuid_bridge {fs_call_id} {target_extension}` | 坐席点击接听后，Java 通知 FS 桥接 |
| `start_streaming` | `uuid_record ...` | 桥接成功后，Java 通知 FS 开始推流 |
| `hangup` | `uuid_kill {fs_call_id} {cause}` | 坐席点击挂断后，Java 通知 FS 挂断 |

> 也可以保留现有的 `/api/bridge`、`/api/stream/start`、`/api/hangup` 三个分开的接口，但需要加上 `X-Internal-Key` 认证头校验。两种方式 Java 端都能适配。

### 4.4 配置项变更

`config.ini` 修改 `[VoiceNexus]` section：

```ini
[VoiceNexus]
# Java 服务端 HTTP 地址（路由申请、事件上报）
base_url = http://192.168.1.21:19157/jeecg-boot

# Java 服务端 WS 音频上行地址（新增）
ws_audio_url = ws://192.168.1.21:19157/jeecg-boot/call/audio

# 内部认证密钥
internal_key = btmo2zaoSdHXZTLPLSpy53PqDPg3DQ1E
```

去掉：
```ini
# 以下配置不再需要
# [Webhook]
# notify_url = ...
```

## 5. 改造后完整时序

```
来电 → FreeSWITCH 接收 SIP INVITE，创建 fs_call_id
         │
         ▼
freeswichService 监听 ESL CHANNEL_CREATE 事件
         │
         ▼
freeswichService 监听 ESL CHANNEL_ANSWER 事件
         │
         ▼
POST /internal/calls/{fsCallId}/route
  请求: { customer_phone, called_number, skill_group, fs_metadata }
  响应: { success, call_session_id, route_action, target_agent_id, target_extension }
         │
         ├── route_action = "RING"
         │     │
         │     ▼
         │   建立 WS 连接: ws://java/call/audio/{call_session_id}?key=xxx
         │     │
         │     ▼
         │   等待 Java 下发 bridge 指令
         │     │
         │     ▼
         │   收到 POST /api/v1/calls/action { action: "bridge", target_extension }
         │     │
         │     ▼
         │   执行 fs_cli: uuid_bridge {fs_call_id} {target_extension}
         │     │
         │     ▼
         │   收到 POST /api/v1/calls/action { action: "start_streaming", rtmp_url }
         │     │
         │     ▼
         │   执行 fs_cli: uuid_record ... → FreeSWITCH 开始 RTMP 推流
         │     │
         │     ▼
         │   接收 RTMP → 解码 → 声道分离 → VAD → WAV → Protobuf
         │     │
         │     ▼
         │   通过 WS 连接发送 AudioFrame 给 Java（循环）
         │
         ├── route_action = "QUEUE"
         │     │
         │     ▼
         │   建立 WS 连接（预建立，等待后续 bridge 指令）
         │   播放排队等待音乐（本地 FS 操作）
         │
         │
         ▼ （通话结束）
freeswichService 监听 ESL CHANNEL_HANGUP 事件
         │
         ▼
关闭 WS 连接
         │
         ▼
POST /internal/calls/{fsCallId}/events
  请求: { event_type: "CALL_ENDED", ended_by, duration_sec, timestamp, metadata }
```

## 6. Java 服务端对应提供的接口

供参考，以下是 Java 服务端已实现或将实现的接口：

| 接口 | 方向 | 说明 |
|------|------|------|
| `POST /api/v1/internal/calls/{fsCallId}/route` | FS → Java | 路由申请（已实现） |
| `POST /api/v1/internal/calls/{fsCallId}/events` | FS → Java | 事件上报（已实现） |
| `WS /call/audio/{callSessionId}` | FS → Java | 音频上行（已实现，FS 作为 Client 连入） |
| `POST /api/v1/calls/action` (FS 端提供) | Java → FS | 控制指令下发（需 FS 新增） |

## 7. 建议改造步骤

1. **第一步**：新增 `/api/v1/calls/action` 统一控制接口（或给现有 bridge/hangup 接口加 X-Internal-Key）
2. **第二步**：实现 WS Client（AudioUplink），在 /route 成功后建立连接
3. **第三步**：将 rtmp_web_relay 的音频输出从 WS Server 改为通过 AudioUplink 发送
4. **第四步**：去掉 notify_url 回调和端口池管理
5. **第五步**：联调验证

## 8. 联调验证 Checklist

- [ ] freeswichService 启动后能正常连接 FreeSWITCH ESL
- [ ] 来电时 `/route` 接口调通，拿到 call_session_id
- [ ] WS 连接 `/call/audio/{callSessionId}` 建立成功
- [ ] 收到 bridge 指令后 FreeSWITCH 桥接成功
- [ ] RTMP 推流启动，音频帧通过 WS 正常上行
- [ ] Java 端收到 AudioFrame 并触发 ASR 流程
- [ ] 挂断时 WS 正常关闭，/events 上报成功
- [ ] WS 断线后能自动重连
- [ ] X-Internal-Key 认证校验正常
