package com.ecommerce.mq;

import com.ecommerce.entity.MqConsumeLog;
import com.ecommerce.mapper.MqConsumeLogMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class MqConsumeLogService {

    private final MqConsumeLogMapper consumeLogMapper;

    public MqConsumeLogService(MqConsumeLogMapper consumeLogMapper) {
        this.consumeLogMapper = consumeLogMapper;
    }

    public boolean tryAcquire(String eventId, String consumerName) {
        MqConsumeLog log = new MqConsumeLog();
        log.setEventId(eventId);
        log.setConsumerName(consumerName);
        try {
            consumeLogMapper.insert(log);
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }
}
