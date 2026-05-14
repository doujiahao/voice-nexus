-- ============================================================
-- AIRag 模块 PostgreSQL 建表脚本
-- 转换自 MySQL flyway: V3.8.0_2__airag_init_db.sql
-- 仅建表结构，不含示例数据（示例数据含大量JSON，按需导入）
-- ============================================================

-- ----------------------------
-- Table: airag_app
-- ----------------------------
CREATE TABLE IF NOT EXISTS airag_app (
    id varchar(36) NOT NULL,
    create_by varchar(50) DEFAULT NULL,
    create_time timestamp DEFAULT NULL,
    update_by varchar(50) DEFAULT NULL,
    update_time timestamp DEFAULT NULL,
    sys_org_code varchar(64) DEFAULT NULL,
    tenant_id varchar(32) DEFAULT NULL,
    name varchar(100) DEFAULT NULL,
    descr varchar(255) DEFAULT NULL,
    icon varchar(255) DEFAULT NULL,
    type varchar(50) DEFAULT NULL,
    prologue text DEFAULT NULL,
    prompt text DEFAULT NULL,
    model_id varchar(36) DEFAULT NULL,
    knowledge_ids varchar(255) DEFAULT NULL,
    flow_id varchar(32) DEFAULT NULL,
    status varchar(20) DEFAULT NULL,
    msg_num integer DEFAULT NULL,
    metadata varchar(500) DEFAULT NULL,
    preset_question text DEFAULT NULL,
    quick_command varchar(500) DEFAULT NULL,
    PRIMARY KEY (id)
);

COMMENT ON COLUMN airag_app.create_by IS '创建人';
COMMENT ON COLUMN airag_app.create_time IS '创建日期';
COMMENT ON COLUMN airag_app.update_by IS '更新人';
COMMENT ON COLUMN airag_app.update_time IS '更新日期';
COMMENT ON COLUMN airag_app.sys_org_code IS '所属部门';
COMMENT ON COLUMN airag_app.tenant_id IS '租户id';
COMMENT ON COLUMN airag_app.name IS '应用名称';
COMMENT ON COLUMN airag_app.descr IS '应用描述';
COMMENT ON COLUMN airag_app.icon IS '应用图标';
COMMENT ON COLUMN airag_app.type IS '应用类型';
COMMENT ON COLUMN airag_app.prologue IS '开场白';
COMMENT ON COLUMN airag_app.prompt IS '提示词';
COMMENT ON COLUMN airag_app.model_id IS '模型id';
COMMENT ON COLUMN airag_app.knowledge_ids IS '知识库';
COMMENT ON COLUMN airag_app.flow_id IS '流程';
COMMENT ON COLUMN airag_app.status IS '状态';
COMMENT ON COLUMN airag_app.msg_num IS '历史消息数';
COMMENT ON COLUMN airag_app.metadata IS '元数据';
COMMENT ON COLUMN airag_app.preset_question IS '预设问题';
COMMENT ON COLUMN airag_app.quick_command IS '快捷指令';

-- ----------------------------
-- Table: airag_flow
-- ----------------------------
CREATE TABLE IF NOT EXISTS airag_flow (
    id varchar(36) NOT NULL,
    create_by varchar(50) DEFAULT NULL,
    create_time timestamp DEFAULT NULL,
    update_by varchar(50) DEFAULT NULL,
    update_time timestamp DEFAULT NULL,
    sys_org_code varchar(64) DEFAULT NULL,
    tenant_id varchar(32) DEFAULT NULL,
    application_name varchar(50) DEFAULT NULL,
    name varchar(50) DEFAULT NULL,
    descr varchar(200) DEFAULT NULL,
    icon varchar(255) DEFAULT NULL,
    chain text DEFAULT NULL,
    design text DEFAULT NULL,
    status varchar(20) DEFAULT NULL,
    metadata text DEFAULT NULL,
    PRIMARY KEY (id)
);

COMMENT ON COLUMN airag_flow.create_by IS '创建人';
COMMENT ON COLUMN airag_flow.create_time IS '创建日期';
COMMENT ON COLUMN airag_flow.update_by IS '更新人';
COMMENT ON COLUMN airag_flow.update_time IS '更新日期';
COMMENT ON COLUMN airag_flow.sys_org_code IS '所属部门';
COMMENT ON COLUMN airag_flow.tenant_id IS '租户id';
COMMENT ON COLUMN airag_flow.application_name IS '应用名称';
COMMENT ON COLUMN airag_flow.name IS '名称';
COMMENT ON COLUMN airag_flow.descr IS '描述';
COMMENT ON COLUMN airag_flow.icon IS '应用图标';
COMMENT ON COLUMN airag_flow.chain IS '编排规则';
COMMENT ON COLUMN airag_flow.design IS '编排设计';
COMMENT ON COLUMN airag_flow.status IS '状态';
COMMENT ON COLUMN airag_flow.metadata IS '元数据';

