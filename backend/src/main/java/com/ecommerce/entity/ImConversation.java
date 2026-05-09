package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("im_conversation")
public class ImConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String conversationNo;

    private String conversationType;

    private Long userId;

    private Long merchantId;

    private Long supportAgentId;

    private Long orderId;

    private Long productId;

    private String status;

    private Integer isEscalated;

    private String priority;

    private String lastMessage;

    private String lastMessageType;

    private String lastSenderRole;

    private Long lastSenderId;

    private LocalDateTime lastMessageTime;

    private Integer unreadUser;

    private Integer unreadMerchant;

    private Integer unreadSupport;

    private LocalDateTime closedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

