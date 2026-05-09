package com.ecommerce.common;

import java.lang.annotation.*;

/**
 * API 限流注解
 * 用于标记需要限流的接口
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    
    /**
     * 限流键前缀，默认使用方法名
     */
    String key() default "";
    
    /**
     * 时间窗口（秒），默认60秒
     */
    int window() default 60;
    
    /**
     * 窗口内最大请求数，默认10次
     */
    int max() default 10;
    
    /**
     * 限流类型：IP / USER / ALL
     */
    LimitType type() default LimitType.IP;
    
    /**
     * 限流提示消息
     */
    String message() default "操作过于频繁，请稍后再试";
    
    enum LimitType {
        /** 基于IP限流 */
        IP,
        /** 基于用户ID限流 */
        USER,
        /** 全局限流 */
        ALL
    }
}
