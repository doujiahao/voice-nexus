package org.jeecg.modules.call.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.jeecg.modules.call.entity.Customer;

@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
}
