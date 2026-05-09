-- ============================================
-- 电商个性化推荐系统 - 数据库结构脚本
-- 用途: 建库、删表、建表
-- 数据库: MySQL 8.0+
-- 字符集: utf8mb4
-- ============================================

SET NAMES utf8mb4;


CREATE DATABASE IF NOT EXISTS ecommerce_recommend DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ecommerce_recommend;


-- ============================================
-- 按外键依赖倒序删除
-- ============================================
DROP TABLE IF EXISTS `analytics_kmeans_feature_snapshot`;

DROP TABLE IF EXISTS `analytics_kmeans_user_result`;

DROP TABLE IF EXISTS `analytics_kmeans_segment`;

DROP TABLE IF EXISTS `analytics_kmeans_task`;

DROP TABLE IF EXISTS `analytics_report_snapshot`;

DROP TABLE IF EXISTS `analytics_association_rule`;

DROP TABLE IF EXISTS `analytics_product_similarity`;

DROP TABLE IF EXISTS `analytics_recommendation_exposure`;

DROP TABLE IF EXISTS `analytics_recommendation_result`;

DROP TABLE IF EXISTS `analytics_user_profile_snapshot`;

DROP TABLE IF EXISTS `analytics_rfm_segment_snapshot`;

DROP TABLE IF EXISTS `analytics_rfm_user_snapshot`;

DROP TABLE IF EXISTS `analytics_sales_daily`;

DROP TABLE IF EXISTS `analytics_behavior_heatmap`;

DROP TABLE IF EXISTS `analytics_funnel_daily`;

DROP TABLE IF EXISTS `analytics_behavior_daily`;

DROP TABLE IF EXISTS `analytics_job_log`;

DROP TABLE IF EXISTS `profile_change_request`;

DROP TABLE IF EXISTS `operation_log`;

DROP TABLE IF EXISTS `search_history`;

DROP TABLE IF EXISTS `refund_request`;

DROP TABLE IF EXISTS `mq_consume_log`;

DROP TABLE IF EXISTS `mq_outbox_event`;

DROP TABLE IF EXISTS `message`;

DROP TABLE IF EXISTS `im_ticket`;

DROP TABLE IF EXISTS `im_message`;

DROP TABLE IF EXISTS `im_conversation`;

DROP TABLE IF EXISTS `im_support_agent`;

DROP TABLE IF EXISTS `user_coupon`;

DROP TABLE IF EXISTS `coupon`;

DROP TABLE IF EXISTS `seckill_activity_apply`;

DROP TABLE IF EXISTS `seckill_activity`;

DROP TABLE IF EXISTS `product_review`;

DROP TABLE IF EXISTS `product_review_vote`;

DROP TABLE IF EXISTS `cart_item`;

DROP TABLE IF EXISTS `address`;

DROP TABLE IF EXISTS `wallet_transaction`;

DROP TABLE IF EXISTS `user_favorite`;

DROP TABLE IF EXISTS `user_preference`;

DROP TABLE IF EXISTS `stream_product_hotness_realtime`;

DROP TABLE IF EXISTS `stream_user_category_preference`;

DROP TABLE IF EXISTS `stream_user_behavior_distribution`;

DROP TABLE IF EXISTS `recommendation_event`;

DROP TABLE IF EXISTS `user_behavior`;

DROP TABLE IF EXISTS `order_item`;

DROP TABLE IF EXISTS `order`;

DROP TABLE IF EXISTS `product`;

DROP TABLE IF EXISTS `category`;

DROP TABLE IF EXISTS `banner`;

DROP TABLE IF EXISTS `user`;


