-- ============================================================
-- 智能客服话务平台 - 数据库表设计 DDL
-- PostgreSQL (pgvector)，数据库：voice_nexus
-- 与 JeecgBoot 系统表同库，agent_profile.user_id 关联 sys_user.id
-- 公共字段：id, create_by, create_time, update_by, update_time
-- ============================================================

-- ============================================================
-- 一、通话域
-- ============================================================

-- 1.1 通话会话主表
CREATE TABLE call_session (
    id              VARCHAR(36)  NOT NULL,
    fs_call_id      VARCHAR(64),
    direction       VARCHAR(10)  NOT NULL,                -- INBOUND/OUTBOUND
    status          VARCHAR(20)  NOT NULL DEFAULT 'QUEUING', -- QUEUING/RINGING/ANSWERED/TALKING/HOLDING/TRANSFERRING/ENDED
    customer_id     VARCHAR(36),
    customer_phone  VARCHAR(20),
    called_number   VARCHAR(20),
    skill_group_id  VARCHAR(36),
    agent_id        VARCHAR(36),
    work_order_id   VARCHAR(36),
    ivr_flow_id     VARCHAR(36),                          -- 预留
    campaign_id     VARCHAR(36),                          -- 预留
    queue_enter_time TIMESTAMP,
    ring_time       TIMESTAMP,
    answer_time     TIMESTAMP,
    end_time        TIMESTAMP,
    duration_sec    INTEGER,
    hangup_cause    VARCHAR(30),                          -- NORMAL/TIMEOUT/CANCEL/SYSTEM_ERROR/AGENT_HANGUP/CUSTOMER_HANGUP
    ended_by        VARCHAR(20),                          -- CUSTOMER/AGENT/SYSTEM
    summary         TEXT,
    audio_url       VARCHAR(500),
    remark          VARCHAR(500),
    del_flag        SMALLINT     NOT NULL DEFAULT 0,
    create_by       VARCHAR(50),
    create_time     TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_call_session_fs_call_id ON call_session(fs_call_id);
CREATE INDEX idx_call_session_customer_id ON call_session(customer_id);
CREATE INDEX idx_call_session_agent_id ON call_session(agent_id);
CREATE INDEX idx_call_session_status ON call_session(status);
CREATE INDEX idx_call_session_direction ON call_session(direction);
CREATE INDEX idx_call_session_create_time ON call_session(create_time);
CREATE INDEX idx_call_session_skill_group_id ON call_session(skill_group_id);

COMMENT ON TABLE call_session IS '通话会话主表';
COMMENT ON COLUMN call_session.direction IS '通话方向: INBOUND/OUTBOUND';
COMMENT ON COLUMN call_session.status IS '会话状态: QUEUING/RINGING/ANSWERED/TALKING/HOLDING/TRANSFERRING/ENDED';
COMMENT ON COLUMN call_session.ivr_flow_id IS 'IVR流程ID（预留）';
COMMENT ON COLUMN call_session.campaign_id IS '外呼活动ID（预留）';

-- 1.2 通话参与者表
CREATE TABLE call_participant (
    id               VARCHAR(36)  NOT NULL,
    session_id       VARCHAR(36)  NOT NULL,
    user_id          VARCHAR(36),
    participant_type VARCHAR(20)  NOT NULL,               -- CUSTOMER/AGENT/SUPERVISOR/TRANSFER_TARGET
    role             VARCHAR(20)  NOT NULL,               -- INITIATOR/RECEIVER/MONITOR/COACH
    phone            VARCHAR(20),
    extension        VARCHAR(20),
    join_time        TIMESTAMP,
    leave_time       TIMESTAMP,
    leave_reason     VARCHAR(30),                         -- HANGUP/TRANSFER/KICK
    del_flag         SMALLINT     NOT NULL DEFAULT 0,
    create_by        VARCHAR(50),
    create_time      TIMESTAMP,
    update_by        VARCHAR(50),
    update_time      TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_call_participant_session_id ON call_participant(session_id);
CREATE INDEX idx_call_participant_user_id ON call_participant(user_id);
CREATE INDEX idx_call_participant_type ON call_participant(participant_type);

COMMENT ON TABLE call_participant IS '通话参与者表';

-- 1.3 通话分句/轮次表
CREATE TABLE call_turn (
    id              VARCHAR(36)  NOT NULL,
    session_id      VARCHAR(36)  NOT NULL,
    turn_index      INTEGER      NOT NULL,
    speaker_id      VARCHAR(64),
    speaker_role    VARCHAR(10)  NOT NULL,                -- customer/agent
    text            TEXT,
    corrected_text  TEXT,
    start_time      TIMESTAMP,
    end_time        TIMESTAMP,
    duration_ms     INTEGER,
    audio_url       VARCHAR(500),
    confidence      NUMERIC(5,4),
    intent          VARCHAR(50),
    intent_confidence NUMERIC(5,4),
    emotion         VARCHAR(20),                          -- NEUTRAL/HAPPY/ANGRY/SAD/ANXIOUS
    sentiment_score NUMERIC(5,4),
    urgency         VARCHAR(10),                          -- LOW/MEDIUM/HIGH
    entities        JSONB,
    suggestions     JSONB,
    del_flag        SMALLINT     NOT NULL DEFAULT 0,
    create_by       VARCHAR(50),
    create_time     TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_call_turn_session_id ON call_turn(session_id);
CREATE INDEX idx_call_turn_session_index ON call_turn(session_id, turn_index);
CREATE INDEX idx_call_turn_speaker_role ON call_turn(speaker_role);

COMMENT ON TABLE call_turn IS '通话分句/轮次表';

-- 1.4 通话事件日志表
CREATE TABLE call_event_log (
    id              VARCHAR(36)  NOT NULL,
    session_id      VARCHAR(36)  NOT NULL,
    event_type      VARCHAR(30)  NOT NULL,               -- CALL_STARTED/QUEUED/RINGING/ANSWERED/HOLD/UNHOLD/TRANSFER/CALL_ENDED
    event_time      TIMESTAMP    NOT NULL,
    operator_id     VARCHAR(36),
    operator_type   VARCHAR(20),                          -- SYSTEM/AGENT/CUSTOMER/FSS
    detail          JSONB,
    create_by       VARCHAR(50),
    create_time     TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_call_event_log_session_id ON call_event_log(session_id);
CREATE INDEX idx_call_event_log_event_type ON call_event_log(event_type);
CREATE INDEX idx_call_event_log_event_time ON call_event_log(event_time);

COMMENT ON TABLE call_event_log IS '通话事件日志表';

-- 1.5 通话标签表
CREATE TABLE call_tag (
    id              VARCHAR(36)  NOT NULL,
    session_id      VARCHAR(36)  NOT NULL,
    tag_name        VARCHAR(50)  NOT NULL,
    source          VARCHAR(10)  NOT NULL,                -- AUTO/MANUAL
    confidence      NUMERIC(5,4),
    create_by       VARCHAR(50),
    create_time     TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_call_tag_session_id ON call_tag(session_id);
CREATE INDEX idx_call_tag_tag_name ON call_tag(tag_name);

COMMENT ON TABLE call_tag IS '通话标签表';

-- ============================================================
-- 二、坐席域
-- ============================================================

-- 2.1 坐席扩展信息表（关联 sys_user）
CREATE TABLE agent_profile (
    id              VARCHAR(36)  NOT NULL,
    user_id         VARCHAR(36)  NOT NULL,               -- 关联 sys_user.id
    agent_no        VARCHAR(20),
    extension       VARCHAR(20),
    status          VARCHAR(20)  NOT NULL DEFAULT 'OFFLINE', -- OFFLINE/ONLINE/REST/RINGING/TALKING/HOLDING/WRAP_UP
    status_since    TIMESTAMP,
    max_concurrent  INTEGER      NOT NULL DEFAULT 1,
    last_idle_time  TIMESTAMP,
    del_flag        SMALLINT     NOT NULL DEFAULT 0,
    create_by       VARCHAR(50),
    create_time     TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_agent_profile_user_id ON agent_profile(user_id);
CREATE INDEX idx_agent_profile_status ON agent_profile(status);
CREATE INDEX idx_agent_profile_agent_no ON agent_profile(agent_no);

COMMENT ON TABLE agent_profile IS '坐席扩展信息表';
COMMENT ON COLUMN agent_profile.user_id IS '关联 sys_user.id';

-- 2.2 坐席状态变更日志表
CREATE TABLE agent_status_log (
    id              VARCHAR(36)  NOT NULL,
    agent_id        VARCHAR(36)  NOT NULL,
    user_id         VARCHAR(36)  NOT NULL,
    from_status     VARCHAR(20)  NOT NULL,
    to_status       VARCHAR(20)  NOT NULL,
    change_time     TIMESTAMP    NOT NULL,
    duration_sec    INTEGER,
    reason          VARCHAR(100),
    create_by       VARCHAR(50),
    create_time     TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_agent_status_log_agent_id ON agent_status_log(agent_id);
CREATE INDEX idx_agent_status_log_change_time ON agent_status_log(change_time);

COMMENT ON TABLE agent_status_log IS '坐席状态变更日志表';

-- 2.3 技能组表
CREATE TABLE skill_group (
    id              VARCHAR(36)  NOT NULL,
    group_name      VARCHAR(50)  NOT NULL,
    group_code      VARCHAR(30)  NOT NULL,
    description     VARCHAR(200),
    route_strategy  VARCHAR(20)  NOT NULL DEFAULT 'LONGEST_IDLE', -- LONGEST_IDLE/ROUND_ROBIN/RANDOM
    queue_max_size  INTEGER      NOT NULL DEFAULT 50,
    queue_timeout_sec INTEGER    NOT NULL DEFAULT 60,
    ring_timeout_sec INTEGER     NOT NULL DEFAULT 30,
    enabled         SMALLINT     NOT NULL DEFAULT 1,
    del_flag        SMALLINT     NOT NULL DEFAULT 0,
    create_by       VARCHAR(50),
    create_time     TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_skill_group_code ON skill_group(group_code);

COMMENT ON TABLE skill_group IS '技能组表';

-- 2.4 技能组-坐席关联表
CREATE TABLE skill_group_agent (
    id              VARCHAR(36)  NOT NULL,
    skill_group_id  VARCHAR(36)  NOT NULL,
    agent_id        VARCHAR(36)  NOT NULL,
    skill_level     INTEGER      NOT NULL DEFAULT 1,     -- 1-5，数字越大优先级越高
    del_flag        SMALLINT     NOT NULL DEFAULT 0,
    create_by       VARCHAR(50),
    create_time     TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_skill_group_agent ON skill_group_agent(skill_group_id, agent_id);
CREATE INDEX idx_skill_group_agent_agent_id ON skill_group_agent(agent_id);

COMMENT ON TABLE skill_group_agent IS '技能组-坐席关联表';

-- ============================================================
-- 三、客户域
-- ============================================================

-- 3.1 客户档案表
CREATE TABLE customer (
    id              VARCHAR(36)  NOT NULL,
    customer_no     VARCHAR(30),
    name            VARCHAR(50),
    id_card         VARCHAR(20),
    gender          VARCHAR(10),                          -- M/F/UNKNOWN
    area_code       VARCHAR(20),
    address         VARCHAR(200),
    meter_no        VARCHAR(30),
    account_no      VARCHAR(30),
    voltage_level   VARCHAR(10),
    customer_type   VARCHAR(20),                          -- RESIDENTIAL/COMMERCIAL/INDUSTRIAL
    remark          VARCHAR(500),
    del_flag        SMALLINT     NOT NULL DEFAULT 0,
    create_by       VARCHAR(50),
    create_time     TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_customer_customer_no ON customer(customer_no);
CREATE INDEX idx_customer_meter_no ON customer(meter_no);
CREATE INDEX idx_customer_account_no ON customer(account_no);
CREATE INDEX idx_customer_name ON customer(name);

COMMENT ON TABLE customer IS '客户档案表';
COMMENT ON COLUMN customer.meter_no IS '电表号（电力行业）';
COMMENT ON COLUMN customer.account_no IS '户号（电力行业）';

-- 3.2 客户联系方式表
CREATE TABLE customer_contact (
    id              VARCHAR(36)  NOT NULL,
    customer_id     VARCHAR(36)  NOT NULL,
    contact_type    VARCHAR(10)  NOT NULL,                -- PHONE/MOBILE/FAX
    contact_value   VARCHAR(30)  NOT NULL,
    is_primary      SMALLINT     NOT NULL DEFAULT 0,
    remark          VARCHAR(100),
    del_flag        SMALLINT     NOT NULL DEFAULT 0,
    create_by       VARCHAR(50),
    create_time     TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_customer_contact_customer_id ON customer_contact(customer_id);
CREATE INDEX idx_customer_contact_value ON customer_contact(contact_value);

COMMENT ON TABLE customer_contact IS '客户联系方式表';

-- ============================================================
-- 四、工单域
-- ============================================================

-- 4.1 工单主表
CREATE TABLE work_order (
    id              VARCHAR(36)  NOT NULL,
    order_no        VARCHAR(30)  NOT NULL,
    session_id      VARCHAR(36),
    customer_id     VARCHAR(36),
    agent_id        VARCHAR(36),
    assigned_to     VARCHAR(36),
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    category        VARCHAR(30),                          -- CONSULT/COMPLAINT/REPAIR/BILLING/OTHER
    priority        VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM', -- LOW/MEDIUM/HIGH/URGENT
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING/PROCESSING/COMPLETED/CLOSED
    source          VARCHAR(20)  NOT NULL DEFAULT 'CALL', -- CALL/MANUAL/AI_AUTO
    customer_intent VARCHAR(100),
    key_info        JSONB,
    resolution      TEXT,
    resolved_time   TIMESTAMP,
    closed_time     TIMESTAMP,
    del_flag        SMALLINT     NOT NULL DEFAULT 0,
    create_by       VARCHAR(50),
    create_time     TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_work_order_no ON work_order(order_no);
CREATE INDEX idx_work_order_session_id ON work_order(session_id);
CREATE INDEX idx_work_order_customer_id ON work_order(customer_id);
CREATE INDEX idx_work_order_agent_id ON work_order(agent_id);
CREATE INDEX idx_work_order_assigned_to ON work_order(assigned_to);
CREATE INDEX idx_work_order_status ON work_order(status);
CREATE INDEX idx_work_order_priority ON work_order(priority);
CREATE INDEX idx_work_order_create_time ON work_order(create_time);

COMMENT ON TABLE work_order IS '工单主表';

-- 4.2 工单处理记录表
CREATE TABLE work_order_item (
    id              VARCHAR(36)  NOT NULL,
    order_id        VARCHAR(36)  NOT NULL,
    operator_id     VARCHAR(36)  NOT NULL,
    action          VARCHAR(20)  NOT NULL,                -- CREATE/ASSIGN/PROCESS/RESOLVE/CLOSE/REOPEN/COMMENT
    from_status     VARCHAR(20),
    to_status       VARCHAR(20),
    content         TEXT,
    operate_time    TIMESTAMP    NOT NULL,
    create_by       VARCHAR(50),
    create_time     TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_work_order_item_order_id ON work_order_item(order_id);
CREATE INDEX idx_work_order_item_operator_id ON work_order_item(operator_id);
CREATE INDEX idx_work_order_item_operate_time ON work_order_item(operate_time);

COMMENT ON TABLE work_order_item IS '工单处理记录表';

-- ============================================================
-- 五、预留表（只建结构，本期不写业务逻辑）
-- ============================================================

-- 5.1 外呼活动表
CREATE TABLE outbound_campaign (
    id              VARCHAR(36)  NOT NULL,
    campaign_name   VARCHAR(100) NOT NULL,
    campaign_type   VARCHAR(20)  NOT NULL,               -- PREVIEW/PREDICTIVE/MANUAL
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT', -- DRAFT/RUNNING/PAUSED/COMPLETED/CANCELLED
    skill_group_id  VARCHAR(36),
    start_time      TIMESTAMP,
    end_time        TIMESTAMP,
    total_count     INTEGER      NOT NULL DEFAULT 0,
    completed_count INTEGER      NOT NULL DEFAULT 0,
    success_count   INTEGER      NOT NULL DEFAULT 0,
    description     VARCHAR(500),
    del_flag        SMALLINT     NOT NULL DEFAULT 0,
    create_by       VARCHAR(50),
    create_time     TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_outbound_campaign_status ON outbound_campaign(status);

COMMENT ON TABLE outbound_campaign IS '外呼活动表（预留）';

-- 5.2 外呼号码池表
CREATE TABLE outbound_task_number (
    id              VARCHAR(36)  NOT NULL,
    campaign_id     VARCHAR(36)  NOT NULL,
    phone           VARCHAR(20)  NOT NULL,
    customer_id     VARCHAR(36),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING/CALLING/SUCCESS/FAILED/CANCELLED
    attempt_count   INTEGER      NOT NULL DEFAULT 0,
    last_attempt_time TIMESTAMP,
    session_id      VARCHAR(36),
    remark          VARCHAR(200),
    del_flag        SMALLINT     NOT NULL DEFAULT 0,
    create_by       VARCHAR(50),
    create_time     TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_outbound_task_number_campaign_id ON outbound_task_number(campaign_id);
CREATE INDEX idx_outbound_task_number_status ON outbound_task_number(status);
CREATE INDEX idx_outbound_task_number_phone ON outbound_task_number(phone);

COMMENT ON TABLE outbound_task_number IS '外呼号码池表（预留）';

-- 5.3 IVR流程定义表
CREATE TABLE ivr_flow (
    id              VARCHAR(36)  NOT NULL,
    flow_name       VARCHAR(50)  NOT NULL,
    flow_code       VARCHAR(30)  NOT NULL,
    description     VARCHAR(200),
    status          VARCHAR(10)  NOT NULL DEFAULT 'DRAFT', -- DRAFT/ACTIVE/DISABLED
    version         INTEGER      NOT NULL DEFAULT 1,
    del_flag        SMALLINT     NOT NULL DEFAULT 0,
    create_by       VARCHAR(50),
    create_time     TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_ivr_flow_code ON ivr_flow(flow_code);

COMMENT ON TABLE ivr_flow IS 'IVR流程定义表（预留）';

-- 5.4 IVR节点表
CREATE TABLE ivr_node (
    id              VARCHAR(36)  NOT NULL,
    flow_id         VARCHAR(36)  NOT NULL,
    node_type       VARCHAR(20)  NOT NULL,               -- WELCOME/MENU/INPUT/TRANSFER/HANGUP/CONDITION
    node_name       VARCHAR(50)  NOT NULL,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    config          JSONB,
    next_node_id    VARCHAR(36),
    del_flag        SMALLINT     NOT NULL DEFAULT 0,
    create_by       VARCHAR(50),
    create_time     TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_ivr_node_flow_id ON ivr_node(flow_id);

COMMENT ON TABLE ivr_node IS 'IVR节点表（预留）';

-- 5.5 质检评分表
CREATE TABLE quality_inspection (
    id              VARCHAR(36)  NOT NULL,
    session_id      VARCHAR(36)  NOT NULL,
    inspector_id    VARCHAR(36),
    inspect_type    VARCHAR(10)  NOT NULL,                -- AUTO/MANUAL
    total_score     NUMERIC(5,2),
    score_detail    JSONB,
    comment         TEXT,
    inspect_time    TIMESTAMP,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING/COMPLETED/APPEALED
    del_flag        SMALLINT     NOT NULL DEFAULT 0,
    create_by       VARCHAR(50),
    create_time     TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_quality_inspection_session_id ON quality_inspection(session_id);
CREATE INDEX idx_quality_inspection_inspector_id ON quality_inspection(inspector_id);
CREATE INDEX idx_quality_inspection_status ON quality_inspection(status);

COMMENT ON TABLE quality_inspection IS '质检评分表（预留）';
