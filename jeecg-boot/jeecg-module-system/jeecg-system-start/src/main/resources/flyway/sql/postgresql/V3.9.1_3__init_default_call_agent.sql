INSERT INTO skill_group (id, group_name, group_code, description, route_strategy, queue_max_size, queue_timeout_sec, ring_timeout_sec, enabled, del_flag, create_by, create_time)
SELECT md5('skill_group:default'), '默认技能组', 'default', '默认呼入技能组', 'LONGEST_IDLE', 50, 60, 30, 1, 0, 'admin', CURRENT_TIMESTAMP
WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'skill_group')
  AND NOT EXISTS (SELECT 1 FROM skill_group WHERE group_code = 'default');

UPDATE skill_group
SET enabled = 1,
    del_flag = 0,
    update_by = 'admin',
    update_time = CURRENT_TIMESTAMP
WHERE group_code = 'default';

INSERT INTO agent_profile (id, user_id, agent_no, extension, status, status_since, max_concurrent, del_flag, create_by, create_time)
SELECT md5('agent_profile:' || u.id), u.id, 'A001', '1013', 'ONLINE', CURRENT_TIMESTAMP, 1, 0, 'admin', CURRENT_TIMESTAMP
FROM sys_user u
WHERE u.username = 'admin'
  AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'agent_profile')
  AND NOT EXISTS (SELECT 1 FROM agent_profile ap WHERE ap.user_id = u.id);

UPDATE agent_profile ap
SET status = 'ONLINE',
    extension = COALESCE(NULLIF(ap.extension, ''), '1013'),
    status_since = CURRENT_TIMESTAMP,
    del_flag = 0,
    update_by = 'admin',
    update_time = CURRENT_TIMESTAMP
FROM sys_user u
WHERE ap.user_id = u.id
  AND u.username = 'admin';

INSERT INTO skill_group_agent (id, skill_group_id, agent_id, skill_level, del_flag, create_by, create_time)
SELECT md5('skill_group_agent:' || sg.id || ':' || ap.id), sg.id, ap.id, 100, 0, 'admin', CURRENT_TIMESTAMP
FROM skill_group sg
JOIN sys_user u ON u.username = 'admin'
JOIN agent_profile ap ON ap.user_id = u.id
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
JOIN sys_user u ON u.username = 'admin'
JOIN agent_profile ap ON ap.user_id = u.id
WHERE sga.skill_group_id = sg.id
  AND sga.agent_id = ap.id
  AND sg.group_code = 'default';