-- ============================================
-- 1. 用户表
-- ============================================
CREATE TABLE `user` (
                        `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
                        `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
                        `password` VARCHAR(100) NOT NULL COMMENT '密码(BCrypt加密)',
                        `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
                        `phone` VARCHAR(20) DEFAULT NULL UNIQUE COMMENT '手机号',
                        `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
                        `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
                        `role` VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT '角色: admin/merchant/user',
                        `status` INT DEFAULT 1 COMMENT '状态: 0-禁用 1-正常',
                        `balance` DECIMAL(12,2) DEFAULT 0.00 COMMENT '钱包余额',
                        `email_verified` INT DEFAULT 0 COMMENT '邮箱是否已验证: 0-未验证 1-已验证',
                        `last_profile_change` DATETIME DEFAULT NULL COMMENT '上次资料修改通过时间',
                        `token_version` INT DEFAULT 0 COMMENT 'Token版本号，修改密码/禁用时递增',
                        `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        `deleted` INT DEFAULT 0 COMMENT '逻辑删除: 0-未删 1-已删',
                        PRIMARY KEY (`id`),
                        KEY `idx_username` (`username`),
                        KEY `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';


-- ============================================
-- 2. 商品分类表
-- ============================================
CREATE TABLE `category` (
                            `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类ID',
                            `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
                            `parent_id` BIGINT DEFAULT 0 COMMENT '父分类ID(0为顶级)',
                            `icon` VARCHAR(200) DEFAULT NULL COMMENT '图标',
                            `sort_order` INT DEFAULT 0 COMMENT '排序序号',
                            `audience_type` INT DEFAULT 0 COMMENT '0-all users 1-segment targeted 2-specific users',
                            `target_segment_codes` VARCHAR(255) DEFAULT NULL COMMENT 'comma-separated segment codes',
                            `target_user_ids` TEXT DEFAULT NULL COMMENT 'comma-separated user ids',
                            `audience_note` VARCHAR(255) DEFAULT NULL COMMENT 'coupon audience note',
                            `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                            PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';


-- ============================================
-- 3. 商品表
-- ============================================
CREATE TABLE `product` (
                           `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品ID',
                           `name` VARCHAR(200) NOT NULL COMMENT '商品名称',
                           `description` TEXT COMMENT '商品描述',
                           `price` DECIMAL(10,2) NOT NULL COMMENT '现价',
                           `original_price` DECIMAL(10,2) DEFAULT NULL COMMENT '原价',
                           `category_id` BIGINT DEFAULT NULL COMMENT '分类ID',
                           `merchant_id` BIGINT DEFAULT NULL COMMENT '商家ID',
                           `image` VARCHAR(500) DEFAULT NULL COMMENT '主图URL',
                           `images` JSON DEFAULT NULL COMMENT '图片列表',
                           `tags` JSON DEFAULT NULL COMMENT '商品标签',
                           `stock` INT DEFAULT 0 COMMENT '库存',
                           `sales_count` INT DEFAULT 0 COMMENT '销量',
                           `rating` DECIMAL(2,1) DEFAULT 5.0 COMMENT '评分(1-5)',
                           `status` INT DEFAULT 1 COMMENT '状态: 0-下架 1-上架',
                           `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                           `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                           `deleted` INT DEFAULT 0,
                           PRIMARY KEY (`id`),
                           KEY `idx_category` (`category_id`),
                           KEY `idx_merchant` (`merchant_id`),
                           KEY `idx_status` (`status`),
                           KEY `idx_sales` (`sales_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- ============================================
-- 3.1 商品SKU表（库存量单位）
-- ============================================
CREATE TABLE `product_sku` (
                               `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'SKU ID',
                               `product_id` BIGINT NOT NULL COMMENT '商品ID',
                               `sku_code` VARCHAR(50) NOT NULL COMMENT 'SKU编码',
                               `sku_name` VARCHAR(200) NOT NULL COMMENT 'SKU名称（如：黑色-128GB、L码）',
                               `price` DECIMAL(10,2) NOT NULL COMMENT 'SKU价格',
                               `original_price` DECIMAL(10,2) DEFAULT NULL COMMENT 'SKU原价',
                               `stock` INT DEFAULT 0 COMMENT 'SKU库存',
                               `sales_count` INT DEFAULT 0 COMMENT 'SKU销量',
                               `image` VARCHAR(500) DEFAULT NULL COMMENT 'SKU主图',
                               `spec_values` JSON DEFAULT NULL COMMENT '规格值JSON（如：{"颜色":"黑色","容量":"128GB"}）',
                               `status` INT DEFAULT 1 COMMENT '状态: 0-下架 1-上架',
                               `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                               `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uk_sku_code` (`sku_code`),
                               KEY `idx_product` (`product_id`),
                               KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SKU表';

-- ============================================
-- 3.2 商品规格名称表（如：颜色、尺码、容量）
-- ============================================
CREATE TABLE `product_spec_name` (
                                     `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '规格名称ID',
                                     `product_id` BIGINT NOT NULL COMMENT '商品ID',
                                     `spec_name` VARCHAR(50) NOT NULL COMMENT '规格名称（如：颜色、尺码）',
                                     `sort_order` INT DEFAULT 0 COMMENT '排序',
                                     `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                     PRIMARY KEY (`id`),
                                     KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品规格名称表';

-- ============================================
-- 3.3 商品规格值表（如：红色、蓝色、S、M、L）
-- ============================================
CREATE TABLE `product_spec_value` (
                                      `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '规格值ID',
                                      `spec_name_id` BIGINT NOT NULL COMMENT '规格名称ID',
                                      `spec_value` VARCHAR(100) NOT NULL COMMENT '规格值（如：红色、S码）',
                                      `image` VARCHAR(500) DEFAULT NULL COMMENT '规格值图片（颜色可能有图）',
                                      `sort_order` INT DEFAULT 0 COMMENT '排序',
                                      `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                      PRIMARY KEY (`id`),
                                      KEY `idx_spec_name` (`spec_name_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品规格值表';


-- ============================================
-- 4. 秒杀活动表
-- ============================================
CREATE TABLE `seckill_activity` (
                                    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '活动ID',
                                    `name` VARCHAR(100) NOT NULL COMMENT '活动名称',
                                    `cover_image` VARCHAR(500) DEFAULT NULL COMMENT '活动封面',
                                    `description` VARCHAR(500) DEFAULT NULL COMMENT '活动说明',
                                    `start_time` DATETIME NOT NULL COMMENT '开始时间',
                                    `end_time` DATETIME NOT NULL COMMENT '结束时间',
                                    `publish_status` TINYINT NOT NULL DEFAULT 0 COMMENT '发布状态: 0-下线 1-发布',
                                    `sort_order` INT DEFAULT 0 COMMENT '排序值',
                                    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                    PRIMARY KEY (`id`),
                                    KEY `idx_publish_time` (`publish_status`, `start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动表';


-- ============================================
-- 5. 秒杀活动报名表
-- ============================================
CREATE TABLE `seckill_activity_apply` (
                                          `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '报名ID',
                                          `activity_id` BIGINT NOT NULL COMMENT '活动ID',
                                          `merchant_id` BIGINT NOT NULL COMMENT '商家ID',
                                          `product_id` BIGINT NOT NULL COMMENT '商品ID',
                                          `product_price` DECIMAL(10,2) NOT NULL COMMENT '商品原价快照',
                                          `seckill_price` DECIMAL(10,2) NOT NULL COMMENT '秒杀价',
                                          `seckill_stock` INT NOT NULL COMMENT '秒杀总库存',
                                          `sold_count` INT NOT NULL DEFAULT 0 COMMENT '已售数量',
                                          `limit_per_user` INT NOT NULL DEFAULT 1 COMMENT '每人限购数量',
                                          `audit_status` TINYINT NOT NULL DEFAULT 0 COMMENT '审核状态: 0-待审核 1-通过 2-驳回 3-已撤回',
                                          `reject_reason` VARCHAR(255) DEFAULT NULL COMMENT '驳回原因',
                                          `audit_time` DATETIME DEFAULT NULL COMMENT '审核时间',
                                          `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                          `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                          PRIMARY KEY (`id`),
                                          KEY `idx_activity_status` (`activity_id`, `audit_status`),
                                          KEY `idx_merchant_status` (`merchant_id`, `audit_status`),
                                          KEY `idx_product_activity` (`product_id`, `activity_id`),
                                          UNIQUE KEY `uk_activity_merchant_product` (`activity_id`, `merchant_id`, `product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动报名表';


-- ============================================
-- 6. 订单表
-- ============================================
CREATE TABLE `order` (
                         `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
                         `user_id` BIGINT NOT NULL COMMENT '用户ID',
                         `order_no` VARCHAR(50) NOT NULL UNIQUE COMMENT '订单号',
                         `total_amount` DECIMAL(10,2) NOT NULL COMMENT '实付金额',
                         `original_amount` DECIMAL(10,2) DEFAULT NULL COMMENT '原始金额(优惠前)',
                         `discount_amount` DECIMAL(10,2) DEFAULT 0.00 COMMENT '优惠金额',
                         `user_coupon_id` BIGINT DEFAULT NULL COMMENT '使用的优惠券ID',
                         `seckill_activity_id` BIGINT DEFAULT NULL COMMENT '秒杀活动ID',
                         `seckill_apply_id` BIGINT DEFAULT NULL COMMENT '秒杀报名ID',
                         `status` INT DEFAULT 0 COMMENT '状态: 0-待付款 1-已付款 2-已发货 3-已完成 4-已取消',
                         `address` VARCHAR(500) DEFAULT NULL COMMENT '收货地址',
                         `receiver_name` VARCHAR(50) DEFAULT NULL COMMENT '收货人',
                         `receiver_phone` VARCHAR(20) DEFAULT NULL COMMENT '收货电话',
                         `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
                         `pay_time` DATETIME DEFAULT NULL COMMENT '支付时间',
                         `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                         `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                         PRIMARY KEY (`id`),
                         KEY `idx_user` (`user_id`),
                         KEY `idx_status` (`status`),
                         KEY `idx_order_no` (`order_no`),
                         KEY `idx_seckill_apply` (`seckill_apply_id`),
                         KEY `idx_status_create_time` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';


-- ============================================
-- 5. 订单明细表
-- ============================================
CREATE TABLE `order_item` (
                              `id` BIGINT NOT NULL AUTO_INCREMENT,
                              `order_id` BIGINT NOT NULL COMMENT '订单ID',
                              `product_id` BIGINT NOT NULL COMMENT '商品ID',
                              `sku_id` BIGINT DEFAULT NULL COMMENT 'SKU ID',
                              `sku_name` VARCHAR(200) DEFAULT NULL COMMENT 'SKU名称快照（如：黑色-128GB）',
                              `product_name` VARCHAR(200) DEFAULT NULL COMMENT '商品名(冗余)',
                              `product_image` VARCHAR(500) DEFAULT NULL COMMENT '商品图(冗余)',
                              `price` DECIMAL(10,2) NOT NULL COMMENT '单价',
                              `quantity` INT NOT NULL COMMENT '数量',
                              `subtotal` DECIMAL(10,2) NOT NULL COMMENT '小计',
                              PRIMARY KEY (`id`),
                              KEY `idx_order` (`order_id`),
                              KEY `idx_product` (`product_id`),
                              KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';


-- ============================================
-- 6. 用户行为表 (推荐算法数据源)
-- ============================================
CREATE TABLE `user_behavior` (
                                 `id` BIGINT NOT NULL AUTO_INCREMENT,
                                 `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                 `product_id` BIGINT DEFAULT NULL COMMENT '商品ID',
                                 `behavior_type` VARCHAR(20) NOT NULL COMMENT '行为类型: view/cart/purchase/favorite/search',
                                 `search_keyword` VARCHAR(100) DEFAULT NULL COMMENT '搜索关键词',
                                 `duration` INT DEFAULT NULL COMMENT '浏览时长(秒)',
                                 `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                 PRIMARY KEY (`id`),
                                 KEY `idx_user` (`user_id`),
                                 KEY `idx_product` (`product_id`),
                                 KEY `idx_type` (`behavior_type`),
                                 KEY `idx_time` (`create_time`),
                                 KEY `idx_user_product` (`user_id`, `product_id`),
                                 KEY `idx_product_user` (`product_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为记录表';


-- ============================================
-- 6.1 推荐统一事件日志表（埋点标准）
-- ============================================
CREATE TABLE `recommendation_event` (
                                        `id` BIGINT NOT NULL AUTO_INCREMENT,
                                        `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                        `product_id` BIGINT DEFAULT NULL COMMENT '商品ID',
                                        `event_type` VARCHAR(32) NOT NULL COMMENT '事件类型: exposure/click/dwell/add_cart/order/refund',
                                        `scene` VARCHAR(64) DEFAULT NULL COMMENT '事件场景: home/search/detail/order_pay/refund_approved',
                                        `trace_id` VARCHAR(64) DEFAULT NULL COMMENT '链路追踪ID',
                                        `recommendation_token` VARCHAR(64) DEFAULT NULL COMMENT '推荐曝光token',
                                        `experiment_group` VARCHAR(32) DEFAULT NULL COMMENT 'AB实验分组',
                                        `duration` INT DEFAULT NULL COMMENT '停留时长(秒)',
                                        `order_id` BIGINT DEFAULT NULL COMMENT '关联订单ID',
                                        `amount` DECIMAL(14,2) DEFAULT NULL COMMENT '事件金额',
                                        `event_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件发生时间',
                                        `metadata` TEXT DEFAULT NULL COMMENT '扩展字段(JSON字符串)',
                                        `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                        PRIMARY KEY (`id`),
                                        KEY `idx_rec_event_user_time` (`user_id`, `event_time`),
                                        KEY `idx_rec_event_type_time` (`event_type`, `event_time`),
                                        KEY `idx_rec_event_product_time` (`product_id`, `event_time`),
                                        KEY `idx_rec_event_order` (`order_id`),
                                        KEY `idx_rec_event_trace` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐埋点统一事件日志表';


-- ============================================
-- Streaming 汇总表：Flink CDC 用户行为分布
-- ============================================
CREATE TABLE IF NOT EXISTS `stream_user_behavior_distribution` (
                                                                   `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                                                   `behavior_type` VARCHAR(20) NOT NULL COMMENT '行为类型',
    `behavior_count` BIGINT NOT NULL DEFAULT 0 COMMENT '累计行为次数',
    `last_event_time` DATETIME DEFAULT NULL COMMENT '最近一次行为时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`user_id`, `behavior_type`),
    KEY `idx_stream_behavior_update` (`update_time`),
    KEY `idx_stream_behavior_type` (`behavior_type`)
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


-- ============================================
-- 7. 用户收藏表
-- ============================================
CREATE TABLE `user_favorite` (
                                 `id` BIGINT NOT NULL AUTO_INCREMENT,
                                 `user_id` BIGINT NOT NULL,
                                 `product_id` BIGINT NOT NULL,
                                 `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                 PRIMARY KEY (`id`),
                                 UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
                                 KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏表';


-- ============================================
-- 8. 用户偏好画像表
-- ============================================
CREATE TABLE `user_preference` (
                                   `id` BIGINT NOT NULL AUTO_INCREMENT,
                                   `user_id` BIGINT NOT NULL UNIQUE,
                                   `category_preferences` JSON DEFAULT NULL COMMENT '品类偏好 {categoryId: score}',
                                   `tag_preferences` JSON DEFAULT NULL COMMENT '标签偏好 {tag: score}',
                                   `price_range_min` DECIMAL(10,2) DEFAULT NULL COMMENT '价格偏好下限',
                                   `price_range_max` DECIMAL(10,2) DEFAULT NULL COMMENT '价格偏好上限',
                                   `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                   PRIMARY KEY (`id`),
                                   KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户偏好画像表';


-- ============================================
-- 9. 钱包交易记录表
-- ============================================
CREATE TABLE `wallet_transaction` (
                                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                                      `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                      `type` VARCHAR(20) NOT NULL COMMENT '交易类型: recharge-充值, pay-支付, refund-退款',
                                      `amount` DECIMAL(12,2) NOT NULL COMMENT '交易金额(正=收入, 负=支出)',
                                      `balance_before` DECIMAL(12,2) NOT NULL COMMENT '交易前余额',
                                      `balance_after` DECIMAL(12,2) NOT NULL COMMENT '交易后余额',
                                      `order_no` VARCHAR(50) DEFAULT NULL COMMENT '关联订单号',
                                      `description` VARCHAR(200) DEFAULT NULL COMMENT '交易描述',
                                      `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                      PRIMARY KEY (`id`),
                                      KEY `idx_user` (`user_id`),
                                      KEY `idx_type` (`type`),
                                      KEY `idx_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包交易记录表';


-- ============================================
-- 10. 轮播图Banner表
-- ============================================
CREATE TABLE `banner` (
                          `id` BIGINT NOT NULL AUTO_INCREMENT,
                          `title` VARCHAR(100) NOT NULL COMMENT '标题',
                          `image` VARCHAR(500) NOT NULL COMMENT '图片URL',
                          `link_type` VARCHAR(20) DEFAULT 'product' COMMENT '跳转类型: product/category/url/none',
                          `link_value` VARCHAR(200) DEFAULT NULL COMMENT '跳转值: 商品ID/分类ID/URL',
                          `sort_order` INT DEFAULT 0 COMMENT '排序(越小越前)',
                          `status` INT DEFAULT 1 COMMENT '0-隐藏 1-显示',
                          `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                          PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='轮播图Banner表';


-- ============================================
-- 收货地址表
-- ============================================
CREATE TABLE `address` (
                           `id` BIGINT NOT NULL AUTO_INCREMENT,
                           `user_id` BIGINT NOT NULL COMMENT '用户ID',
                           `receiver_name` VARCHAR(50) NOT NULL COMMENT '收货人姓名',
                           `receiver_phone` VARCHAR(20) NOT NULL COMMENT '收货人电话',
                           `province` VARCHAR(30) NOT NULL COMMENT '省',
                           `city` VARCHAR(30) NOT NULL COMMENT '市',
                           `district` VARCHAR(30) NOT NULL COMMENT '区/县',
                           `detail` VARCHAR(200) NOT NULL COMMENT '详细地址',
                           `is_default` INT DEFAULT 0 COMMENT '是否默认: 0-否 1-是',
                           `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                           `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                           PRIMARY KEY (`id`),
                           KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址表';


-- ============================================
-- 购物车表
-- ============================================
CREATE TABLE `cart_item` (
                             `id` BIGINT NOT NULL AUTO_INCREMENT,
                             `user_id` BIGINT NOT NULL COMMENT '用户ID',
                             `product_id` BIGINT NOT NULL COMMENT '商品ID',
                             `sku_id` BIGINT DEFAULT NULL COMMENT 'SKU ID',
                             `sku_name` VARCHAR(200) DEFAULT NULL COMMENT 'SKU名称（如：黑色-128GB）',
                             `quantity` INT NOT NULL DEFAULT 1 COMMENT '数量',
                             `selected` INT DEFAULT 1 COMMENT '是否选中: 0-否 1-是',
                             `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                             `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             PRIMARY KEY (`id`),
                             UNIQUE KEY `uk_user_product_sku` (`user_id`, `product_id`, `sku_id`),
                             KEY `idx_user_id` (`user_id`),
                             KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';


-- ============================================
-- 商品评价表
-- ============================================
CREATE TABLE `product_review` (
                                  `id` BIGINT NOT NULL AUTO_INCREMENT,
                                  `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                  `product_id` BIGINT NOT NULL COMMENT '商品ID',
                                  `order_id` BIGINT NOT NULL COMMENT '订单ID',
                                  `rating` INT NOT NULL COMMENT '评分 1-5',
                                  `content` TEXT COMMENT '评价内容',
                                  `images` JSON DEFAULT NULL COMMENT '评价图片',
                                  `video_urls` JSON DEFAULT NULL COMMENT '评价视频',
                                  `tags` JSON DEFAULT NULL COMMENT '评价标签，如尺码/物流/质量',
                                  `append_content` TEXT DEFAULT NULL COMMENT '追评内容',
                                  `append_images` JSON DEFAULT NULL COMMENT '追评图片',
                                  `append_video_urls` JSON DEFAULT NULL COMMENT '追评视频',
                                  `append_time` DATETIME DEFAULT NULL COMMENT '追评时间',
                                  `helpful_count` INT NOT NULL DEFAULT 0 COMMENT '有用投票数',
                                  `reply` TEXT DEFAULT NULL COMMENT '商家回复',
                                  `reply_time` DATETIME DEFAULT NULL COMMENT '回复时间',
                                  `status` INT DEFAULT 1 COMMENT '0-待审核 1-已通过 2-已拒绝',
                                  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                  PRIMARY KEY (`id`),
                                  KEY `idx_product` (`product_id`),
                                  KEY `idx_user` (`user_id`),
                                  KEY `idx_order` (`order_id`),
                                  KEY `idx_status_rating_ctime` (`status`, `rating`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评价表';

CREATE TABLE `product_review_vote` (
                                       `id` BIGINT NOT NULL AUTO_INCREMENT,
                                       `review_id` BIGINT NOT NULL COMMENT '评价ID',
                                       `user_id` BIGINT NOT NULL COMMENT '投票用户ID',
                                       `device_fingerprint` VARCHAR(128) DEFAULT NULL COMMENT '设备指纹',
                                       `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                       PRIMARY KEY (`id`),
                                       UNIQUE KEY `uk_review_user` (`review_id`, `user_id`),
                                       KEY `idx_user` (`user_id`),
                                       KEY `idx_device_ctime` (`device_fingerprint`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价有用投票表';


-- ============================================
-- 优惠券表
-- ============================================
CREATE TABLE `coupon` (
                          `id` BIGINT NOT NULL AUTO_INCREMENT,
                          `name` VARCHAR(100) NOT NULL COMMENT '优惠券名称',
                          `type` INT NOT NULL COMMENT '1-满减券 2-折扣券 3-无门槛券',
                          `value` DECIMAL(10,2) NOT NULL COMMENT '面值/折扣值',
                          `min_amount` DECIMAL(10,2) DEFAULT 0.00 COMMENT '最低消费金额',
                          `max_discount` DECIMAL(10,2) DEFAULT NULL COMMENT '折扣券最大优惠金额',
                          `total_count` INT NOT NULL COMMENT '发行总量',
                          `used_count` INT DEFAULT 0 COMMENT '已领取数量',
                          `start_time` DATETIME NOT NULL COMMENT '生效时间',
                          `end_time` DATETIME NOT NULL COMMENT '失效时间',
                          `status` INT DEFAULT 1 COMMENT '0-未开始 1-进行中 2-已结束',
                          `scope_type` TINYINT DEFAULT 0 COMMENT '0-平台通用券 1-商家店铺券',
                          `merchant_id` BIGINT DEFAULT NULL COMMENT '归属商家ID，平台券为空',
                          `audience_type` INT DEFAULT 0 COMMENT '0-公开领取 1-分群定向 2-指定用户',
                          `target_segment_codes` VARCHAR(255) DEFAULT '' COMMENT '目标分群编码，逗号分隔',
                          `target_user_ids` VARCHAR(1000) DEFAULT '' COMMENT '目标用户ID，逗号分隔',
                          `audience_note` VARCHAR(255) DEFAULT '' COMMENT '发放说明',
                          `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                          PRIMARY KEY (`id`),
                          KEY `idx_coupon_scope_merchant` (`scope_type`, `merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券表';


-- ============================================
-- 用户优惠券表
-- ============================================
CREATE TABLE `user_coupon` (
                               `id` BIGINT NOT NULL AUTO_INCREMENT,
                               `user_id` BIGINT NOT NULL COMMENT '用户ID',
                               `coupon_id` BIGINT NOT NULL COMMENT '优惠券ID',
                               `status` INT DEFAULT 0 COMMENT '0-未使用 1-已使用 2-已过期',
                               `order_id` BIGINT DEFAULT NULL COMMENT '使用的订单ID',
                               `use_time` DATETIME DEFAULT NULL COMMENT '使用时间',
                               `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uk_user_coupon` (`user_id`, `coupon_id`),
                               KEY `idx_user` (`user_id`),
                               KEY `idx_coupon` (`coupon_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券表';


-- ============================================
-- 消息通知表
-- ============================================
CREATE TABLE `message` (
                           `id` BIGINT NOT NULL AUTO_INCREMENT,
                           `user_id` BIGINT NOT NULL COMMENT '用户ID',
                           `title` VARCHAR(100) NOT NULL COMMENT '标题',
                           `content` TEXT NOT NULL COMMENT '内容',
                           `type` VARCHAR(20) NOT NULL COMMENT 'order-订单 system-系统 promotion-营销',
                           `related_id` BIGINT DEFAULT NULL COMMENT '关联ID(订单ID等)',
                           `is_read` INT DEFAULT 0 COMMENT '0-未读 1-已读',
                           `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                           PRIMARY KEY (`id`),
                           KEY `idx_user_read` (`user_id`, `is_read`),
                           KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知表';


-- ============================================
-- MQ Outbox 事件表
-- ============================================
CREATE TABLE `mq_outbox_event` (
                                   `id` BIGINT NOT NULL AUTO_INCREMENT,
                                   `event_id` VARCHAR(64) NOT NULL COMMENT '事件ID',
                                   `event_type` VARCHAR(100) NOT NULL COMMENT '事件类型',
                                   `exchange_name` VARCHAR(100) NOT NULL COMMENT '交换机名',
                                   `routing_key` VARCHAR(100) NOT NULL COMMENT '路由键',
                                   `biz_id` VARCHAR(100) DEFAULT NULL COMMENT '业务主键',
                                   `payload` LONGTEXT NOT NULL COMMENT '事件内容',
                                   `status` VARCHAR(20) NOT NULL COMMENT '状态: NEW/FAILED/SENT/DEAD',
                                   `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
                                   `next_retry_time` DATETIME DEFAULT NULL COMMENT '下次重试时间',
                                   `error_message` VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
                                   `sent_time` DATETIME DEFAULT NULL COMMENT '发送时间',
                                   `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                   `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                   PRIMARY KEY (`id`),
                                   UNIQUE KEY `uk_event_id` (`event_id`),
                                   KEY `idx_status_retry` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ outbox 事件表';


-- ============================================
-- MQ 消费幂等日志表
-- ============================================
CREATE TABLE `mq_consume_log` (
                                  `id` BIGINT NOT NULL AUTO_INCREMENT,
                                  `event_id` VARCHAR(64) NOT NULL COMMENT '事件ID',
                                  `consumer_name` VARCHAR(100) NOT NULL COMMENT '消费者名称',
                                  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uk_event_consumer` (`event_id`, `consumer_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ 消费幂等日志表';


-- ============================================
-- 退款申请表
-- ============================================
CREATE TABLE `refund_request` (
                                  `id` BIGINT NOT NULL AUTO_INCREMENT,
                                  `order_id` BIGINT NOT NULL COMMENT '订单ID',
                                  `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                  `reason` VARCHAR(200) NOT NULL COMMENT '退款原因',
                                  `description` TEXT DEFAULT NULL COMMENT '详细说明',
                                  `images` JSON DEFAULT NULL COMMENT '凭证图片',
                                  `amount` DECIMAL(12,2) NOT NULL COMMENT '退款金额',
                                  `status` INT DEFAULT 0 COMMENT '0-待审核 1-已同意 2-已拒绝 3-已退款',
                                  `reject_reason` VARCHAR(200) DEFAULT NULL COMMENT '拒绝原因',
                                  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                  PRIMARY KEY (`id`),
                                  KEY `idx_order` (`order_id`),
                                  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款申请表';


-- ============================================
-- 搜索历史表
-- ============================================
CREATE TABLE `search_history` (
                                  `id` BIGINT NOT NULL AUTO_INCREMENT,
                                  `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                  `keyword` VARCHAR(100) NOT NULL COMMENT '搜索关键词',
                                  `search_count` INT DEFAULT 1 COMMENT '搜索次数',
                                  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uk_user_keyword` (`user_id`, `keyword`),
                                  KEY `idx_user_time` (`user_id`, `update_time`),
                                  KEY `idx_keyword` (`keyword`),
                                  KEY `idx_keyword_count` (`keyword`, `search_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索历史表';


-- ============================================
-- 操作日志表
-- ============================================
CREATE TABLE `operation_log` (
                                 `id` BIGINT NOT NULL AUTO_INCREMENT,
                                 `user_id` BIGINT DEFAULT NULL COMMENT '操作人ID',
                                 `username` VARCHAR(50) DEFAULT NULL COMMENT '操作人用户名',
                                 `role` VARCHAR(20) DEFAULT NULL COMMENT '操作人角色',
                                 `module` VARCHAR(50) DEFAULT NULL COMMENT '操作模块',
                                 `action` VARCHAR(100) DEFAULT NULL COMMENT '操作动作',
                                 `method` VARCHAR(10) DEFAULT NULL COMMENT 'HTTP方法',
                                 `url` VARCHAR(200) DEFAULT NULL COMMENT '请求URL',
                                 `params` TEXT DEFAULT NULL COMMENT '请求参数',
                                 `ip` VARCHAR(50) DEFAULT NULL COMMENT '操作IP',
                                 `status` INT DEFAULT NULL COMMENT '1-成功 0-失败',
                                 `error_msg` VARCHAR(500) DEFAULT NULL COMMENT '错误信息',
                                 `cost_time` BIGINT DEFAULT NULL COMMENT '耗时(ms)',
                                 `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                 PRIMARY KEY (`id`),
                                 KEY `idx_user` (`user_id`),
                                 KEY `idx_module` (`module`),
                                 KEY `idx_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';


-- ============================================
-- 用户资料修改审核表
-- ============================================
CREATE TABLE IF NOT EXISTS `profile_change_request` (
                                                        `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                        `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                                        `new_nickname` VARCHAR(50) DEFAULT NULL COMMENT '申请的新昵称',
    `new_avatar` VARCHAR(500) DEFAULT NULL COMMENT '申请的新头像URL',
    `old_nickname` VARCHAR(50) DEFAULT NULL COMMENT '修改前昵称',
    `old_avatar` VARCHAR(500) DEFAULT NULL COMMENT '修改前头像URL',
    `status` INT DEFAULT 0 COMMENT '0-待审核 1-通过 2-拒绝',
    `reject_reason` VARCHAR(200) DEFAULT NULL COMMENT '拒绝原因',
    `review_time` DATETIME DEFAULT NULL COMMENT '审核时间',
    `reviewer_id` BIGINT DEFAULT NULL COMMENT '审核人ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_time` (`create_time`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户资料修改审核表';


-- ============================================
-- IM 客服与会话表
-- ============================================
CREATE TABLE IF NOT EXISTS `im_support_agent` (
                                                  `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                  `user_id` BIGINT NOT NULL COMMENT '客服账号ID，对应 user.id',
                                                  `display_name` VARCHAR(64) NOT NULL COMMENT '展示名',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像',
    `agent_type` VARCHAR(20) NOT NULL DEFAULT 'official' COMMENT 'official/platform',
    `online_status` TINYINT NOT NULL DEFAULT 1 COMMENT '0-离线 1-在线',
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '0-禁用 1-启用',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_im_support_user` (`user_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='官方客服席位表';

CREATE TABLE IF NOT EXISTS `im_conversation` (
                                                 `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                 `conversation_no` VARCHAR(40) NOT NULL COMMENT '会话编号',
    `conversation_type` VARCHAR(20) NOT NULL COMMENT 'merchant/support',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `merchant_id` BIGINT DEFAULT NULL COMMENT '商家ID，对应 user.id',
    `support_agent_id` BIGINT DEFAULT NULL COMMENT '官方客服账号ID，对应 user.id',
    `order_id` BIGINT DEFAULT NULL COMMENT '关联订单ID',
    `product_id` BIGINT DEFAULT NULL COMMENT '关联商品ID',
    `status` VARCHAR(20) NOT NULL DEFAULT 'open' COMMENT 'open/pending_support/ai_serving/resolved/closed',
    `is_escalated` TINYINT NOT NULL DEFAULT 0 COMMENT '0-普通沟通 1-平台已介入',
    `priority` VARCHAR(20) NOT NULL DEFAULT 'normal' COMMENT 'low/normal/high/urgent',
    `last_message` VARCHAR(1000) DEFAULT NULL COMMENT '最后一条消息摘要',
    `last_message_type` VARCHAR(20) DEFAULT NULL COMMENT 'text/system/image/order_card',
    `last_sender_role` VARCHAR(20) DEFAULT NULL COMMENT 'user/merchant/admin/system',
    `last_sender_id` BIGINT DEFAULT NULL COMMENT '最后发送人ID',
    `last_message_time` DATETIME DEFAULT NULL COMMENT '最后消息时间',
    `unread_user` INT NOT NULL DEFAULT 0 COMMENT '用户未读',
    `unread_merchant` INT NOT NULL DEFAULT 0 COMMENT '商家未读',
    `unread_support` INT NOT NULL DEFAULT 0 COMMENT '客服未读',
    `closed_time` DATETIME DEFAULT NULL COMMENT '关闭时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_im_conversation_no` (`conversation_no`),
    KEY `idx_im_user` (`user_id`, `status`, `last_message_time`),
    KEY `idx_im_merchant` (`merchant_id`, `status`, `last_message_time`),
    KEY `idx_im_support` (`support_agent_id`, `status`, `last_message_time`),
    KEY `idx_im_order` (`order_id`),
    KEY `idx_im_product` (`product_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IM 会话表';

CREATE TABLE IF NOT EXISTS `im_message` (
                                            `id` BIGINT NOT NULL AUTO_INCREMENT,
                                            `conversation_id` BIGINT NOT NULL COMMENT '会话ID',
                                            `sender_role` VARCHAR(20) NOT NULL COMMENT 'user/merchant/admin/system/ai',
    `sender_id` BIGINT DEFAULT NULL COMMENT '发送人ID',
    `message_type` VARCHAR(20) NOT NULL DEFAULT 'text' COMMENT 'text/system/image/order_card/product_card',
    `content` TEXT NOT NULL COMMENT '消息正文',
    `payload_json` JSON DEFAULT NULL COMMENT '扩展卡片数据',
    `is_system` TINYINT NOT NULL DEFAULT 0 COMMENT '是否系统消息',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_im_message_conversation` (`conversation_id`, `id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IM 消息表';

CREATE TABLE IF NOT EXISTS `im_ticket` (
                                           `id` BIGINT NOT NULL AUTO_INCREMENT,
                                           `conversation_id` BIGINT NOT NULL COMMENT '关联会话ID',
                                           `review_id` BIGINT DEFAULT NULL COMMENT '关联低分评价ID',
                                           `ticket_no` VARCHAR(40) NOT NULL COMMENT '工单编号',
    `ticket_status` VARCHAR(20) NOT NULL DEFAULT 'pending_assign' COMMENT 'pending_assign/processing/resolved/closed',
    `source_type` VARCHAR(30) NOT NULL DEFAULT 'user_support' COMMENT 'user_support/merchant_escalation/ai_transfer',
    `issue_type` VARCHAR(30) NOT NULL DEFAULT 'general' COMMENT 'general/logistics/refund/dispute/product',
    `issue_summary` VARCHAR(255) NOT NULL COMMENT '问题摘要',
    `issue_detail` TEXT DEFAULT NULL COMMENT '问题详情',
    `created_by_user_id` BIGINT NOT NULL COMMENT '创建人(用户)ID',
    `assigned_support_id` BIGINT DEFAULT NULL COMMENT '指派客服ID，对应 user.id',
    `resolved_by_id` BIGINT DEFAULT NULL COMMENT '结单人ID',
    `assigned_time` DATETIME DEFAULT NULL COMMENT '分配时间',
    `resolved_time` DATETIME DEFAULT NULL COMMENT '完成时间',
    `sla_deadline_time` DATETIME DEFAULT NULL COMMENT 'SLA 截止时间',
    `sla_escalation_level` INT NOT NULL DEFAULT 0 COMMENT 'SLA 升级等级',
    `last_escalation_time` DATETIME DEFAULT NULL COMMENT '最近一次升级时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_im_ticket_no` (`ticket_no`),
    KEY `idx_im_ticket_conversation` (`conversation_id`),
    KEY `idx_im_ticket_support` (`assigned_support_id`, `ticket_status`),
    KEY `idx_im_ticket_review` (`review_id`),
    KEY `idx_im_ticket_status_deadline` (`ticket_status`, `sla_deadline_time`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服介入工单表';


-- ============================================
-- 数据分析扩展表
-- 用途:
--   1. Python 离线运行分析/训练任务
--   2. 结果写入以下 MySQL 表
--   3. Java 读取这些表并对外提供稳定 API
-- ============================================

-- ============================================
-- 1. 任务执行日志表
-- ============================================
CREATE TABLE IF NOT EXISTS `analytics_job_log` (
                                                   `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                   `job_name` VARCHAR(64) NOT NULL COMMENT '任务名称，如 rfm_daily、recommend_train',
    `batch_no` VARCHAR(64) NOT NULL COMMENT 'Python 生成的批次号',
    `job_type` VARCHAR(32) DEFAULT 'python_offline' COMMENT '任务类型: python_offline/python_realtime',
    `status` VARCHAR(20) NOT NULL COMMENT '状态: running/success/failed',
    `snapshot_date` DATE DEFAULT NULL COMMENT '逻辑快照日期',
    `processed_count` BIGINT DEFAULT 0 COMMENT '源数据处理行数',
    `output_count` BIGINT DEFAULT 0 COMMENT '写入结果行数',
    `result_summary` JSON DEFAULT NULL COMMENT 'JSON 结果摘要',
    `error_message` VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
    `start_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '任务开始时间',
    `end_time` DATETIME DEFAULT NULL COMMENT '任务结束时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_job_batch` (`job_name`, `batch_no`),
    KEY `idx_job_status` (`job_name`, `status`),
    KEY `idx_snapshot_date` (`snapshot_date`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Python 分析任务日志表';


-- ============================================
-- 2. 每日行为汇总表
-- ============================================
CREATE TABLE IF NOT EXISTS `analytics_behavior_daily` (
                                                          `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                          `stat_date` DATE NOT NULL COMMENT '统计日期',
                                                          `behavior_type` VARCHAR(20) NOT NULL COMMENT '行为类型: view/cart/favorite/purchase/search',
    `user_count` BIGINT DEFAULT 0 COMMENT '去重用户数',
    `event_count` BIGINT DEFAULT 0 COMMENT '总事件数',
    `product_count` BIGINT DEFAULT 0 COMMENT '涉及去重商品数',
    `avg_duration` DECIMAL(10,2) DEFAULT NULL COMMENT '平均浏览时长(秒)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_stat_behavior` (`stat_date`, `behavior_type`),
    KEY `idx_behavior_type` (`behavior_type`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日用户行为汇总表';


-- ============================================
-- 3. 每日漏斗分析表
-- ============================================
CREATE TABLE IF NOT EXISTS `analytics_funnel_daily` (
                                                        `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                        `stat_date` DATE NOT NULL COMMENT '统计日期',
                                                        `view_user_count` BIGINT DEFAULT 0,
                                                        `cart_user_count` BIGINT DEFAULT 0,
                                                        `favorite_user_count` BIGINT DEFAULT 0,
                                                        `purchase_user_count` BIGINT DEFAULT 0,
                                                        `view_to_cart_rate` DECIMAL(8,2) DEFAULT 0.00 COMMENT '浏览→加购转化率(%)',
    `cart_to_purchase_rate` DECIMAL(8,2) DEFAULT 0.00 COMMENT '加购→下单转化率(%)',
    `view_to_purchase_rate` DECIMAL(8,2) DEFAULT 0.00 COMMENT '浏览→下单转化率(%)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_funnel_date` (`stat_date`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日漏斗分析表';


-- ============================================
-- 4. 行为热力图表
-- ============================================
CREATE TABLE IF NOT EXISTS `analytics_behavior_heatmap` (
                                                            `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                            `stat_date` DATE NOT NULL COMMENT '统计日期',
                                                            `day_of_week` TINYINT NOT NULL COMMENT '星期(1-7, 周一到周日)',
                                                            `hour_of_day` TINYINT NOT NULL COMMENT '小时(0-23)',
                                                            `behavior_type` VARCHAR(20) NOT NULL DEFAULT 'all' COMMENT '行为类型: all/view/cart/favorite/purchase/search',
    `event_count` BIGINT DEFAULT 0 COMMENT '该时段事件数',
    `user_count` BIGINT DEFAULT 0 COMMENT '该时段去重用户数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_heatmap_bucket` (`stat_date`, `day_of_week`, `hour_of_day`, `behavior_type`),
    KEY `idx_heatmap_query` (`stat_date`, `behavior_type`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按星期和小时的行为热力图表';


-- ============================================
-- 5. 每日销售趋势与预测表
-- ============================================
CREATE TABLE IF NOT EXISTS `analytics_sales_daily` (
                                                       `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                       `stat_date` DATE NOT NULL COMMENT '业务日期',
                                                       `is_forecast` TINYINT DEFAULT 0 COMMENT '0-实际值 1-预测值',
                                                       `paid_order_count` BIGINT DEFAULT 0,
                                                       `paid_user_count` BIGINT DEFAULT 0,
                                                       `revenue` DECIMAL(14,2) DEFAULT 0.00 COMMENT '已付款收入',
    `refund_amount` DECIMAL(14,2) DEFAULT 0.00 COMMENT '退款金额',
    `avg_order_value` DECIMAL(14,2) DEFAULT 0.00,
    `moving_avg_7d` DECIMAL(14,2) DEFAULT NULL,
    `week_over_week` DECIMAL(10,2) DEFAULT NULL COMMENT '周环比(%)',
    `forecast_confidence` DECIMAL(5,2) DEFAULT NULL COMMENT '预测置信度(0-100)',
    `model_version` VARCHAR(50) DEFAULT NULL COMMENT 'Python 模型版本',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sales_daily` (`stat_date`, `is_forecast`),
    KEY `idx_sales_query` (`is_forecast`, `stat_date`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日销售数据与预测表';


-- ============================================
-- 6. RFM 用户快照表
-- ============================================
CREATE TABLE IF NOT EXISTS `analytics_rfm_user_snapshot` (
                                                             `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                             `snapshot_date` DATE NOT NULL COMMENT '快照日期',
                                                             `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                                             `recency_days` INT DEFAULT 0,
                                                             `frequency_count` INT DEFAULT 0,
                                                             `monetary_amount` DECIMAL(14,2) DEFAULT 0.00,
    `r_score` TINYINT DEFAULT 0,
    `f_score` TINYINT DEFAULT 0,
    `m_score` TINYINT DEFAULT 0,
    `rfm_code` VARCHAR(8) DEFAULT NULL COMMENT 'RFM 编码，如 111',
    `segment_name` VARCHAR(50) DEFAULT NULL COMMENT '分群名称，如 high_value',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rfm_user_snapshot` (`snapshot_date`, `user_id`),
    KEY `idx_rfm_segment` (`snapshot_date`, `segment_name`),
    KEY `idx_rfm_user` (`user_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RFM 用户快照表';


-- ============================================
-- 7. RFM 分群快照表
-- ============================================
CREATE TABLE IF NOT EXISTS `analytics_rfm_segment_snapshot` (
                                                                `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                                `snapshot_date` DATE NOT NULL COMMENT '快照日期',
                                                                `segment_name` VARCHAR(50) NOT NULL COMMENT '分群标签',
    `user_count` BIGINT DEFAULT 0,
    `percentage` DECIMAL(8,2) DEFAULT 0.00 COMMENT '当前快照占比(%)',
    `avg_recency_days` DECIMAL(10,2) DEFAULT 0.00,
    `avg_frequency` DECIMAL(10,2) DEFAULT 0.00,
    `avg_monetary` DECIMAL(14,2) DEFAULT 0.00,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rfm_segment_snapshot` (`snapshot_date`, `segment_name`),
    KEY `idx_rfm_segment_date` (`snapshot_date`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RFM 分群汇总快照表';


-- ============================================
-- 8. 用户画像快照表(推荐用)
-- ============================================
CREATE TABLE IF NOT EXISTS `analytics_user_profile_snapshot` (
                                                                 `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                                 `snapshot_date` DATE NOT NULL COMMENT '快照日期',
                                                                 `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                                                 `total_behaviors` BIGINT DEFAULT 0,
                                                                 `category_preferences` JSON DEFAULT NULL COMMENT '品类偏好映射',
                                                                 `tag_preferences` JSON DEFAULT NULL COMMENT '标签偏好映射',
                                                                 `price_range_min` DECIMAL(10,2) DEFAULT NULL,
    `price_range_max` DECIMAL(10,2) DEFAULT NULL,
    `cold_start` TINYINT DEFAULT 0 COMMENT '是否冷启动: 0-否 1-是',
    `model_version` VARCHAR(50) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_profile_snapshot` (`snapshot_date`, `user_id`),
    KEY `idx_profile_user` (`user_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Python 生成的用户画像快照表';


-- ============================================
-- 9. 推荐结果表
-- ============================================
CREATE TABLE IF NOT EXISTS `analytics_recommendation_result` (
                                                                 `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                                 `snapshot_date` DATE NOT NULL COMMENT '快照日期',
                                                                 `scene` VARCHAR(32) NOT NULL DEFAULT 'guess_you_like' COMMENT '推荐场景: guess_you_like/personal/hot/similar',
    `user_id` BIGINT NOT NULL DEFAULT 0 COMMENT '用户ID(0 表示全局场景如热门)',
    `product_id` BIGINT NOT NULL COMMENT '推荐商品ID',
    `rank_no` INT NOT NULL COMMENT '排名位置',
    `score` DECIMAL(12,6) DEFAULT NULL COMMENT '模型最终得分',
    `algorithm` VARCHAR(32) DEFAULT NULL COMMENT '算法: cf/cb/hybrid/rules/hot',
    `reason` VARCHAR(255) DEFAULT NULL COMMENT '推荐理由简述',
    `model_version` VARCHAR(50) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_recommend_rank` (`snapshot_date`, `scene`, `user_id`, `rank_no`),
    UNIQUE KEY `uk_recommend_product` (`snapshot_date`, `scene`, `user_id`, `product_id`),
    KEY `idx_recommend_lookup` (`scene`, `user_id`, `snapshot_date`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Python 生成的推荐结果表';


-- ============================================
-- 9.1 推荐曝光归因表
-- ============================================
CREATE TABLE IF NOT EXISTS `analytics_recommendation_exposure` (
                                                                   `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                                   `exposure_token` VARCHAR(64) NOT NULL COMMENT '单条曝光商品 Token(返回前端)',
    `request_token` VARCHAR(64) NOT NULL COMMENT '一次推荐请求的分组 Token',
    `user_id` BIGINT NOT NULL COMMENT '用户ID(仅记录登录流量)',
    `product_id` BIGINT NOT NULL COMMENT '推荐商品ID',
    `scene` VARCHAR(32) NOT NULL COMMENT '推荐场景: personal/guess_you_like/hot/similar',
    `rank_no` INT NOT NULL COMMENT '返回列表中的排名位置',
    `algorithm` VARCHAR(64) DEFAULT NULL COMMENT '快照算法或实时策略标签',
    `source_type` VARCHAR(32) DEFAULT 'live' COMMENT '来源类型: snapshot/live',
    `reason_type` VARCHAR(64) DEFAULT NULL COMMENT '推荐理由类型: BEHAVIOR_MATCH/HOT_TREND/SIMILARITY等',
    `model_version` VARCHAR(50) DEFAULT NULL COMMENT '离线或实时模型版本',
    `experiment_group` VARCHAR(32) DEFAULT NULL COMMENT 'AB 实验分组编码',
    `segment_code` VARCHAR(32) DEFAULT NULL COMMENT '曝光时用户的 K-means 分群编码',
    `segment_name` VARCHAR(64) DEFAULT NULL COMMENT '曝光时用户的 K-means 分群名称',
    `exposure_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '推荐曝光时间',
    `click_time` DATETIME DEFAULT NULL COMMENT '曝光后首次详情页点击时间',
    `favorite_time` DATETIME DEFAULT NULL COMMENT '曝光后首次收藏时间',
    `cart_time` DATETIME DEFAULT NULL COMMENT '曝光后首次加购时间',
    `purchase_time` DATETIME DEFAULT NULL COMMENT '曝光后首次付款归因时间',
    `order_id` BIGINT DEFAULT NULL COMMENT '转化成功的订单ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_recommend_exposure_token` (`exposure_token`),
    KEY `idx_recommend_exposure_scene_time` (`scene`, `exposure_time`),
    KEY `idx_recommend_exposure_user_product` (`user_id`, `product_id`, `exposure_time`),
    KEY `idx_recommend_exposure_segment` (`segment_code`, `exposure_time`),
    KEY `idx_recommend_exposure_purchase` (`purchase_time`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐曝光与归因事实表';

CREATE TABLE IF NOT EXISTS `analytics_recommendation_metric_daily` (
                                                                       `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                                       `stat_date` DATE NOT NULL COMMENT '统计日期',
                                                                       `scene` VARCHAR(64) NOT NULL DEFAULT 'unknown' COMMENT '推荐场景',
    `algorithm` VARCHAR(64) NOT NULL DEFAULT 'unknown' COMMENT '算法或召回策略',
    `segment_code` VARCHAR(64) NOT NULL DEFAULT 'unknown' COMMENT '用户分群',
    `exposure_count` BIGINT DEFAULT 0,
    `click_count` BIGINT DEFAULT 0,
    `cart_count` BIGINT DEFAULT 0,
    `order_count` BIGINT DEFAULT 0,
    `paid_order_count` BIGINT DEFAULT 0,
    `refund_count` BIGINT DEFAULT 0,
    `gmv` DECIMAL(14,2) DEFAULT 0.00,
    `refund_amount` DECIMAL(14,2) DEFAULT 0.00,
    `ctr` DECIMAL(10,4) DEFAULT 0.0000,
    `cvr` DECIMAL(10,4) DEFAULT 0.0000,
    `cart_rate` DECIMAL(10,4) DEFAULT 0.0000,
    `refund_rate` DECIMAL(10,4) DEFAULT 0.0000,
    `model_version` VARCHAR(50) DEFAULT NULL,
    `detail_json` JSON DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_recommend_metric_daily` (`stat_date`, `scene`, `algorithm`, `segment_code`),
    KEY `idx_recommend_metric_scene` (`scene`, `stat_date`),
    KEY `idx_recommend_metric_segment` (`segment_code`, `stat_date`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日推荐指标快照表';

CREATE TABLE IF NOT EXISTS `analytics_data_quality_alert` (
                                                              `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                              `stat_date` DATE NOT NULL COMMENT '检测日期',
                                                              `check_code` VARCHAR(64) NOT NULL COMMENT '检测项编码',
    `severity` VARCHAR(16) NOT NULL DEFAULT 'info' COMMENT 'info/warning/critical',
    `status` VARCHAR(16) NOT NULL DEFAULT 'open' COMMENT 'open/resolved',
    `actual_value` DECIMAL(18,6) DEFAULT NULL,
    `threshold_value` DECIMAL(18,6) DEFAULT NULL,
    `message` VARCHAR(500) DEFAULT NULL,
    `detail_json` JSON DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_quality_alert` (`stat_date`, `check_code`),
    KEY `idx_quality_status` (`status`, `severity`, `stat_date`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据质量检测告警表';

CREATE TABLE IF NOT EXISTS `analytics_ltr_training_sample` (
                                                               `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                               `snapshot_date` DATE NOT NULL,
                                                               `user_id` BIGINT NOT NULL,
                                                               `product_id` BIGINT NOT NULL,
                                                               `scene` VARCHAR(64) NOT NULL DEFAULT 'unknown',
    `segment_code` VARCHAR(64) DEFAULT NULL,
    `feature_json` JSON NOT NULL,
    `label_click` TINYINT DEFAULT 0,
    `label_cart` TINYINT DEFAULT 0,
    `label_order` TINYINT DEFAULT 0,
    `label_refund` TINYINT DEFAULT 0,
    `label_value` DECIMAL(10,4) DEFAULT 0.0000,
    `model_version` VARCHAR(50) DEFAULT 'ltr-skeleton-v1',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ltr_sample` (`snapshot_date`, `user_id`, `product_id`, `scene`),
    KEY `idx_ltr_scene` (`scene`, `snapshot_date`),
    KEY `idx_ltr_segment` (`segment_code`, `snapshot_date`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Learning-to-rank 训练样本表';

CREATE TABLE IF NOT EXISTS `analytics_ltr_model_artifact` (
                                                              `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                              `snapshot_date` DATE NOT NULL,
                                                              `model_version` VARCHAR(50) NOT NULL,
    `algorithm` VARCHAR(50) NOT NULL DEFAULT 'weighted-logistic-skeleton',
    `feature_columns` JSON NOT NULL,
    `train_summary` JSON DEFAULT NULL,
    `artifact_uri` VARCHAR(500) DEFAULT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'success',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ltr_artifact` (`snapshot_date`, `model_version`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Learning-to-rank 训练产物表';


-- ============================================
-- 10. 商品相似度表
-- ============================================
CREATE TABLE IF NOT EXISTS `analytics_product_similarity` (
                                                              `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                              `snapshot_date` DATE NOT NULL COMMENT '快照日期',
                                                              `product_id` BIGINT NOT NULL,
                                                              `similar_product_id` BIGINT NOT NULL,
                                                              `similarity` DECIMAL(12,6) NOT NULL COMMENT '相似度得分',
    `source_algorithm` VARCHAR(32) DEFAULT NULL COMMENT '算法来源: item_cf/content/jaccard/embedding',
    `rank_no` INT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_similarity` (`snapshot_date`, `product_id`, `similar_product_id`),
    KEY `idx_product_similarity_rank` (`product_id`, `snapshot_date`, `rank_no`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品相似度 Top-N 表';


-- ============================================
-- 11. 购物篮关联规则表
-- ============================================
CREATE TABLE IF NOT EXISTS `analytics_association_rule` (
                                                            `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                            `snapshot_date` DATE NOT NULL COMMENT '快照日期',
                                                            `lhs_product_id` BIGINT NOT NULL COMMENT '规则前项商品ID',
                                                            `rhs_product_id` BIGINT NOT NULL COMMENT '规则后项商品ID',
                                                            `support_count` BIGINT DEFAULT 0,
                                                            `support_rate` DECIMAL(10,6) DEFAULT 0.000000,
    `confidence` DECIMAL(10,6) DEFAULT 0.000000,
    `lift` DECIMAL(10,4) DEFAULT 0.0000,
    `rank_no` INT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_association_rule` (`snapshot_date`, `lhs_product_id`, `rhs_product_id`),
    KEY `idx_association_lhs` (`snapshot_date`, `lhs_product_id`, `rank_no`),
    KEY `idx_association_lift` (`snapshot_date`, `lift`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物篮关联规则表';


-- ============================================
-- 12. 报表快照表
-- ============================================
CREATE TABLE IF NOT EXISTS `analytics_report_snapshot` (
                                                           `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                           `snapshot_date` DATE NOT NULL COMMENT '快照日期',
                                                           `report_code` VARCHAR(50) NOT NULL COMMENT '报表编码: dashboard_overview/merchant_daily/recommend_summary',
    `report_name` VARCHAR(100) NOT NULL COMMENT '报表显示名称',
    `report_data` JSON NOT NULL COMMENT '报表 JSON 数据(供 Java 控制器使用)',
    `source_tables` VARCHAR(500) DEFAULT NULL COMMENT '数据来源表(逗号分隔)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_report_snapshot` (`snapshot_date`, `report_code`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物化报表快照表';


-- ============================================
-- 建议读取优先级
-- 1. analytics_* 表
-- 2. Redis 缓存(热门看板/推荐)
-- 3. 降级到 Java 实时计算
-- ============================================

-- ============================================
-- 13. K-means 聚类任务表
-- MySQL 职责:
--   - 持久化任务状态
--   - 模型元数据
--   - 最新及历史快照
-- Redis 职责:
--   - 仅缓存最新摘要
-- ============================================
CREATE TABLE IF NOT EXISTS `analytics_kmeans_task` (
                                                       `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                       `batch_no` VARCHAR(64) NOT NULL COMMENT 'Python 生成的批次号',
    `snapshot_date` DATE NOT NULL COMMENT '逻辑快照日期',
    `status` VARCHAR(20) NOT NULL COMMENT '状态: running/success/failed',
    `algorithm_name` VARCHAR(32) NOT NULL DEFAULT 'kmeans',
    `model_version` VARCHAR(50) DEFAULT NULL COMMENT 'Python 模型版本',
    `feature_version` VARCHAR(50) DEFAULT NULL COMMENT '特征工程版本',
    `cluster_count` INT DEFAULT 0 COMMENT '配置的聚类数',
    `sample_user_count` BIGINT DEFAULT 0 COMMENT '总采样用户数',
    `clustered_user_count` BIGINT DEFAULT 0 COMMENT '实际参与聚类的用户数',
    `cold_start_user_count` BIGINT DEFAULT 0 COMMENT '被排除的冷启动用户数',
    `silhouette_score` DECIMAL(10,4) DEFAULT NULL,
    `inertia_score` DECIMAL(18,6) DEFAULT NULL,
    `feature_columns` JSON DEFAULT NULL COMMENT '有序特征列名列表',
    `result_summary` JSON DEFAULT NULL COMMENT '看板摘要数据(供 Java 使用)',
    `llm_overview` JSON DEFAULT NULL COMMENT 'LLM 生成的分析说明与运营建议',
    `error_message` VARCHAR(1000) DEFAULT NULL,
    `start_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `end_time` DATETIME DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_kmeans_batch_no` (`batch_no`),
    KEY `idx_kmeans_snapshot` (`snapshot_date`, `status`),
    KEY `idx_kmeans_created` (`create_time`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='K-means 聚类任务快照表';


-- ============================================
-- 14. K-means 分群快照表
-- MySQL 职责:
--   - 每个分群卡片及历史对比
-- Redis 职责:
--   - 缓存最新分群列表
-- ============================================
CREATE TABLE IF NOT EXISTS `analytics_kmeans_segment` (
                                                          `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                          `task_id` BIGINT NOT NULL COMMENT '关联 analytics_kmeans_task.id',
                                                          `snapshot_date` DATE NOT NULL COMMENT '逻辑快照日期',
                                                          `segment_code` VARCHAR(32) NOT NULL COMMENT '稳定编码，如 S1/S2/S3',
    `segment_name` VARCHAR(64) NOT NULL COMMENT 'LLM 生成的显示名称',
    `segment_description` VARCHAR(255) DEFAULT NULL COMMENT '分群描述',
    `llm_summary` VARCHAR(500) DEFAULT NULL COMMENT 'LLM 分析说明',
    `operation_suggestion` VARCHAR(500) DEFAULT NULL COMMENT 'CRM 运营建议',
    `user_count` BIGINT DEFAULT 0,
    `percentage` DECIMAL(8,2) DEFAULT 0.00,
    `avg_order_count_90d` DECIMAL(10,2) DEFAULT 0.00,
    `avg_order_amount_90d` DECIMAL(14,2) DEFAULT 0.00,
    `avg_behavior_count_30d` DECIMAL(10,2) DEFAULT 0.00,
    `avg_active_days_30d` DECIMAL(10,2) DEFAULT 0.00,
    `avg_recency_days` DECIMAL(10,2) DEFAULT 0.00,
    `avg_price_per_order` DECIMAL(14,2) DEFAULT 0.00,
    `feature_center` JSON DEFAULT NULL COMMENT '各特征的聚类中心值',
    `top_categories` JSON DEFAULT NULL COMMENT '热门品类ID或名称',
    `top_tags` JSON DEFAULT NULL COMMENT '热门标签',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_kmeans_segment` (`task_id`, `segment_code`),
    KEY `idx_kmeans_segment_snapshot` (`snapshot_date`, `segment_code`),
    KEY `idx_kmeans_segment_count` (`task_id`, `user_count`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='K-means 分群汇总快照表';


-- ============================================
-- 15. K-means 用户分配结果表
-- MySQL 职责:
--   - 可分页的用户列表
--   - 用户与分群的关联关系
--   - Java 查询的数据源
-- Redis 职责:
--   - 不作为数据源
-- ============================================
CREATE TABLE IF NOT EXISTS `analytics_kmeans_user_result` (
                                                              `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                              `task_id` BIGINT NOT NULL COMMENT '关联 analytics_kmeans_task.id',
                                                              `snapshot_date` DATE NOT NULL COMMENT '逻辑快照日期',
                                                              `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                                              `segment_code` VARCHAR(32) NOT NULL COMMENT '稳定分群编码',
    `segment_name` VARCHAR(64) DEFAULT NULL COMMENT '冗余显示名称(便于查询)',
    `cluster_index` INT DEFAULT NULL COMMENT '原始 K-means 聚类索引',
    `distance_to_center` DECIMAL(14,6) DEFAULT NULL COMMENT '到聚类中心的欧氏距离',
    `confidence_score` DECIMAL(8,4) DEFAULT NULL COMMENT '基于距离的置信度',
    `is_cold_start` TINYINT DEFAULT 0 COMMENT '是否冷启动: 0-否 1-是',
    `sort_order` INT DEFAULT 0 COMMENT '分群内的展示排序',
    `persona_summary` VARCHAR(255) DEFAULT NULL COMMENT 'LLM 生成的用户简要画像',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_kmeans_user_task` (`task_id`, `user_id`),
    KEY `idx_kmeans_segment_user` (`task_id`, `segment_code`, `sort_order`),
    KEY `idx_kmeans_user_lookup` (`user_id`, `snapshot_date`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='K-means 用户分配结果表';


-- ============================================
-- 16. K-means 用户特征快照表
-- MySQL 职责:
--   - 可解释性
--   - 用户详情页
--   - 论文/演示的可复现性
-- Redis 职责:
--   - 后续如需要可缓存最新用户详情
-- ============================================
CREATE TABLE IF NOT EXISTS `analytics_kmeans_feature_snapshot` (
                                                                   `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                                   `task_id` BIGINT NOT NULL COMMENT '关联 analytics_kmeans_task.id',
                                                                   `snapshot_date` DATE NOT NULL COMMENT '逻辑快照日期',
                                                                   `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                                                   `order_count_90d` INT DEFAULT 0,
                                                                   `order_amount_90d` DECIMAL(14,2) DEFAULT 0.00,
    `avg_order_amount_90d` DECIMAL(14,2) DEFAULT 0.00,
    `distinct_category_count_90d` INT DEFAULT 0,
    `behavior_count_30d` INT DEFAULT 0,
    `view_count_30d` INT DEFAULT 0,
    `cart_count_30d` INT DEFAULT 0,
    `favorite_count_30d` INT DEFAULT 0,
    `purchase_behavior_count_30d` INT DEFAULT 0,
    `active_days_30d` INT DEFAULT 0,
    `avg_duration_30d` DECIMAL(14,2) DEFAULT 0.00,
    `recency_order_days` INT DEFAULT 9999,
    `recency_behavior_days` INT DEFAULT 9999,
    `tenure_days` INT DEFAULT 0,
    `raw_features` JSON DEFAULT NULL COMMENT '原始特征映射',
    `normalized_features` JSON DEFAULT NULL COMMENT '归一化后的特征映射',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_kmeans_feature_user_task` (`task_id`, `user_id`),
    KEY `idx_kmeans_feature_snapshot` (`snapshot_date`, `user_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='K-means 用户特征快照表';


-- ============================================
-- 性能优化索引（高频查询路径）
-- ============================================
CREATE INDEX `idx_order_user_status_ctime` ON `order` (`user_id`, `status`, `create_time`);
CREATE INDEX `idx_order_item_order_product` ON `order_item` (`order_id`, `product_id`);
CREATE INDEX `idx_product_public_sales` ON `product` (`deleted`, `status`, `sales_count`, `id`);
CREATE INDEX `idx_product_public_category_id` ON `product` (`deleted`, `status`, `category_id`, `id`);
CREATE INDEX `idx_product_public_create` ON `product` (`deleted`, `status`, `create_time`, `id`);
CREATE INDEX `idx_product_merchant_status_update` ON `product` (`merchant_id`, `deleted`, `status`, `update_time`);
CREATE INDEX `idx_order_user_status_id` ON `order` (`user_id`, `status`, `id`);
CREATE INDEX `idx_order_status_ctime_id` ON `order` (`status`, `create_time`, `id`);
CREATE INDEX `idx_order_item_product_order` ON `order_item` (`product_id`, `order_id`);
CREATE INDEX `idx_user_behavior_type_user` ON `user_behavior` (`behavior_type`, `user_id`);
CREATE INDEX `idx_mq_outbox_status_retry_id` ON `mq_outbox_event` (`status`, `next_retry_time`, `id`);
CREATE INDEX `idx_refund_status_ctime` ON `refund_request` (`status`, `create_time`);
CREATE INDEX `idx_refund_ctime` ON `refund_request` (`create_time`);
CREATE INDEX `idx_im_message_conv_ctime` ON `im_message` (`conversation_id`, `create_time`);
CREATE INDEX `idx_im_conversation_user_utime` ON `im_conversation` (`user_id`, `update_time`);
