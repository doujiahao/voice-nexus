package org.jeecg.config.init;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
public class CallWorkspacePermissionInitializer implements ApplicationRunner {

    private static final String CALL_ROOT_ID = "call_root";
    private static final String CALL_WORKSPACE_ID = "call_workspace";
    private static final String LEGACY_CALL_WORKBENCH_ID = "call_workbench";
    private static final String CALL_AGENT_ROLE_ID = "call_agent_role";
    private static final String CALL_AGENT_ROLE_CODE = "call_agent";

    private final JdbcTemplate jdbcTemplate;

    public CallWorkspacePermissionInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (!hasTable("sys_permission") || !hasTable("sys_role") || !hasTable("sys_role_permission")) {
                return;
            }
            normalizeLegacyWorkspaceMenu();
            ensurePermission(CALL_ROOT_ID, null, "话务管理", "/call", "layouts/default/index", null,
                    "/call/workspace", 0, null, "1", 10.0, 0, "ant-design:phone-outlined",
                    0, 0, 0, 0, "智能客服话务平台", "1", 0);
            ensurePermission(CALL_WORKSPACE_ID, CALL_ROOT_ID, "坐席工作台", "/call/workspace", "call/index", null,
                    null, 1, null, "1", 1.0, 0, "ant-design:customer-service-outlined",
                    1, 1, 0, 0, null, "1", 0);

            String callAgentRoleId = ensureRole(CALL_AGENT_ROLE_ID, "坐席", CALL_AGENT_ROLE_CODE, "智能客服坐席角色");
            assignPermission(callAgentRoleId, CALL_ROOT_ID);
            assignPermission(callAgentRoleId, CALL_WORKSPACE_ID);

            String adminRoleId = queryRoleId("admin");
            if (adminRoleId != null) {
                assignPermission(adminRoleId, CALL_ROOT_ID);
                assignPermission(adminRoleId, CALL_WORKSPACE_ID);
            }

            if (hasTable("sys_role_index")) {
                ensureRoleIndex(CALL_AGENT_ROLE_CODE, "/call/workspace", "call/index");
            }
        } catch (Exception e) {
            log.warn("初始化坐席工作台菜单权限失败，请检查 sys_permission/sys_role/sys_role_permission 数据", e);
        }
    }

    private void normalizeLegacyWorkspaceMenu() {
        if (existsPermission(LEGACY_CALL_WORKBENCH_ID) && !existsPermission(CALL_WORKSPACE_ID)) {
            jdbcTemplate.update("update sys_role_permission set permission_id = ? where permission_id = ?", CALL_WORKSPACE_ID, LEGACY_CALL_WORKBENCH_ID);
            jdbcTemplate.update("update sys_permission set id = ? where id = ?", CALL_WORKSPACE_ID, LEGACY_CALL_WORKBENCH_ID);
        }
        if (existsPermission(LEGACY_CALL_WORKBENCH_ID) && existsPermission(CALL_WORKSPACE_ID)) {
            jdbcTemplate.update("update sys_permission set del_flag = 1, status = '0', hidden = ? where id = ?", 1, LEGACY_CALL_WORKBENCH_ID);
        }
    }

    private void ensurePermission(String id, String parentId, String name, String url, String component, String componentName,
                                  String redirect, Integer menuType, String perms, String permsType, Double sortNo,
                                  int alwaysShow, String icon, int leaf, int keepAlive, int hidden,
                                  int hideTab, String description, String status, int internalOrExternal) {
        if (existsPermission(id)) {
            jdbcTemplate.update("update sys_permission set parent_id = ?, name = ?, url = ?, component = ?, is_route = ?, component_name = ?, redirect = ?, menu_type = ?, perms = ?, perms_type = ?, sort_no = ?, always_show = ?, icon = ?, is_leaf = ?, keep_alive = ?, hidden = ?, hide_tab = ?, description = ?, status = ?, del_flag = 0, rule_flag = 0, update_by = 'admin', update_time = CURRENT_TIMESTAMP, internal_or_external = ? where id = ?",
                    parentId, name, url, component, 1, componentName, redirect, menuType, perms, permsType, sortNo,
                    alwaysShow, icon, leaf, keepAlive, hidden, hideTab, description, status, internalOrExternal, id);
            return;
        }
        jdbcTemplate.update("insert into sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'admin', CURRENT_TIMESTAMP, null, null, 0, 0, ?, ?)",
                id, parentId, name, url, component, 1, componentName, redirect, menuType, perms, permsType, sortNo,
                alwaysShow, icon, leaf, keepAlive, hidden, hideTab, description, status, internalOrExternal);
    }

    private String ensureRole(String id, String roleName, String roleCode, String description) {
        String existingId = queryRoleId(roleCode);
        if (existingId != null) {
            jdbcTemplate.update("update sys_role set role_name = ?, description = ?, update_by = 'admin', update_time = CURRENT_TIMESTAMP where role_code = ?",
                    roleName, description, roleCode);
            return existingId;
        }
        jdbcTemplate.update("insert into sys_role (id, role_name, role_code, description, create_by, create_time, update_by, update_time, tenant_id) values (?, ?, ?, ?, 'admin', CURRENT_TIMESTAMP, null, null, 0)",
                id, roleName, roleCode, description);
        return id;
    }

    private void assignPermission(String roleId, String permissionId) {
        Integer count = jdbcTemplate.queryForObject("select count(1) from sys_role_permission where role_id = ? and permission_id = ?", Integer.class, roleId, permissionId);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("insert into sys_role_permission (id, role_id, permission_id, data_rule_ids, operate_date, operate_ip) values (?, ?, ?, null, CURRENT_TIMESTAMP, null)",
                stableId("role_permission:" + roleId + ":" + permissionId), roleId, permissionId);
    }

    private void ensureRoleIndex(String roleCode, String url, String component) {
        Integer count = jdbcTemplate.queryForObject("select count(1) from sys_role_index where role_code = ? and relation_type = 'ROLE'", Integer.class, roleCode);
        if (count != null && count > 0) {
            jdbcTemplate.update("update sys_role_index set url = ?, component = ?, is_route = ?, priority = 1, status = '1', update_by = 'admin', update_time = CURRENT_TIMESTAMP where role_code = ? and relation_type = 'ROLE'",
                    url, component, 1, roleCode);
            return;
        }
        jdbcTemplate.update("insert into sys_role_index (id, role_code, url, component, is_route, priority, status, create_by, create_time, update_by, update_time, sys_org_code, relation_type) values (?, ?, ?, ?, ?, 1, '1', 'admin', CURRENT_TIMESTAMP, null, null, null, 'ROLE')",
                stableId("role_index:" + roleCode), roleCode, url, component, 1);
    }

    private boolean existsPermission(String id) {
        Integer count = jdbcTemplate.queryForObject("select count(1) from sys_permission where id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private String queryRoleId(String roleCode) {
        return jdbcTemplate.query("select id from sys_role where role_code = ?", rs -> rs.next() ? rs.getString(1) : null, roleCode);
    }

    private boolean hasTable(String tableName) {
        try {
            jdbcTemplate.queryForObject("select count(1) from " + tableName + " where 1 = 0", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String stableId(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }
}
