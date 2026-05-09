package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    int deductBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);

    int addBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);
}
