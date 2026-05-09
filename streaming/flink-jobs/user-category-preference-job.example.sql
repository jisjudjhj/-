-- Kafka/Flink 增强版示例：
-- 将行为事件按用户、品类聚合到 dws.user_category_preference

CREATE TABLE dwd_user_behavior_event (
  user_id BIGINT,
  product_id BIGINT,
  category_name STRING,
  behavior_type STRING,
  event_time TIMESTAMP(3),
  WATERMARK FOR event_time AS event_time - INTERVAL '5' SECOND
) WITH (
  'connector' = 'kafka',
  'topic' = 'dwd.user_behavior_event',
  'properties.bootstrap.servers' = 'localhost:9092',
  'properties.group.id' = 'flink-user-category-preference',
  'scan.startup.mode' = 'latest-offset',
  'format' = 'json'
);

CREATE TABLE dws_user_category_preference (
  userId BIGINT,
  categoryName STRING,
  weight DOUBLE,
  tags ARRAY<STRING>,
  ts STRING
) WITH (
  'connector' = 'kafka',
  'topic' = 'dws.user_category_preference',
  'properties.bootstrap.servers' = 'localhost:9092',
  'format' = 'json'
);

INSERT INTO dws_user_category_preference
SELECT
  user_id AS userId,
  category_name AS categoryName,
  SUM(
    CASE behavior_type
      WHEN 'purchase' THEN 5.0
      WHEN 'favorite' THEN 3.0
      WHEN 'cart' THEN 2.0
      WHEN 'view' THEN 1.0
      ELSE 0.5
    END
  ) AS weight,
  ARRAY[category_name] AS tags,
  DATE_FORMAT(CURRENT_TIMESTAMP, 'yyyy-MM-dd''T''HH:mm:ss') AS ts
FROM dwd_user_behavior_event
GROUP BY user_id, category_name;
