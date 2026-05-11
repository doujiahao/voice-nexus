# Proposal: 呼入链路统一服务端开发

## Intent

为电力行业客服话务系统构建统一服务端（JavaService），作为整个呼入链路的唯一入口、状态中心、存储入口和业务编排层。

## Why

- 当前无统一服务端，各子系统（Web、freeswichService、Call_Handing_Gateway）各自为政，状态分散
- 需要一个中心节点统管账号权限、呼入路由、通话会话、音频处理、AI 编排、数据存储和实时推送
- 其他子系统已有各自团队开发，本项目只需聚焦服务端

## Scope

### In Scope

- 账号与权限模块（JWT + RBAC，基于 JeecgBoot 用户体系扩展）
- 坐席状态管理（Redis 实时 + DB 回写）
- 呼入路由引擎（技能组匹配 + 分布式锁防重复分配）
- 通话会话管理（call_session / call_turn 生命周期）
- 音频处理管道（AAC/PCM 解码 → 声道分离 → VAD 断句 → WAV 封装）
- AI 编排层（调用 Gateway 的 ASR/NLP/AgentAssist/摘要接口）
- WebSocket 实时推送（振铃/转写/分析/结束事件）
- 内部接口（供 freeswichService 调用：路由申请、事件上报、音频上行）
- 数据库表设计与 MyBatis-Plus 实体
- Redis 缓存策略
- MinIO 音频存储

### Out of Scope

- Web 前端开发（其他团队）
- freeswichService 开发（其他团队）
- Call_Handing_Gateway 开发（其他团队）
- FreeSWITCH 配置与运维
- AI 模型训练与部署
- 外呼、工单、报表、质检等非呼入链路功能

## Constraints

- 基于 JeecgBoot 脚手架，遵循其 Maven 多模块结构
- 私有化部署，≤100 并发坐席
- API 响应 <4s，实时推送尽量 <2s
- 内部服务间使用 X-Internal-Key 认证
- DB/Redis/MinIO 只允许本服务端直接访问

## Success Criteria

- freeswichService 可通过内部接口完成路由申请、音频上行、事件上报
- Web 前端可通过 REST + WebSocket 完成登录、坐席操作、接收实时事件
- 100 路并发通话下服务端稳定运行
- 通话全链路（呼入→振铃→接听→实时处理→挂断→摘要）可走通

## References

- 技术架构设计：`docs/design-docs/呼入链路工程化架构设计.md`
- 产品需求规格：`docs/product-specs/呼入链路.md`
