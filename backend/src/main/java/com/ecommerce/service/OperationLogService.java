package com.ecommerce.service;

import com.ecommerce.entity.OperationLog;
import com.ecommerce.mapper.OperationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationLogService {

    private static final Logger logger = LoggerFactory.getLogger(OperationLogService.class);

    @Autowired
    private OperationLogMapper logMapper;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAsync(OperationLog opLog) {
        try {
            logMapper.insert(opLog);
            logger.info(">>> 操作日志已保存: [{}] {} - userId={}", opLog.getModule(), opLog.getAction(), opLog.getUserId());
        } catch (Exception e) {
            logger.error(">>> 操作日志保存失败: {}", e.getMessage(), e);
        }
    }
}
