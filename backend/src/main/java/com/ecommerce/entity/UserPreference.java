package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "user_preference", autoResultMap = true)
public class UserPreference {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Integer> categoryPreferences;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Integer> tagPreferences;

    private BigDecimal priceRangeMin;

    private BigDecimal priceRangeMax;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
