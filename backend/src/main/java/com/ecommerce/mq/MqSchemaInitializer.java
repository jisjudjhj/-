package com.ecommerce.mq;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class MqSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public MqSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS mq_outbox_event (" +
                        "id BIGINT NOT NULL AUTO_INCREMENT," +
                        "event_id VARCHAR(64) NOT NULL," +
                        "event_type VARCHAR(100) NOT NULL," +
                        "exchange_name VARCHAR(100) NOT NULL," +
                        "routing_key VARCHAR(100) NOT NULL," +
                        "biz_id VARCHAR(100) DEFAULT NULL," +
                        "payload LONGTEXT NOT NULL," +
                        "status VARCHAR(20) NOT NULL," +
                        "retry_count INT NOT NULL DEFAULT 0," +
                        "next_retry_time DATETIME DEFAULT NULL," +
                        "error_message VARCHAR(1000) DEFAULT NULL," +
                        "sent_time DATETIME DEFAULT NULL," +
                        "create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                        "PRIMARY KEY (id)," +
                        "UNIQUE KEY uk_event_id (event_id)," +
                        "KEY idx_status_retry (status, next_retry_time)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );

        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS mq_consume_log (" +
                        "id BIGINT NOT NULL AUTO_INCREMENT," +
                        "event_id VARCHAR(64) NOT NULL," +
                        "consumer_name VARCHAR(100) NOT NULL," +
                        "create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "PRIMARY KEY (id)," +
                        "UNIQUE KEY uk_event_consumer (event_id, consumer_name)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
    }
}
