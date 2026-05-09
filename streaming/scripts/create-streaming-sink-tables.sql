CREATE TABLE IF NOT EXISTS `stream_user_behavior_distribution` (
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `behavior_type` VARCHAR(20) NOT NULL COMMENT '行为类型',
  `behavior_count` BIGINT NOT NULL DEFAULT 0 COMMENT '累计行为次数',
  `last_event_time` DATETIME DEFAULT NULL COMMENT '最近一次行为时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`, `behavior_type`),
  KEY `idx_update_time` (`update_time`),
  KEY `idx_behavior_type` (`behavior_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Flink CDC 用户行为实时分布汇总表';

CREATE TABLE IF NOT EXISTS `stream_user_category_preference` (
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `category_id` BIGINT NOT NULL COMMENT '分类ID',
  `category_name` VARCHAR(100) DEFAULT NULL COMMENT '分类名称',
  `preference_score` DOUBLE NOT NULL DEFAULT 0 COMMENT '偏好权重分数',
  `behavior_count` BIGINT NOT NULL DEFAULT 0 COMMENT '累计行为次数',
  `last_event_time` DATETIME DEFAULT NULL COMMENT '最近一次行为时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`, `category_id`),
  KEY `idx_stream_user_category_update` (`update_time`),
  KEY `idx_stream_user_category_score` (`preference_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Flink CDC 用户实时品类偏好表';

CREATE TABLE IF NOT EXISTS `stream_product_hotness_realtime` (
  `product_id` BIGINT NOT NULL COMMENT '商品ID',
  `category_id` BIGINT DEFAULT NULL COMMENT '分类ID',
  `hot_score` DOUBLE NOT NULL DEFAULT 0 COMMENT '实时热度分数',
  `behavior_count` BIGINT NOT NULL DEFAULT 0 COMMENT '累计行为次数',
  `purchase_count` BIGINT NOT NULL DEFAULT 0 COMMENT '累计购买次数',
  `last_event_time` DATETIME DEFAULT NULL COMMENT '最近一次行为时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`product_id`),
  KEY `idx_stream_product_hot_update` (`update_time`),
  KEY `idx_stream_product_hot_score` (`hot_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Flink CDC 商品实时热度表';
