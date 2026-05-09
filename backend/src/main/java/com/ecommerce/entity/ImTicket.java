package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("im_ticket")
public class ImTicket {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private Long reviewId;

    private String ticketNo;

    private String ticketStatus;

    private String sourceType;

    private String issueType;

    private String issueSummary;

    private String issueDetail;

    private Long createdByUserId;

    private Long assignedSupportId;

    private Long resolvedById;

    private LocalDateTime assignedTime;

    private LocalDateTime resolvedTime;

    private LocalDateTime slaDeadlineTime;

    private Integer slaEscalationLevel;

    private LocalDateTime lastEscalationTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
