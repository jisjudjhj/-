package com.ecommerce.dto;

import lombok.Data;
import org.springframework.util.StringUtils;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class SeckillOrderCreateDTO {

    /**
     * 兼容字段：新接口优先使用 seckillApplyId，旧字段 applyId 仍可用
     */
    private Long applyId;

    private Long seckillApplyId;

    private Long productId;

    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量必须大于0")
    private Integer quantity;

    private Long addressId;

    private String address;

    private String receiverName;

    private String receiverPhone;

    private String remark;

    /**
     * 请求幂等键，建议前端在提交秒杀订单时传入，网络重试需复用同一值
     */
    private String idempotencyKey;

    public Long resolveApplyId() {
        return seckillApplyId != null ? seckillApplyId : applyId;
    }

    public String resolveIdempotencyKey(String headerKey) {
        if (StringUtils.hasText(idempotencyKey)) {
            return idempotencyKey.trim();
        }
        if (StringUtils.hasText(headerKey)) {
            return headerKey.trim();
        }
        return null;
    }
}
