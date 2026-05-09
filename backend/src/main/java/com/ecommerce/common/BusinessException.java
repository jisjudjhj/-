package com.ecommerce.common;

import lombok.Getter;

/**
 * 业务异常基类
 * 统一业务层异常处理，提供标准化的错误码和消息
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    private final String module;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
        this.module = null;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.module = null;
    }

    public BusinessException(int code, String message, String module) {
        super(message);
        this.code = code;
        this.module = module;
    }

    // ========== 静态工厂方法，便于创建常用异常 ==========

    /** 资源未找到 */
    public static BusinessException notFound(String resource) {
        return new BusinessException(404, resource + "不存在");
    }

    /** 无权限访问 */
    public static BusinessException forbidden(String message) {
        return new BusinessException(403, message);
    }

    /** 参数错误 */
    public static BusinessException badRequest(String message) {
        return new BusinessException(400, message);
    }

    /** 未授权 */
    public static BusinessException unauthorized(String message) {
        return new BusinessException(401, message);
    }

    /** 模块已禁用 */
    public static BusinessException moduleDisabled(String module) {
        return new BusinessException(Constants.ErrorCode.MODULE_DISABLED, 
                "功能模块【" + module + "】已关闭", module);
    }

    /** 库存不足 */
    public static BusinessException insufficientStock(String productName) {
        return new BusinessException(400, "商品库存不足: " + productName);
    }

    /** 余额不足 */
    public static BusinessException insufficientBalance(String message) {
        return new BusinessException(400, message);
    }

    /** 订单状态错误 */
    public static BusinessException invalidOrderStatus(String message) {
        return new BusinessException(400, message);
    }

    /** 操作频繁 */
    public static BusinessException tooManyRequests(String message) {
        return new BusinessException(429, message);
    }
}
