package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("im_support_agent")
public class ImSupportAgent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String displayName;

    private String avatar;

    private String agentType;

    private Integer onlineStatus;

    private Integer enabled;
}

