-- DWD 标准化示例：
-- 将 Debezium 的 cdc.product 标准化为 dwd.product_changed_event

CREATE TABLE cdc_product (
  id BIGINT,
  name STRING,
  category_id BIGINT,
  price DECIMAL(16, 2),
  status INT,
  update_time TIMESTAMP(3),
  PRIMARY KEY (id) NOT ENFORCED
) WITH (
  'connector' = 'kafka',
  'topic' = 'cdc.product',
  'properties.bootstrap.servers' = 'localhost:9092',
  'properties.group.id' = 'flink-dwd-product',
  'scan.startup.mode' = 'latest-offset',
  'format' = 'debezium-json',
  'debezium-json.schema-include' = 'false'
);

CREATE TABLE dwd_product_changed_event (
  product_id BIGINT,
  product_name STRING,
  category_id BIGINT,
  price DOUBLE,
  status INT,
  event_time TIMESTAMP(3),
  source STRING,
  ts STRING
) WITH (
  'connector' = 'kafka',
  'topic' = 'dwd.product_changed_event',
  'properties.bootstrap.servers' = 'localhost:9092',
  'format' = 'json'
);

INSERT INTO dwd_product_changed_event
SELECT
  id AS product_id,
  name AS product_name,
  category_id,
  CAST(price AS DOUBLE) AS price,
  status,
  COALESCE(update_time, CURRENT_TIMESTAMP) AS event_time,
  'product_cdc' AS source,
  DATE_FORMAT(COALESCE(update_time, CURRENT_TIMESTAMP), 'yyyy-MM-dd''T''HH:mm:ss') AS ts
FROM cdc_product
WHERE id IS NOT NULL;
