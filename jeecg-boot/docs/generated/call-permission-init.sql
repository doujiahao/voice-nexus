-- 话务模块菜单权限初始化 SQL
-- 适用于 JeecgBoot sys_permission 表

-- 一级菜单：话务管理
INSERT INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, status, del_flag, rule_flag, create_by, create_time, update_by, update_time, internal_or_external)
VALUES ('call_root', NULL, '话务管理', '/call', 'layouts/default/index', 1, NULL, '/call/workspace', 0, NULL, '1', 10, 0, 'ant-design:phone-outlined', 0, 0, 0, 0, '智能客服话务平台', '1', 0, 0, 'admin', CURRENT_TIMESTAMP, NULL, NULL, 0);

-- 二级菜单：坐席工作台
INSERT INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, status, del_flag, rule_flag, create_by, create_time, update_by, update_time, internal_or_external)
VALUES ('call_workspace', 'call_root', '坐席工作台', '/call/workspace', 'call/index', 1, NULL, NULL, 1, NULL, '1', 1, 0, 'ant-design:customer-service-outlined', 1, 1, 0, 0, NULL, '1', 0, 0, 'admin', CURRENT_TIMESTAMP, NULL, NULL, 0);

-- 二级菜单：通话记录
INSERT INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, status, del_flag, rule_flag, create_by, create_time, update_by, update_time, internal_or_external)
VALUES ('call_records', 'call_root', '通话记录', '/call/records', 'call/Records', 1, NULL, NULL, 1, NULL, '1', 2, 0, 'unordered-list', 1, 0, 0, 0, NULL, '1', 0, 0, 'admin', CURRENT_TIMESTAMP, NULL, NULL, 0);

-- 二级菜单：客户档案
INSERT INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, status, del_flag, rule_flag, create_by, create_time, update_by, update_time, internal_or_external)
VALUES ('call_customers', 'call_root', '客户档案', '/call/customers', 'call/Customers', 1, NULL, NULL, 1, NULL, '1', 3, 0, 'contacts', 1, 0, 0, 0, NULL, '1', 0, 0, 'admin', CURRENT_TIMESTAMP, NULL, NULL, 0);

-- 二级菜单：工单管理
INSERT INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, status, del_flag, rule_flag, create_by, create_time, update_by, update_time, internal_or_external)
VALUES ('call_workorders', 'call_root', '工单管理', '/call/work-orders', 'call/WorkOrders', 1, NULL, NULL, 1, NULL, '1', 4, 0, 'file-text', 1, 0, 0, 0, NULL, '1', 0, 0, 'admin', CURRENT_TIMESTAMP, NULL, NULL, 0);

-- 二级菜单：技能组管理
INSERT INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, status, del_flag, rule_flag, create_by, create_time, update_by, update_time, internal_or_external)
VALUES ('call_skillgroups', 'call_root', '技能组管理', '/call/skill-groups', 'call/SkillGroups', 1, NULL, NULL, 1, NULL, '1', 5, 0, 'team', 1, 0, 0, 0, NULL, '1', 0, 0, 'admin', CURRENT_TIMESTAMP, NULL, NULL, 0);

-- 二级菜单：坐席管理（管理员）
INSERT INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, status, del_flag, rule_flag, create_by, create_time, update_by, update_time, internal_or_external)
VALUES ('call_agents_admin', 'call_root', '坐席管理', '/call/agents', 'call/AgentsAdmin', 1, NULL, NULL, 1, NULL, '1', 6, 0, 'user', 1, 0, 0, 0, NULL, '1', 0, 0, 'admin', CURRENT_TIMESTAMP, NULL, NULL, 0);

-- 按钮权限：通话记录
INSERT INTO sys_permission (id, parent_id, name, url, component, menu_type, perms, perms_type, sort_no, status, del_flag, create_by, create_time)
VALUES ('call_records_view', 'call_records', '查看', NULL, NULL, 2, 'call:records:view', '1', 1, '1', 0, 'admin', CURRENT_TIMESTAMP);
INSERT INTO sys_permission (id, parent_id, name, url, component, menu_type, perms, perms_type, sort_no, status, del_flag, create_by, create_time)
VALUES ('call_records_export', 'call_records', '导出', NULL, NULL, 2, 'call:records:export', '1', 2, '1', 0, 'admin', CURRENT_TIMESTAMP);

-- 按钮权限：客户档案
INSERT INTO sys_permission (id, parent_id, name, url, component, menu_type, perms, perms_type, sort_no, status, del_flag, create_by, create_time)
VALUES ('call_customers_view', 'call_customers', '查看', NULL, NULL, 2, 'call:customers:view', '1', 1, '1', 0, 'admin', CURRENT_TIMESTAMP);
INSERT INTO sys_permission (id, parent_id, name, url, component, menu_type, perms, perms_type, sort_no, status, del_flag, create_by, create_time)
VALUES ('call_customers_add', 'call_customers', '新增', NULL, NULL, 2, 'call:customers:add', '1', 2, '1', 0, 'admin', CURRENT_TIMESTAMP);
INSERT INTO sys_permission (id, parent_id, name, url, component, menu_type, perms, perms_type, sort_no, status, del_flag, create_by, create_time)
VALUES ('call_customers_edit', 'call_customers', '编辑', NULL, NULL, 2, 'call:customers:edit', '1', 3, '1', 0, 'admin', CURRENT_TIMESTAMP);
INSERT INTO sys_permission (id, parent_id, name, url, component, menu_type, perms, perms_type, sort_no, status, del_flag, create_by, create_time)
VALUES ('call_customers_delete', 'call_customers', '删除', NULL, NULL, 2, 'call:customers:delete', '1', 4, '1', 0, 'admin', CURRENT_TIMESTAMP);

