package com.ecommerce.service;

import java.util.Collection;
import java.util.Map;

public interface StreamKafkaMonitorService {

    Map<String, Object> getRealtimeMonitor(String consumerGroupId, Collection<String> topics);
}
