package org.jeecg.modules.system.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysCallAgentMapper {

    @Select("select count(1) from sys_role where id = #{roleId} and role_code = 'call_agent'")
    Integer countCallAgentRole(@Param("roleId") String roleId);

    @Select("select count(1) from agent_profile where 1 = 0")
    Integer checkAgentProfileTable();

    @Select("select count(1) from skill_group where 1 = 0")
    Integer checkSkillGroupTable();

    @Select("select count(1) from skill_group_agent where 1 = 0")
    Integer checkSkillGroupAgentTable();

    @Select("select id from skill_group where group_code = 'default'")
    String queryDefaultSkillGroupId();

    @Insert("insert into skill_group (id, group_name, group_code, description, route_strategy, queue_max_size, queue_timeout_sec, ring_timeout_sec, enabled, del_flag, create_by, create_time) values (#{id}, '默认技能组', 'default', '默认呼入技能组', 'LONGEST_IDLE', 50, 60, 30, 1, 0, 'admin', CURRENT_TIMESTAMP)")
    void insertDefaultSkillGroup(@Param("id") String id);

    @Update("update skill_group set enabled = 1, del_flag = 0, update_by = 'admin', update_time = CURRENT_TIMESTAMP where id = #{id}")
    void enableSkillGroup(@Param("id") String id);

    @Select("select id from agent_profile where user_id = #{userId}")
    String queryAgentProfileId(@Param("userId") String userId);

    @Insert("insert into agent_profile (id, user_id, agent_no, extension, status, status_since, max_concurrent, del_flag, create_by, create_time) values (#{id}, #{userId}, #{agentNo}, null, 'OFFLINE', CURRENT_TIMESTAMP, 1, 0, 'admin', CURRENT_TIMESTAMP)")
    void insertAgentProfile(@Param("id") String id, @Param("userId") String userId, @Param("agentNo") String agentNo);

    @Update("update agent_profile set del_flag = 0, update_by = 'admin', update_time = CURRENT_TIMESTAMP where id = #{id}")
    void enableAgentProfile(@Param("id") String id);

    @Select("select id from skill_group_agent where skill_group_id = #{skillGroupId} and agent_id = #{agentId}")
    String querySkillGroupAgentId(@Param("skillGroupId") String skillGroupId, @Param("agentId") String agentId);

    @Insert("insert into skill_group_agent (id, skill_group_id, agent_id, skill_level, del_flag, create_by, create_time) values (#{id}, #{skillGroupId}, #{agentId}, 100, 0, 'admin', CURRENT_TIMESTAMP)")
    void insertSkillGroupAgent(@Param("id") String id, @Param("skillGroupId") String skillGroupId, @Param("agentId") String agentId);

    @Update("update skill_group_agent set skill_level = 100, del_flag = 0, update_by = 'admin', update_time = CURRENT_TIMESTAMP where id = #{id}")
    void enableSkillGroupAgent(@Param("id") String id);
}
