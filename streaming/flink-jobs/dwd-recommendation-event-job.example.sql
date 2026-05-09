-- DWD 标准化示例：
-- 将 Debezium 的 cdc.recommendation_event 标准化为 dwd.recommendation_event

CREATE TABLE cdc_recommendation_event (
  id BIGINT,
  user_id BIGINT,
  product_id BIGINT,
  event_type STRING,
  scene STRING,
  trace_id STRING,
  recommendation_token STRING,
  experiment_group STRING,
  duration INT,
  order_id BIGINT,
  amount DECIMAL(16, 2),
  event_time TIMESTAMP(3),
  create_time TIMESTAMP(3),
  PRIMARY KEY (id) NOT ENFORCED
) WITH (
  'connector' = 'kafka',
  'topic' = 'cdc.recommendation_event',
  'properties.bootstrap.servers' = 'localhost:9092',
  'properties.group.id' = 'flink-dwd-recommendation-event',
  'scan.startup.mode' = 'latest-offset',
  'format' = 'debezium-json',
  'debezium-json.schema-include' = 'false'
);

CREATE TABLE dwd_recommendation_event (
  user_id BIGINT,
  product_id BIGINT,
  event_type STRING,
  scene STRING,
  trace_id STRING,
  recommendation_token STRING,
  experiment_group STRING,
  duration INT,
  order_id BIGINT,
  amount DOUBLE,
  event_time TIMESTAMP(3),
  event_day STRING,
  source STRING,
  ts STRING
) WITH (
  'connector' = 'kafka',
  'topic' = 'dwd.recommendation_event',
  'properties.bootstrap.servers' = 'localhost:9092',
  'format' = 'json'
);

INSERT INTO dwd_recommendation_event
SELECT
  user_id,
  product_id,
  LOWER(TRIM(event_type)) AS event_type,
  scene,
  trace_id,
  recommendation_token,
  experiment_group,
  duration,
  order_id,
  CAST(amount AS DOUBLE) AS amount,
  COALESCE(event_time, create_time, CURRENT_TIMESTAMP) AS event_time,
  DATE_FORMAT(COALESCE(event_time, create_time, CURRENT_TIMESTAMP), 'yyyy-MM-dd') AS event_day,
  'recommendation_event' AS source,
  DATE_FORMAT(COALESCE(event_time, create_time, CURRENT_TIMESTAMP), 'yyyy-MM-dd''T''HH:mm:ss') AS ts
FROM cdc_recommendation_event
WHERE user_id IS NOT NULL
  AND event_type IS NOT NULL
  AND LOWER(TRIM(event_type)) IN ('exposure', 'click', 'dwell', 'add_cart', 'order', 'refund');
