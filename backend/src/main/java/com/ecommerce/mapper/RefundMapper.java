package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.RefundRequest;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RefundMapper extends BaseMapper<RefundRequest> {
}
