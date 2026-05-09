package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("profile_change_request")
public class ProfileChangeRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String newNickname;

    private String newAvatar;

    private String oldNickname;

    private String oldAvatar;

    private Integer status;

    private String rejectReason;

    private LocalDateTime reviewTime;

    private Long reviewerId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
