package org.jeecg.modules.call.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.call.dto.CallListVO;
import org.jeecg.modules.call.entity.CallSession;

@Mapper
public interface CallSessionMapper extends BaseMapper<CallSession> {

    Page<CallListVO> selectCallListPage(
            Page<?> page,
            @Param("status") String status,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime);
}