-- 按钮权限：工单管理
INSERT INTO sys_permission (id, parent_id, name, url, component, menu_type, perms, perms_type, sort_no, status, del_flag, create_by, create_time)
VALUES ('call_workorders_view', 'call_workorders', '查看', NULL, NULL, 2, 'call:workorders:view', '1', 1, '1', 0, 'admin', CURRENT_TIMESTAMP);
INSERT INTO sys_permission (id, parent_id, name, url, component, menu_type, perms, perms_type, sort_no, status, del_flag, create_by, create_time)
VALUES ('call_workorders_add', 'call_workorders', '新增', NULL, NULL, 2, 'call:workorders:add', '1', 2, '1', 0, 'admin', CURRENT_TIMESTAMP);
INSERT INTO sys_permission (id, parent_id, name, url, component, menu_type, perms, perms_type, sort_no, status, del_flag, create_by, create_time)
VALUES ('call_workorders_edit', 'call_workorders', '编辑', NULL, NULL, 2, 'call:workorders:edit', '1', 3, '1', 0, 'admin', CURRENT_TIMESTAMP);
INSERT INTO sys_permission (id, parent_id, name, url, component, menu_type, perms, perms_type, sort_no, status, del_flag, create_by, create_time)
VALUES ('call_workorders_delete', 'call_workorders', '删除', NULL, NULL, 2, 'call:workorders:delete', '1', 4, '1', 0, 'admin', CURRENT_TIMESTAMP);

-- 按钮权限：技能组管理
INSERT INTO sys_permission (id, parent_id, name, url, component, menu_type, perms, perms_type, sort_no, status, del_flag, create_by, create_time)
VALUES ('call_skillgroups_view', 'call_skillgroups', '查看', NULL, NULL, 2, 'call:skillgroups:view', '1', 1, '1', 0, 'admin', CURRENT_TIMESTAMP);
INSERT INTO sys_permission (id, parent_id, name, url, component, menu_type, perms, perms_type, sort_no, status, del_flag, create_by, create_time)
VALUES ('call_skillgroups_add', 'call_skillgroups', '新增', NULL, NULL, 2, 'call:skillgroups:add', '1', 2, '1', 0, 'admin', CURRENT_TIMESTAMP);
INSERT INTO sys_permission (id, parent_id, name, url, component, menu_type, perms, perms_type, sort_no, status, del_flag, create_by, create_time)
VALUES ('call_skillgroups_edit', 'call_skillgroups', '编辑', NULL, NULL, 2, 'call:skillgroups:edit', '1', 3, '1', 0, 'admin', CURRENT_TIMESTAMP);
INSERT INTO sys_permission (id, parent_id, name, url, component, menu_type, perms, perms_type, sort_no, status, del_flag, create_by, create_time)
VALUES ('call_skillgroups_delete', 'call_skillgroups', '删除', NULL, NULL, 2, 'call:skillgroups:delete', '1', 4, '1', 0, 'admin', CURRENT_TIMESTAMP);

-- 按钮权限：坐席管理
INSERT INTO sys_permission (id, parent_id, name, url, component, menu_type, perms, perms_type, sort_no, status, del_flag, create_by, create_time)
VALUES ('call_agents_view', 'call_agents_admin', '查看', NULL, NULL, 2, 'call:agents:view', '1', 1, '1', 0, 'admin', CURRENT_TIMESTAMP);
INSERT INTO sys_permission (id, parent_id, name, url, component, menu_type, perms, perms_type, sort_no, status, del_flag, create_by, create_time)
VALUES ('call_agents_force_status', 'call_agents_admin', '强制状态变更', NULL, NULL, 2, 'call:agents:forceStatus', '1', 2, '1', 0, 'admin', CURRENT_TIMESTAMP);

-- 角色：坐席
INSERT INTO sys_role (id, role_name, role_code, description, create_by, create_time, update_by, update_time, tenant_id)
VALUES ('call_agent_role', '坐席', 'call_agent', '智能客服坐席角色', 'admin', CURRENT_TIMESTAMP, NULL, NULL, 0);

-- 角色分配：坐席角色和管理员可访问话务管理/坐席工作台
INSERT INTO sys_role_permission (id, role_id, permission_id, data_rule_ids, operate_date, operate_ip)
SELECT md5('role_permission:' || r.id || ':call_root'), r.id, 'call_root', NULL, CURRENT_TIMESTAMP, NULL
FROM sys_role r WHERE r.role_code IN ('call_agent', 'admin');

INSERT INTO sys_role_permission (id, role_id, permission_id, data_rule_ids, operate_date, operate_ip)
SELECT md5('role_permission:' || r.id || ':call_workspace'), r.id, 'call_workspace', NULL, CURRENT_TIMESTAMP, NULL
FROM sys_role r WHERE r.role_code IN ('call_agent', 'admin');

-- 坐席角色默认首页
INSERT INTO sys_role_index (id, role_code, url, component, is_route, priority, status, create_by, create_time, update_by, update_time, sys_org_code, relation_type)
VALUES (md5('role_index:call_agent'), 'call_agent', '/call/workspace', 'call/index', 1, 1, '1', 'admin', CURRENT_TIMESTAMP, NULL, NULL, NULL, 'ROLE');
