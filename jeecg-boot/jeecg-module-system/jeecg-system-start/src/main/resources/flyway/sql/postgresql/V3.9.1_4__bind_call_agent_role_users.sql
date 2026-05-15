INSERT INTO agent_profile (id, user_id, agent_no, extension, status, status_since, max_concurrent, del_flag, create_by, create_time)
SELECT md5('agent_profile:' || u.id), u.id, 'A' || substring(md5('agent_profile:' || u.id), 1, 6), NULL, 'OFFLINE', CURRENT_TIMESTAMP, 1, 0, 'admin', CURRENT_TIMESTAMP
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.id
JOIN sys_role r ON r.id = ur.role_id
WHERE r.role_code = 'call_agent'
  AND NOT EXISTS (SELECT 1 FROM agent_profile ap WHERE ap.user_id = u.id);

UPDATE agent_profile ap
SET del_flag = 0,
    update_by = 'admin',
    update_time = CURRENT_TIMESTAMP
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.id
JOIN sys_role r ON r.id = ur.role_id
WHERE ap.user_id = u.id
  AND r.role_code = 'call_agent';

INSERT INTO skill_group_agent (id, skill_group_id, agent_id, skill_level, del_flag, create_by, create_time)
SELECT md5('skill_group_agent:' || sg.id || ':' || ap.id), sg.id, ap.id, 100, 0, 'admin', CURRENT_TIMESTAMP
FROM skill_group sg
JOIN sys_role r ON r.role_code = 'call_agent'
JOIN sys_user_role ur ON ur.role_id = r.id
JOIN agent_profile ap ON ap.user_id = ur.user_id
WHERE sg.group_code = 'default'
  AND NOT EXISTS (
      SELECT 1
      FROM skill_group_agent sga
      WHERE sga.skill_group_id = sg.id
        AND sga.agent_id = ap.id
  );

UPDATE skill_group_agent sga
SET skill_level = 100,
    del_flag = 0,
    update_by = 'admin',
    update_time = CURRENT_TIMESTAMP
FROM skill_group sg
JOIN sys_role r ON r.role_code = 'call_agent'
JOIN sys_user_role ur ON ur.role_id = r.id
JOIN agent_profile ap ON ap.user_id = ur.user_id
WHERE sga.skill_group_id = sg.id
  AND sga.agent_id = ap.id
  AND sg.group_code = 'default';
