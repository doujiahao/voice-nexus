# Tasks: 呼入链路统一服务端

## Task 1: 数据库表设计与实体层

**Description**: 设计并创建呼入链路所需的数据库表，编写 MyBatis-Plus Entity/Mapper。

**Deliverables**:
- [ ] call_session 表（通话会话主表）
- [ ] call_turn 表（通话分句/轮次表）
- [ ] agent_status 表（坐席状态表，扩展 JeecgBoot 用户表）
- [ ] skill_group 表（技能组配置表）
- [ ] skill_group_agent 表（技能组-坐席关联表）
- [ ] 对应的 Entity、Mapper、XML

**Dependencies**: 无

---

## Task 2: 账号与权限模块

**Description**: 基于 JeecgBoot 现有用户体系，扩展坐席角色和话务相关权限。

**Deliverables**:
- [ ] 坐席角色定义与菜单权限配置
- [ ] 管理员角色权限配置
- [ ] 内部服务认证（X-Internal-Key 校验拦截器）
- [ ] Token 黑名单机制（Redis）

**Dependencies**: Task 1

---

## Task 3: 坐席状态管理

**Description**: 实现坐席在线状态的实时管理（Redis 热数据 + DB 持久化）。

**Deliverables**:
- [ ] 坐席状态枚举（离线/在线/休息/振铃中/通话中）
- [ ] 状态切换 Service + Controller（POST /api/v1/agent/status）
- [ ] Redis 坐席状态缓存读写
- [ ] 状态变更 DB 日志记录

**Dependencies**: Task 1, Task 2

---

## Task 4: 呼入路由引擎

**Description**: 实现来电分配逻辑，根据技能组和坐席状态选择目标坐席。

**Deliverables**:
- [ ] 路由申请接口（POST /internal/calls/{fs_call_id}/route）
- [ ] 技能组匹配策略（轮询/最长空闲）
- [ ] 分布式锁防重复分配（Redis）
- [ ] 路由超时处理

**Dependencies**: Task 1, Task 3

---

## Task 5: 通话会话管理

**Description**: 管理通话从创建到结束的完整生命周期。

**Deliverables**:
- [ ] 创建通话会话（路由成功时）
- [ ] 更新会话状态（振铃→接听→通话中→结束）
- [ ] 坐席操作接口（POST /api/v1/calls/{id}/actions：accept/hangup）
- [ ] 通话结束事件接口（POST /internal/calls/{fs_call_id}/events）
- [ ] Redis 通话热缓存

**Dependencies**: Task 1, Task 4

---

## Task 6: WebSocket 实时推送

**Description**: 实现服务端到 Web 前端的实时事件推送通道。

**Deliverables**:
- [ ] WebSocket 端点（/ws/call-events）
- [ ] 连接管理（JWT 鉴权、心跳、重连）
- [ ] Redis 连接映射（支持多实例部署）
- [ ] 事件推送：振铃、转写、分析结果、通话结束

**Dependencies**: Task 2

---

## Task 7: 音频处理管道

**Description**: 接收 freeswichService 上行的音频帧，完成解码、分离、断句。

**Deliverables**:
- [ ] 音频接收 WebSocket 端点（/internal/ws/audio）
- [ ] AAC/PCM 解码
- [ ] 双声道分离（客户/坐席）
- [ ] VAD 语音活动检测与断句
- [ ] WAV 封装并存储到 MinIO

**Dependencies**: Task 1, Task 5

---

## Task 8: AI 编排层

**Description**: 调用 Call_Handing_Gateway 的各 AI 接口，编排分析流程。

**Deliverables**:
- [ ] ASR 调用（POST /api/v1/asr/transcribe）
- [ ] NLP 调用（intent/emotion/entities）
- [ ] AgentAssist 调用（坐席辅助建议）
- [ ] 摘要调用（通话结束后）
- [ ] 结果写入 DB/Redis + 触发 WebSocket 推送

**Dependencies**: Task 5, Task 6, Task 7

---

## Task 9: 管理员功能

**Description**: 技能组管理、坐席管理等后台配置接口。

**Deliverables**:
- [ ] 技能组 CRUD 接口
- [ ] 技能组-坐席分配接口
- [ ] 坐席列表/状态查询接口（管理视角）
- [ ] JeecgBoot 菜单权限数据初始化 SQL

**Dependencies**: Task 1, Task 2

---

## Task 10: 集成联调与异常处理

**Description**: 全链路联调，补充异常恢复和边界处理。

**Deliverables**:
- [ ] 服务端崩溃恢复（基于 DB/Redis 状态重建未结束通话）
- [ ] freeswichService 断连处理
- [ ] WebSocket 断连重连后状态同步
- [ ] 全链路日志（通话事件追踪）
- [ ] 接口联调测试

**Dependencies**: Task 4, Task 5, Task 6, Task 7, Task 8, Task 9