-- ----------------------------
-- Table: airag_knowledge
-- ----------------------------
CREATE TABLE IF NOT EXISTS airag_knowledge (
    id varchar(36) NOT NULL,
    create_by varchar(50) DEFAULT NULL,
    create_time timestamp DEFAULT NULL,
    update_by varchar(50) DEFAULT NULL,
    update_time timestamp DEFAULT NULL,
    sys_org_code varchar(64) DEFAULT NULL,
    tenant_id varchar(32) DEFAULT NULL,
    name varchar(100) DEFAULT NULL,
    descr varchar(500) DEFAULT NULL,
    embed_id varchar(32) DEFAULT NULL,
    status varchar(32) DEFAULT NULL,
    PRIMARY KEY (id)
);

COMMENT ON COLUMN airag_knowledge.create_by IS '创建人';
COMMENT ON COLUMN airag_knowledge.create_time IS '创建日期';
COMMENT ON COLUMN airag_knowledge.update_by IS '更新人';
COMMENT ON COLUMN airag_knowledge.update_time IS '更新日期';
COMMENT ON COLUMN airag_knowledge.sys_org_code IS '所属部门';
COMMENT ON COLUMN airag_knowledge.tenant_id IS '租户id';
COMMENT ON COLUMN airag_knowledge.name IS '知识库名称';
COMMENT ON COLUMN airag_knowledge.descr IS '描述';
COMMENT ON COLUMN airag_knowledge.embed_id IS '向量模型id';
COMMENT ON COLUMN airag_knowledge.status IS '状态';

-- ----------------------------
-- Table: airag_knowledge_doc
-- ----------------------------
CREATE TABLE IF NOT EXISTS airag_knowledge_doc (
    id varchar(36) NOT NULL,
    create_by varchar(50) DEFAULT NULL,
    create_time timestamp DEFAULT NULL,
    update_by varchar(50) DEFAULT NULL,
    update_time timestamp DEFAULT NULL,
    sys_org_code varchar(64) DEFAULT NULL,
    tenant_id varchar(32) DEFAULT NULL,
    knowledge_id varchar(32) DEFAULT NULL,
    title varchar(100) DEFAULT NULL,
    type varchar(32) DEFAULT NULL,
    content text DEFAULT NULL,
    status varchar(32) DEFAULT NULL,
    metadata text DEFAULT NULL,
    PRIMARY KEY (id)
);

COMMENT ON COLUMN airag_knowledge_doc.create_by IS '创建人';
COMMENT ON COLUMN airag_knowledge_doc.create_time IS '创建日期';
COMMENT ON COLUMN airag_knowledge_doc.update_by IS '更新人';
COMMENT ON COLUMN airag_knowledge_doc.update_time IS '更新日期';
COMMENT ON COLUMN airag_knowledge_doc.sys_org_code IS '所属部门';
COMMENT ON COLUMN airag_knowledge_doc.tenant_id IS '租户id';
COMMENT ON COLUMN airag_knowledge_doc.knowledge_id IS '知识库id';
COMMENT ON COLUMN airag_knowledge_doc.title IS '标题';
COMMENT ON COLUMN airag_knowledge_doc.type IS '类型';
COMMENT ON COLUMN airag_knowledge_doc.content IS '内容';
COMMENT ON COLUMN airag_knowledge_doc.status IS '状态';
COMMENT ON COLUMN airag_knowledge_doc.metadata IS '元数据';

-- ----------------------------
-- Table: airag_model
-- ----------------------------
CREATE TABLE IF NOT EXISTS airag_model (
    id varchar(36) NOT NULL,
    create_by varchar(50) DEFAULT NULL,
    create_time timestamp DEFAULT NULL,
    update_by varchar(50) DEFAULT NULL,
    update_time timestamp DEFAULT NULL,
    sys_org_code varchar(64) DEFAULT NULL,
    tenant_id varchar(32) DEFAULT NULL,
    name varchar(100) DEFAULT NULL,
    provider varchar(50) DEFAULT NULL,
    model_name varchar(100) DEFAULT NULL,
    credential varchar(500) DEFAULT NULL,
    base_url varchar(500) DEFAULT NULL,
    model_type varchar(32) DEFAULT NULL,
    model_params varchar(500) DEFAULT NULL,
    PRIMARY KEY (id)
);

