UPDATE sys_role_permission
SET permission_id = 'call_workspace'
WHERE permission_id = 'call_workbench'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 'call_workspace');

UPDATE sys_permission
SET id = 'call_workspace'
WHERE id = 'call_workbench'
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 'call_workspace');

UPDATE sys_permission
SET del_flag = 1, status = '0', hidden = 1
WHERE id = 'call_workbench'
  AND EXISTS (SELECT 1 FROM sys_permission WHERE id = 'call_workspace');

INSERT INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
SELECT 'call_root', NULL, '话务管理', '/call', 'layouts/default/index', 1, NULL, '/call/workspace', 0, NULL, '1', 10, 0, 'ant-design:phone-outlined', 0, 0, 0, 0, '智能客服话务平台', 'admin', CURRENT_TIMESTAMP, NULL, NULL, 0, 0, '1', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 'call_root');

UPDATE sys_permission
SET parent_id = NULL,
    name = '话务管理',
    url = '/call',
    component = 'layouts/default/index',
    is_route = 1,
    component_name = NULL,
    redirect = '/call/workspace',
    menu_type = 0,
    perms = NULL,
    perms_type = '1',
    sort_no = 10,
    always_show = 0,
    icon = 'ant-design:phone-outlined',
    is_leaf = 0,
    keep_alive = 0,
    hidden = 0,
    hide_tab = 0,
    description = '智能客服话务平台',
    status = '1',
    del_flag = 0,
    rule_flag = 0,
    update_by = 'admin',
    update_time = CURRENT_TIMESTAMP,
    internal_or_external = 0
WHERE id = 'call_root';

INSERT INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
SELECT 'call_workspace', 'call_root', '坐席工作台', '/call/workspace', 'call/index', 1, NULL, NULL, 1, NULL, '1', 1, 0, 'ant-design:customer-service-outlined', 1, 1, 0, 0, NULL, 'admin', CURRENT_TIMESTAMP, NULL, NULL, 0, 0, '1', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 'call_workspace');

UPDATE sys_permission
SET parent_id = 'call_root',
    name = '坐席工作台',
    url = '/call/workspace',
    component = 'call/index',
    is_route = 1,
    component_name = NULL,
    redirect = NULL,
    menu_type = 1,
    perms = NULL,
    perms_type = '1',
    sort_no = 1,
    always_show = 0,
    icon = 'ant-design:customer-service-outlined',
    is_leaf = 1,
    keep_alive = 1,
    hidden = 0,
    hide_tab = 0,
    description = NULL,
    status = '1',
    del_flag = 0,
    rule_flag = 0,
    update_by = 'admin',
    update_time = CURRENT_TIMESTAMP,
    internal_or_external = 0
WHERE id = 'call_workspace';

INSERT INTO sys_role (id, role_name, role_code, description, create_by, create_time, update_by, update_time, tenant_id)
SELECT 'call_agent_role', '坐席', 'call_agent', '智能客服坐席角色', 'admin', CURRENT_TIMESTAMP, NULL, NULL, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'call_agent');

UPDATE sys_role
SET role_name = '坐席', description = '智能客服坐席角色', update_by = 'admin', update_time = CURRENT_TIMESTAMP
WHERE role_code = 'call_agent';

INSERT INTO sys_role_permission (id, role_id, permission_id, data_rule_ids, operate_date, operate_ip)
SELECT md5('role_permission:' || r.id || ':call_root'), r.id, 'call_root', NULL, CURRENT_TIMESTAMP, NULL
FROM sys_role r
WHERE r.role_code IN ('call_agent', 'admin')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = 'call_root'
  );

INSERT INTO sys_role_permission (id, role_id, permission_id, data_rule_ids, operate_date, operate_ip)
SELECT md5('role_permission:' || r.id || ':call_workspace'), r.id, 'call_workspace', NULL, CURRENT_TIMESTAMP, NULL
FROM sys_role r
WHERE r.role_code IN ('call_agent', 'admin')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = 'call_workspace'
  );

INSERT INTO sys_role_index (id, role_code, url, component, is_route, priority, status, create_by, create_time, update_by, update_time, sys_org_code, relation_type)
SELECT md5('role_index:call_agent'), 'call_agent', '/call/workspace', 'call/index', 1, 1, '1', 'admin', CURRENT_TIMESTAMP, NULL, NULL, NULL, 'ROLE'
WHERE NOT EXISTS (SELECT 1 FROM sys_role_index WHERE role_code = 'call_agent' AND relation_type = 'ROLE');

UPDATE sys_role_index
SET url = '/call/workspace', component = 'call/index', is_route = 1, priority = 1, status = '1', update_by = 'admin', update_time = CURRENT_TIMESTAMP
WHERE role_code = 'call_agent' AND relation_type = 'ROLE';
