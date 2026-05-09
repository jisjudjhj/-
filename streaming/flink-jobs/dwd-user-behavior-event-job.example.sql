-- DWD 标准化示例：
-- 将 Debezium 的 cdc.user_behavior 标准化为 dwd.user_behavior_event

CREATE TABLE cdc_user_behavior (
  id BIGINT,
  user_id BIGINT,
  product_id BIGINT,
  behavior_type STRING,
  create_time TIMESTAMP(3),
  PRIMARY KEY (id) NOT ENFORCED
) WITH (
  'connector' = 'kafka',
  'topic' = 'cdc.user_behavior',
  'properties.bootstrap.servers' = 'localhost:9092',
  'properties.group.id' = 'flink-dwd-user-behavior',
  'scan.startup.mode' = 'latest-offset',
  'format' = 'debezium-json',
  'debezium-json.schema-include' = 'false'
);

CREATE TABLE dwd_user_behavior_event (
  user_id BIGINT,
  product_id BIGINT,
  behavior_type STRING,
  event_time TIMESTAMP(3),
  source STRING,
  ts STRING
) WITH (
  'connector' = 'kafka',
  'topic' = 'dwd.user_behavior_event',
  'properties.bootstrap.servers' = 'localhost:9092',
  'format' = 'json'
);

INSERT INTO dwd_user_behavior_event
SELECT
  user_id,
  product_id,
  behavior_type,
  COALESCE(create_time, CURRENT_TIMESTAMP) AS event_time,
  'user_behavior' AS source,
  DATE_FORMAT(COALESCE(create_time, CURRENT_TIMESTAMP), 'yyyy-MM-dd''T''HH:mm:ss') AS ts
FROM cdc_user_behavior
WHERE user_id IS NOT NULL
  AND product_id IS NOT NULL
  AND behavior_type IS NOT NULL;