COMMENT ON COLUMN airag_model.create_by IS '创建人';
COMMENT ON COLUMN airag_model.create_time IS '创建日期';
COMMENT ON COLUMN airag_model.update_by IS '更新人';
COMMENT ON COLUMN airag_model.update_time IS '更新日期';
COMMENT ON COLUMN airag_model.sys_org_code IS '所属部门';
COMMENT ON COLUMN airag_model.tenant_id IS '租户id';
COMMENT ON COLUMN airag_model.name IS '名称';
COMMENT ON COLUMN airag_model.provider IS '供应者';
COMMENT ON COLUMN airag_model.model_name IS '模型名称';
COMMENT ON COLUMN airag_model.credential IS '凭证信息';
COMMENT ON COLUMN airag_model.base_url IS 'API域名';
COMMENT ON COLUMN airag_model.model_type IS '模型类型';
COMMENT ON COLUMN airag_model.model_params IS '模型参数';

-- ----------------------------
-- 字典数据（airag相关）
-- 如果 sys_dict / sys_dict_item 表已存在则直接插入
-- ----------------------------
INSERT INTO sys_dict (id, dict_name, dict_code, description, del_flag, create_by, create_time, update_by, update_time, type, tenant_id, low_app_id)
VALUES
    ('1894701158027554818', 'AI应用类型', 'ai_app_type', NULL, 0, 'jeecg', '2025-02-26 18:48:53', NULL, NULL, 0, 0, NULL),
    ('1891672414555860993', '知识库文档类型', 'know_doc_type', NULL, 0, 'jeecg', '2025-02-18 10:13:44', NULL, NULL, 0, 0, NULL),
    ('1891456510739890177', '模型类型', 'model_type', NULL, 0, 'jeecg', '2025-02-17 19:55:48', NULL, NULL, 0, 0, NULL),
    ('1890229208685322242', '模型提供者', 'model_provider', NULL, 0, 'jeecg', '2025-02-14 10:38:57', NULL, NULL, 0, 0, NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_dict_item (id, dict_id, item_text, item_value, description, sort_order, status, create_by, create_time, update_by, update_time, item_color)
VALUES
    ('1894701332930031618', '1894701158027554818', '高级编排', 'chatFLow', NULL, 2, 1, 'jeecg', '2025-02-26 18:49:34', NULL, NULL, NULL),
    ('1894701277019959298', '1894701158027554818', '简单配置', 'chatSimple', NULL, 1, 1, 'jeecg', '2025-02-26 18:49:21', NULL, NULL, NULL),
    ('1891672567924781058', '1891672414555860993', '网页', 'web', NULL, 1, 1, 'jeecg', '2025-02-18 10:14:20', NULL, NULL, NULL),
    ('1891672540963794946', '1891672414555860993', '文件', 'file', NULL, 1, 1, 'jeecg', '2025-02-18 10:14:14', NULL, NULL, NULL),
    ('1891672501432479746', '1891672414555860993', '文本', 'text', NULL, 1, 1, 'jeecg', '2025-02-18 10:14:05', NULL, NULL, NULL),
    ('1891458099609354241', '1891456510739890177', '向量模型', 'EMBED', NULL, 1, 1, 'jeecg', '2025-02-17 20:02:07', 'jeecg', '2025-02-17 20:39:01', NULL),
    ('1891456733029613569', '1891456510739890177', '语言模型', 'LLM', NULL, 1, 1, 'jeecg', '2025-02-17 19:56:41', 'jeecg', '2025-02-17 20:02:15', NULL),
    ('1890230437670920194', '1890229208685322242', 'Ollama', 'OLLAMA', NULL, 1, 1, 'jeecg', '2025-02-14 10:43:50', NULL, NULL, NULL),
    ('1890230384159989762', '1890229208685322242', 'DeepSeek', 'DEEPSEEK', NULL, 1, 1, 'jeecg', '2025-02-14 10:43:37', NULL, NULL, NULL),
    ('1890230305948803073', '1890229208685322242', '通义千问', 'QWEN', NULL, 1, 1, 'jeecg', '2025-02-14 10:43:18', NULL, NULL, NULL),
    ('1890230107835047937', '1890229208685322242', '千帆大模型', 'QIANFAN', NULL, 1, 1, 'jeecg', '2025-02-14 10:42:31', NULL, NULL, NULL),
    ('1890230018852888577', '1890229208685322242', '智谱AI', 'ZHIPU', NULL, 1, 1, 'jeecg', '2025-02-14 10:42:10', 'jeecg', '2025-02-14 10:42:42', NULL),
    ('1890229967585910786', '1890229208685322242', 'OpenAI', 'OPENAI', NULL, 1, 1, 'jeecg', '2025-02-14 10:41:58', 'jeecg', '2025-02-14 10:42:48', NULL)
ON CONFLICT (id) DO NOTHING;
