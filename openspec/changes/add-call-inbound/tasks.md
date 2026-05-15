# Tasks: 智能客服话务平台 — 统一服务端

## Task 1: 模块骨架 + 数据库表设计

**Description**: 新建 Maven 子模块，设计并创建全部数据库表，编写 MyBatis-Plus Entity/Mapper。

**Deliverables**:
- [ ] 新建 `jeecg-boot-module-call`（parent → jeecg-boot-module）
- [ ] 通话域：call_session / call_participant / call_turn / call_event_log / call_tag
- [ ] 坐席域：agent_profile / agent_status_log / skill_group / skill_group_agent
- [ ] 客户域：customer / customer_contact
- [ ] 工单域：work_order / work_order_item
- [ ] 预留表：outbound_campaign / outbound_task_number / ivr_flow / ivr_node / quality_inspection
- [ ] 对应的 Entity、Mapper、XML

**Dependencies**: 无

---

## Task 2: 账号权限 + 坐席状态

**Description**: 基于 JeecgBoot 用户体系扩展坐席角色，实现坐席状态管理。

**Deliverables**:
- [x] 坐席角色定义与菜单权限配置
- [ ] 内部服务认证（X-Internal-Key 校验拦截器）
- [ ] 坐席状态枚举（OFFLINE / ONLINE / REST / RINGING / TALKING / HOLDING / WRAP_UP）
- [ ] 状态切换 Service + Controller
- [ ] Redis 坐席状态缓存读写
- [ ] 状态变更 DB 日志记录

**Dependencies**: Task 1

---

## Task 3: 呼入路由 + 排队

**Description**: 实现来电分配逻辑，含排队等待机制。

**Deliverables**:
- [ ] 路由申请接口（POST /internal/calls/{fs_call_id}/route）
- [ ] 技能组匹配策略（最长空闲优先）
- [ ] 分布式锁防重复分配（Redisson）
- [ ] 来电排队队列（Redis，按技能组分队列，FIFO）
- [ ] 坐席空闲时自动从队列取出下一个来电
- [ ] 排队超时处理（通知 freeswichService）
- [ ] 路由超时处理

**Dependencies**: Task 1, Task 2

---

## Task 4: 通话会话管理 + 客户匹配

**Description**: 管理通话从创建到结束的完整生命周期，含来电客户匹配。

**Deliverables**:
- [ ] 来电客户匹配（根据来电号码查 customer_contact）
- [ ] 创建通话会话（含 call_participant 写入）
- [ ] 更新会话状态（QUEUING→RINGING→ANSWERED→TALKING→ENDED）
- [ ] 坐席操作接口（POST /api/v1/calls/{id}/actions：accept/hangup）
- [ ] 通话结束事件接口（POST /internal/calls/{fs_call_id}/events）
- [ ] Redis 通话热缓存

**Dependencies**: Task 1, Task 3

---

## Task 5: WebSocket 实时推送

**Description**: 实现服务端到 Web 前端的实时事件推送通道。

**Deliverables**:
- [ ] WebSocket 端点（/ws/call-events）
- [ ] 连接管理（JWT 鉴权、心跳、重连）
- [ ] Redis 连接映射（支持多实例部署）
- [ ] 事件推送：振铃（含客户信息）、转写、分析结果、通话结束

**Dependencies**: Task 2

---

## Task 6: 音频接收与存储

**Description**: 接收 freeswichService 上行的 WAV 语音段（Protobuf AudioFrame），存储到 MinIO。

> 注意：AAC 解码、声道分离、VAD 断句、WAV 封装均由 freeswichService 完成。
> 服务端收到的是已切好的单声道 WAV 语音段（每段一句话），带 speaker_role 和 sequence_number。

**Deliverables**:
- [ ] 音频接收 WebSocket 端点（/internal/ws/audio）
- [ ] Protobuf AudioFrame 反序列化（提取 WAV + speaker_role + sequence + is_last）
- [ ] WAV 存储到 MinIO（按 session_id/turn 组织路径）
- [ ] 录音文件预签名 URL 生成接口（供前端回放）

**Dependencies**: Task 1, Task 4

---

## Task 7: AI 编排层

**Description**: 调用 Call_Handing_Gateway 的各 AI 接口，维护通话上下文，编排分析流程。

**Deliverables**:
- [ ] 通话上下文管理（Redis 维护活跃 session 全量 turns）
- [ ] ASR 调用（POST /api/v1/asr/transcribe）
- [ ] NLP 调用（intent/emotion/entities，传入全量 turns）
- [ ] AgentAssist 调用（坐席辅助建议，传入全量 turns）
- [ ] 摘要调用（通话结束后，传入全量 turns）
- [ ] 通话标签自动生成（基于 AI 分析结果）
- [ ] 结果写入 DB/Redis + 触发 WebSocket 推送

**Dependencies**: Task 4, Task 5, Task 6

---

## Task 8: 客户档案管理

**Description**: 客户信息 CRUD，支持多联系方式，来电弹屏匹配。

**Deliverables**:
- [ ] 客户档案 CRUD 接口
- [ ] 客户联系方式管理（多号码）
- [ ] 客户历史通话记录查询

**Dependencies**: Task 1, Task 2

---

## Task 9: 通话记录查询

**Description**: 提供通话历史查询、详情、录音回放能力。

**Deliverables**:
- [ ] 通话记录列表接口（分页、筛选：时间/坐席/状态）
- [ ] 通话记录详情接口（完整转写、情绪、意图、实体、摘要）
- [ ] 录音回放接口（MinIO 预签名 URL + 文本同步定位数据）
- [ ] 通话标签管理接口（查看、手动编辑）

**Dependencies**: Task 4, Task 6, Task 7

---

## Task 10: 工单管理

**Description**: 工单 CRUD、大模型辅助填充、状态流转。

**Deliverables**:
- [ ] 工单 CRUD 接口
- [ ] 工单状态流转（待处理/处理中/已完成/已关闭）
- [ ] 通话结束后大模型辅助生成工单（Prompt 提取关键信息）
- [ ] 工单与通话记录关联

**Dependencies**: Task 4, Task 7

---

## Task 11: 管理员功能

**Description**: 技能组管理、坐席管理等后台配置接口。

**Deliverables**:
- [ ] 技能组 CRUD 接口
- [ ] 技能组-坐席分配接口
- [ ] 管理员坐席列表/状态查询接口
- [x] JeecgBoot 菜单权限数据初始化 SQL

**Dependencies**: Task 1, Task 2

---

## Task 12: 异常处理 + 集成联调

**Description**: 全链路联调，补充异常恢复和边界处理。

**Deliverables**:
- [ ] 服务端崩溃恢复（基于 DB/Redis 状态重建未结束通话）
- [ ] freeswichService 断连处理
- [ ] WebSocket 断连重连后状态同步
- [ ] 全链路日志（call_session_id 贯穿）
- [ ] 接口联调测试（与前端/freeswichService/Gateway）

**Dependencies**: Task 3, Task 4, Task 5, Task 6, Task 7, Task 8, Task 9, Task 10, Task 11
