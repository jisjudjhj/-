package com.ecommerce.aspect;

import com.alibaba.fastjson2.JSON;
import com.ecommerce.common.Log;
import com.ecommerce.entity.OperationLog;
import com.ecommerce.service.OperationLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

@Aspect
@Component
public class OperationLogAspect {

    private static final Logger logger = LoggerFactory.getLogger(OperationLogAspect.class);

    @Autowired
    private OperationLogService operationLogService;

    @PostConstruct
    public void init() {
        logger.info(">>> OperationLogAspect 已加载，AOP 日志切面就绪");
    }

    @Around("@annotation(com.ecommerce.common.Log)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        logger.debug(">>> AOP 拦截到 @Log 方法: {}", joinPoint.getSignature().toShortString());
        long startTime = System.currentTimeMillis();
        OperationLog opLog = new OperationLog();

        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                Long userId = (Long) request.getAttribute("userId");
                String role = (String) request.getAttribute("role");
                String username = (String) request.getAttribute("username");

                opLog.setUserId(userId);
                opLog.setUsername(username);
                opLog.setRole(role);
                opLog.setUrl(request.getRequestURI());
                opLog.setMethod(request.getMethod());
                opLog.setIp(getClientIp(request));
            }

            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Log logAnno = method.getAnnotation(Log.class);
            if (logAnno != null) {
                opLog.setModule(logAnno.module());
                opLog.setAction(logAnno.action());
            }

            String params = truncate(getParams(joinPoint), 2000);
            opLog.setParams(params);

        } catch (Exception e) {
            logger.warn("日志采集异常: {}", e.getMessage(), e);
        }

        Object result;
        try {
            result = joinPoint.proceed();
            opLog.setStatus(1);
        } catch (Throwable e) {
            opLog.setStatus(0);
            opLog.setErrorMsg(truncate(e.getMessage(), 500));
            throw e;
        } finally {
            opLog.setCostTime(System.currentTimeMillis() - startTime);
            operationLogService.saveAsync(opLog);
        }
        return result;
    }

    private String getParams(ProceedingJoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            if (args == null || args.length == 0) return "";
            StringBuilder sb = new StringBuilder();
            for (Object arg : args) {
                if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse) continue;
                if (sb.length() > 0) sb.append(",");
                sb.append(JSON.toJSONString(arg));
            }
            return sb.toString();
        } catch (Exception e) {
            return "参数序列化失败";
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }
}
