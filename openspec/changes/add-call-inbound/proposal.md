# Proposal: 智能客服话务平台 — 统一服务端开发

## Intent

为电力行业智能客服话务平台构建统一服务端（JavaService），作为整个话务系统的唯一入口、状态中心、存储入口和业务编排层。本期聚焦呼入链路，同时在数据库层面为呼出、IVR、质检等后续功能预留扩展。

## Why

- 当前无统一服务端，各子系统（Web、freeswichService、Call_Handing_Gateway）各自为政，状态分散
- 需要一个中心节点统管账号权限、呼入路由、通话会话、音频处理、AI 编排、数据存储和实时推送
- 其他子系统已有各自团队开发，本项目只需聚焦服务端
- 项目经理已框定四阶段建设路线（基础语音→智能机器人→外呼→大模型工单），本期对应第一阶段 + 第四阶段部分能力

## Architecture Decision

**单体应用**：基于 JeecgBoot `jeecg-system-start` 统一启动，新增 `jeecg-boot-module-call` 子模块。不引入 Spring Cloud / Nacos / Gateway 等微服务组件。

理由：
- ≤100 并发坐席，单机性能足够
- 私有化部署，运维复杂度需最小化
- 服务边界已在进程级别划好（freeswichService / Call_Handing_Gateway 是独立进程）

## Scope

### In Scope（本期开发）

- 账号与权限模块（JWT + RBAC，基于 JeecgBoot 用户体系扩展）
- 坐席状态管理（Redis 实时 + DB 回写）
- 呼入路由引擎（技能组匹配 + 分布式锁防重复分配）
- 通话会话管理（call_session / call_participant / call_turn 生命周期）
- 音频接收与存储（接收 freeswichService 上行的 WAV 语音段，存储 MinIO）
- AI 编排层（调用 Gateway 的 ASR/NLP/AgentAssist/摘要接口，维护通话全量上下文）
- WebSocket 实时推送（振铃/转写/分析/结束事件）
- 内部接口（供 freeswichService 调用：路由申请、事件上报、音频上行）
- 客户档案管理（来电弹屏匹配、客户信息 CRUD）
- 工单管理（通话结束后自动/手动创建工单，大模型辅助填充）
- 数据库表设计（含扩展预留）与 MyBatis-Plus 实体
- Redis 缓存策略
- MinIO 音频存储

### In Scope（本期预留表结构，不写业务逻辑）

- 外呼活动/任务表（outbound_campaign / outbound_task_number）
- IVR 流程定义表（ivr_flow / ivr_node）
- 质检评分表（quality_inspection）
- 通话状态枚举预留：HOLDING / TRANSFERRING / CONFERENCING
- call_participant 角色预留：SUPERVISOR / TRANSFER_TARGET

### Out of Scope

- Web 前端开发（其他团队）
- freeswichService 开发（其他团队）
- Call_Handing_Gateway 开发（其他团队）
- FreeSWITCH 配置与运维
- AI 模型训练与部署
- 外呼业务逻辑实现
- IVR 机器人引擎
- 质检业务逻辑
- 报表与统计
- 班长监听/强插/强拆（预留角色，不实现）

## Constraints

- 单体架构，基于 JeecgBoot 脚手架 Maven 多模块结构
- 私有化部署，≤100 并发坐席
- API 响应 <4s，实时推送尽量 <2s
- 内部服务间使用 X-Internal-Key 认证
- DB/Redis/MinIO 只允许本服务端直接访问

## Success Criteria

- freeswichService 可通过内部接口完成路由申请、音频上行、事件上报
- Web 前端可通过 REST + WebSocket 完成登录、坐席操作、接收实时事件
- 来电时自动匹配客户档案并弹屏
- 通话结束后可生成工单（大模型辅助提取关键信息）
- 100 路并发通话下服务端稳定运行
- 通话全链路（呼入→振铃→接听→实时处理→挂断→摘要→工单）可走通

## References

- 技术架构设计：`docs/design-docs/呼入链路工程化架构设计.md`
- 产品需求规格：`docs/product-specs/呼入链路.md`
- 项目建设框定：`docs/design-docs/自研话务平台建设内容框定.docx`
- 禅道项目：「智能客服话务平台」（http://192.168.1.22:8089）
