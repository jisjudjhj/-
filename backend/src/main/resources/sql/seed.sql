-- ============================================
-- ????????? - ???????
-- ??: ????/????
-- ??: ???? schema.sql
-- ???: MySQL 8.0+
-- ???: utf8mb4
-- ============================================

SET NAMES utf8mb4;
USE ecommerce_recommend;

-- ============================================
-- ============================================
--              初始数据
-- ============================================
-- ============================================

-- 用户 (密码 123456 → BCrypt加密)
-- 管理员/商家: 用用户名+密码登录
-- 普通用户: 用手机号+密码 或 手机号+验证码 登录
INSERT INTO `user` (`username`, `password`, `nickname`, `role`, `phone`, `email`, `balance`, `email_verified`, `avatar`) VALUES
('admin',       '$2a$10$5A3U8xoCIITLjqFn7nGWl.b/.D154d9AwEyjLzEkdFpbRnfWbVbPa', '系统管理员', 'admin',    '13800000001', 'admin@ecommerce.com',    0.00,     1, 'https://api.dicebear.com/7.x/initials/svg?seed=Admin&backgroundColor=1677ff'),
('merchant',    '$2a$10$5A3U8xoCIITLjqFn7nGWl.b/.D154d9AwEyjLzEkdFpbRnfWbVbPa', '优品旗舰店', 'merchant', '13800000002', 'merchant@ecommerce.com', 0.00,     1, 'https://api.dicebear.com/7.x/initials/svg?seed=Shop&backgroundColor=f5222d'),
('merchant2',   '$2a$10$5A3U8xoCIITLjqFn7nGWl.b/.D154d9AwEyjLzEkdFpbRnfWbVbPa', '数码生活馆', 'merchant', '13800000008', 'merchant2@ecommerce.com',0.00,     1, 'https://api.dicebear.com/7.x/initials/svg?seed=Digi&backgroundColor=722ed1'),
('13800000003', '$2a$10$5A3U8xoCIITLjqFn7nGWl.b/.D154d9AwEyjLzEkdFpbRnfWbVbPa', '小明',       'user',     '13800000003', 'xiaoming@qq.com',        10000.00, 1, 'https://api.dicebear.com/7.x/adventurer/svg?seed=Felix'),
('13800000004', '$2a$10$5A3U8xoCIITLjqFn7nGWl.b/.D154d9AwEyjLzEkdFpbRnfWbVbPa', '小红',       'user',     '13800000004', 'xiaohong@qq.com',        5000.00,  1, 'https://api.dicebear.com/7.x/adventurer/svg?seed=Aneka'),
('13800000005', '$2a$10$5A3U8xoCIITLjqFn7nGWl.b/.D154d9AwEyjLzEkdFpbRnfWbVbPa', '小李',       'user',     '13800000005', 'xiaoli@qq.com',          20000.00, 1, 'https://api.dicebear.com/7.x/adventurer/svg?seed=Leo'),
('13800000006', '$2a$10$5A3U8xoCIITLjqFn7nGWl.b/.D154d9AwEyjLzEkdFpbRnfWbVbPa', '小王',       'user',     '13800000006', 'xiaowang@qq.com',        8000.00,  1, 'https://api.dicebear.com/7.x/adventurer/svg?seed=Max'),
('13800000007', '$2a$10$5A3U8xoCIITLjqFn7nGWl.b/.D154d9AwEyjLzEkdFpbRnfWbVbPa', '小张',       'user',     '13800000007', 'xiaozhang@qq.com',       15000.00, 1, 'https://api.dicebear.com/7.x/adventurer/svg?seed=Zoe');


-- 商品分类
INSERT INTO `category` (`name`, `parent_id`, `icon`, `sort_order`) VALUES
('手机数码', 0, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/huawei-mate-60-pro.webp',  1),
('电脑办公', 0, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/macbook-pro-14.webp',  2),
('家用电器', 0, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/haier-fridge-501l.webp',  3),
('服饰鞋包', 0, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/nike-air-force-1.webp',  4),
('美妆护肤', 0, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/lancome-genifique.webp',  5),
('食品生鲜', 0, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/three-squirrels-nuts.webp',  6),
('图书文具', 0, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/to-live-yu-hua.webp',   7),
('运动户外', 0, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/nike-drifit-tshirt.webp',  8),
('母婴玩具', 0, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/pigeon-baby-bottle.webp',   9),
('家居家装', 0, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/quanyou-fabric-sofa.webp',  10);


-- ============================================
-- 商品 (40个，覆盖10个分类，每分类4个)
-- merchant_id=2 优品旗舰店, merchant_id=3 数码生活馆
-- images 字段为每个商品的3-4张详情图
-- ============================================
INSERT INTO `product` (`name`, `description`, `price`, `original_price`, `category_id`, `merchant_id`, `image`, `images`, `tags`, `stock`, `sales_count`, `rating`) VALUES
-- 手机数码 (1-4)
('华为Mate 60 Pro',
 '搭载麒麟9000S处理器，卫星通信技术，超感知影像系统。支持北斗卫星消息和天通卫星通话功能，XMAGE影像品牌加持，拥有超光变XMAGE影像系统。',
 6999.00, 7299.00, 1, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/huawei-mate-60-pro.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/huawei-mate-60-pro.webp"]',
 '["华为","旗舰","5G","卫星通信"]', 500, 3280, 4.9),

('iPhone 15 Pro Max',
 'A17 Pro芯片，钛金属设计，4800万像素相机系统，支持USB-C接口，Action Button自定义按键。全新超视网膜XDR显示屏，ProMotion自适应刷新率。',
 9999.00, 10999.00, 1, 3,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/iphone-15-pro-max.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/iphone-15-pro-max.webp"]',
 '["苹果","旗舰","iOS","钛金属"]', 300, 2150, 4.8),

('小米14 Ultra',
 '骁龙8Gen3处理器，徕卡光学镜头，超大电池5300mAh。支持小米澎湃OS系统，120W有线快充+50W无线快充，IP68防水。',
 5999.00, 6299.00, 1, 3,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/xiaomi-14-ultra.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/xiaomi-14-ultra.webp"]',
 '["小米","旗舰","徕卡","5G"]', 800, 1890, 4.7),

('OPPO Find X7',
 '天玑9300处理器，哈苏影像，2K超清屏幕120Hz，5000mAh大电池，100W闪充。全新潮汐架构带来更强性能。',
 4499.00, 4999.00, 1, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/oppo-find-x7.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/oppo-find-x7.webp"]',
 '["OPPO","旗舰","哈苏","拍照"]', 600, 980, 4.6),

-- 电脑办公 (5-8)
('MacBook Pro 14英寸',
 'M3 Pro芯片，18GB内存，Liquid Retina XDR显示屏，长达17小时电池续航。MagSafe 3充电，Thunderbolt 4接口。',
 14999.00, 16499.00, 2, 3,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/macbook-pro-14.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/macbook-pro-14.webp"]',
 '["苹果","笔记本","M3","专业"]', 200, 1560, 4.9),

('联想ThinkPad X1 Carbon',
 '英特尔酷睿Ultra处理器，14英寸2.8K OLED屏，1.08kg超轻便携，支持人脸识别+指纹解锁。',
 9999.00, 11299.00, 2, 3,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/thinkpad-x1-carbon.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/thinkpad-x1-carbon.webp"]',
 '["联想","商务","轻薄","ThinkPad"]', 350, 870, 4.7),

('华为MateBook X Pro',
 '酷睿Ultra处理器，3.1K OLED触控屏，超轻薄1.26kg，支持多屏协同，隐藏式摄像头。',
 11999.00, 12999.00, 2, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/matebook-x-pro.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/matebook-x-pro.webp"]',
 '["华为","轻薄","OLED","触控"]', 280, 650, 4.8),

('机械革命无界14',
 'R7-7840HS处理器，2.8K 120Hz高刷屏，全金属机身，高性价比之选。支持PD 100W充电。',
 3999.00, 4599.00, 2, 3,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/mechrevo-14.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/mechrevo-14.webp"]',
 '["机械革命","高性价比","AMD","游戏"]', 500, 1230, 4.5),

-- 家用电器 (9-12)
('海尔智能冰箱 501L',
 '十字对开门，变频风冷无霜，智能温控，一级能效，干湿分储，杀菌净味。',
 4299.00, 5299.00, 3, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/haier-fridge-501l.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/haier-fridge-501l.webp"]',
 '["海尔","冰箱","变频","智能"]', 150, 780, 4.6),

('美的空调 1.5匹',
 '新一级能效，变频冷暖，智能WiFi控制，自清洁功能，静音运行低至18分贝。',
 2699.00, 3299.00, 3, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/midea-ac-1p5.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/midea-ac-1p5.webp"]',
 '["美的","空调","变频","节能"]', 400, 2340, 4.7),

('戴森V15 Detect吸尘器',
 '激光检测灰尘，HEPA过滤，强劲吸力240AW，60分钟续航，LCD屏幕实时显示微尘数量。',
 4490.00, 5490.00, 3, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/dyson-v15-detect.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/dyson-v15-detect.webp"]',
 '["戴森","吸尘器","无线","激光"]', 200, 560, 4.8),

('格力空气净化器',
 '除甲醛除菌，双重过滤系统，CADR值450m³/h，静音设计，适用40-60㎡。',
 1999.00, 2499.00, 3, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/gree-air-purifier.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/gree-air-purifier.webp"]',
 '["格力","净化器","除甲醛","静音"]', 300, 890, 4.5),

-- 服饰鞋包 (13-16)
('Nike Air Force 1',
 '经典白色运动鞋，Air缓震科技，舒适透气，百搭潮流。全粒面皮革鞋面，耐磨橡胶外底。',
 799.00, 899.00, 4, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/nike-air-force-1.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/nike-air-force-1.webp"]',
 '["Nike","运动鞋","经典","百搭"]', 1000, 5680, 4.8),

('优衣库轻薄羽绒服',
 '超轻便携，90%白鸭绒，防风保暖，可折叠收纳入袋。蓬松度700+，面料防水涂层处理。',
 499.00, 699.00, 4, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/uniqlo-down-jacket.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/uniqlo-down-jacket.webp"]',
 '["优衣库","羽绒服","轻薄","保暖"]', 800, 3450, 4.6),

('李维斯501牛仔裤',
 '经典直筒版型，原色丹宁面料，百年经典单品。纽扣门襟，五袋款式设计。',
 599.00, 799.00, 4, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/levis-501-jeans.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/levis-501-jeans.webp"]',
 '["李维斯","牛仔裤","经典","百搭"]', 600, 2180, 4.7),

('新秀丽商务双肩包',
 '防泼水面料，电脑隔层(可放15.6寸笔记本)，减压肩带，多功能分区。商务出差旅行首选。',
 899.00, 1199.00, 4, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/samsonite-backpack.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/samsonite-backpack.webp"]',
 '["新秀丽","双肩包","商务","防水"]', 400, 1230, 4.6),

-- 美妆护肤 (17-20)
('兰蔻小黑瓶精华液',
 '第二代小黑瓶，微生态护肤科技，修护肌底屏障，提升肌肤光泽度。50ml装，适合所有肤质。',
 980.00, 1280.00, 5, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/lancome-genifique.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/lancome-genifique.webp"]',
 '["兰蔻","精华液","修护","抗老"]', 500, 4560, 4.9),

('SK-II神仙水 230ml',
 '天然酵母Pitera精华，调节肌肤水油平衡，改善肤质，使肌肤晶莹剔透。日本原产。',
 1590.00, 1790.00, 5, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/skii-essence.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/skii-essence.webp"]',
 '["SK-II","化妆水","酵母","经典"]', 300, 3200, 4.8),

('雅诗兰黛眼霜',
 '小棕瓶眼霜15ml，淡化黑眼圈细纹，紧致眼周肌肤。ChronoluxCB技术，修护昼夜节律紊乱。',
 520.00, 680.00, 5, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/estee-lauder-eye-cream.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/estee-lauder-eye-cream.webp"]',
 '["雅诗兰黛","眼霜","抗皱","淡化黑眼圈"]', 400, 2890, 4.7),

('MAC口红 #646',
 '雾面丝绒质地，高显色持久不易脱妆。Marrakesh色号，经典砖红棕色调。',
 230.00, 310.00, 5, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/mac-lipstick-646.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/mac-lipstick-646.webp"]',
 '["MAC","口红","雾面","持久"]', 800, 6780, 4.6),

-- 食品生鲜 (21-24)
('三只松鼠坚果大礼包',
 '每日坚果混合装，30袋独立包装。含核桃仁、腰果、巴旦木、蔓越莓等多种坚果果干。',
 128.00, 198.00, 6, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/three-squirrels-nuts.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/three-squirrels-nuts.webp"]',
 '["三只松鼠","坚果","零食","礼盒"]', 2000, 12560, 4.7),

('农夫山泉矿泉水 24瓶',
 '天然矿泉水，550ml*24瓶整箱装。来自长白山自涌泉水源，天然弱碱性。',
 39.90, 49.90, 6, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/nongfu-spring-water.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/nongfu-spring-water.webp"]',
 '["农夫山泉","矿泉水","天然","整箱"]', 5000, 35680, 4.5),

('蒙牛特仑苏纯牛奶',
 '3.6g优质蛋白，250ml*12盒。源自专属牧场，全程冷链运输，品质保障。',
 68.00, 79.90, 6, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/mengniu-deluxe-milk.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/mengniu-deluxe-milk.webp"]',
 '["蒙牛","牛奶","纯牛奶","高蛋白"]', 3000, 18900, 4.6),

('良品铺子肉松饼',
 '传统糕点工艺，肉松丰富，酥松可口。独立包装便于携带，办公室零食首选。',
 29.90, 39.90, 6, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/bestore-pork-floss.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/bestore-pork-floss.webp"]',
 '["良品铺子","糕点","肉松饼","零食"]', 1500, 8900, 4.5),

-- 图书文具 (25-28)
('活着 - 余华',
 '余华经典代表作，讲述一个人一生的故事。全球销量超2000万册，被译为40多种语言。',
 32.00, 45.00, 7, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/to-live-yu-hua.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/to-live-yu-hua.webp"]',
 '["余华","小说","经典","文学"]', 1000, 15680, 4.9),

('三体全集',
 '刘慈欣科幻巨著，雨果奖获奖作品。三部曲完整版，中国科幻里程碑之作。',
 98.00, 128.00, 7, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/three-body-problem.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/three-body-problem.webp"]',
 '["刘慈欣","科幻","三体","雨果奖"]', 800, 12300, 4.9),

('百年孤独',
 '马尔克斯魔幻现实主义代表作，1982年诺贝尔文学奖得主。范晔经典译本。',
 55.00, 69.80, 7, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/one-hundred-years.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/one-hundred-years.webp"]',
 '["马尔克斯","小说","魔幻现实","诺贝尔奖"]', 600, 8900, 4.8),

('斑马速干中性笔 5支装',
 '0.5mm笔尖，速干不洇墨，顺滑书写。黑色墨水，适合学生和办公使用。',
 15.80, 25.00, 7, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/zebra-gel-pen.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/zebra-gel-pen.webp"]',
 '["斑马","中性笔","速干","学生"]', 3000, 25600, 4.6),

-- 运动户外 (29-32)
('Nike Dri-FIT运动T恤',
 '速干透气面料Dri-FIT科技，运动休闲两穿。吸湿排汗，保持干爽舒适。',
 299.00, 399.00, 8, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/nike-drifit-tshirt.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/nike-drifit-tshirt.webp"]',
 '["Nike","运动","速干","透气"]', 600, 3450, 4.7),

('迪卡侬跑步鞋',
 '轻量缓震设计，透气网面鞋面，适合日常跑步训练。EVA中底提供舒适脚感。',
 249.00, 349.00, 8, 3,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/decathlon-running-shoes.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/decathlon-running-shoes.webp"]',
 '["迪卡侬","跑步鞋","轻量","缓震"]', 500, 4560, 4.6),

('李宁羽毛球拍',
 '全碳素拍框，攻守兼备。配送球包和手胶。适合中高级选手，中杆适中弹性。',
 359.00, 499.00, 8, 3,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/lining-badminton.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/lining-badminton.webp"]',
 '["李宁","羽毛球拍","碳素","专业"]', 300, 1890, 4.7),

('飞鸽26寸自行车',
 '城市通勤车，7速禧玛诺变速，前后碟刹安全可靠。铝合金车架，轻便耐用。',
 899.00, 1299.00, 8, 3,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/pigeon-bicycle-26.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/pigeon-bicycle-26.webp"]',
 '["飞鸽","自行车","通勤","变速"]', 100, 670, 4.5),

-- 母婴玩具 (33-36)
('贝亲婴儿奶瓶 240ml',
 '玻璃奶瓶，防胀气设计，仿母乳宽口径奶嘴。耐高温玻璃材质，安全健康。',
 128.00, 168.00, 9, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/pigeon-baby-bottle.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/pigeon-baby-bottle.webp"]',
 '["贝亲","奶瓶","婴儿","防胀气"]', 800, 5670, 4.8),

('乐高城市系列积木',
 '消防救援车60374，适合6岁以上儿童。502个颗粒，含4个人仔，可与其他乐高城市系列组合。',
 299.00, 399.00, 9, 3,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/lego-city-set.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/lego-city-set.webp"]',
 '["乐高","积木","益智","城市系列"]', 400, 3450, 4.9),

('巴布豆儿童运动鞋',
 '透气网面鞋面，防滑橡胶底，多色可选。轻便舒适，适合日常运动和上学穿着。',
 129.00, 199.00, 9, 3,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/bobdog-kids-shoes.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/bobdog-kids-shoes.webp"]',
 '["巴布豆","童鞋","运动","透气"]', 1000, 7890, 4.6),

('好孩子婴儿推车',
 '轻便折叠推车，可坐可躺，UPF50+遮阳蓬。一键折叠收车，仅重5.9kg，出行无负担。',
 899.00, 1399.00, 9, 3,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/goodbaby-stroller.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/goodbaby-stroller.webp"]',
 '["好孩子","推车","轻便","折叠"]', 200, 2340, 4.7),

-- 家居家装 (37-40)
('全友家居布艺沙发',
 '现代简约设计，高密度海绵填充，可拆洗布套。三人位+贵妃位L型组合，适合客厅。',
 3999.00, 5999.00, 10, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/quanyou-fabric-sofa.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/quanyou-fabric-sofa.webp"]',
 '["全友","沙发","布艺","简约"]', 100, 560, 4.6),

('宜家KALLAX书架',
 '方格置物架，多层收纳，白色。可横放可竖放，尺寸77x147cm，搭配收纳盒使用更整洁。',
 399.00, 499.00, 10, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/ikea-kallax-shelf.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/ikea-kallax-shelf.webp"]',
 '["宜家","书架","收纳","简约"]', 300, 2340, 4.5),

('水星家纺四件套',
 '全棉贡缎面料，60支长绒棉，亲肤舒适。含被套*1+床单*1+枕套*2，适合1.5/1.8m床。',
 399.00, 599.00, 10, 2,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/mercury-bedding-set.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/mercury-bedding-set.webp"]',
 '["水星","四件套","纯棉","贡缎"]', 500, 4560, 4.7),

('飞利浦LED台灯',
 '国AA级护眼标准，五档调光+三档色温，无频闪无蓝光危害。适合阅读学习办公。',
 299.00, 399.00, 10, 3,
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/philips-led-lamp.webp',
 '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/philips-led-lamp.webp"]',
 '["飞利浦","台灯","护眼","LED"]', 400, 3210, 4.8);


-- ============================================
-- OSS 商品种子数据（新增）
-- 图片来源于公开示例商品数据，并已上传至 OSS
-- ============================================
INSERT INTO `product` (`name`, `description`, `price`, `original_price`, `category_id`, `merchant_id`, `image`, `images`, `tags`, `stock`, `sales_count`, `rating`) VALUES
('iPhone 5s 经典智能手机', '经典轻巧机身设计，日常通讯与备用机使用都很合适，延续苹果一贯的流畅体验。', 1399.00, 1599.00, 1, 3, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/iphone-5s-classic.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/iphone-5s-classic.webp"]', '["苹果","手机","经典","iPhone"]', 120, 86, 4.50),
('iPhone 6 大屏智能手机', '更大的屏幕带来更舒适的浏览体验，机身轻薄，适合日常使用和收藏备用。', 1699.00, 1899.00, 1, 3, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/iphone-6-large-screen.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/iphone-6-large-screen.webp"]', '["苹果","大屏","智能手机","轻薄"]', 95, 74, 4.40),
('Apple MacBook Pro 14 英寸', '高性能轻薄笔记本，适合内容创作、开发办公和多任务处理，屏幕显示细腻。', 14999.00, 16999.00, 2, 3, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/apple-macbook-pro-14.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/apple-macbook-pro-14.webp"]', '["苹果","笔记本","办公","创作"]', 45, 33, 4.90),
('华硕双屏创作本', '双屏设计提升创作效率，性能强劲，适合视频剪辑、设计制图和专业办公场景。', 12999.00, 13999.00, 2, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/asus-dual-screen.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/asus-dual-screen.webp"]', '["华硕","双屏","创作本","高性能"]', 38, 21, 4.70),
('Essence 浓密睫毛膏', '刷头贴合睫毛，帮助打造浓密卷翘妆效，日常通勤和约会妆都很好搭配。', 79.00, 99.00, 5, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/essence-mascara.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/essence-mascara.webp"]', '["睫毛膏","彩妆","浓密","卷翘"]', 260, 142, 4.60),
('Chanel Coco Noir 香水', '花果木质调层次丰富，气质优雅，适合正式场合和夜间使用，留香表现稳定。', 899.00, 1099.00, 5, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/chanel-coco-noir.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/chanel-coco-noir.webp"]', '["香水","香奈儿","优雅","留香"]', 88, 47, 4.80),
('精品苹果礼盒', '新鲜爽脆，适合日常食用、办公室加餐和节日送礼，果香自然，口感清甜。', 39.90, 49.90, 6, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/premium-apple-giftbox.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/premium-apple-giftbox.webp"]', '["苹果","水果","新鲜","礼盒"]', 520, 196, 4.50),
('Annibale Colombo 现代沙发', '现代风格家居沙发，线条简洁，坐感舒适，适合作为客厅主位沙发提升空间质感。', 6999.00, 7999.00, 10, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/colombo-modern-sofa.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/colombo-modern-sofa.webp"]', '["沙发","家居","现代","客厅"]', 18, 12, 4.70);


-- ============================================
-- 轮播图 Banner 数据
-- ============================================
INSERT INTO `banner` (`title`, `image`, `link_type`, `link_value`, `sort_order`, `status`) VALUES
('华为Mate 60 Pro 卫星通信旗舰',
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/seed/banners/2026/03/20/19d07cd56be44ad49a0107c766349b4f.png',
 'product', '1', 1, 1),
('iPhone 15 Pro 钛金属设计',
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/seed/banners/2026/03/20/27a60d2cc22e428b955e0322013ea423.png',
 'product', '2', 2, 1),
('春季焕新 服饰鞋包专场',
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/seed/banners/2026/03/20/7d9de42d75d24366bca4da0e757ee751.png',
 'category', '4', 3, 1),
('美妆大牌日 满300减50',
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/seed/banners/2026/03/20/c3c8c674402a4b53a64f242a14843061.png',
 'category', '5', 4, 1),
('超值坚果礼盒 年货节特惠',
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/seed/banners/2026/03/20/2edfbe8c07f84caf9e63136b01863a20.png',
 'product', '21', 5, 1),
('MacBook Pro M3 创造力无限',
 'https://cyy050722.oss-cn-beijing.aliyuncs.com/seed/banners/2026/03/20/71a873c7fadd41b0b6ea1442d4118a34.png',
 'product', '5', 6, 1);


-- ============================================
-- 用户行为数据 (多用户多样化行为，支撑协同过滤)
-- ============================================

-- user-小明(id=4) 偏好: 手机数码 + 图书 + 食品
INSERT INTO `user_behavior` (`user_id`, `product_id`, `behavior_type`, `duration`, `create_time`) VALUES
(4, 1, 'view', 120, '2024-01-05 10:00:00'), (4, 1, 'favorite', NULL, '2024-01-05 10:02:00'),
(4, 2, 'view', 90, '2024-01-06 14:30:00'), (4, 3, 'view', 60, '2024-01-07 09:15:00'),
(4, 3, 'cart', NULL, '2024-01-07 09:16:00'), (4, 5, 'view', 180, '2024-01-10 11:00:00'),
(4, 5, 'purchase', NULL, '2024-01-10 11:05:00'),
(4, 13, 'view', 45, '2024-01-15 16:20:00'), (4, 13, 'purchase', NULL, '2024-01-15 16:22:00'),
(4, 17, 'view', 80, '2024-01-20 13:40:00'), (4, 17, 'favorite', NULL, '2024-01-20 13:42:00'),
(4, 21, 'view', 30, '2024-02-01 08:30:00'), (4, 21, 'purchase', NULL, '2024-02-01 08:32:00'),
(4, 25, 'view', 150, '2024-02-05 19:00:00'), (4, 25, 'favorite', NULL, '2024-02-05 19:03:00'),
(4, 25, 'purchase', NULL, '2024-02-05 19:05:00'), (4, 26, 'view', 120, '2024-02-10 20:15:00'),
(4, 26, 'purchase', NULL, '2024-02-10 20:18:00'),
(4, 29, 'view', 40, '2024-02-15 10:30:00'), (4, 29, 'cart', NULL, '2024-02-15 10:31:00'),
(4, 6, 'view', 70, '2024-02-20 14:00:00'), (4, 9, 'view', 55, '2024-03-01 11:20:00'),
(4, 10, 'view', 90, '2024-03-05 15:40:00'), (4, 10, 'purchase', NULL, '2024-03-05 15:45:00'),
(4, 14, 'view', 35, '2024-03-10 09:00:00'), (4, 18, 'view', 65, '2024-03-15 17:30:00'),
(4, 23, 'view', 50, '2024-03-20 12:00:00'), (4, 23, 'purchase', NULL, '2024-03-20 12:05:00'),
(4, 28, 'view', 30, '2024-03-25 08:40:00');


-- user-小红(id=5) 偏好: 美妆护肤 + 服饰 + 食品
INSERT INTO `user_behavior` (`user_id`, `product_id`, `behavior_type`, `duration`, `create_time`) VALUES
(5, 17, 'view', 200, '2024-01-08 10:00:00'), (5, 17, 'purchase', NULL, '2024-01-08 10:05:00'),
(5, 18, 'view', 180, '2024-01-12 14:30:00'), (5, 18, 'favorite', NULL, '2024-01-12 14:33:00'),
(5, 18, 'purchase', NULL, '2024-01-12 14:35:00'),
(5, 19, 'view', 90, '2024-01-18 09:20:00'), (5, 19, 'purchase', NULL, '2024-01-18 09:25:00'),
(5, 20, 'view', 60, '2024-01-22 16:40:00'), (5, 20, 'cart', NULL, '2024-01-22 16:41:00'),
(5, 20, 'purchase', NULL, '2024-01-22 16:45:00'),
(5, 13, 'view', 45, '2024-02-01 13:00:00'), (5, 13, 'favorite', NULL, '2024-02-01 13:02:00'),
(5, 14, 'view', 55, '2024-02-05 11:30:00'), (5, 14, 'purchase', NULL, '2024-02-05 11:35:00'),
(5, 15, 'view', 40, '2024-02-10 15:20:00'), (5, 16, 'view', 35, '2024-02-15 09:00:00'),
(5, 16, 'cart', NULL, '2024-02-15 09:02:00'),
(5, 21, 'view', 30, '2024-02-20 08:30:00'), (5, 21, 'purchase', NULL, '2024-02-20 08:33:00'),
(5, 24, 'view', 25, '2024-03-01 14:00:00'), (5, 24, 'purchase', NULL, '2024-03-01 14:05:00'),
(5, 39, 'view', 60, '2024-03-10 17:40:00'), (5, 39, 'favorite', NULL, '2024-03-10 17:42:00'),
(5, 37, 'view', 75, '2024-03-15 11:00:00'), (5, 33, 'view', 40, '2024-03-20 10:20:00');


-- user-小李(id=6) 偏好: 电脑办公 + 运动户外
INSERT INTO `user_behavior` (`user_id`, `product_id`, `behavior_type`, `duration`, `create_time`) VALUES
(6, 5, 'view', 240, '2024-01-03 10:00:00'), (6, 5, 'favorite', NULL, '2024-01-03 10:04:00'),
(6, 6, 'view', 180, '2024-01-08 14:30:00'), (6, 6, 'purchase', NULL, '2024-01-08 14:35:00'),
(6, 7, 'view', 90, '2024-01-15 09:20:00'), (6, 7, 'cart', NULL, '2024-01-15 09:22:00'),
(6, 8, 'view', 120, '2024-01-20 11:00:00'), (6, 8, 'purchase', NULL, '2024-01-20 11:05:00'),
(6, 29, 'view', 80, '2024-02-01 16:20:00'), (6, 29, 'purchase', NULL, '2024-02-01 16:25:00'),
(6, 30, 'view', 70, '2024-02-08 13:40:00'), (6, 30, 'purchase', NULL, '2024-02-08 13:45:00'),
(6, 31, 'view', 60, '2024-02-15 10:30:00'), (6, 31, 'favorite', NULL, '2024-02-15 10:32:00'),
(6, 32, 'view', 40, '2024-02-22 09:00:00'),
(6, 1, 'view', 50, '2024-03-01 15:00:00'), (6, 3, 'view', 45, '2024-03-05 14:30:00'),
(6, 40, 'view', 35, '2024-03-10 11:20:00'), (6, 40, 'purchase', NULL, '2024-03-10 11:25:00'),
(6, 28, 'view', 25, '2024-03-15 08:40:00'), (6, 28, 'cart', NULL, '2024-03-15 08:42:00');


-- user-小王(id=7) 偏好: 母婴玩具 + 家居家装
INSERT INTO `user_behavior` (`user_id`, `product_id`, `behavior_type`, `duration`, `create_time`) VALUES
(7, 33, 'view', 150, '2024-01-05 10:00:00'), (7, 33, 'purchase', NULL, '2024-01-05 10:05:00'),
(7, 34, 'view', 120, '2024-01-10 14:30:00'), (7, 34, 'favorite', NULL, '2024-01-10 14:32:00'),
(7, 34, 'purchase', NULL, '2024-01-10 14:35:00'),
(7, 35, 'view', 90, '2024-01-18 09:20:00'), (7, 35, 'purchase', NULL, '2024-01-18 09:25:00'),
(7, 36, 'view', 80, '2024-01-25 16:40:00'), (7, 36, 'cart', NULL, '2024-01-25 16:42:00'),
(7, 37, 'view', 200, '2024-02-01 11:00:00'), (7, 37, 'purchase', NULL, '2024-02-01 11:05:00'),
(7, 38, 'view', 60, '2024-02-08 13:30:00'),
(7, 39, 'view', 100, '2024-02-15 15:40:00'), (7, 39, 'purchase', NULL, '2024-02-15 15:45:00'),
(7, 40, 'view', 90, '2024-02-22 10:00:00'), (7, 40, 'purchase', NULL, '2024-02-22 10:05:00'),
(7, 9, 'view', 45, '2024-03-01 09:20:00'), (7, 10, 'view', 40, '2024-03-05 14:00:00'),
(7, 21, 'view', 30, '2024-03-10 08:30:00'), (7, 23, 'view', 25, '2024-03-15 17:20:00');


-- user-小张(id=8) 偏好: 家电 + 食品 + 图书
INSERT INTO `user_behavior` (`user_id`, `product_id`, `behavior_type`, `duration`, `create_time`) VALUES
(8, 9, 'view', 160, '2024-01-03 10:00:00'), (8, 9, 'purchase', NULL, '2024-01-03 10:05:00'),
(8, 10, 'view', 120, '2024-01-08 14:30:00'), (8, 10, 'purchase', NULL, '2024-01-08 14:35:00'),
(8, 11, 'view', 90, '2024-01-15 09:20:00'), (8, 11, 'favorite', NULL, '2024-01-15 09:22:00'),
(8, 12, 'view', 80, '2024-01-22 16:40:00'), (8, 12, 'cart', NULL, '2024-01-22 16:42:00'),
(8, 21, 'view', 60, '2024-02-01 08:30:00'), (8, 21, 'purchase', NULL, '2024-02-01 08:33:00'),
(8, 22, 'view', 50, '2024-02-08 13:00:00'), (8, 22, 'purchase', NULL, '2024-02-08 13:05:00'),
(8, 23, 'view', 40, '2024-02-15 15:20:00'), (8, 23, 'purchase', NULL, '2024-02-15 15:25:00'),
(8, 24, 'view', 35, '2024-02-22 09:00:00'),
(8, 25, 'view', 180, '2024-03-01 19:00:00'), (8, 25, 'purchase', NULL, '2024-03-01 19:05:00'),
(8, 27, 'view', 60, '2024-03-08 11:20:00'), (8, 27, 'purchase', NULL, '2024-03-08 11:25:00'),
(8, 1, 'view', 70, '2024-03-15 14:00:00'), (8, 5, 'view', 55, '2024-03-20 10:30:00'),
(8, 37, 'view', 45, '2024-03-25 16:00:00'), (8, 34, 'view', 30, '2024-03-28 08:40:00');


-- 搜索行为(带时间)
INSERT INTO `user_behavior` (`user_id`, `behavior_type`, `search_keyword`, `create_time`) VALUES
(4, 'search', '华为手机', '2024-01-05 09:50:00'), (4, 'search', '笔记本电脑', '2024-01-10 10:50:00'),
(4, 'search', '运动鞋', '2024-01-15 16:10:00'), (4, 'search', '科幻小说', '2024-02-05 18:50:00'),
(4, 'search', '坚果', '2024-02-01 08:20:00'), (4, 'search', '空调', '2024-03-05 15:30:00'),
(5, 'search', '兰蔻', '2024-01-08 09:50:00'), (5, 'search', '口红', '2024-01-22 16:30:00'),
(5, 'search', '连衣裙', '2024-02-10 15:10:00'), (5, 'search', '零食', '2024-03-01 13:50:00'),
(5, 'search', '护肤品', '2024-01-18 09:10:00'), (5, 'search', 'SK-II', '2024-01-12 14:20:00'),
(6, 'search', 'MacBook', '2024-01-03 09:50:00'), (6, 'search', '跑步鞋', '2024-02-08 13:30:00'),
(6, 'search', '机械键盘', '2024-01-20 10:50:00'), (6, 'search', '运动T恤', '2024-02-01 16:10:00'),
(7, 'search', '婴儿用品', '2024-01-05 09:50:00'), (7, 'search', '沙发', '2024-02-01 10:50:00'),
(7, 'search', '乐高', '2024-01-10 14:20:00'), (7, 'search', '推车', '2024-01-25 16:30:00'),
(8, 'search', '冰箱', '2024-01-03 09:50:00'), (8, 'search', '牛奶', '2024-02-08 12:50:00'),
(8, 'search', '小说', '2024-03-01 18:50:00'), (8, 'search', '吸尘器', '2024-01-15 09:10:00');


-- ============================================
-- 收藏数据
-- ============================================
INSERT INTO `user_favorite` (`user_id`, `product_id`) VALUES
(4, 1), (4, 17), (4, 25), (4, 26), (4, 3), (4, 29),
(5, 17), (5, 18), (5, 13), (5, 39), (5, 20), (5, 14),
(6, 5), (6, 31), (6, 6), (6, 8), (6, 40),
(7, 33), (7, 34), (7, 37), (7, 39), (7, 36),
(8, 9), (8, 11), (8, 25), (8, 27), (8, 22);


-- ============================================
-- 用户偏好画像
-- ============================================
INSERT INTO `user_preference` (`user_id`, `category_preferences`, `tag_preferences`, `price_range_min`, `price_range_max`) VALUES
(4, '{"1":8,"7":6,"6":4,"4":3,"3":2}', '{"华为":5,"旗舰":4,"科幻":3,"小说":3,"坚果":2}',    15.00, 15000.00),
(5, '{"5":9,"4":6,"6":3,"10":2}',       '{"兰蔻":5,"口红":4,"Nike":3,"SK-II":4,"护肤":3}',    29.00, 2000.00),
(6, '{"2":8,"8":6,"1":2,"7":1}',         '{"苹果":5,"笔记本":4,"运动":3,"轻薄":3,"AMD":2}',    200.00, 15000.00),
(7, '{"9":7,"10":6,"3":2,"6":1}',        '{"乐高":4,"婴儿":3,"沙发":3,"推车":3,"积木":2}',     100.00, 6000.00),
(8, '{"3":7,"6":6,"7":4,"9":2,"10":1}',  '{"冰箱":4,"牛奶":3,"小说":3,"吸尘器":2,"海尔":3}',   15.00, 5000.00);


-- ============================================
-- 订单数据 (覆盖2024年1-6月，各种状态)
-- ============================================
INSERT INTO `order` (`user_id`, `order_no`, `total_amount`, `status`, `address`, `receiver_name`, `receiver_phone`, `remark`, `create_time`) VALUES
-- 小明(id=4)的订单
(4, 'ORD20240101001', 14999.00, 3, '北京市朝阳区建国路88号',       '小明', '13800000003', NULL,         '2024-01-10 11:30:00'),
(4, 'ORD20240115002', 799.00,   3, '北京市朝阳区建国路88号',       '小明', '13800000003', NULL,         '2024-01-15 16:30:00'),
(4, 'ORD20240201003', 128.00,   3, '北京市朝阳区建国路88号',       '小明', '13800000003', '尽快发货',   '2024-02-01 09:00:00'),
(4, 'ORD20240210004', 2699.00,  2, '北京市朝阳区建国路88号',       '小明', '13800000003', NULL,         '2024-03-05 16:00:00'),
(4, 'ORD20240305005', 130.00,   1, '北京市朝阳区建国路88号',       '小明', '13800000003', NULL,         '2024-03-20 12:10:00'),
(4, 'ORD20240320006', 6999.00,  0, '北京市海淀区中关村大街1号',     '小明', '13800000003', '要白色款',   '2024-04-01 09:30:00'),
-- 小红(id=5)的订单
(5, 'ORD20240108007', 2570.00,  3, '上海市浦东新区陆家嘴金融中心', '小红', '13800000004', NULL,         '2024-01-08 10:10:00'),
(5, 'ORD20240122008', 1029.00,  3, '上海市浦东新区陆家嘴金融中心', '小红', '13800000004', NULL,         '2024-01-22 17:00:00'),
(5, 'ORD20240205009', 799.00,   3, '上海市浦东新区陆家嘴金融中心', '小红', '13800000004', NULL,         '2024-02-05 11:40:00'),
(5, 'ORD20240220010', 157.90,   2, '上海市浦东新区陆家嘴金融中心', '小红', '13800000004', NULL,         '2024-02-20 08:40:00'),
(5, 'ORD20240310011', 399.00,   1, '上海市静安区南京西路100号',     '小红', '13800000004', '送朋友的',   '2024-03-10 17:50:00'),
-- 小李(id=6)的订单
(6, 'ORD20240108012', 9999.00,  3, '广州市天河区珠江新城',          '小李', '13800000005', NULL,         '2024-01-08 14:40:00'),
(6, 'ORD20240120013', 3999.00,  3, '广州市天河区珠江新城',          '小李', '13800000005', NULL,         '2024-01-20 11:10:00'),
(6, 'ORD20240201014', 548.00,   3, '广州市天河区珠江新城',          '小李', '13800000005', NULL,         '2024-02-01 16:30:00'),
(6, 'ORD20240215015', 299.00,   2, '广州市天河区珠江新城',          '小李', '13800000005', NULL,         '2024-03-10 11:30:00'),
(6, 'ORD20240310016', 15.80,    0, '广州市天河区珠江新城',          '小李', '13800000005', NULL,         '2024-04-05 08:50:00'),
-- 小王(id=7)的订单
(7, 'ORD20240105017', 556.00,   3, '深圳市南山区科技园',            '小王', '13800000006', NULL,         '2024-01-05 10:10:00'),
(7, 'ORD20240110018', 299.00,   3, '深圳市南山区科技园',            '小王', '13800000006', NULL,         '2024-01-10 14:40:00'),
(7, 'ORD20240118019', 129.00,   3, '深圳市南山区科技园',            '小王', '13800000006', NULL,         '2024-01-18 09:30:00'),
(7, 'ORD20240201020', 4898.00,  3, '深圳市南山区科技园',            '小王', '13800000006', '周末送',     '2024-02-01 11:10:00'),
(7, 'ORD20240215021', 698.00,   2, '深圳市福田区华强北路',          '小王', '13800000006', NULL,         '2024-02-15 15:50:00'),
(7, 'ORD20240310022', 899.00,   1, '深圳市南山区科技园',            '小王', '13800000006', NULL,         '2024-03-10 10:00:00'),
-- 小张(id=8)的订单
(8, 'ORD20240103023', 6998.00,  3, '杭州市西湖区文三路',            '小张', '13800000007', NULL,         '2024-01-03 10:10:00'),
(8, 'ORD20240201024', 188.00,   3, '杭州市西湖区文三路',            '小张', '13800000007', NULL,         '2024-02-01 08:40:00'),
(8, 'ORD20240208025', 166.00,   3, '杭州市西湖区文三路',            '小张', '13800000007', NULL,         '2024-02-08 13:10:00'),
(8, 'ORD20240215026', 29.90,    3, '杭州市西湖区文三路',            '小张', '13800000007', NULL,         '2024-02-15 15:30:00'),
(8, 'ORD20240301027', 87.00,    2, '杭州市西湖区文三路',            '小张', '13800000007', NULL,         '2024-03-01 19:10:00'),
(8, 'ORD20240308028', 55.00,    1, '杭州市余杭区仓前街道',          '小张', '13800000007', NULL,         '2024-03-08 11:30:00'),
(8, 'ORD20240401029', 4490.00,  0, '杭州市西湖区文三路',            '小张', '13800000007', '要蓝色款',   '2024-04-01 10:00:00'),
(8, 'ORD20240315030', 598.00,   4, '杭州市西湖区文三路',            '小张', '13800000007', NULL,         '2024-03-15 09:20:00');


-- ============================================
-- 订单明细
-- ============================================
INSERT INTO `order_item` (`order_id`, `product_id`, `product_name`, `product_image`, `price`, `quantity`, `subtotal`) VALUES
-- 小明的订单
(1, 5, 'MacBook Pro 14英寸', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/macbook-pro-14.webp', 14999.00, 1, 14999.00),
(2, 13, 'Nike Air Force 1', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/nike-air-force-1.webp', 799.00, 1, 799.00),
(3, 21, '三只松鼠坚果大礼包', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/three-squirrels-nuts.webp', 128.00, 1, 128.00),
(4, 10, '美的空调 1.5匹', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/midea-ac-1p5.webp', 2699.00, 1, 2699.00),
(5, 25, '活着 - 余华', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/to-live-yu-hua.webp', 32.00, 1, 32.00),
(5, 26, '三体全集', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/three-body-problem.webp', 98.00, 1, 98.00),
(6, 1, '华为Mate 60 Pro', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/huawei-mate-60-pro.webp', 6999.00, 1, 6999.00),
-- 小红的订单
(7, 17, '兰蔻小黑瓶精华液', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/lancome-genifique.webp', 980.00, 1, 980.00),
(7, 18, 'SK-II神仙水 230ml', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/skii-essence.webp', 1590.00, 1, 1590.00),
(8, 20, 'MAC口红 #646', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/mac-lipstick-646.webp', 230.00, 1, 230.00),
(8, 14, '优衣库轻薄羽绒服', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/uniqlo-down-jacket.webp', 499.00, 1, 499.00),
(8, 13, 'Nike Air Force 1', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/nike-air-force-1.webp', 300.00, 1, 300.00),
(9, 14, '优衣库轻薄羽绒服', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/uniqlo-down-jacket.webp', 499.00, 1, 499.00),
(9, 13, 'Nike Air Force 1', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/nike-air-force-1.webp', 300.00, 1, 300.00),
(10, 21, '三只松鼠坚果大礼包', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/three-squirrels-nuts.webp', 128.00, 1, 128.00),
(10, 24, '良品铺子肉松饼', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/bestore-pork-floss.webp', 29.90, 1, 29.90),
(11, 39, '水星家纺四件套', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/mercury-bedding-set.webp', 399.00, 1, 399.00),
-- 小李的订单
(12, 6, '联想ThinkPad X1 Carbon', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/thinkpad-x1-carbon.webp', 9999.00, 1, 9999.00),
(13, 8, '机械革命无界14', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/mechrevo-14.webp', 3999.00, 1, 3999.00),
(14, 29, 'Nike Dri-FIT运动T恤', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/nike-drifit-tshirt.webp', 299.00, 1, 299.00),
(14, 30, '迪卡侬跑步鞋', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/decathlon-running-shoes.webp', 249.00, 1, 249.00),
(15, 40, '飞利浦LED台灯', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/philips-led-lamp.webp', 299.00, 1, 299.00),
(16, 28, '斑马速干中性笔 5支装', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/zebra-gel-pen.webp', 15.80, 1, 15.80),
-- 小王的订单
(17, 33, '贝亲婴儿奶瓶 240ml', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/pigeon-baby-bottle.webp', 128.00, 1, 128.00),
(17, 35, '巴布豆儿童运动鞋', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/bobdog-kids-shoes.webp', 129.00, 1, 129.00),
(17, 40, '飞利浦LED台灯', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/philips-led-lamp.webp', 299.00, 1, 299.00),
(18, 34, '乐高城市系列积木', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/lego-city-set.webp', 299.00, 1, 299.00),
(19, 35, '巴布豆儿童运动鞋', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/bobdog-kids-shoes.webp', 129.00, 1, 129.00),
(20, 37, '全友家居布艺沙发', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/quanyou-fabric-sofa.webp', 3999.00, 1, 3999.00),
(20, 36, '好孩子婴儿推车', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/goodbaby-stroller.webp', 899.00, 1, 899.00),
(21, 39, '水星家纺四件套', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/mercury-bedding-set.webp', 399.00, 1, 399.00),
(21, 40, '飞利浦LED台灯', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/philips-led-lamp.webp', 299.00, 1, 299.00),
(22, 36, '好孩子婴儿推车', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/goodbaby-stroller.webp', 899.00, 1, 899.00),
-- 小张的订单
(23, 9, '海尔智能冰箱 501L', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/haier-fridge-501l.webp', 4299.00, 1, 4299.00),
(23, 10, '美的空调 1.5匹', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/midea-ac-1p5.webp', 2699.00, 1, 2699.00),
(24, 21, '三只松鼠坚果大礼包', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/three-squirrels-nuts.webp', 128.00, 1, 128.00),
(24, 23, '蒙牛特仑苏纯牛奶', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/mengniu-deluxe-milk.webp', 60.00, 1, 60.00),
(25, 22, '农夫山泉矿泉水 24瓶', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/nongfu-spring-water.webp', 39.90, 2, 79.80),
(25, 23, '蒙牛特仑苏纯牛奶', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/mengniu-deluxe-milk.webp', 68.00, 1, 68.00),
(25, 28, '斑马速干中性笔 5支装', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/zebra-gel-pen.webp', 15.80, 1, 15.80),
(26, 24, '良品铺子肉松饼', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/bestore-pork-floss.webp', 29.90, 1, 29.90),
(27, 25, '活着 - 余华', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/to-live-yu-hua.webp', 32.00, 1, 32.00),
(27, 27, '百年孤独', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/one-hundred-years.webp', 55.00, 1, 55.00),
(28, 27, '百年孤独', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/one-hundred-years.webp', 55.00, 1, 55.00),
(29, 11, '戴森V15 Detect吸尘器', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/dyson-v15-detect.webp', 4490.00, 1, 4490.00),
(30, 34, '乐高城市系列积木', 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/lego-city-set.webp', 299.00, 2, 598.00);


-- ============================================
-- 钱包交易记录 (充值 + 部分支付)
-- ============================================
INSERT INTO `wallet_transaction` (`user_id`, `type`, `amount`, `balance_before`, `balance_after`, `order_no`, `description`, `create_time`) VALUES
(4, 'recharge', 10000.00,  0.00,     10000.00, NULL, '模拟充值 ¥10000.00', '2024-01-01 08:00:00'),
(5, 'recharge', 5000.00,   0.00,     5000.00,  NULL, '模拟充值 ¥5000.00',  '2024-01-01 08:00:00'),
(6, 'recharge', 20000.00,  0.00,     20000.00, NULL, '模拟充值 ¥20000.00', '2024-01-01 08:00:00'),
(7, 'recharge', 8000.00,   0.00,     8000.00,  NULL, '模拟充值 ¥8000.00',  '2024-01-01 08:00:00'),
(8, 'recharge', 15000.00,  0.00,     15000.00, NULL, '模拟充值 ¥15000.00', '2024-01-01 08:00:00'),
(4, 'pay',     -14999.00,  10000.00, -4999.00, 'ORD20240101001', '支付订单 ORD20240101001', '2024-01-10 11:31:00'),
(4, 'recharge', 10000.00, -4999.00,  5001.00,  NULL, '模拟充值 ¥10000.00', '2024-01-12 09:00:00'),
(4, 'pay',     -799.00,    5001.00,  4202.00,  'ORD20240115002', '支付订单 ORD20240115002', '2024-01-15 16:31:00'),
(5, 'pay',     -2570.00,   5000.00,  2430.00,  'ORD20240108007', '支付订单 ORD20240108007', '2024-01-08 10:11:00'),
(5, 'pay',     -1029.00,   2430.00,  1401.00,  'ORD20240122008', '支付订单 ORD20240122008', '2024-01-22 17:01:00'),
(5, 'recharge', 5000.00,   1401.00,  6401.00,  NULL, '模拟充值 ¥5000.00',  '2024-02-01 08:00:00'),
(6, 'pay',     -9999.00,   20000.00, 10001.00, 'ORD20240108012', '支付订单 ORD20240108012', '2024-01-08 14:41:00'),
(6, 'pay',     -3999.00,   10001.00, 6002.00,  'ORD20240120013', '支付订单 ORD20240120013', '2024-01-20 11:11:00'),
(8, 'pay',     -6998.00,   15000.00, 8002.00,  'ORD20240103023', '支付订单 ORD20240103023', '2024-01-03 10:11:00');


-- ============================================
-- 收货地址种子数据
-- ============================================
INSERT INTO `address` (`user_id`, `receiver_name`, `receiver_phone`, `province`, `city`, `district`, `detail`, `is_default`) VALUES
(4, '小明', '13800000003', '北京市', '北京市', '朝阳区', '建国路88号SOHO现代城A座1203', 1),
(4, '小明', '13800000003', '北京市', '北京市', '海淀区', '中关村大街1号海龙大厦6层', 0),
(5, '小红', '13800000004', '上海市', '上海市', '浦东新区', '陆家嘴金融中心18楼', 1),
(5, '小红', '13800000004', '上海市', '上海市', '静安区', '南京西路100号恒隆广场', 0),
(6, '小李', '13800000005', '广东省', '广州市', '天河区', '珠江新城花城大道68号', 1),
(7, '小王', '13800000006', '广东省', '深圳市', '南山区', '科技园南区深南大道9988号', 1),
(7, '小王', '13800000006', '广东省', '深圳市', '福田区', '华强北路赛格电子市场2层', 0),
(8, '小张', '13800000007', '浙江省', '杭州市', '西湖区', '文三路398号东信大厦5楼', 1),
(8, '小张', '13800000007', '浙江省', '杭州市', '余杭区', '仓前街道欧美金融城12栋', 0);


-- ============================================
-- 购物车种子数据
-- ============================================
INSERT INTO `cart_item` (`user_id`, `product_id`, `quantity`, `selected`) VALUES
(4, 3, 1, 1),
(4, 15, 2, 1),
(4, 22, 1, 0),
(5, 17, 1, 1),
(5, 20, 2, 1),
(6, 7, 1, 1),
(7, 34, 1, 1),
(7, 35, 1, 1),
(8, 11, 1, 1);


-- ============================================
-- 商品评价种子数据
-- ============================================
INSERT INTO `product_review` (`user_id`, `product_id`, `order_id`, `rating`, `content`, `images`, `reply`, `reply_time`, `status`, `create_time`) VALUES
(4, 5, 1, 5, '非常棒的笔记本电脑！屏幕效果超级好，M2芯片运行流畅，编程一点都不卡。', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/macbook-pro-14.webp"]', '感谢您的好评，祝您使用愉快！', '2024-01-15 10:00:00', 1, '2024-01-12 14:30:00'),
(4, 13, 2,  4, '鞋子质量很好，穿着很舒服，就是尺码偏大了半码。', NULL, NULL, NULL, 1, '2024-01-18 09:20:00'),
(4, 21, 3, 5, '坚果新鲜，包装精美，送人很有面子！每种坚果都很大颗饱满。', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/three-squirrels-nuts.webp"]', NULL, NULL, 1, '2024-02-01 11:10:00'),
(5, 17, 7,  5, '兰蔻小黑瓶真的很好用！用了一周皮肤明显细腻了，吸收很快。', NULL, '感谢亲的认可，坚持使用效果更佳哦~', '2024-01-12 14:00:00', 1, '2024-01-10 20:30:00'),
(5, 18, 7,  4, 'SK-II神仙水确实好用，但价格有点贵，等活动再囤货。', NULL, NULL, NULL, 1, '2024-01-11 09:10:00'),
(5, 14, 8,  5, '羽绒服很轻薄但保暖性很好，冬天穿正好，非常满意！', NULL, NULL, NULL, 1, '2024-01-16 15:40:00'),
(6, 6,  12, 5, 'ThinkPad品质一如既往，键盘手感超棒，商务办公首选！', NULL, '感谢选择ThinkPad！', '2024-01-12 09:00:00', 1, '2024-01-10 16:20:00'),
(6, 8,  13, 4, '机械革命性价比很高，游戏性能不错，就是风扇声音稍大。', NULL, NULL, NULL, 1, '2024-01-22 11:30:00'),
(7, 33, 17, 5, '贝亲奶瓶质量很好，宝宝很爱用，防胀气效果明显。', NULL, NULL, NULL, 1, '2024-01-08 20:10:00'),
(7, 34, 18, 5, '乐高积木孩子非常喜欢，拼了一下午，锻炼动手能力。', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/lego-city-set.webp"]', NULL, NULL, 1, '2024-01-12 18:30:00'),
(8, 9,  23, 5, '海尔冰箱容量很大，静音效果好，智能温控很方便。', NULL, '感谢您的认可！', '2024-01-08 10:00:00', 1, '2024-01-06 14:00:00'),
(8, 10, 23, 4, '美的空调制冷效果很好，安装师傅服务也不错。', NULL, NULL, NULL, 1, '2024-01-07 09:20:00'),
(8, 21, 24, 5, '坚果很新鲜好吃！分量足，性价比高，会回购。', NULL, NULL, NULL, 1, '2024-02-05 10:40:00'),
(8, 25, 27, 5, '余华的书写得太好了，一口气读完，感触很深。', NULL, NULL, NULL, 1, '2024-03-05 21:00:00'),
(8, 27, 27, 4, '百年孤独确实是经典，不过需要静下心来慢慢品味。', NULL, NULL, NULL, 1, '2024-03-06 15:30:00');

-- AI 评价总结测试数据：按每个分类销量前三商品补充多维度评论
-- 说明：不固定商品 ID，导入时自动选择各分类销量靠前商品，方便测试 AI 总结的优点、缺点、物流、包装和复购意愿。
INSERT INTO `product_review`
(`user_id`, `product_id`, `order_id`, `rating`, `content`, `images`, `video_urls`, `tags`, `append_content`, `append_time`, `helpful_count`, `reply`, `reply_time`, `status`, `create_time`)
SELECT
  CASE MOD(top_product.id + review_tpl.seq, 5)
    WHEN 0 THEN 4
    WHEN 1 THEN 5
    WHEN 2 THEN 6
    WHEN 3 THEN 7
    ELSE 8
  END AS user_id,
  top_product.id AS product_id,
  CASE MOD(top_product.id + review_tpl.seq, 30)
    WHEN 0 THEN 1
    ELSE MOD(top_product.id + review_tpl.seq, 30)
  END AS order_id,
  review_tpl.rating,
  CONCAT(
    '【AI总结样本-', top_product.category_name, '】',
    REPLACE(REPLACE(review_tpl.content_tpl, '{product}', top_product.name), '{category}', top_product.category_name)
  ) AS content,
  NULL AS images,
  NULL AS video_urls,
  JSON_ARRAY(review_tpl.tag_a, review_tpl.tag_b, review_tpl.tag_c) AS tags,
  CASE
    WHEN review_tpl.seq IN (2, 5, 8) THEN CONCAT('追评：用了几天再来反馈，', review_tpl.append_tpl)
    ELSE NULL
  END AS append_content,
  CASE
    WHEN review_tpl.seq IN (2, 5, 8) THEN DATE_ADD('2026-04-01 10:00:00', INTERVAL (top_product.id + review_tpl.seq + 2) DAY)
    ELSE NULL
  END AS append_time,
  6 + MOD(top_product.sales_count + review_tpl.seq * 7, 48) AS helpful_count,
  CASE
    WHEN review_tpl.rating <= 3 THEN '感谢反馈，我们会继续优化商品说明、质检和售后响应。'
    WHEN review_tpl.seq IN (1, 4, 7) THEN '感谢认可，后续会继续保持发货速度和服务体验。'
    ELSE NULL
  END AS reply,
  CASE
    WHEN review_tpl.rating <= 3 OR review_tpl.seq IN (1, 4, 7) THEN DATE_ADD('2026-04-01 18:00:00', INTERVAL (top_product.id + review_tpl.seq + 1) DAY)
    ELSE NULL
  END AS reply_time,
  1 AS status,
  DATE_ADD('2026-04-01 09:00:00', INTERVAL (top_product.id + review_tpl.seq) DAY) AS create_time
FROM (
  SELECT p.id, p.name, p.category_id, p.sales_count, p.rating, c.name AS category_name
  FROM `product` p
  JOIN `category` c ON c.id = p.category_id
  WHERE p.status = 1
    AND p.deleted = 0
    AND (
      SELECT COUNT(*)
      FROM `product` p2
      WHERE p2.category_id = p.category_id
        AND p2.status = 1
        AND p2.deleted = 0
        AND (
          p2.sales_count > p.sales_count
          OR (p2.sales_count = p.sales_count AND p2.rating > p.rating)
          OR (p2.sales_count = p.sales_count AND p2.rating = p.rating AND p2.id < p.id)
        )
    ) < 3
) top_product
JOIN (
  SELECT 1 AS seq, 5 AS rating, '买的是{product}，整体体验很稳。最满意的是做工和细节，和{category}分类里其他商品比起来，质感更好，包装也完整，收到后没有磕碰。' AS content_tpl, '做工扎实' AS tag_a, '包装完整' AS tag_b, '体验稳定' AS tag_c, '目前没有发现明显问题，家里人也觉得值得。' AS append_tpl
  UNION ALL SELECT 2, 5, '{product}比预期好，发货快，客服回复也及时。实际使用下来核心功能没有虚标，适合日常高频使用，后面活动价合适还会回购。', '物流很快', '功能实用', '值得回购', '核心体验保持不错，复购意愿比较强。'
  UNION ALL SELECT 3, 4, '优点是{product}外观干净、上手简单，价格和体验基本匹配。小问题是说明书写得不够细，新用户需要摸索一下，但不影响正常使用。', '上手简单', '说明一般', '性价比高', '熟悉以后使用更顺手，建议详情页补充更多说明。'
  UNION ALL SELECT 4, 4, '这款{product}适合追求稳定体验的人。包装、配送、售后都比较顺，实际效果和页面描述接近，作为{category}里的热销款是有原因的。', '描述相符', '售后顺畅', '热销可信', '用了几天以后感觉稳定性比预期更好。'
  UNION ALL SELECT 5, 5, '收到{product}后第一感觉是细节不错，没有廉价感。使用场景覆盖比较全，家人也觉得方便，属于买完不会后悔的类型。', '细节到位', '质感高级', '家用合适', '家里人连续用了几天，反馈都比较正向。'
  UNION ALL SELECT 6, 3, '{product}能用，但有一些需要改进的地方。包装保护可以再加强，细节处理也还有提升空间，如果对品质要求很高，建议先看清参数和评价。', '包装待加强', '细节一般', '谨慎选择', '后续客服有联系处理，态度还可以。'
  UNION ALL SELECT 7, 4, '对比了几款{category}商品后选了{product}。它的优势是稳定、价格不夸张，短板是没有特别惊艳的功能，适合务实型用户。', '价格合适', '体验稳定', '功能够用', '越用越觉得适合日常，不适合追求极致配置的人。'
  UNION ALL SELECT 8, 5, '{product}的整体口碑和销量基本对得上，物流速度、外观、使用体验都在线。希望后面能多做组合优惠，复购会更方便。', '销量可信', '外观好看', '期待优惠', '如果后续有满减或组合购，会考虑再买。'
) review_tpl
WHERE NOT EXISTS (
  SELECT 1
  FROM `product_review` existed
  WHERE existed.product_id = top_product.id
    AND existed.content LIKE CONCAT('【AI总结样本-', top_product.category_name, '】%')
    AND existed.content LIKE CONCAT('%', top_product.name, '%')
);


-- ============================================
-- 优惠券种子数据
-- ============================================
INSERT INTO `coupon` (`name`, `type`, `value`, `min_amount`, `max_discount`, `total_count`, `used_count`, `start_time`, `end_time`, `status`, `scope_type`, `merchant_id`, `audience_type`, `audience_note`) VALUES
('平台新人礼 满99减20',       1, 20.00,  99.00,   NULL, 1200, 126, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 0, NULL, 0, '平台通用，新用户和普通用户均可领取'),
('平台通用券 满199减35',      1, 35.00,  199.00,  NULL, 1000, 214, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 0, NULL, 0, '全场通用，适合购物车凑单展示'),
('平台无门槛 8元券',          3, 8.00,   0.00,    NULL, 800,  188, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 0, NULL, 0, '无门槛立减，方便新用户快速体验领券'),
('优品旗舰店 满159减25',      1, 25.00,  159.00,  NULL, 600,  72,  '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 1, 2,    0, '商家店铺券，仅限优品旗舰店商品'),
('优品旗舰店 9折券',          2, 9.00,   299.00,  80.00, 450,  41,  '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 1, 2,    0, '商家折扣券，最高优惠80元'),
('数码生活馆 满999减120',     1, 120.00, 999.00,  NULL, 500,  58,  '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 1, 3,    0, '数码生活馆专属，适合高客单价商品'),
('数码生活馆 配件满299减40',  1, 40.00,  299.00,  NULL, 700,  96,  '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 1, 3,    0, '数码配件和办公好物可用');

INSERT INTO `coupon`
(`name`, `type`, `value`, `min_amount`, `max_discount`, `total_count`, `used_count`, `start_time`, `end_time`, `status`, `scope_type`, `merchant_id`, `audience_type`, `target_segment_codes`, `target_user_ids`, `audience_note`, `create_time`)
VALUES
('AI省钱 平台无门槛5元券',        3, 5.00,    0.00,    NULL, 3000, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 0, NULL, 0, '', '', '低价商品也能展示自动省钱', NOW()),
('AI省钱 平台无门槛12元券',       3, 12.00,   0.00,    NULL, 2600, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 0, NULL, 0, '', '', '兜底可用券，保证结算页有优惠', NOW()),
('AI省钱 平台满49减8',           1, 8.00,    49.00,   NULL, 2500, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 0, NULL, 0, '', '', '小额订单满减', NOW()),
('AI省钱 平台满79减12',          1, 12.00,   79.00,   NULL, 2400, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 0, NULL, 0, '', '', '日常购物满减', NOW()),
('AI省钱 平台满99减18',          1, 18.00,   99.00,   NULL, 2300, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 0, NULL, 0, '', '', '百元订单优先候选', NOW()),
('AI省钱 平台满129减25',         1, 25.00,   129.00,  NULL, 2200, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 0, NULL, 0, '', '', '凑单建议候选券', NOW()),
('AI省钱 平台满159减32',         1, 32.00,   159.00,  NULL, 2100, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 0, NULL, 0, '', '', '中低客单省钱券', NOW()),
('AI省钱 平台满199减45',         1, 45.00,   199.00,  NULL, 2000, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 0, NULL, 0, '', '', '购物车常用满减', NOW()),
('AI省钱 平台满299减70',         1, 70.00,   299.00,  NULL, 1900, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 0, NULL, 0, '', '', '多件商品组合满减', NOW()),
('AI省钱 平台满399减95',         1, 95.00,   399.00,  NULL, 1800, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 0, NULL, 0, '', '', '中客单强优惠', NOW()),
('AI省钱 平台满599减140',        1, 140.00,  599.00,  NULL, 1700, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 0, NULL, 0, '', '', '高客单满减', NOW()),
('AI省钱 平台满999减220',        1, 220.00,  999.00,  NULL, 1600, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 0, NULL, 0, '', '', '大额订单强优惠', NOW()),
('AI省钱 平台满1999减420',       1, 420.00,  1999.00, NULL, 1400, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 0, NULL, 0, '', '', '家电数码订单候选', NOW()),
('AI省钱 平台满4999减900',       1, 900.00,  4999.00, NULL, 1200, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 0, NULL, 0, '', '', '高客单智能匹配券', NOW()),
('AI省钱 平台9折最高80',         2, 9.00,    199.00,  80.00, 1600, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 0, NULL, 0, '', '', '折扣券对比满减券', NOW()),
('AI省钱 平台88折最高180',       2, 8.80,    499.00,  180.00,1500, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 0, NULL, 0, '', '', '让 AI 在折扣和满减间做选择', NOW()),
('AI省钱 优品满59减10',          1, 10.00,   59.00,   NULL, 1600, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 1, 2,    0, '', '', '优品旗舰店低门槛店铺券', NOW()),
('AI省钱 优品满99减18',          1, 18.00,   99.00,   NULL, 1500, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 1, 2,    0, '', '', '优品旗舰店百元档券', NOW()),
('AI省钱 优品满159减35',         1, 35.00,   159.00,  NULL, 1400, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 1, 2,    0, '', '', '优品旗舰店中档券', NOW()),
('AI省钱 优品满299减70',         1, 70.00,   299.00,  NULL, 1300, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 1, 2,    0, '', '', '优品旗舰店组合购券', NOW()),
('AI省钱 优品满599减150',        1, 150.00,  599.00,  NULL, 1200, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 1, 2,    0, '', '', '优品旗舰店高客单券', NOW()),
('AI省钱 优品满999减260',        1, 260.00,  999.00,  NULL, 1100, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 1, 2,    0, '', '', '优品旗舰店大额券', NOW()),
('AI省钱 优品9折最高120',        2, 9.00,    299.00,  120.00,1000, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 1, 2,    0, '', '', '优品旗舰店折扣券', NOW()),
('AI省钱 数码满99减15',          1, 15.00,   99.00,   NULL, 1600, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 1, 3,    0, '', '', '数码生活馆低门槛券', NOW()),
('AI省钱 数码满199减40',         1, 40.00,   199.00,  NULL, 1500, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 1, 3,    0, '', '', '数码生活馆常用券', NOW()),
('AI省钱 数码满299减65',         1, 65.00,   299.00,  NULL, 1400, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 1, 3,    0, '', '', '数码生活馆配件组合券', NOW()),
('AI省钱 数码满599减130',        1, 130.00,  599.00,  NULL, 1300, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 1, 3,    0, '', '', '数码生活馆中高档券', NOW()),
('AI省钱 数码满999减230',        1, 230.00,  999.00,  NULL, 1200, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 1, 3,    0, '', '', '数码生活馆大额券', NOW()),
('AI省钱 数码满1999减450',       1, 450.00,  1999.00, NULL, 1100, 5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 1, 3,    0, '', '', '数码生活馆高客单券', NOW()),
('AI省钱 数码满4999减1000',      1, 1000.00, 4999.00, NULL, 900,  5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 1, 3,    0, '', '', '数码生活馆旗舰商品券', NOW()),
('AI省钱 数码88折最高800',       2, 8.80,    999.00,  800.00,900,  5, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1, 1, 3,    0, '', '', '数码生活馆折扣券', NOW());


-- ============================================
-- 用户优惠券种子数据
-- ============================================
INSERT INTO `user_coupon` (`user_id`, `coupon_id`, `status`, `order_id`, `use_time`, `create_time`) VALUES
(4, 1, 1, 3,    '2024-01-20 11:00:00', '2024-01-01 10:00:00'),
(4, 2, 0, NULL,  NULL,                  '2024-01-05 09:00:00'),
(4, 4, 0, NULL,  NULL,                  '2024-01-10 14:00:00'),
(5, 1, 1, 8,    '2024-01-13 09:30:00', '2024-01-02 10:00:00'),
(5, 3, 0, NULL,  NULL,                  '2024-01-05 11:00:00'),
(5, 6, 0, NULL,  NULL,                  '2024-02-01 10:00:00'),
(6, 2, 1, 12,   '2024-01-08 14:50:00', '2024-01-03 09:00:00'),
(6, 5, 0, NULL,  NULL,                  '2024-01-10 10:00:00'),
(7, 1, 0, NULL,  NULL,                  '2024-01-04 10:00:00'),
(7, 7, 0, NULL,  NULL,                  '2024-03-05 09:00:00'),
(8, 4, 1, 23,   '2024-01-03 10:15:00', '2024-01-01 08:00:00'),
(8, 7, 0, NULL,  NULL,                  '2024-01-15 10:00:00');

INSERT IGNORE INTO `user_coupon` (`user_id`, `coupon_id`, `status`, `order_id`, `use_time`, `create_time`)
SELECT u.`id`, c.`id`, 0, NULL, NULL, NOW()
FROM `user` u
JOIN `coupon` c ON c.`name` LIKE 'AI省钱%'
WHERE u.`id` IN (4, 5, 6, 7, 8);


-- ============================================
-- 消息通知种子数据
-- ============================================
INSERT INTO `message` (`user_id`, `title`, `content`, `type`, `related_id`, `is_read`, `create_time`) VALUES
(4, '欢迎注册',       '欢迎来到智能推荐商城！我们为您精选了海量好物，祝您购物愉快~', 'system', NULL, 1, '2024-01-01 08:00:00'),
(4, '下单成功',       '您的订单 ORD20240101001 已创建，请尽快支付', 'order', 1, 1, '2024-01-10 11:30:00'),
(4, '支付成功',       '您的订单 ORD20240101001 已支付成功，金额 ¥14999.00', 'order', 1, 1, '2024-01-10 11:31:00'),
(4, '新人专属优惠',   '您有一张满100减20优惠券待领取，快来看看吧！', 'promotion', NULL, 0, '2024-01-15 10:00:00'),
(5, '欢迎注册',       '欢迎来到智能推荐商城！我们为您精选了海量好物，祝您购物愉快~', 'system', NULL, 1, '2024-01-01 08:00:00'),
(5, '订单已发货',     '您的订单 ORD20240108007 已发货，请注意查收', 'order', 7, 1, '2024-01-10 10:00:00'),
(5, '春季美妆节',     '美妆专区全场88折，限时抢购中！', 'promotion', NULL, 0, '2024-03-01 09:00:00'),
(6, '欢迎注册',       '欢迎来到智能推荐商城！我们为您精选了海量好物，祝您购物愉快~', 'system', NULL, 1, '2024-01-01 08:00:00'),
(6, '确认收货',       '您的订单 ORD20240108012 已确认收货', 'order', 12, 1, '2024-01-15 10:00:00'),
(7, '欢迎注册',       '欢迎来到智能推荐商城！我们为您精选了海量好物，祝您购物愉快~', 'system', NULL, 1, '2024-01-01 08:00:00'),
(7, '亲子好物推荐',   '母婴专区上新啦，精选安全好物，呵护宝宝成长~', 'promotion', NULL, 0, '2024-02-20 09:00:00'),
(8, '欢迎注册',       '欢迎来到智能推荐商城！我们为您精选了海量好物，祝您购物愉快~', 'system', NULL, 1, '2024-01-01 08:00:00'),
(8, '订单已取消',     '您的订单 ORD20240315030 已取消', 'order', 30, 1, '2024-03-15 09:25:00'),
(8, '图书节来啦',     '世界读书日，全场图书满60减10！', 'promotion', NULL, 0, '2024-04-01 09:00:00');


-- ============================================
-- 退款申请种子数据
-- ============================================
INSERT INTO `refund_request` (`order_id`, `user_id`, `reason`, `description`, `images`, `amount`, `status`, `reject_reason`, `create_time`) VALUES
(30, 8, '不想要了', '下单后发现家里已经有类似的了', NULL, 598.00, 3, NULL, '2024-03-15 09:22:00'),
(22, 7, '质量问题', '收到的商品有轻微划痕', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/replacement/products/2026/03/23/product-036-goodbaby-stroller.jpg"]', 899.00, 0, NULL, '2024-02-18 10:30:00');


-- ============================================
-- 搜索历史种子数据
-- ============================================
INSERT INTO `search_history` (`user_id`, `keyword`, `search_count`, `create_time`, `update_time`) VALUES
-- 小明的搜索
(4, '笔记本电脑',   5, '2024-01-05 10:00:00', '2024-03-20 14:30:00'),
(4, 'MacBook',      3, '2024-01-08 11:00:00', '2024-03-15 09:20:00'),
(4, '运动鞋',       2, '2024-01-12 15:00:00', '2024-02-20 16:40:00'),
(4, '坚果零食',     2, '2024-01-18 09:00:00', '2024-02-15 10:10:00'),
(4, '空调',         1, '2024-01-20 14:00:00', '2024-01-20 14:00:00'),
(4, '手机壳',       1, '2024-02-01 11:00:00', '2024-02-01 11:00:00'),
-- 小红的搜索
(5, '护肤品',       6, '2024-01-03 09:00:00', '2024-03-25 20:10:00'),
(5, '兰蔻',         4, '2024-01-05 10:00:00', '2024-03-10 15:30:00'),
(5, 'SK-II',        3, '2024-01-06 14:00:00', '2024-02-28 11:00:00'),
(5, '口红',         3, '2024-01-10 16:00:00', '2024-03-05 19:20:00'),
(5, '羽绒服',       2, '2024-01-11 11:00:00', '2024-01-15 10:00:00'),
(5, '连衣裙',       1, '2024-03-01 14:00:00', '2024-03-01 14:00:00'),
-- 小李的搜索
(6, '游戏本',       4, '2024-01-02 10:00:00', '2024-03-18 15:00:00'),
(6, 'ThinkPad',     3, '2024-01-03 11:00:00', '2024-02-20 09:30:00'),
(6, '运动装备',     2, '2024-01-15 16:00:00', '2024-02-10 14:00:00'),
(6, '机械键盘',     2, '2024-02-01 10:00:00', '2024-03-05 11:00:00'),
(6, '显示器',       1, '2024-02-10 15:00:00', '2024-02-10 15:00:00'),
-- 小王的搜索
(7, '婴儿用品',     5, '2024-01-02 09:00:00', '2024-03-22 10:00:00'),
(7, '儿童玩具',     4, '2024-01-05 10:00:00', '2024-03-15 14:30:00'),
(7, '乐高',         3, '2024-01-06 11:00:00', '2024-02-25 16:00:00'),
(7, '推车',         2, '2024-01-10 14:00:00', '2024-02-01 09:00:00'),
(7, '奶粉',         2, '2024-01-15 10:00:00', '2024-03-01 10:00:00'),
(7, '台灯',         1, '2024-02-01 15:00:00', '2024-02-01 15:00:00'),
-- 小张的搜索
(8, '家电',         5, '2024-01-01 10:00:00', '2024-03-28 11:00:00'),
(8, '冰箱',         3, '2024-01-02 11:00:00', '2024-03-10 14:00:00'),
(8, '吸尘器',       3, '2024-01-05 14:00:00', '2024-03-20 16:30:00'),
(8, '图书',         2, '2024-02-01 10:00:00', '2024-03-05 09:00:00'),
(8, '余华',         2, '2024-02-05 11:00:00', '2024-03-05 14:00:00'),
(8, '牛奶',         1, '2024-02-08 09:00:00', '2024-02-08 09:00:00'),
(8, '沙发',         1, '2024-03-01 10:00:00', '2024-03-01 10:00:00');


-- ============================================
-- 批量新增商品种子数据 (80个新商品, 覆盖10个分类)
-- 图片已上传至 OSS
-- ============================================
INSERT INTO `product` (`name`, `description`, `price`, `original_price`, `category_id`, `merchant_id`, `image`, `images`, `tags`, `stock`, `sales_count`, `rating`) VALUES
-- 手机数码 (8个)
('Samsung Galaxy S24 Ultra', '骁龙8Gen3芯片，2亿像素主摄，S Pen手写笔，钛金属边框，6.8英寸动态AMOLED屏幕，AI智能修图。', 9499.00, 10199.00, 1, 3, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/samsung-s24-ultra.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/samsung-s24-ultra.webp"]', '["三星","旗舰","S Pen","AI"]', 400, 1680, 4.8),
('vivo X100 Pro', '天玑9300旗舰芯片，蔡司2亿像素APO超级长焦，自研蓝海电池6000mAh续航出色，120W闪充。', 4999.00, 5499.00, 1, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/vivo-x100-pro.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/vivo-x100-pro.webp"]', '["vivo","蔡司","长焦","旗舰"]', 600, 2100, 4.7),
('荣耀Magic6 Pro', '骁龙8Gen3，荣耀青海湖电池5600mAh，鹰眼相机系统，66W有线+50W无线快充。', 4599.00, 4999.00, 1, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/honor-magic6-pro.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/honor-magic6-pro.webp"]', '["荣耀","青海湖电池","鹰眼","旗舰"]', 500, 1450, 4.7),
('Apple AirPods Pro 2', '自适应降噪2.0，H2芯片驱动，个性化空间音频，触控操作，MagSafe充电盒，IP54防水。', 1799.00, 1999.00, 1, 3, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/airpods-pro-2.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/airpods-pro-2.webp"]', '["苹果","降噪","耳机","无线"]', 1200, 5680, 4.9),
('Sony WH-1000XM5 降噪耳机', '行业领先降噪技术，30mm驱动单元，30小时续航，多点连接，佩戴检测自动暂停。', 2499.00, 2999.00, 1, 3, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/sony-wh1000xm5.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/sony-wh1000xm5.webp"]', '["索尼","降噪","头戴式","HIFI"]', 350, 2340, 4.8),
('Apple Watch Ultra 2', '钛金属表壳，S9芯片，双频精准GPS，100米防水，36小时续航，户外探险专属。', 5999.00, 6499.00, 1, 3, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/apple-watch-ultra-2.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/apple-watch-ultra-2.webp"]', '["苹果","手表","运动","钛金属"]', 200, 890, 4.8),
('一加12', '骁龙8Gen3，2K+120Hz ProXDR屏幕，哈苏影像，5400mAh+100W超级闪充，全新绿洲护眼屏。', 4299.00, 4799.00, 1, 3, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/oneplus-12.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/oneplus-12.webp"]', '["一加","骁龙8","哈苏","2K"]', 450, 1560, 4.7),
('华为FreeBuds Pro 3', '超感知原声双单元，智慧动态降噪3.0，星闪连接低延迟，LDAC高清音频，30小时续航。', 1499.00, 1699.00, 1, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/huawei-freebuds-pro-3.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/huawei-freebuds-pro-3.webp"]', '["华为","降噪","耳机","星闪"]', 800, 3200, 4.7),
-- 电脑办公 (8个)
('iPad Pro 12.9英寸 M2', 'M2芯片，Liquid Retina XDR显示屏，ProMotion，支持Apple Pencil悬停，120Hz高刷。', 8999.00, 9999.00, 2, 3, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/ipad-pro-129.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/ipad-pro-129.webp"]', '["苹果","平板","M2","创作"]', 300, 1890, 4.9),
('Dell XPS 15 笔记本', '英特尔酷睿i7-13700H，15.6英寸3.5K OLED触控屏，32GB内存+1TB SSD，雷电4接口。', 12999.00, 14499.00, 2, 3, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/dell-xps-15.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/dell-xps-15.webp"]', '["戴尔","OLED","轻薄","商务"]', 150, 670, 4.7),
('Microsoft Surface Pro 9', '第12代英特尔酷睿处理器，13英寸PixelSense触控屏，平板笔电二合一，支持Surface Pen。', 8988.00, 9988.00, 2, 3, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/surface-pro-9.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/surface-pro-9.webp"]', '["微软","二合一","触控","Surface"]', 200, 560, 4.6),
('罗技MX Master 3S鼠标', '8K DPI传感器，MagSpeed电磁滚轮，多设备切换，静音点击，70天续航，USB-C充电。', 749.00, 899.00, 2, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/logitech-mx-master-3s.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/logitech-mx-master-3s.webp"]', '["罗技","鼠标","办公","无线"]', 800, 4560, 4.8),
('Cherry MX Board 3.0S机械键盘', 'Cherry MX红轴，全键无冲，铝合金面板，USB有线连接，办公游戏兼顾。', 599.00, 799.00, 2, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/cherry-mx-keyboard.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/cherry-mx-keyboard.webp"]', '["Cherry","机械键盘","红轴","办公"]', 500, 2340, 4.6),
('Dell U2723QE 4K显示器', '27英寸4K IPS Black面板，HDR400，USB-C 90W供电，99% sRGB，旋转升降支架。', 3999.00, 4599.00, 2, 3, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/dell-u2723qe-monitor.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/dell-u2723qe-monitor.webp"]', '["戴尔","4K","显示器","HDR"]', 250, 890, 4.7),
('惠普LaserJet打印机', '黑白激光打印，每分钟20页，自动双面打印，WiFi无线连接，适合小型办公。', 1299.00, 1599.00, 2, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/hp-laserjet-printer.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/hp-laserjet-printer.webp"]', '["惠普","打印机","激光","办公"]', 400, 1560, 4.5),
('西部数据Elements 2TB移动硬盘', 'USB 3.0高速传输，即插即用，兼容Windows/Mac，紧凑便携设计，可靠数据存储。', 469.00, 599.00, 2, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/wd-elements-2tb.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/wd-elements-2tb.webp"]', '["西数","移动硬盘","2TB","存储"]', 1000, 6780, 4.6),
-- 家用电器 (8个)
('小天鹅10KG滚筒洗衣机', '变频电机，银离子除菌，15分钟快洗，智能投放洗涤剂，大容量满足全家需求。', 2999.00, 3699.00, 3, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/littleswan-washer-10kg.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/littleswan-washer-10kg.webp"]', '["小天鹅","洗衣机","滚筒","变频"]', 200, 1230, 4.7),
('松下NN-DS59JB微波炉', '变频微波+蒸汽+烧烤三合一，27L容量，智能感应加热，食物不翻转均匀受热。', 1899.00, 2299.00, 3, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/panasonic-microwave.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/panasonic-microwave.webp"]', '["松下","微波炉","变频","蒸烤"]', 300, 890, 4.6),
('虎牌JKT-D IH电饭煲', '5层远红外涂层内锅，IH多段加热，土锅涂层模拟柴火焖饭，3升适合2-4人家庭。', 1599.00, 1999.00, 3, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/tiger-rice-cooker.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/tiger-rice-cooker.webp"]', '["虎牌","电饭煲","IH","日本"]', 400, 2100, 4.8),
('美的PT3530W电烤箱 35L', '上下独立控温，360°热风循环，内置照明灯，4层烤位，搪瓷内胆易清洁。', 399.00, 549.00, 3, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/midea-oven-35l.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/midea-oven-35l.webp"]', '["美的","烤箱","热风","烘焙"]', 600, 3450, 4.5),
('石头T8扫地机器人', 'LiDAR精准导航，5500Pa超强吸力，自动集尘，智能避障，全屋规划清扫。', 3299.00, 3999.00, 3, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/roborock-t8.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/roborock-t8.webp"]', '["石头","扫地机器人","智能","自动"]', 250, 1670, 4.7),
('象印CV-WA电热水壶', 'VE真空保温，4段温度选择，1.0L容量，省电模式，安全倾倒防漏设计。日本原产。', 899.00, 1099.00, 3, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/zojirushi-kettle.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/zojirushi-kettle.webp"]', '["象印","热水壶","保温","日本"]', 350, 1890, 4.6),
('飞利浦Sonicare电动牙刷', '31000次/分钟声波清洁，3种刷牙模式，2分钟智能计时，14天续航，IPX7防水。', 399.00, 549.00, 3, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/philips-sonicare.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/philips-sonicare.webp"]', '["飞利浦","电动牙刷","声波","防水"]', 700, 4560, 4.7),
('九阳Y1破壁料理机', '10叶破壁刀，35000转/分，自清洗功能，预约定时，豆浆/辅食/冰沙/浓汤多功能。', 599.00, 799.00, 3, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/joyoung-blender.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/joyoung-blender.webp"]', '["九阳","破壁机","豆浆","多功能"]', 500, 3210, 4.6),
-- 服饰鞋包 (8个)
('Adidas Ultraboost Light跑鞋', 'BOOST中底极致缓震，Primeknit+鞋面透气贴合，Continental橡胶大底抓地力强。', 1099.00, 1299.00, 4, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/adidas-ultraboost.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/adidas-ultraboost.webp"]', '["阿迪达斯","跑鞋","BOOST","透气"]', 600, 3450, 4.8),
('New Era 9FORTY棒球帽', '经典弯檐款型，可调节尺寸，纯棉材质透气舒适，多色可选，街头潮流必备。', 199.00, 269.00, 4, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/new-era-cap.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/new-era-cap.webp"]', '["New Era","棒球帽","潮流","经典"]', 1000, 6780, 4.5),
('Champion经典刺绣卫衣', '柔软抓绒内里，宽松落肩剪裁，经典Logo刺绣，百搭休闲，男女同款。', 399.00, 549.00, 4, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/champion-hoodie.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/champion-hoodie.webp"]', '["Champion","卫衣","潮流","百搭"]', 800, 5230, 4.6),
('ZARA碎花雪纺连衣裙', 'V领碎花设计，轻盈雪纺面料，收腰A字版型，春夏优雅通勤约会首选。', 299.00, 399.00, 4, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/zara-floral-dress.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/zara-floral-dress.webp"]', '["ZARA","连衣裙","碎花","春夏"]', 500, 4560, 4.5),
('Ray-Ban飞行员太阳镜', '经典飞行员款型RB3025，偏光镜片防紫外线，金属镜框轻盈佩戴，附品牌皮套。', 1180.00, 1480.00, 4, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/rayban-aviator.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/rayban-aviator.webp"]', '["雷朋","太阳镜","偏光","经典"]', 350, 2340, 4.7),
('卡西欧G-SHOCK GA-2100', '碳纤维保护结构，200米防水，世界时间，秒表计时，矿物玻璃镜面，超薄八角表壳。', 899.00, 1190.00, 4, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/casio-gshock-2100.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/casio-gshock-2100.webp"]', '["卡西欧","G-SHOCK","防水","运动"]', 400, 3120, 4.7),
('COACH Tabby链条单肩包', '标志性Signature印花，优质皮革，可拆卸链条肩带，精致转锁扣，经典百搭。', 2999.00, 3899.00, 4, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/coach-tabby-bag.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/coach-tabby-bag.webp"]', '["COACH","链条包","单肩包","经典"]', 200, 1230, 4.6),
('Nike Dri-FIT男士运动短裤', 'Dri-FIT速干科技，弹力面料不束缚，侧兜实用设计，健身跑步训练必备。', 249.00, 329.00, 4, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/nike-drifit-shorts.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/nike-drifit-shorts.webp"]', '["Nike","运动短裤","速干","训练"]', 900, 5670, 4.6),
-- 美妆护肤 (8个)
('安耐晒金钻防晒霜 60ml', 'SPF50+ PA++++，防水防汗，超轻质感不泛白，适合户外运动和日常通勤。', 228.00, 298.00, 5, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/anessa-sunscreen.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/anessa-sunscreen.webp"]', '["安耐晒","防晒","SPF50","防水"]', 600, 8900, 4.7),
('春雨蜂蜜面膜 10片装', '天然蜂蜜提取物，深层补水保湿，温和不刺激，敏感肌可用，韩国原装进口。', 89.00, 129.00, 5, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/papa-recipe-mask.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/papa-recipe-mask.webp"]', '["春雨","面膜","补水","蜂蜜"]', 1500, 12300, 4.6),
('芙丽芳丝氨基酸洗面奶', '氨基酸温和配方，绵密泡沫深层清洁不紧绷，不含皂基，敏感肌友好。130g装。', 128.00, 168.00, 5, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/freeplus-cleanser.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/freeplus-cleanser.webp"]', '["芙丽芳丝","洗面奶","氨基酸","温和"]', 800, 7890, 4.7),
('阿玛尼红管唇釉 #405', '经典番茄红色号，丝绒哑光质地，高显色持久不拔干，一涂惊艳。', 310.00, 390.00, 5, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/armani-lip-405.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/armani-lip-405.webp"]', '["阿玛尼","唇釉","红管","持久"]', 500, 5670, 4.8),
('资生堂红腰子精华 75ml', '核心Ultimune科技，增强肌肤自我修复力，改善粗糙暗沉，提升光泽感。', 760.00, 950.00, 5, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/shiseido-ultimune.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/shiseido-ultimune.webp"]', '["资生堂","精华","修护","红腰子"]', 350, 3450, 4.8),
('Dior真我女士香水 50ml', '花果香调，前调佛手柑，中调茉莉花瓣，尾调檀木温暖沉醉。经典优雅。', 980.00, 1280.00, 5, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/dior-jadore-50ml.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/dior-jadore-50ml.webp"]', '["Dior","香水","花果调","优雅"]', 250, 2340, 4.9),
('完美日记动物眼影盘 小狗盘', '12色日常配色，粉质细腻显色，哑光珠光组合，新手友好，适合日常妆容。', 89.00, 129.00, 5, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/perfect-diary-eyeshadow.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/perfect-diary-eyeshadow.webp"]', '["完美日记","眼影","国货","平价"]', 1200, 9800, 4.5),
('薇诺娜舒敏保湿特护霜 50g', '青刺果油+马齿苋精华，舒缓修护敏感泛红，强化皮肤屏障，皮肤科医生推荐。', 268.00, 328.00, 5, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/winona-cream.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/winona-cream.webp"]', '["薇诺娜","面霜","舒敏","修护"]', 600, 4560, 4.7),
-- 食品生鲜 (8个)
('德芙丝滑牛奶巧克力 252g', '进口可可脂，丝滑口感入口即化，经典牛奶味，送礼自享两相宜。', 39.90, 52.00, 6, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/dove-chocolate.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/dove-chocolate.webp"]', '["德芙","巧克力","牛奶","丝滑"]', 2000, 15600, 4.6),
('星巴克哥伦比亚咖啡豆 250g', '单一产地精品咖啡豆，中深烘焙，坚果巧克力香气，适合手冲和意式萃取。', 88.00, 108.00, 6, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/starbucks-coffee-beans.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/starbucks-coffee-beans.webp"]', '["星巴克","咖啡豆","精品","中烘"]', 800, 5670, 4.7),
('百花牌天然蜂蜜 500g', '秦岭原产洋槐蜜，波美度42+，口感清甜，自然结晶天然好蜜，老品牌值得信赖。', 45.00, 68.00, 6, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/baihua-honey.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/baihua-honey.webp"]', '["百花","蜂蜜","天然","洋槐"]', 1000, 6780, 4.5),
('桂格即食燕麦片 1kg', '100%澳洲进口燕麦，高纤低GI，冲泡即食方便快捷，早餐首选健康粗粮。', 29.90, 39.90, 6, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/quaker-oatmeal.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/quaker-oatmeal.webp"]', '["桂格","燕麦","即食","早餐"]', 1500, 9800, 4.5),
('奥利奥原味夹心饼干 388g', '经典黑白配色，浓郁可可饼干夹心香甜奶油，扭一扭泡一泡舔一舔，快乐零食。', 16.90, 22.00, 6, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/oreo-cookies.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/oreo-cookies.webp"]', '["奥利奥","饼干","夹心","经典"]', 3000, 23400, 4.5),
('竹叶青峨眉高山绿茶 100g', '明前嫩芽，手工采摘，汤色嫩绿清亮，口感鲜爽回甘。精美罐装，品茗佳选。', 168.00, 228.00, 6, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/zhuyeqing-green-tea.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/zhuyeqing-green-tea.webp"]', '["竹叶青","绿茶","明前","高山"]', 500, 3450, 4.7),
('百草味猪肉脯 200g', '精选优质猪腿肉，蜜汁烘烤，肉质鲜嫩弹牙，独立小包装，追剧零食必备。', 25.90, 35.90, 6, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/baicaowei-pork-jerky.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/baicaowei-pork-jerky.webp"]', '["百草味","肉脯","零食","蜜汁"]', 2000, 12300, 4.5),
('瑞士莲软心巧克力球 200g', '瑞士原装进口，柔滑巧克力壳包裹流心夹心，牛奶/榛果/黑巧多口味可选。', 59.00, 79.00, 6, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/lindt-truffle.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/lindt-truffle.webp"]', '["瑞士莲","巧克力","软心","进口"]', 1200, 7890, 4.7),
-- 图书文具 (8个)
('人类简史：从动物到上帝', '尤瓦尔·赫拉利全球畅销书，宏观视角讲述人类演化与文明发展。入选比尔盖茨书单。', 45.00, 68.00, 7, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/sapiens-book.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/sapiens-book.webp"]', '["人类简史","社科","畅销书","历史"]', 800, 12300, 4.9),
('小王子（精装插图版）', '圣埃克苏佩里经典童话，全球销量超2亿册。精装彩色插图版，适合各年龄段阅读。', 28.00, 38.00, 7, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/little-prince.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/little-prince.webp"]', '["小王子","童话","经典","插图"]', 1000, 18900, 4.8),
('解忧杂货店 - 东野圭吾', '东野圭吾温情治愈之作，穿越时空的书信传递温暖与希望。全球销量超1000万册。', 35.00, 49.50, 7, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/miracles-general-store.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/miracles-general-store.webp"]', '["东野圭吾","小说","治愈","日本"]', 600, 9800, 4.8),
('英雄616钢笔', '经典暗尖设计，书写细腻流畅，铱金笔尖耐磨。学生练字和日常书写皆宜。', 18.00, 28.00, 7, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/hero-616-pen.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/hero-616-pen.webp"]', '["英雄","钢笔","经典","练字"]', 2000, 25600, 4.5),
('Moleskine经典硬面笔记本', '意大利品牌，酸性纸不渗墨，圆角设计，丝带书签+弹力绑带+后袋。240页大容量。', 168.00, 218.00, 7, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/moleskine-notebook.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/moleskine-notebook.webp"]', '["Moleskine","笔记本","经典","意大利"]', 500, 5670, 4.7),
('创意金属书签套装 6枚', '中国风古典镂空设计，覆膜不生锈，送礼文艺精致。含竹节/荷花/梅花等图案。', 29.90, 45.00, 7, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/metal-bookmark-set.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/metal-bookmark-set.webp"]', '["书签","金属","中国风","文创"]', 1500, 8900, 4.6),
('明朝那些事儿（全9册）', '当年明月幽默讲述明朝三百年历史，通俗易懂又不失严谨。全套9册完整版。', 178.00, 258.00, 7, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/ming-dynasty-books.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/ming-dynasty-books.webp"]', '["历史","小说","明朝","畅销"]', 400, 6780, 4.8),
('得力办公文具套装 12件', '含订书机+起钉器+打孔器+计算器+剪刀+笔筒+胶带等，一站式配齐办公桌面。', 69.00, 99.00, 7, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/deli-office-set.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/deli-office-set.webp"]', '["得力","文具","套装","办公"]', 800, 5670, 4.5),
-- 运动户外 (8个)
('Lululemon Align瑜伽垫 5mm', '天然橡胶材质，防滑纹理，5mm适中厚度保护关节。附收纳带，适合瑜伽普拉提。', 580.00, 780.00, 8, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/lululemon-yoga-mat.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/lululemon-yoga-mat.webp"]', '["Lululemon","瑜伽垫","防滑","天然橡胶"]', 300, 2340, 4.8),
('可调节哑铃套装 20KG', '电镀铃片+泡棉手柄，2-20KG自由组合，居家健身增肌塑形。含连接杆可变杠铃。', 299.00, 459.00, 8, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/adjustable-dumbbell-20kg.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/adjustable-dumbbell-20kg.webp"]', '["哑铃","健身","可调节","增肌"]', 500, 3450, 4.6),
('The North Face 2人露营帐篷', '防风防雨PU3000+涂层，铝合金支架轻量便携，双层结构防结露。3季通用。', 1299.00, 1799.00, 8, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/northface-tent.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/northface-tent.webp"]', '["北面","帐篷","露营","防水"]', 150, 890, 4.7),
('膳魔师保温杯 500ml', '真空断热技术，保温12小时/保冷24小时。316不锈钢内胆，一键开盖，便携轻量。', 199.00, 279.00, 8, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/thermos-bottle-500ml.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/thermos-bottle-500ml.webp"]', '["膳魔师","保温杯","不锈钢","便携"]', 800, 6780, 4.7),
('Osprey Stratos 36L登山包', '背负系统通风透气，多点可调节肩带腰带，防雨罩内置。适合单日至两日徒步。', 1099.00, 1399.00, 8, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/osprey-stratos-36l.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/osprey-stratos-36l.webp"]', '["Osprey","登山包","徒步","透气"]', 200, 1230, 4.8),
('Speedo竞速泳镜', '防雾涂层镜片，硅胶眼罩贴合不压眼，可调节鼻桥，UV防护。专业训练竞速款。', 169.00, 229.00, 8, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/speedo-goggles.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/speedo-goggles.webp"]', '["Speedo","泳镜","防雾","竞速"]', 600, 3450, 4.6),
('迪卡侬运动腰包', '弹力面料贴身不晃动，反光条夜跑安全，手机口袋+钥匙口袋，跑步骑行通用。', 49.90, 79.00, 8, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/decathlon-waist-bag.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/decathlon-waist-bag.webp"]', '["迪卡侬","腰包","跑步","轻便"]', 1000, 8900, 4.5),
('凯乐石防晒皮肤衣', 'UPF50+全面防护，10D超轻面料仅100g，防泼水透气，可折叠收纳入口袋。', 299.00, 459.00, 8, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/kailas-uv-jacket.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/kailas-uv-jacket.webp"]', '["凯乐石","皮肤衣","防晒","超轻"]', 400, 2340, 4.6),
-- 母婴玩具 (8个)
('花王妙而舒纸尿裤 L54片', '日本原装进口，棉柔透气表层，3D立体防漏，弹力腰贴自由活动不束缚。', 139.00, 179.00, 9, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/merries-diapers-l54.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/merries-diapers-l54.webp"]', '["花王","纸尿裤","婴儿","透气"]', 2000, 15600, 4.8),
('飞鹤星飞帆婴儿奶粉 3段 900g', '全乳糖配方，OPO结构脂+乳铁蛋白+DHA，适合12-36月龄宝宝，中国宝宝专属配方。', 299.00, 379.00, 9, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/feihe-formula.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/feihe-formula.webp"]', '["飞鹤","奶粉","婴儿","国产"]', 1000, 8900, 4.7),
('Hape彩虹早教积木 80粒', '德国品牌，环保水性漆无毒，圆角打磨安全，多形状色彩认知，适合1-3岁宝宝。', 159.00, 219.00, 9, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/hape-rainbow-blocks.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/hape-rainbow-blocks.webp"]', '["Hape","积木","早教","安全"]', 600, 4560, 4.7),
('膳魔师FHL-401儿童保温水杯', '316不锈钢内胆，一键开盖吸管杯，保温8小时，防漏设计，可爱卡通图案。', 219.00, 289.00, 9, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/thermos-kids-cup.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/thermos-kids-cup.webp"]', '["膳魔师","儿童水杯","保温","吸管"]', 500, 3450, 4.6),
('迪士尼经典绘本套装 20册', '迪士尼官方授权，含冰雪奇缘、狮子王等经典故事。中英双语，精美插图，亲子阅读。', 128.00, 198.00, 9, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/disney-picture-books.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/disney-picture-books.webp"]', '["迪士尼","绘本","双语","儿童"]', 800, 6780, 4.8),
('Micro迷你三轮滑板车', '瑞士品牌，重力转向培养平衡感，静音PU轮，三档可调高度。适合2-5岁宝宝。', 599.00, 799.00, 9, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/micro-mini-scooter.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/micro-mini-scooter.webp"]', '["Micro","滑板车","儿童","平衡"]', 300, 2340, 4.7),
('费雪安抚海马', '柔和灯光+舒缓音乐+白噪音，助力宝宝安静入睡。PP棉填充可机洗，0-3岁适用。', 129.00, 179.00, 9, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/fisher-price-seahorse.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/fisher-price-seahorse.webp"]', '["费雪","安抚","睡眠","毛绒"]', 700, 5670, 4.8),
('贝亲婴儿洗衣液 1.2L', '植物配方温和不伤手，有效去除奶渍果渍，无荧光剂无残留，宝宝衣物专用。', 45.00, 65.00, 9, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/pigeon-baby-detergent.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/pigeon-baby-detergent.webp"]', '["贝亲","洗衣液","婴儿","温和"]', 1500, 12300, 4.6),
-- 家居家装 (8个)
('简约遮光窗帘 2.5x2.7m', '加厚遮光面料，遮光率95%，隔热隔音。多色可选，打孔安装简便，卧室客厅通用。', 129.00, 199.00, 10, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/blackout-curtains.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/blackout-curtains.webp"]', '["窗帘","遮光","隔热","卧室"]', 500, 5670, 4.6),
('北欧风圆形地毯 120cm', '柔软短绒材质，防滑底层，简约纯色设计。客厅卧室书房皆可，机洗不掉色。', 189.00, 289.00, 10, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/nordic-round-rug.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/nordic-round-rug.webp"]', '["地毯","北欧","圆形","柔软"]', 400, 3450, 4.5),
('景德镇手工陶瓷花瓶', '手工拉坯烧制，清雅青花釉色，简约瓶型，水培插花两宜。高35cm，口径8cm。', 128.00, 198.00, 10, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/jingdezhen-vase.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/jingdezhen-vase.webp"]', '["花瓶","陶瓷","景德镇","手工"]', 300, 2340, 4.7),
('304不锈钢衣架 20支装', '加粗加厚304不锈钢，承重强不变形，防风卡槽设计，适用阳台晾晒和衣柜收纳。', 49.90, 79.00, 10, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/stainless-hangers.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/stainless-hangers.webp"]', '["衣架","不锈钢","防风","收纳"]', 2000, 15600, 4.5),
('天马Tenma抽屉式收纳箱 3只', '日本品牌，PP材质安全环保，透明可视，可叠放节省空间。衣物、杂物分类收纳。', 99.00, 149.00, 10, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/tenma-storage-box.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/tenma-storage-box.webp"]', '["天马","收纳箱","抽屉","透明"]', 1000, 8900, 4.6),
('北欧极简落地灯', '铁艺灯杆+布艺灯罩，三色调光(暖白/自然/冷白)，E27接口。高158cm，客厅书房装饰。', 299.00, 459.00, 10, 2, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/nordic-floor-lamp.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/nordic-floor-lamp.webp"]', '["落地灯","北欧","极简","装饰"]', 250, 1890, 4.6),
('小米智能门锁Pro', '指纹+密码+NFC+钥匙多种开锁方式，C级锁芯，半导体指纹识别0.5s开门。', 1499.00, 1999.00, 10, 3, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/xiaomi-smart-lock.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/xiaomi-smart-lock.webp"]', '["小米","门锁","智能","指纹"]', 300, 2340, 4.7),
('科沃斯窗宝W1 Pro擦窗机器人', '智能规划路径，强力吸附不掉落，喷水湿擦，遥控操作。适用各种窗户玻璃。', 2299.00, 2999.00, 10, 3, 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/ecovacs-window-robot.webp', '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/ecovacs-window-robot.webp"]', '["科沃斯","擦窗","机器人","智能"]', 150, 670, 4.5);


INSERT INTO `operation_log` (`user_id`, `username`, `role`, `module`, `action`, `method`, `url`, `params`, `ip`, `status`, `error_msg`, `cost_time`, `create_time`) VALUES
(1, 'admin', 'admin', '商品管理', '修改商品状态', 'PUT', '/api/admin/products/3/status', '{"status":1}', '127.0.0.1', 1, NULL, 45, '2024-01-05 09:12:30'),
(1, 'admin', 'admin', '用户管理', '修改用户状态', 'PUT', '/api/admin/users/5/status', '{"status":0}', '127.0.0.1', 1, NULL, 32, '2024-01-06 10:20:15'),
(1, 'admin', 'admin', '用户管理', '修改用户状态', 'PUT', '/api/admin/users/5/status', '{"status":1}', '127.0.0.1', 1, NULL, 28, '2024-01-06 14:35:00'),
(1, 'admin', 'admin', '订单管理', '修改订单状态', 'PUT', '/api/admin/orders/1/status', '{"status":2}', '127.0.0.1', 1, NULL, 56, '2024-01-10 11:45:20'),
(1, 'admin', 'admin', '优惠券管理', '创建优惠券', 'POST', '/api/admin/coupons', '{"name":"春节满减","type":"AMOUNT","value":50}', '127.0.0.1', 1, NULL, 120, '2024-01-15 09:00:00'),
(1, 'admin', 'admin', '系统通知', '群发公告', 'POST', '/api/admin/messages/broadcast', '{"title":"春节快乐","type":"system"}', '127.0.0.1', 1, NULL, 230, '2024-01-20 10:00:00'),
(2, 'merchant', 'merchant', '商家商品', '创建商品', 'POST', '/api/merchant/products', '{"name":"新品测试","price":99.00}', '192.168.1.100', 1, NULL, 88, '2024-01-22 14:30:00'),
(1, 'admin', 'admin', '商品管理', '删除商品', 'DELETE', '/api/admin/products/50', '', '127.0.0.1', 1, NULL, 35, '2024-01-25 16:10:00'),
(2, 'merchant', 'merchant', '商家订单', '发货', 'POST', '/api/merchant/orders/5/ship', '', '192.168.1.100', 1, NULL, 42, '2024-01-28 09:20:00'),
(1, 'admin', 'admin', '用户管理', '删除用户', 'DELETE', '/api/admin/users/99', '', '127.0.0.1', 0, '用户不存在', 15, '2024-02-01 11:00:00'),
(1, 'admin', 'admin', '订单管理', '修改订单状态', 'PUT', '/api/admin/orders/8/status', '{"status":3}', '127.0.0.1', 1, NULL, 48, '2024-02-05 15:30:00'),
(2, 'merchant', 'merchant', '商家商品', '删除商品', 'DELETE', '/api/merchant/products/50', '', '192.168.1.100', 1, NULL, 30, '2024-02-08 10:15:00'),
(1, 'admin', 'admin', '商品管理', '修改商品状态', 'PUT', '/api/admin/products/15/status', '{"status":0}', '127.0.0.1', 1, NULL, 38, '2024-02-10 09:45:00'),
(1, 'admin', 'admin', '退款管理', '同意退款', 'POST', '/api/refunds/1/approve', '', '127.0.0.1', 1, NULL, 180, '2024-02-12 14:20:00'),
(1, 'admin', 'admin', '系统通知', '群发公告', 'POST', '/api/admin/messages/broadcast', '{"title":"情人节活动","type":"promotion"}', '127.0.0.1', 1, NULL, 195, '2024-02-14 08:00:00'),
(2, 'merchant', 'merchant', '商家商品', '批量更新商品状态', 'PUT', '/api/merchant/products/batch-status', '{"ids":[1,2,3],"status":1}', '192.168.1.100', 1, NULL, 65, '2024-02-18 11:30:00'),
(1, 'admin', 'admin', '优惠券管理', '创建优惠券', 'POST', '/api/admin/coupons', '{"name":"38女神节","type":"PERCENT","value":15}', '127.0.0.1', 1, NULL, 95, '2024-03-01 09:00:00'),
(1, 'admin', 'admin', '用户管理', '修改用户状态', 'PUT', '/api/admin/users/8/status', '{"status":0}', '127.0.0.1', 1, NULL, 25, '2024-03-05 10:00:00'),
(2, 'merchant', 'merchant', '商家订单', '发货', 'POST', '/api/merchant/orders/12/ship', '', '192.168.1.100', 1, NULL, 55, '2024-03-08 16:00:00'),
(1, 'admin', 'admin', '钱包管理', '管理员调整余额', 'POST', '/api/admin/wallet/adjust', '{"userId":4,"amount":500,"reason":"活动奖励"}', '127.0.0.1', 1, NULL, 72, '2024-03-10 09:30:00'),
(1, 'admin', 'admin', '退款管理', '拒绝退款', 'POST', '/api/refunds/2/reject', '{"rejectReason":"不符合退款条件"}', '127.0.0.1', 1, NULL, 40, '2024-03-12 14:00:00'),
(1, 'admin', 'admin', '商品管理', '修改商品状态', 'PUT', '/api/admin/products/22/status', '{"status":1}', '127.0.0.1', 1, NULL, 33, '2024-03-15 11:20:00'),
(2, 'merchant', 'merchant', '商家商品', '批量更新商品库存', 'PUT', '/api/merchant/products/batch-stock', '{"ids":[5,6],"stock":200}', '192.168.1.100', 1, NULL, 48, '2024-03-18 15:45:00'),
(1, 'admin', 'admin', '用户管理', '修改用户状态', 'PUT', '/api/admin/users/8/status', '{"status":1}', '127.0.0.1', 1, NULL, 22, '2024-03-20 09:10:00');


-- ============================================
-- 河南特色商品运营数据
-- 说明：用于地域商品运营场景，把大部分商品替换为河南食品、茶礼、非遗与文创，并重建评价样本。
-- ============================================
DROP TEMPORARY TABLE IF EXISTS `tmp_henan_product_seed`;

-- ============================================
-- 热榜校准：保证热门推荐页优先出现河南特色商品
-- 说明：热门推荐按 product.sales_count 或实时热度回退排序；旧通用商品热度过高时会挤占首屏。
-- ============================================
UPDATE `product`
SET `sales_count` = 600 + MOD(`id` * 137, 3600)
WHERE `deleted` = 0
  AND `status` = 1
  AND NOT JSON_CONTAINS(`tags`, JSON_QUOTE('河南特色'));

UPDATE `product`
SET `sales_count` = 12000 + MOD(`id` * 293, 7600)
WHERE `deleted` = 0
  AND `status` = 1
  AND JSON_CONTAINS(`tags`, JSON_QUOTE('河南特色'));

UPDATE `product`
SET `sales_count` = CASE `name`
  WHEN '信阳毛尖茶叶礼盒 · 礼盒装' THEN 33800
  WHEN '郑州滋补烩面礼盒 · 礼盒装' THEN 32600
  WHEN '道口烧鸡整只装 · 礼盒装' THEN 31400
  WHEN '逍遥镇胡辣汤家庭装 · 礼盒装' THEN 30200
  WHEN '洛阳唐三彩马摆件 · 礼盒装' THEN 29100
  WHEN '温县铁棍山药 · 礼盒装' THEN 28000
  WHEN '开封花生糕 · 礼盒装' THEN 26900
  WHEN '汝瓷天青茶杯 · 礼盒装' THEN 25800
  WHEN '南阳玉雕平安扣 · 礼盒装' THEN 24700
  WHEN '朱仙镇木版年画 · 礼盒装' THEN 23600
  ELSE `sales_count`
END
WHERE `name` IN (
  '信阳毛尖茶叶礼盒 · 礼盒装',
  '郑州滋补烩面礼盒 · 礼盒装',
  '道口烧鸡整只装 · 礼盒装',
  '逍遥镇胡辣汤家庭装 · 礼盒装',
  '洛阳唐三彩马摆件 · 礼盒装',
  '温县铁棍山药 · 礼盒装',
  '开封花生糕 · 礼盒装',
  '汝瓷天青茶杯 · 礼盒装',
  '南阳玉雕平安扣 · 礼盒装',
  '朱仙镇木版年画 · 礼盒装'
);

INSERT INTO `stream_product_hotness_realtime`
(`product_id`, `category_id`, `hot_score`, `behavior_count`, `purchase_count`, `last_event_time`, `update_time`)
SELECT
  p.id,
  p.category_id,
  p.sales_count * 1.18,
  p.sales_count + 1800,
  GREATEST(80, FLOOR(p.sales_count / 9)),
  DATE_SUB(NOW(), INTERVAL MOD(p.id, 45) MINUTE),
  NOW()
FROM `product` p
WHERE p.`deleted` = 0
  AND p.`status` = 1
  AND p.`name` IN (
    '信阳毛尖茶叶礼盒 · 礼盒装',
    '郑州滋补烩面礼盒 · 礼盒装',
    '道口烧鸡整只装 · 礼盒装',
    '逍遥镇胡辣汤家庭装 · 礼盒装',
    '洛阳唐三彩马摆件 · 礼盒装',
    '温县铁棍山药 · 礼盒装',
    '开封花生糕 · 礼盒装',
    '汝瓷天青茶杯 · 礼盒装',
    '南阳玉雕平安扣 · 礼盒装',
    '朱仙镇木版年画 · 礼盒装'
  )
ON DUPLICATE KEY UPDATE
  `category_id` = VALUES(`category_id`),
  `hot_score` = VALUES(`hot_score`),
  `behavior_count` = VALUES(`behavior_count`),
  `purchase_count` = VALUES(`purchase_count`),
  `last_event_time` = VALUES(`last_event_time`),
  `update_time` = VALUES(`update_time`);

-- ============================================
-- 小程序固定体验账号与推荐归因数据
-- 固定体验账号: 16666666666 / 123456
-- 相似画像账号: 16666666667 / 16666666668 / 16666666669
-- ============================================
INSERT IGNORE INTO `user` (`username`, `password`, `nickname`, `role`, `phone`, `email`, `balance`, `email_verified`, `avatar`) VALUES
('16666666666', '$2a$10$5A3U8xoCIITLjqFn7nGWl.b/.D154d9AwEyjLzEkdFpbRnfWbVbPa', '数码优选用户', 'user', '16666666666', 'demo16666666666@competition.local', 30000.00, 1, 'https://api.dicebear.com/7.x/adventurer/svg?seed=CompetitionDemo'),
('16666666667', '$2a$10$5A3U8xoCIITLjqFn7nGWl.b/.D154d9AwEyjLzEkdFpbRnfWbVbPa', '相似用户A', 'user', '16666666667', 'demo16666666667@competition.local', 18000.00, 1, 'https://api.dicebear.com/7.x/adventurer/svg?seed=SimilarA'),
('16666666668', '$2a$10$5A3U8xoCIITLjqFn7nGWl.b/.D154d9AwEyjLzEkdFpbRnfWbVbPa', '相似用户B', 'user', '16666666668', 'demo16666666668@competition.local', 22000.00, 1, 'https://api.dicebear.com/7.x/adventurer/svg?seed=SimilarB'),
('16666666669', '$2a$10$5A3U8xoCIITLjqFn7nGWl.b/.D154d9AwEyjLzEkdFpbRnfWbVbPa', '相似用户C', 'user', '16666666669', 'demo16666666669@competition.local', 16000.00, 1, 'https://api.dicebear.com/7.x/adventurer/svg?seed=SimilarC');

INSERT INTO `user_preference` (`user_id`, `category_preferences`, `tag_preferences`, `price_range_min`, `price_range_max`)
SELECT u.id, '{"1":10,"2":8,"8":4,"7":2}', '{"旗舰":6,"苹果":5,"华为":5,"轻薄":4,"耳机":3,"办公":3}', 800.00, 16000.00
FROM `user` u WHERE u.phone = '16666666666'
ON DUPLICATE KEY UPDATE
  `category_preferences` = VALUES(`category_preferences`),
  `tag_preferences` = VALUES(`tag_preferences`),
  `price_range_min` = VALUES(`price_range_min`),
  `price_range_max` = VALUES(`price_range_max`);

INSERT INTO `user_preference` (`user_id`, `category_preferences`, `tag_preferences`, `price_range_min`, `price_range_max`)
SELECT u.id, '{"1":9,"2":7,"8":3}', '{"旗舰":5,"苹果":5,"华为":4,"轻薄":4,"降噪":3}', 900.00, 15000.00
FROM `user` u WHERE u.phone IN ('16666666667','16666666668','16666666669')
ON DUPLICATE KEY UPDATE
  `category_preferences` = VALUES(`category_preferences`),
  `tag_preferences` = VALUES(`tag_preferences`),
  `price_range_min` = VALUES(`price_range_min`),
  `price_range_max` = VALUES(`price_range_max`);

INSERT INTO `address` (`user_id`, `receiver_name`, `receiver_phone`, `province`, `city`, `district`, `detail`, `is_default`)
SELECT u.id, u.nickname, u.phone, '河南省', '郑州市', '金水区', '未来路优购中心 1 号', 1
FROM `user` u
WHERE u.phone IN ('16666666666','16666666667','16666666668','16666666669')
  AND NOT EXISTS (SELECT 1 FROM `address` a WHERE a.user_id = u.id AND a.receiver_phone = u.phone);

INSERT INTO `user_behavior` (`user_id`, `product_id`, `behavior_type`, `duration`, `create_time`)
SELECT u.id, b.product_id, b.behavior_type, b.duration, b.create_time
FROM `user` u
JOIN (
  SELECT '16666666666' AS phone, 1 AS product_id, 'view' AS behavior_type, 120 AS duration, DATE_SUB(NOW(), INTERVAL 5 DAY) AS create_time UNION ALL
  SELECT '16666666666', 1, 'cart', 30, DATE_SUB(NOW(), INTERVAL 4 DAY) UNION ALL
  SELECT '16666666666', 2, 'favorite', 60, DATE_SUB(NOW(), INTERVAL 4 DAY) UNION ALL
  SELECT '16666666666', 3, 'view', 90, DATE_SUB(NOW(), INTERVAL 3 DAY) UNION ALL
  SELECT '16666666666', 5, 'purchase', 20, DATE_SUB(NOW(), INTERVAL 2 DAY) UNION ALL
  SELECT '16666666667', 1, 'purchase', 80, DATE_SUB(NOW(), INTERVAL 6 DAY) UNION ALL
  SELECT '16666666667', 2, 'cart', 55, DATE_SUB(NOW(), INTERVAL 5 DAY) UNION ALL
  SELECT '16666666667', 4, 'purchase', 40, DATE_SUB(NOW(), INTERVAL 4 DAY) UNION ALL
  SELECT '16666666667', 6, 'favorite', 45, DATE_SUB(NOW(), INTERVAL 3 DAY) UNION ALL
  SELECT '16666666668', 1, 'cart', 65, DATE_SUB(NOW(), INTERVAL 6 DAY) UNION ALL
  SELECT '16666666668', 3, 'purchase', 70, DATE_SUB(NOW(), INTERVAL 5 DAY) UNION ALL
  SELECT '16666666668', 6, 'purchase', 35, DATE_SUB(NOW(), INTERVAL 3 DAY) UNION ALL
  SELECT '16666666668', 8, 'view', 50, DATE_SUB(NOW(), INTERVAL 2 DAY) UNION ALL
  SELECT '16666666669', 2, 'purchase', 70, DATE_SUB(NOW(), INTERVAL 6 DAY) UNION ALL
  SELECT '16666666669', 3, 'cart', 45, DATE_SUB(NOW(), INTERVAL 5 DAY) UNION ALL
  SELECT '16666666669', 4, 'favorite', 35, DATE_SUB(NOW(), INTERVAL 4 DAY) UNION ALL
  SELECT '16666666669', 7, 'purchase', 30, DATE_SUB(NOW(), INTERVAL 2 DAY)
) b ON b.phone = u.phone
WHERE NOT EXISTS (
  SELECT 1 FROM `user_behavior` ub
  WHERE ub.user_id = u.id
    AND ub.product_id = b.product_id
    AND ub.behavior_type = b.behavior_type
    AND ub.create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)
);

INSERT INTO `user_behavior` (`user_id`, `behavior_type`, `search_keyword`, `create_time`)
SELECT u.id, 'search', s.keyword, s.create_time
FROM `user` u
JOIN (
  SELECT '16666666666' AS phone, '旗舰手机' AS keyword, DATE_SUB(NOW(), INTERVAL 5 DAY) AS create_time UNION ALL
  SELECT '16666666666', '轻薄笔记本', DATE_SUB(NOW(), INTERVAL 4 DAY) UNION ALL
  SELECT '16666666667', '降噪耳机', DATE_SUB(NOW(), INTERVAL 3 DAY) UNION ALL
  SELECT '16666666668', '苹果手机', DATE_SUB(NOW(), INTERVAL 3 DAY) UNION ALL
  SELECT '16666666669', '办公数码', DATE_SUB(NOW(), INTERVAL 2 DAY)
) s ON s.phone = u.phone
WHERE NOT EXISTS (
  SELECT 1 FROM `user_behavior` ub
  WHERE ub.user_id = u.id
    AND ub.behavior_type = 'search'
    AND ub.search_keyword = s.keyword
);

INSERT INTO `analytics_recommendation_exposure`
(`exposure_token`, `request_token`, `user_id`, `product_id`, `scene`, `rank_no`, `algorithm`, `source_type`, `reason_type`, `model_version`, `segment_code`, `segment_name`, `exposure_time`, `click_time`, `cart_time`, `purchase_time`, `create_time`)
SELECT e.exposure_token, e.request_token, u.id, e.product_id, e.scene, e.rank_no, e.algorithm, e.source_type, e.reason_type, e.model_version, e.segment_code, e.segment_name, e.exposure_time, e.click_time, e.cart_time, e.purchase_time, NOW()
FROM `user` u
JOIN (
  SELECT '16666666666' AS phone, 'demo_cf_166_04' AS exposure_token, 'demo_req_166_cf' AS request_token, 4 AS product_id, 'collaborative_filtering' AS scene, 1 AS rank_no, 'cf' AS algorithm, 'snapshot' AS source_type, 'SIMILAR_USERS' AS reason_type, 'competition-cf-v1' AS model_version, 'S1' AS segment_code, '高价值数码人群' AS segment_name, DATE_SUB(NOW(), INTERVAL 2 DAY) AS exposure_time, DATE_SUB(NOW(), INTERVAL 2 DAY) AS click_time, DATE_SUB(NOW(), INTERVAL 2 DAY) AS cart_time, DATE_SUB(NOW(), INTERVAL 1 DAY) AS purchase_time UNION ALL
  SELECT '16666666666', 'demo_cf_166_06', 'demo_req_166_cf', 6, 'collaborative_filtering', 2, 'cf', 'snapshot', 'SIMILAR_USERS', 'competition-cf-v1', 'S1', '高价值数码人群', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL UNION ALL
  SELECT '16666666666', 'demo_hot_166_08', 'demo_req_166_hot', 8, 'hot', 3, 'hot', 'live', 'HOT_TREND', 'competition-hot-v1', 'S1', '高价值数码人群', DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL, NULL
) e ON e.phone = u.phone
ON DUPLICATE KEY UPDATE
  `click_time` = VALUES(`click_time`),
  `cart_time` = VALUES(`cart_time`),
  `purchase_time` = VALUES(`purchase_time`),
  `model_version` = VALUES(`model_version`);

INSERT INTO `recommendation_event` (`user_id`, `product_id`, `event_type`, `scene`, `recommendation_token`, `amount`, `event_time`, `metadata`)
SELECT u.id, ev.product_id, ev.event_type, ev.scene, ev.recommendation_token, ev.amount, ev.event_time, ev.metadata
FROM `user` u
JOIN (
  SELECT '16666666666' AS phone, 4 AS product_id, 'exposure' AS event_type, 'collaborative_filtering' AS scene, 'demo_cf_166_04' AS recommendation_token, NULL AS amount, DATE_SUB(NOW(), INTERVAL 2 DAY) AS event_time, '{"demo":true,"source":"similar_users"}' AS metadata UNION ALL
  SELECT '16666666666', 4, 'click', 'collaborative_filtering', 'demo_cf_166_04', NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), '{"demo":true}' UNION ALL
  SELECT '16666666666', 4, 'add_cart', 'collaborative_filtering', 'demo_cf_166_04', NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), '{"demo":true}' UNION ALL
  SELECT '16666666666', 4, 'order', 'collaborative_filtering', 'demo_cf_166_04', 6999.00, DATE_SUB(NOW(), INTERVAL 1 DAY), '{"demo":true,"attribution":"paid"}' UNION ALL
  SELECT '16666666666', 6, 'exposure', 'collaborative_filtering', 'demo_cf_166_06', NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), '{"demo":true,"source":"similar_users"}' UNION ALL
  SELECT '16666666666', 6, 'click', 'collaborative_filtering', 'demo_cf_166_06', NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), '{"demo":true}' UNION ALL
  SELECT '16666666666', 8, 'exposure', 'hot', 'demo_hot_166_08', NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), '{"demo":true,"source":"hot"}'
) ev ON ev.phone = u.phone
WHERE NOT EXISTS (
  SELECT 1 FROM `recommendation_event` old
  WHERE old.user_id = u.id
    AND old.product_id = ev.product_id
    AND old.event_type = ev.event_type
    AND old.recommendation_token = ev.recommendation_token
);


-- ============================================
-- 河南特色商品扩容数据
-- 说明：只追加图片能对应的商品；没有合适公开图的品类不硬凑。
-- ============================================
DROP TEMPORARY TABLE IF EXISTS `tmp_henan_extra_product_seed`;
CREATE TEMPORARY TABLE `tmp_henan_extra_product_seed` (
  `seq` INT NOT NULL PRIMARY KEY,
  `name` VARCHAR(200) NOT NULL,
  `description` VARCHAR(700) NOT NULL,
  `price` DECIMAL(10,2) NOT NULL,
  `original_price` DECIMAL(10,2) NOT NULL,
  `category_id` BIGINT NOT NULL,
  `image` VARCHAR(500) NOT NULL,
  `tag1` VARCHAR(40) NOT NULL,
  `tag2` VARCHAR(40) NOT NULL,
  `tag3` VARCHAR(40) NOT NULL
) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4;

INSERT INTO `tmp_henan_extra_product_seed`
(`seq`, `name`, `description`, `price`, `original_price`, `category_id`, `image`, `tag1`, `tag2`, `tag3`) VALUES
(1, '开封灌汤包礼盒', '开封风味灌汤包，皮薄汤足，适合家庭早餐和地方美食搭配。', 49.90, 69.90, 6, 'https://commons.wikimedia.org/wiki/Special:FilePath/Steamed_soup_buns_of_Kaifeng.jpg?width=960', '开封', '灌汤包', '早餐'),
(2, '河南水煎包早餐装', '底部焦香、内馅饱满，适合早餐专区和即时复购场景。', 35.90, 49.90, 6, 'https://commons.wikimedia.org/wiki/Special:FilePath/Water-baked_steamed_bun.jpg?width=960', '河南', '水煎包', '早餐'),
(3, '中原芝麻烧饼', '芝麻香明显，外酥内软，适合搭配胡辣汤展示河南早餐链路。', 29.90, 39.90, 6, 'https://commons.wikimedia.org/wiki/Special:FilePath/The_Changzhou_Sesame_cake.jpg?width=960', '中原', '烧饼', '芝麻'),
(4, '洛阳牛肉汤面', '牛肉汤底浓郁，面条顺滑，适合热食推荐和夜宵场景。', 42.90, 56.90, 6, 'https://commons.wikimedia.org/wiki/Special:FilePath/Beef_Belly_with_Noodles_Soup_-_Noodles_Soup_2023-10-24.jpg?width=960', '洛阳', '牛肉汤', '面食'),
(5, '鸡汤烩面速食装', '鸡汤风味清鲜，面体筋道，适合宿舍、办公和家庭快手餐。', 39.90, 52.90, 6, 'https://commons.wikimedia.org/wiki/Special:FilePath/Chicken_with_Noodles_Soup_-_Noodles_Soup_2023-10-24.jpg?width=960', '河南', '鸡汤', '烩面'),
(6, '许昌腐竹卷', '腐竹卷豆香浓，泡发快，适合凉拌、火锅和家常菜。', 33.90, 45.90, 6, 'https://commons.wikimedia.org/wiki/Special:FilePath/Tofu_skin_rolls.jpg?width=960', '许昌', '腐竹', '豆制品'),
(7, '许昌腐竹段家庭装', '腐竹段筋道耐煮，家庭装分量足，适合囤货。', 48.90, 62.90, 6, 'https://commons.wikimedia.org/wiki/Special:FilePath/Yonghui_black_tofu_skin_sticks.jpg?width=960', '许昌', '腐竹', '干货'),
(8, '芝麻酱拌面组合', '芝麻酱香气厚实，搭配面食和凉菜都合适，适合厨房复购。', 36.90, 49.90, 6, 'https://commons.wikimedia.org/wiki/Special:FilePath/Sesame_paste.jpg?width=960', '芝麻', '拌面', '厨房'),
(9, '开封菊花茶罐装', '菊花茶清香柔和，适合办公室冲泡和轻养生场景。', 45.00, 59.00, 6, 'https://commons.wikimedia.org/wiki/Special:FilePath/Chrysanthemum_tea_(20240131).jpg?width=960', '开封', '菊花茶', '冲饮'),
(10, '灵宝柿饼袋装', '柿饼软糯香甜，独立分装，适合茶点和长辈礼赠。', 52.00, 69.00, 6, 'https://commons.wikimedia.org/wiki/Special:FilePath/Dried_persimmon_cakes.jpg?width=960', '灵宝', '柿饼', '茶点'),
(11, '好柿成双礼盒', '柿饼礼盒寓意好，甜度自然，适合年节伴手礼。', 68.00, 89.00, 6, 'https://commons.wikimedia.org/wiki/Special:FilePath/Good_fortune_persimmon_cake.jpg?width=960', '灵宝', '柿饼', '礼盒'),
(12, '中牟大蒜家庭装', '蒜头饱满，辛香明显，适合家庭厨房常备和生鲜专区。', 26.90, 36.90, 6, 'https://commons.wikimedia.org/wiki/Special:FilePath/Garlic_Bulbs.jpg?width=960', '中牟', '大蒜', '生鲜'),
(13, '蒜香辣椒油', '辣椒油香辣鲜明，适合拌面、蘸料和家常调味。', 29.90, 39.90, 6, 'https://commons.wikimedia.org/wiki/Special:FilePath/Chinese_homemade_chili_oil_made_with_cooking_oil_and_dried_chili_peppers.jpg?width=960', '蒜香', '辣椒油', '调味'),
(14, '倒蒸红薯干', '红薯干软糯有嚼劲，甜度自然，适合休闲零食。', 31.90, 43.90, 6, 'https://commons.wikimedia.org/wiki/Special:FilePath/Steamed_dried_sweet_potato.jpg?width=960', '红薯干', '软糯', '零食'),
(15, '红薯干分享装', '红薯香气足，分量适中，适合办公室分享和家庭囤货。', 34.90, 46.90, 6, 'https://commons.wikimedia.org/wiki/Special:FilePath/Dried_sweet_potatoes.jpg?width=960', '红薯', '分享装', '零食'),
(16, '红薯粉丝干货', '红薯粉丝耐煮顺滑，适合火锅、炖菜和凉拌。', 37.90, 49.90, 6, 'https://commons.wikimedia.org/wiki/Special:FilePath/Nouilles_patates_douces_Wencheng.jpg?width=960', '粉丝', '红薯', '干货'),
(17, '信阳毛尖散茶', '绿茶叶形清晰，茶汤鲜爽，适合日常口粮茶。', 128.00, 168.00, 6, 'https://commons.wikimedia.org/wiki/Special:FilePath/Laoshan_green_tea.jpg?width=960', '信阳', '绿茶', '散茶'),
(18, '芝麻酱月饼', '芝麻酱馅香气浓郁，适合节令礼盒和茶点搭配。', 59.00, 79.00, 6, 'https://commons.wikimedia.org/wiki/Special:FilePath/Sesame_paste_mooncakes.jpg?width=960', '芝麻酱', '月饼', '节令'),
(19, '唐三彩骆驼摆件', '唐三彩骆驼造型饱满，色彩鲜明，适合洛阳文创陈列。', 238.00, 328.00, 10, 'https://commons.wikimedia.org/wiki/Special:FilePath/Bactrian_camel%2C_Shaanxi_or_Henan_province%2C_China%2C_Tang_dynasty%2C_675-750_AD%2C_earthenware_with_sancai_glaze_-_Portland_Art_Museum_-_Portland%2C_Oregon_-_DSC08428.jpg?width=960', '洛阳', '唐三彩', '骆驼'),
(20, '唐三彩仕女摆件', '唐风人物摆件，色彩柔和，适合历史文化主题陈列。', 198.00, 268.00, 10, 'https://commons.wikimedia.org/wiki/Special:FilePath/Caparisoned_horse%2C_Shaanxi_or_Henan_province%2C_China%2C_Tang_dynasty%2C_675-750_AD%2C_earthenware_with_sancai_glaze_-_Portland_Art_Museum_-_Portland%2C_Oregon_-_DSC08396.jpg?width=960', '唐风', '三彩', '摆件'),
(21, '洛阳唐三彩马', '唐三彩马线条明快，适合文旅伴手礼和桌面摆设。', 168.00, 228.00, 10, 'https://commons.wikimedia.org/wiki/Special:FilePath/Tang_sancai_horse%2C_Xi%27an.JPG?width=960', '洛阳', '唐三彩', '马'),
(22, '汝瓷开片茶杯', '汝瓷茶杯釉色温润，适合茶席、礼盒和家居陈列。', 139.00, 188.00, 10, 'https://commons.wikimedia.org/wiki/Special:FilePath/Ru_Ware_%28cropped%29.JPG?width=960', '汝州', '汝瓷', '茶杯'),
(23, '钧瓷窑变杯', '钧瓷窑变色彩自然，每件纹理不同，适合非遗文创展示。', 159.00, 218.00, 10, 'https://commons.wikimedia.org/wiki/Special:FilePath/Jin_Jun_Ware_Cup.jpg?width=960', '禹州', '钧瓷', '杯'),
(24, '钧瓷洗摆件', '窑变器物层次丰富，适合桌面陈设和礼赠。', 328.00, 468.00, 10, 'https://commons.wikimedia.org/wiki/Special:FilePath/Northern_Song_Jun_Ware_Washer.jpg?width=960', '钧瓷', '窑变', '摆件'),
(25, '青瓷花器', '青瓷花器器形简洁，适合新中式家居陈列。', 188.00, 258.00, 10, 'https://commons.wikimedia.org/wiki/Special:FilePath/Qing_ceramics_Jim_Thompson_Museum_IMG_7038.jpg?width=960', '青瓷', '花器', '家居'),
(26, '南阳玉佩', '玉佩造型温润，适合饰品礼赠和文化商品展示。', 188.00, 268.00, 4, 'https://commons.wikimedia.org/wiki/Special:FilePath/Ming_Jade_Pendant.jpg?width=960', '南阳', '玉佩', '饰品'),
(27, '南阳玉叶挂件', '玉叶造型细腻，寓意丰盈，适合礼品和随身饰品。', 218.00, 298.00, 4, 'https://commons.wikimedia.org/wiki/Special:FilePath/Chinese_Family_Heirloom_Jade_Pendant_with_Leaf_and_Grape_Design.jpg?width=960', '南阳', '玉雕', '挂件'),
(28, '玉龙纹挂件', '龙纹玉挂件造型古雅，适合文创收藏和礼赠。', 238.00, 328.00, 4, 'https://commons.wikimedia.org/wiki/Special:FilePath/Jade_pendant_in_the_shape_of_a_dragon_from_the_tomb_of_the_King_of_Chu_Shizi_Mountain_Xuzhou_Jiangsu_China_Western_Han_period_2nd_century_BCE_%2835413108214%29.jpg?width=960', '玉雕', '龙纹', '挂件'),
(29, '文房砚台', '中式砚台质感沉稳，适合书房陈设和文创礼盒。', 128.00, 188.00, 7, 'https://commons.wikimedia.org/wiki/Special:FilePath/Inkstone.jpg?width=960', '文房', '砚台', '书房'),
(30, '书法毛笔套装', '毛笔套装适合练字、书房和文化礼品场景。', 69.00, 99.00, 7, 'https://commons.wikimedia.org/wiki/Special:FilePath/Chinese_Brush_for_Writing_Calligraphy.jpg?width=960', '书法', '毛笔', '文房'),
(31, '龙纹毛笔礼盒', '龙纹毛笔细节精致，适合书法爱好者和文创礼赠。', 99.00, 139.00, 7, 'https://commons.wikimedia.org/wiki/Special:FilePath/CMOC_Treasures_of_Ancient_China_exhibit_-_calligraphy_brush_with_dragon_design.jpg?width=960', '文创', '毛笔', '礼盒'),
(32, '中式折扇画扇', '折扇带书画元素，轻便雅致，适合少林、洛阳文旅伴手礼。', 49.00, 69.00, 7, 'https://commons.wikimedia.org/wiki/Special:FilePath/Chinese_-_Folding_Fan_with_Painting_and_Calligraphy_-_138-2011.1_-_Saint_Louis_Art_Museum.jpg?width=960', '折扇', '书画', '文旅'),
(33, '汴梁纸灯笼', '纸灯笼红色醒目，适合节庆陈列和民俗文创专区。', 39.00, 59.00, 7, 'https://commons.wikimedia.org/wiki/Special:FilePath/Chinese_Lantern_(made_with_paper).jpg?width=960', '汴梁', '灯笼', '民俗'),
(34, '节庆灯笼挂饰', '灯笼挂饰适合春节、庙会和门店氛围布置。', 29.00, 45.00, 7, 'https://commons.wikimedia.org/wiki/Special:FilePath/Chinese_lantern_(48906).jpg?width=960', '节庆', '灯笼', '挂饰'),
(35, '朱仙镇门神年画', '门神年画色彩饱满，民俗识别度高，适合非遗展示。', 58.00, 78.00, 7, 'https://commons.wikimedia.org/wiki/Special:FilePath/MET_DP302934.jpg?width=960', '朱仙镇', '门神', '年画'),
(36, '汴梁年画长卷', '年画长卷图案丰富，适合陈列、收藏和节庆礼品。', 88.00, 128.00, 7, 'https://commons.wikimedia.org/wiki/Special:FilePath/One_hundred_thirty-five_woodblock_prints_including_New_Year%27s_pictures_%28nianhua%29%2C_door_gods%2C_historical_figures_and_Taoist_deities_MET_DP-17327-122.jpg?width=960', '汴梁', '年画', '非遗'),
(37, '牡丹花器摆件', '牡丹主题花器，视觉柔和，适合洛阳牡丹文化陈列。', 128.00, 168.00, 10, 'https://commons.wikimedia.org/wiki/Special:FilePath/Luoyang_Peony_Museum.jpg?width=960', '洛阳', '牡丹', '摆件'),
(38, '牡丹图案丝巾', '牡丹图案柔和雅致，适合女性礼品和文旅纪念。', 109.00, 149.00, 4, 'https://commons.wikimedia.org/wiki/Special:FilePath/Bairei_kach%C5%8D_gafu%2C_Spring_13%2C_Chinese_peony_and_cranes.jpg?width=960', '牡丹', '丝巾', '礼品'),
(39, '青瓷小罐', '青瓷小罐线条简洁，适合茶席收纳和桌面陈设。', 98.00, 138.00, 10, 'https://commons.wikimedia.org/wiki/Special:FilePath/Han_Celadon_Pot.jpg?width=960', '青瓷', '小罐', '茶席'),
(40, '少林禅意茶席', '茶席组合风格克制，适合禅茶主题和礼品专区。', 188.00, 258.00, 10, 'https://commons.wikimedia.org/wiki/Special:FilePath/Chinese_tea_set_celadon.jpg?width=960', '少林', '禅茶', '茶席'),
(41, '传统竹编篮', '竹编篮自然朴素，适合农产品礼盒和家居收纳。', 59.00, 79.00, 10, 'https://commons.wikimedia.org/wiki/Special:FilePath/Bamboo_basket.jpg?width=960', '竹编', '收纳', '农礼'),
(42, '中式木梳', '木梳纹理清晰，适合日用小礼品和文创货架。', 29.90, 39.90, 4, 'https://commons.wikimedia.org/wiki/Special:FilePath/Wooden_comb.jpg?width=960', '木梳', '日用', '文创'),
(43, '中式筷子礼盒', '筷子礼盒简洁实用，适合餐厨用品和伴手礼。', 45.00, 68.00, 10, 'https://commons.wikimedia.org/wiki/Special:FilePath/Chinese_chopsticks.jpg?width=960', '筷子', '餐厨', '礼盒'),
(44, '铜香炉摆件', '香炉造型沉稳，适合茶室、书房和新中式家居。', 168.00, 238.00, 10, 'https://commons.wikimedia.org/wiki/Special:FilePath/Chinese_incense_burner.jpg?width=960', '香炉', '书房', '摆件'),
(45, '刺绣香囊', '香囊图案精致，适合节庆礼品和非遗小件展示。', 35.00, 49.00, 4, 'https://commons.wikimedia.org/wiki/Special:FilePath/Chinese_sachet.jpg?width=960', '香囊', '刺绣', '非遗'),
(46, '中式拨浪鼓', '拨浪鼓小巧有趣，适合民俗玩具和亲子礼品。', 26.00, 36.00, 9, 'https://commons.wikimedia.org/wiki/Special:FilePath/Chinese_hand_drum.jpg?width=960', '民俗', '拨浪鼓', '玩具'),
(47, '太极练习折扇', '太极主题折扇轻便耐用，适合陈家沟太极文创。', 39.00, 59.00, 8, 'https://commons.wikimedia.org/wiki/Special:FilePath/Folding_fan_with_figures_in_garden_setting_%28recto%29_%28CBL_C_3066%2C_front%29.jpg?width=960', '太极', '折扇', '练习'),
(48, '太极纪念短袖', '太极主题短袖适合运动、文旅和赛事纪念场景。', 79.00, 109.00, 4, 'https://commons.wikimedia.org/wiki/Special:FilePath/TaiChiMrTan-2018-2-DairyFarmEstate-Singapore.jpg?width=960', '太极', '短袖', '运动');

INSERT INTO `product`
(`name`, `description`, `price`, `original_price`, `category_id`, `merchant_id`, `image`, `images`, `tags`, `stock`, `sales_count`, `rating`, `status`, `deleted`)
SELECT
  e.name,
  e.description,
  e.price,
  e.original_price,
  e.category_id,
  2,
  e.image,
  JSON_ARRAY(e.image),
  JSON_ARRAY('河南特色', e.tag1, e.tag2, e.tag3),
  260 + MOD(e.seq * 41, 700),
  1200 + MOD(e.seq * 317, 9600),
  CAST(4.6 + (MOD(e.seq, 4) * 0.1) AS DECIMAL(2,1)),
  1,
  0
FROM `tmp_henan_extra_product_seed` e
WHERE NOT EXISTS (
  SELECT 1 FROM `product` p WHERE p.name = e.name AND p.deleted = 0
);

INSERT INTO `product_sku`
(`product_id`, `sku_code`, `sku_name`, `price`, `original_price`, `stock`, `sales_count`, `image`, `spec_values`, `status`)
SELECT p.id, CONCAT('HN-', p.id, '-STD'), '标准装', p.price, p.original_price, GREATEST(30, FLOOR(p.stock / 3)), p.sales_count, p.image, JSON_OBJECT('规格', '标准装'), 1
FROM `product` p
JOIN `tmp_henan_extra_product_seed` e ON e.name = p.name
WHERE NOT EXISTS (SELECT 1 FROM `product_sku` s WHERE s.product_id = p.id);

INSERT INTO `product_spec_name` (`product_id`, `spec_name`, `sort_order`)
SELECT p.id, '规格', 1
FROM `product` p
JOIN `tmp_henan_extra_product_seed` e ON e.name = p.name
WHERE NOT EXISTS (SELECT 1 FROM `product_spec_name` n WHERE n.product_id = p.id AND n.spec_name = '规格');

INSERT INTO `product_spec_value` (`spec_name_id`, `spec_value`, `image`, `sort_order`)
SELECT n.id, '标准装', NULL, 1
FROM `product_spec_name` n
JOIN `product` p ON p.id = n.product_id
JOIN `tmp_henan_extra_product_seed` e ON e.name = p.name
WHERE NOT EXISTS (SELECT 1 FROM `product_spec_value` v WHERE v.spec_name_id = n.id AND v.spec_value = '标准装');

DELETE rv
FROM `product_review_vote` rv
JOIN `product_review` pr ON pr.id = rv.review_id
JOIN `product` p ON p.id = pr.product_id
WHERE JSON_CONTAINS(p.tags, JSON_QUOTE('河南特色'));

DELETE pr
FROM `product_review` pr
JOIN `product` p ON p.id = pr.product_id
WHERE JSON_CONTAINS(p.tags, JSON_QUOTE('河南特色'));

INSERT INTO `product_review`
(`user_id`, `product_id`, `order_id`, `rating`, `content`, `images`, `video_urls`, `tags`, `append_content`, `append_time`, `helpful_count`, `reply`, `reply_time`, `status`, `create_time`)
SELECT
  4 + MOD(p.id + r.seq, 5) AS user_id,
  p.id AS product_id,
  1 + MOD(p.id + r.seq, 30) AS order_id,
  r.rating,
  REPLACE(REPLACE(REPLACE(r.content_tpl, '{product}', p.name), '{tag1}', JSON_UNQUOTE(JSON_EXTRACT(p.tags, '$[1]'))), '{tag2}', JSON_UNQUOTE(JSON_EXTRACT(p.tags, '$[2]'))) AS content,
  CASE WHEN r.seq IN (1, 4) THEN JSON_ARRAY(p.image) ELSE NULL END AS images,
  NULL AS video_urls,
  JSON_ARRAY(r.tag_a, r.tag_b, r.tag_c) AS tags,
  CASE WHEN r.seq IN (2, 6) THEN REPLACE(r.append_tpl, '{product}', p.name) ELSE NULL END AS append_content,
  CASE WHEN r.seq IN (2, 6) THEN DATE_ADD('2026-04-12 10:00:00', INTERVAL (p.id + r.seq) DAY) ELSE NULL END AS append_time,
  10 + MOD(p.sales_count + r.seq * 13, 72) AS helpful_count,
  CASE
    WHEN r.rating <= 3 THEN '收到反馈，我们会优化包装和发货检查。'
    WHEN r.seq IN (1, 5) THEN '感谢认可，已同步给运营和仓配。'
    ELSE NULL
  END AS reply,
  CASE WHEN r.rating <= 3 OR r.seq IN (1, 5) THEN DATE_ADD('2026-04-12 18:00:00', INTERVAL (p.id + r.seq) DAY) ELSE NULL END AS reply_time,
  1 AS status,
  DATE_ADD('2026-04-08 09:00:00', INTERVAL (p.id + r.seq) DAY) AS create_time
FROM `product` p
JOIN (
  SELECT 1 AS seq, 5 AS rating, '买了{product}，图片和实物一致，{tag1}特色清楚，包装也干净。' AS content_tpl, '实物一致' AS tag_a, '包装完整' AS tag_b, '特色明显' AS tag_c, '追评：家里人反馈不错，准备回购。' AS append_tpl
  UNION ALL SELECT 2, 5, '{product}比预期好，{tag2}卖点突出，发货快，收到没有破损。', '物流快', '卖点明确', '会回购', '追评：第二次使用体验依旧稳定。'
  UNION ALL SELECT 3, 4, '整体满意，{product}的质感和价格匹配，详情图也比较直观。', '质价匹配', '详情清楚', '体验稳定', '追评：细节越看越耐看。'
  UNION ALL SELECT 4, 5, '{product}很适合河南特产专区，识别度高，不像通用货。', '地域特色', '识别度高', '非通用货', '追评：摆在首页很有记忆点。'
  UNION ALL SELECT 5, 4, '给长辈买的{product}，口味或做工都比较稳，没有夸张宣传。', '长辈喜欢', '宣传克制', '实用', '追评：长辈说下次还买。'
  UNION ALL SELECT 6, 3, '{product}能用，但外包装边角有一点压痕，希望仓库加强防护。', '包装压痕', '可改进', '售后及时', '追评：客服处理得比较快。'
  UNION ALL SELECT 7, 5, '对比几款后选了{product}，主要看中{tag1}和{tag2}，没有踩雷。', '没有踩雷', '选择明确', '体验稳定', '追评：放进礼盒组合很合适。'
  UNION ALL SELECT 8, 4, '{product}适合做活动主推，价格不虚高，图片辨识度也够。', '主推合适', '图片清楚', '价格合理', '追评：活动价会更有吸引力。'
) r
WHERE JSON_CONTAINS(p.tags, JSON_QUOTE('河南特色'));

DROP TEMPORARY TABLE IF EXISTS `tmp_henan_extra_product_seed`;
CREATE TEMPORARY TABLE `tmp_henan_product_seed` (
  `seq` INT NOT NULL PRIMARY KEY,
  `name` VARCHAR(200) NOT NULL,
  `description` VARCHAR(700) NOT NULL,
  `price` DECIMAL(10,2) NOT NULL,
  `original_price` DECIMAL(10,2) NOT NULL,
  `category_id` BIGINT NOT NULL,
  `image` VARCHAR(700) NOT NULL,
  `tag1` VARCHAR(40) NOT NULL,
  `tag2` VARCHAR(40) NOT NULL,
  `tag3` VARCHAR(40) NOT NULL
) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4;

INSERT INTO `tmp_henan_product_seed`
(`seq`, `name`, `description`, `price`, `original_price`, `category_id`, `image`, `tag1`, `tag2`, `tag3`) VALUES
(1, '郑州滋补烩面礼盒', '河南经典烩面礼盒，宽面筋道，汤底浓郁，适合家庭速煮和地方美食展示。', 69.00, 89.00, 6, 'https://upload.wikimedia.org/wikipedia/commons/thumb/e/e1/Henan_Huimian_at_Suzhou-20230716.jpg/960px-Henan_Huimian_at_Suzhou-20230716.jpg', '郑州', '烩面', '地方风味'),
(2, '老郑州羊肉烩面料包', '羊肉汤底搭配手工宽面，口味醇厚，复热方便，适合地域特色推荐。', 78.00, 98.00, 6, 'https://upload.wikimedia.org/wikipedia/commons/thumb/c/c7/Henan_braised_noodles.JPG/960px-Henan_braised_noodles.JPG', '老郑州', '羊肉汤', '速食'),
(3, '新乡老秦记烩面', '新乡风味烩面，面条厚实，汤头鲜香，适合高频复购的特色食品场景。', 56.00, 76.00, 6, 'https://upload.wikimedia.org/wikipedia/commons/thumb/a/ae/20230217_Laoqinji_Smelly_Huimian_in_Xinxiang.jpg/960px-20230217_Laoqinji_Smelly_Huimian_in_Xinxiang.jpg', '新乡', '烩面', '热销'),
(4, '逍遥镇胡辣汤家庭装', '胡辣汤家庭装，汤色浓、香辛足，早餐冲泡方便，是河南代表性早餐单品。', 39.90, 49.90, 6, 'https://upload.wikimedia.org/wikipedia/commons/thumb/f/f9/20210708_Henan-style_Hulatang.jpg/960px-20210708_Henan-style_Hulatang.jpg', '周口', '胡辣汤', '早餐'),
(5, '方中山胡辣汤料包', '经典胡辣汤料包，辣香层次明显，适合搭配油馍头、包子和家庭早餐。', 45.00, 59.00, 6, 'https://upload.wikimedia.org/wikipedia/commons/thumb/f/f9/Fang_Zhongshan_Hulatang_20170205.jpg/960px-Fang_Zhongshan_Hulatang_20170205.jpg', '郑州', '胡辣汤', '老字号'),
(6, '牛肉胡辣汤冲调装', '牛肉风味胡辣汤，粉料细腻，冲调顺滑，适合办公早餐和宿舍囤货。', 32.90, 42.90, 6, 'https://upload.wikimedia.org/wikipedia/commons/thumb/7/76/Beef_spicy_soup_powder_-_Hulatang.jpg/960px-Beef_spicy_soup_powder_-_Hulatang.jpg', '牛肉味', '冲调', '便携'),
(7, '道口烧鸡整只装', '滑县道口烧鸡风味，肉质酥软，香料入味，适合熟食礼盒和地方特产展示。', 88.00, 118.00, 6, 'https://upload.wikimedia.org/wikipedia/commons/thumb/0/06/Dezhou_braised_chicken_%2820160511210319%29.jpg/960px-Dezhou_braised_chicken_%2820160511210319%29.jpg', '安阳', '烧鸡', '熟食'),
(8, '开封桶子鸡熟食', '开封风味桶子鸡，咸香紧实，切盘即食，适合作为中原熟食专区商品。', 96.00, 128.00, 6, 'https://upload.wikimedia.org/wikipedia/commons/thumb/b/b1/Three_Cup_Chicken_in_Clay_Pot.jpg/960px-Three_Cup_Chicken_in_Clay_Pot.jpg', '开封', '桶子鸡', '即食'),
(9, '温县铁棍山药', '焦作温县铁棍山药，粉糯细密，适合蒸煮煲汤，兼具农产品与健康食材属性。', 59.00, 79.00, 6, 'https://upload.wikimedia.org/wikipedia/commons/thumb/2/23/Chinese_yam_-_air-potato_-_dioscorea_polystachya_IMG_8131.jpg/960px-Chinese_yam_-_air-potato_-_dioscorea_polystachya_IMG_8131.jpg', '温县', '山药', '农特产'),
(10, '怀山药粉冲饮', '怀山药粉，粉质细腻，冲饮顺滑，适合早餐代餐和长辈礼盒。', 68.00, 88.00, 6, 'https://upload.wikimedia.org/wikipedia/commons/thumb/9/9b/Chinese_yam_-_air-potato_-_dioscorea_polystachya_IMG_8134.jpg/960px-Chinese_yam_-_air-potato_-_dioscorea_polystachya_IMG_8134.jpg', '怀府', '山药粉', '养生'),
(11, '信阳毛尖明前绿茶', '信阳毛尖明前茶，芽叶细嫩，汤色清亮，适合茶礼、复购和高客单推荐场景。', 168.00, 228.00, 6, 'https://upload.wikimedia.org/wikipedia/commons/thumb/0/0e/Xinyang_Maojian.jpg/960px-Xinyang_Maojian.jpg', '信阳', '毛尖', '绿茶'),
(12, '信阳毛尖茶叶礼盒', '信阳毛尖礼盒装，清香鲜爽，包装克制高级，适合商务伴手礼。', 238.00, 328.00, 6, 'https://upload.wikimedia.org/wikipedia/commons/f/f2/Maojian.jpg', '茶礼', '明前茶', '礼盒'),
(13, '南湾湖毛尖茶', '南湾湖茶区毛尖，香气清雅，入口回甘，适合展示地域溯源和内容推荐。', 198.00, 268.00, 6, 'https://upload.wikimedia.org/wikipedia/commons/thumb/8/87/Nanwan_Lake-_Maojian_Tea_Demonstration.jpg/960px-Nanwan_Lake-_Maojian_Tea_Demonstration.jpg', '南湾湖', '毛尖', '产地'),
(14, '开封花生糕', '开封花生糕，酥香细腻，甜度适中，适合旅游伴手礼和零食礼盒。', 36.90, 49.90, 6, 'https://upload.wikimedia.org/wikipedia/commons/thumb/3/3c/Nkati_Cake.jpg/960px-Nkati_Cake.jpg', '开封', '花生糕', '伴手礼'),
(15, '开封芝麻花生酥', '芝麻与花生香气融合，入口酥脆，独立包装便于分享。', 42.90, 56.90, 6, 'https://upload.wikimedia.org/wikipedia/commons/thumb/e/e5/Nkati_Cake_02.jpg/960px-Nkati_Cake_02.jpg', '芝麻', '花生酥', '零食'),
(16, '新郑灰枣礼盒', '新郑灰枣，果肉紧实，甜度自然，适合家庭囤货和年节礼赠。', 49.90, 69.90, 6, 'https://upload.wikimedia.org/wikipedia/commons/thumb/6/6e/Ziziphus_jujuba%2C_Santa_Coloma_de_Farners.jpg/960px-Ziziphus_jujuba%2C_Santa_Coloma_de_Farners.jpg', '新郑', '灰枣', '礼盒'),
(17, '商丘酥梨礼盒', '酥梨果形饱满，汁水充足，适合生鲜专区和地域农产品展示。', 58.00, 78.00, 6, 'https://upload.wikimedia.org/wikipedia/commons/7/76/Asian_Pear_.jpg', '商丘', '酥梨', '生鲜'),
(18, '信阳板栗仁', '板栗仁香甜软糯，开袋即食，适合零食、茶点和长辈休闲食品。', 46.00, 59.00, 6, 'https://upload.wikimedia.org/wikipedia/commons/5/53/Roasting_Chestnuts_in_Tianjin_China.JPG', '信阳', '板栗', '即食'),
(19, '小磨香油礼盒', '传统小磨香油，芝麻香浓，适合凉拌、蘸料和家庭厨房礼盒。', 79.00, 99.00, 6, 'https://upload.wikimedia.org/wikipedia/commons/thumb/a/ae/Sesame_oil_with_matsutake_mushrooms.jpg/960px-Sesame_oil_with_matsutake_mushrooms.jpg', '小磨', '香油', '厨房'),
(20, '禹州红薯粉条', '禹州红薯粉条，久煮不易断，适合炖菜、火锅和家庭常备。', 34.90, 45.90, 6, 'https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Vietnam%2C_Noodle_soup_set%2C_Chinese_style.jpg/960px-Vietnam%2C_Noodle_soup_set%2C_Chinese_style.jpg', '禹州', '粉条', '干货'),
(21, '许昌腐竹干货', '腐竹豆香浓郁，泡发后筋道，适合凉拌、火锅和家常炒菜。', 38.90, 52.00, 6, 'https://upload.wikimedia.org/wikipedia/commons/0/0d/Tofu_skin_%28Yuba%29.JPG', '许昌', '腐竹', '干货'),
(22, '汝瓷天青茶杯', '汝瓷天青釉色温润，器形克制，适合作为中原瓷器文创商品。', 129.00, 168.00, 10, 'https://upload.wikimedia.org/wikipedia/commons/thumb/a/a9/Ru_Ware_%28cropped%29.JPG/960px-Ru_Ware_%28cropped%29.JPG', '汝州', '汝瓷', '茶器'),
(23, '汝瓷水仙盆摆件', '汝瓷水仙盆造型雅致，釉面温润，适合家居陈设和礼品场景。', 268.00, 358.00, 10, 'https://upload.wikimedia.org/wikipedia/commons/thumb/8/8b/%E9%9D%92%E7%A3%81_%E6%B0%B4%E4%BB%99%E7%9B%86_NARCISSUS_BASIN%2C_celadon.jpg/960px-%E9%9D%92%E7%A3%81_%E6%B0%B4%E4%BB%99%E7%9B%86_NARCISSUS_BASIN%2C_celadon.jpg', '汝瓷', '水仙盆', '摆件'),
(24, '禹州钧瓷茶盏', '钧瓷窑变茶盏，釉色自然流动，每只纹理不同，适合非遗文创展示。', 158.00, 218.00, 10, 'https://upload.wikimedia.org/wikipedia/commons/thumb/4/46/Jin_Jun_Ware_Cup.jpg/960px-Jin_Jun_Ware_Cup.jpg', '禹州', '钧瓷', '茶盏'),
(25, '钧瓷窑变洗', '钧瓷窑变器物，色彩层次丰富，适合家居陈列和高端礼赠。', 328.00, 468.00, 10, 'https://upload.wikimedia.org/wikipedia/commons/thumb/3/39/Northern_Song_Jun_Ware_Washer.jpg/960px-Northern_Song_Jun_Ware_Washer.jpg', '钧瓷', '窑变', '陈设'),
(26, '洛阳唐三彩马摆件', '唐三彩马造型生动，色彩明快，适合洛阳文创和历史文化展示。', 188.00, 268.00, 10, 'https://upload.wikimedia.org/wikipedia/commons/7/7b/Sancai_Glaze_Horse%2C_Tang_Dynasty.jpg', '洛阳', '唐三彩', '文创'),
(27, '洛阳牡丹花茶', '牡丹花茶香气清雅，花形舒展，适合轻养生和女性用户推荐场景。', 58.00, 78.00, 6, 'https://upload.wikimedia.org/wikipedia/commons/thumb/4/4c/Luoyang_Peony_Museum.jpg/960px-Luoyang_Peony_Museum.jpg', '洛阳', '牡丹', '花茶'),
(28, '洛阳牡丹丝巾', '牡丹图案丝巾，色彩柔和，适合文旅伴手礼和礼品专区。', 99.00, 139.00, 4, 'https://upload.wikimedia.org/wikipedia/commons/9/9c/Stamp_of_Moldova_-_2017_-_Colnect_705998_-_Chinese_Peony_Paeonia_lactiflora.jpeg', '牡丹', '丝巾', '文旅'),
(29, '南阳玉雕平安扣', '南阳玉雕平安扣，造型圆润，寓意平安，适合饰品与礼赠场景。', 168.00, 238.00, 4, 'https://upload.wikimedia.org/wikipedia/commons/thumb/9/9a/Qing_Jade_Deer.jpg/960px-Qing_Jade_Deer.jpg', '南阳', '玉雕', '平安扣'),
(30, '南阳玉雕生肖摆件', '玉雕小摆件，线条圆润，适合桌面陈设和中式礼品推荐。', 238.00, 328.00, 10, 'https://upload.wikimedia.org/wikipedia/commons/thumb/0/0e/Jade_carving_of_a_pig_Han_Dynasty_%28206_BCE_-_220_CE%29_Shaanxi_Province_Lantian_County_China.jpg/960px-Jade_carving_of_a_pig_Han_Dynasty_%28206_BCE_-_220_CE%29_Shaanxi_Province_Lantian_County_China.jpg', '玉雕', '生肖', '摆件'),
(31, '朱仙镇木版年画', '木版年画色彩鲜明，民俗气息浓，适合非遗文创和节庆礼品展示。', 48.00, 68.00, 7, 'https://upload.wikimedia.org/wikipedia/commons/7/74/One_hundred_thirty-five_woodblock_prints_including_New_Year%27s_pictures_%28nianhua%29%2C_door_gods%2C_historical_figures_and_Taoist_deities_MET_DP-17327-122.jpg', '朱仙镇', '木版年画', '非遗'),
(32, '少林功夫纪念折扇', '中式折扇文创，轻巧便携，适合少林功夫主题伴手礼。', 39.00, 59.00, 7, 'https://upload.wikimedia.org/wikipedia/commons/2/22/Folding_fan_with_figures_in_garden_setting_%28recto%29_%28CBL_C_3066%2C_front%29.jpg', '少林', '折扇', '文创');

UPDATE `product` p
JOIN `tmp_henan_product_seed` h ON h.seq = ((p.id - 1) % 32) + 1
SET
  p.name = CONCAT(h.name, CASE
    WHEN p.id BETWEEN 33 AND 64 THEN ' · 精选装'
    WHEN p.id BETWEEN 65 AND 96 THEN ' · 礼盒装'
    ELSE ''
  END),
  p.description = h.description,
  p.price = h.price + (FLOOR((p.id - 1) / 32) * 6),
  p.original_price = h.original_price + (FLOOR((p.id - 1) / 32) * 8),
  p.category_id = h.category_id,
  p.image = h.image,
  p.images = JSON_ARRAY(h.image),
  p.tags = JSON_ARRAY('河南特色', h.tag1, h.tag2, h.tag3),
  p.stock = 180 + MOD(p.id * 37, 620),
  p.sales_count = 900 + MOD(p.id * 193, 8800),
  p.rating = CAST(4.6 + (MOD(p.id, 4) * 0.1) AS DECIMAL(2,1)),
  p.status = 1,
  p.deleted = 0
WHERE p.id <= 96;

UPDATE `product_sku` s
JOIN `product` p ON p.id = s.product_id
SET
  s.sku_name = CASE MOD(s.id, 3)
    WHEN 0 THEN '标准装'
    WHEN 1 THEN '家庭装'
    ELSE '礼盒装'
  END,
  s.price = p.price,
  s.original_price = p.original_price,
  s.stock = GREATEST(20, FLOOR(p.stock / 3)),
  s.image = p.image,
  s.spec_values = JSON_OBJECT('规格', s.sku_name)
WHERE s.product_id <= 96;

UPDATE `product_spec_name`
SET `spec_name` = '规格'
WHERE `product_id` <= 96;

UPDATE `product_spec_value` v
JOIN `product_spec_name` n ON n.id = v.spec_name_id
SET
  v.spec_value = CASE MOD(v.id, 3)
    WHEN 0 THEN '标准装'
    WHEN 1 THEN '家庭装'
    ELSE '礼盒装'
  END,
  v.image = NULL
WHERE n.product_id <= 96;

UPDATE `order_item` oi
JOIN `product` p ON p.id = oi.product_id
SET oi.product_name = p.name,
    oi.product_image = p.image,
    oi.price = p.price
WHERE oi.product_id <= 96;

DELETE rv
FROM `product_review_vote` rv
JOIN `product_review` pr ON pr.id = rv.review_id
WHERE pr.product_id <= 96;

DELETE FROM `product_review`
WHERE `product_id` <= 96;

INSERT INTO `product_review`
(`user_id`, `product_id`, `order_id`, `rating`, `content`, `images`, `video_urls`, `tags`, `append_content`, `append_time`, `helpful_count`, `reply`, `reply_time`, `status`, `create_time`)
SELECT
  4 + MOD(p.id + r.seq, 5) AS user_id,
  p.id AS product_id,
  1 + MOD(p.id + r.seq, 30) AS order_id,
  r.rating,
  REPLACE(REPLACE(REPLACE(r.content_tpl, '{product}', p.name), '{tag1}', JSON_UNQUOTE(JSON_EXTRACT(p.tags, '$[1]'))), '{tag2}', JSON_UNQUOTE(JSON_EXTRACT(p.tags, '$[2]'))) AS content,
  CASE WHEN r.seq IN (1, 4) THEN JSON_ARRAY(p.image) ELSE NULL END AS images,
  NULL AS video_urls,
  JSON_ARRAY(r.tag_a, r.tag_b, r.tag_c) AS tags,
  CASE WHEN r.seq IN (2, 6) THEN REPLACE(r.append_tpl, '{product}', p.name) ELSE NULL END AS append_content,
  CASE WHEN r.seq IN (2, 6) THEN DATE_ADD('2026-04-05 10:00:00', INTERVAL (p.id + r.seq) DAY) ELSE NULL END AS append_time,
  8 + MOD(p.sales_count + r.seq * 11, 64) AS helpful_count,
  CASE
    WHEN r.rating <= 3 THEN '收到反馈，我们会优化包装和发货检查。'
    WHEN r.seq IN (1, 5) THEN '感谢认可，已把建议同步给运营和仓配。'
    ELSE NULL
  END AS reply,
  CASE WHEN r.rating <= 3 OR r.seq IN (1, 5) THEN DATE_ADD('2026-04-05 18:00:00', INTERVAL (p.id + r.seq) DAY) ELSE NULL END AS reply_time,
  1 AS status,
  DATE_ADD('2026-04-01 09:00:00', INTERVAL (p.id + r.seq) DAY) AS create_time
FROM `product` p
JOIN (
  SELECT 1 AS seq, 5 AS rating, '买了{product}，图片和实物一致，{tag1}特色很明显，包装干净，送人也体面。' AS content_tpl, '实物一致' AS tag_a, '包装完整' AS tag_b, '适合送礼' AS tag_c, '追评：家里人反馈不错，准备回购。' AS append_tpl
  UNION ALL SELECT 2, 5, '{product}比预期好，{tag2}风味突出，发货快，收到没有破损，适合地方特色推荐。', '物流快', '风味正', '会回购', '追评：第二次使用体验依旧稳定。'
  UNION ALL SELECT 3, 4, '整体满意，{product}的质感和价格匹配。小建议是详情页可以再补一张近景图，方便判断细节。', '质价匹配', '细节清楚', '建议补图', '追评：细节越看越耐看。'
  UNION ALL SELECT 4, 5, '{product}很适合做河南特产专区，识别度高，打开包装就能看出不是通用货。', '地域特色', '识别度高', '非通用货', '追评：摆在首页很有记忆点。'
  UNION ALL SELECT 5, 4, '给长辈买的{product}，口味和做工都比较稳，没有夸张宣传，属于实用型好物。', '长辈喜欢', '宣传克制', '实用', '追评：长辈说下次还买。'
  UNION ALL SELECT 6, 3, '{product}能用，但外包装边角有一点压痕，希望仓库后续再加强防护。', '包装压痕', '可改进', '售后及时', '追评：客服处理得比较快，态度可以。'
  UNION ALL SELECT 7, 5, '对比了几款后选了{product}，主要看中{tag1}和{tag2}，实际收到后没有踩雷。', '没有踩雷', '选择明确', '体验稳定', '追评：放进礼盒组合很合适。'
  UNION ALL SELECT 8, 4, '{product}适合做活动主推，价格不虚高，图片辨识度也够，列表里一眼能看出来。', '主推合适', '图片清楚', '价格合理', '追评：活动价会更有吸引力。'
) r
WHERE p.id <= 96;

DROP TEMPORARY TABLE IF EXISTS `tmp_henan_product_seed`;

-- BEGIN PRODUCT IMAGE OSS BACKFILL
-- 商品图片 OSS 全量回填 SQL
-- 生成时间: 2026-05-02 05:53:22
-- 覆盖 product.image / product.images，并同步 SKU、规格值和历史订单冗余图

UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0001.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0001.jpg"]', `update_time` = NOW() WHERE `id` = 1;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0002.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0002.jpg"]', `update_time` = NOW() WHERE `id` = 2;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0003.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0003.jpg"]', `update_time` = NOW() WHERE `id` = 3;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0004.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0004.jpg"]', `update_time` = NOW() WHERE `id` = 4;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0005.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0005.jpg"]', `update_time` = NOW() WHERE `id` = 5;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0006.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0006.jpg"]', `update_time` = NOW() WHERE `id` = 6;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0007.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0007.jpg"]', `update_time` = NOW() WHERE `id` = 7;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0008.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0008.jpg"]', `update_time` = NOW() WHERE `id` = 8;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0009.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0009.jpg"]', `update_time` = NOW() WHERE `id` = 9;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0010.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0010.jpg"]', `update_time` = NOW() WHERE `id` = 10;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0011.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0011.jpg"]', `update_time` = NOW() WHERE `id` = 11;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0012.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0012.jpg"]', `update_time` = NOW() WHERE `id` = 12;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0013.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0013.jpg"]', `update_time` = NOW() WHERE `id` = 13;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0014.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0014.jpg"]', `update_time` = NOW() WHERE `id` = 14;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0015.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0015.jpg"]', `update_time` = NOW() WHERE `id` = 15;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0016.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0016.jpg"]', `update_time` = NOW() WHERE `id` = 16;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0017.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0017.jpg"]', `update_time` = NOW() WHERE `id` = 17;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0018.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0018.jpg"]', `update_time` = NOW() WHERE `id` = 18;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0019.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0019.jpg"]', `update_time` = NOW() WHERE `id` = 19;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0020.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0020.jpg"]', `update_time` = NOW() WHERE `id` = 20;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0021.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0021.jpg"]', `update_time` = NOW() WHERE `id` = 21;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0022.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0022.jpg"]', `update_time` = NOW() WHERE `id` = 22;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0023.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0023.jpg"]', `update_time` = NOW() WHERE `id` = 23;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0024.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0024.jpg"]', `update_time` = NOW() WHERE `id` = 24;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0025.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0025.jpg"]', `update_time` = NOW() WHERE `id` = 25;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0026.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0026.jpg"]', `update_time` = NOW() WHERE `id` = 26;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0027.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0027.jpg"]', `update_time` = NOW() WHERE `id` = 27;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0028.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0028.jpg"]', `update_time` = NOW() WHERE `id` = 28;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0029.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0029.jpg"]', `update_time` = NOW() WHERE `id` = 29;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0030.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0030.jpg"]', `update_time` = NOW() WHERE `id` = 30;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0031.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0031.jpg"]', `update_time` = NOW() WHERE `id` = 31;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0032.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0032.jpg"]', `update_time` = NOW() WHERE `id` = 32;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0033.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0033.jpg"]', `update_time` = NOW() WHERE `id` = 33;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0034.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0034.jpg"]', `update_time` = NOW() WHERE `id` = 34;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0035.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0035.jpg"]', `update_time` = NOW() WHERE `id` = 35;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0036.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0036.jpg"]', `update_time` = NOW() WHERE `id` = 36;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0037.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0037.jpg"]', `update_time` = NOW() WHERE `id` = 37;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0038.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0038.jpg"]', `update_time` = NOW() WHERE `id` = 38;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0039.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0039.jpg"]', `update_time` = NOW() WHERE `id` = 39;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0040.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0040.jpg"]', `update_time` = NOW() WHERE `id` = 40;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0041.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0041.jpg"]', `update_time` = NOW() WHERE `id` = 41;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0042.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0042.jpg"]', `update_time` = NOW() WHERE `id` = 42;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0043.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0043.jpg"]', `update_time` = NOW() WHERE `id` = 43;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0044.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0044.jpg"]', `update_time` = NOW() WHERE `id` = 44;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0045.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0045.jpg"]', `update_time` = NOW() WHERE `id` = 45;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0046.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0046.jpg"]', `update_time` = NOW() WHERE `id` = 46;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0047.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0047.jpg"]', `update_time` = NOW() WHERE `id` = 47;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0048.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0048.jpg"]', `update_time` = NOW() WHERE `id` = 48;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0049.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0049.jpg"]', `update_time` = NOW() WHERE `id` = 49;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0050.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0050.jpg"]', `update_time` = NOW() WHERE `id` = 50;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0051.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0051.jpg"]', `update_time` = NOW() WHERE `id` = 51;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0052.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0052.jpg"]', `update_time` = NOW() WHERE `id` = 52;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0053.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0053.jpg"]', `update_time` = NOW() WHERE `id` = 53;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0054.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0054.jpg"]', `update_time` = NOW() WHERE `id` = 54;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0055.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0055.jpg"]', `update_time` = NOW() WHERE `id` = 55;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0056.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0056.jpg"]', `update_time` = NOW() WHERE `id` = 56;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0057.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0057.jpg"]', `update_time` = NOW() WHERE `id` = 57;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0058.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0058.jpg"]', `update_time` = NOW() WHERE `id` = 58;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0059.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0059.jpg"]', `update_time` = NOW() WHERE `id` = 59;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0060.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0060.jpg"]', `update_time` = NOW() WHERE `id` = 60;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0061.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0061.jpg"]', `update_time` = NOW() WHERE `id` = 61;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0062.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0062.jpg"]', `update_time` = NOW() WHERE `id` = 62;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0063.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0063.jpg"]', `update_time` = NOW() WHERE `id` = 63;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0064.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0064.jpg"]', `update_time` = NOW() WHERE `id` = 64;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0065.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0065.jpg"]', `update_time` = NOW() WHERE `id` = 65;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0066.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0066.jpg"]', `update_time` = NOW() WHERE `id` = 66;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0067.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0067.jpg"]', `update_time` = NOW() WHERE `id` = 67;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0068.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0068.jpg"]', `update_time` = NOW() WHERE `id` = 68;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0069.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0069.jpg"]', `update_time` = NOW() WHERE `id` = 69;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0070.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0070.jpg"]', `update_time` = NOW() WHERE `id` = 70;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0071.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0071.jpg"]', `update_time` = NOW() WHERE `id` = 71;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0072.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0072.jpg"]', `update_time` = NOW() WHERE `id` = 72;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0073.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0073.jpg"]', `update_time` = NOW() WHERE `id` = 73;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0074.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0074.jpg"]', `update_time` = NOW() WHERE `id` = 74;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0075.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0075.jpg"]', `update_time` = NOW() WHERE `id` = 75;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0076.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0076.jpg"]', `update_time` = NOW() WHERE `id` = 76;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0077.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0077.jpg"]', `update_time` = NOW() WHERE `id` = 77;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0078.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0078.jpg"]', `update_time` = NOW() WHERE `id` = 78;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0079.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0079.jpg"]', `update_time` = NOW() WHERE `id` = 79;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0080.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0080.jpg"]', `update_time` = NOW() WHERE `id` = 80;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0081.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0081.jpg"]', `update_time` = NOW() WHERE `id` = 81;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0082.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0082.jpg"]', `update_time` = NOW() WHERE `id` = 82;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0083.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0083.jpg"]', `update_time` = NOW() WHERE `id` = 83;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0084.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0084.jpg"]', `update_time` = NOW() WHERE `id` = 84;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0085.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0085.jpg"]', `update_time` = NOW() WHERE `id` = 85;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0086.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0086.jpg"]', `update_time` = NOW() WHERE `id` = 86;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0087.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0087.jpg"]', `update_time` = NOW() WHERE `id` = 87;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0088.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0088.jpg"]', `update_time` = NOW() WHERE `id` = 88;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0089.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0089.jpg"]', `update_time` = NOW() WHERE `id` = 89;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0090.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0090.jpg"]', `update_time` = NOW() WHERE `id` = 90;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0091.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0091.jpg"]', `update_time` = NOW() WHERE `id` = 91;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0092.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0092.jpg"]', `update_time` = NOW() WHERE `id` = 92;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0093.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0093.jpg"]', `update_time` = NOW() WHERE `id` = 93;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0094.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0094.jpg"]', `update_time` = NOW() WHERE `id` = 94;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0095.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0095.jpg"]', `update_time` = NOW() WHERE `id` = 95;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0096.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0096.jpg"]', `update_time` = NOW() WHERE `id` = 96;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0097.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0097.jpg"]', `update_time` = NOW() WHERE `id` = 97;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0098.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0098.jpg"]', `update_time` = NOW() WHERE `id` = 98;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0099.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0099.jpg"]', `update_time` = NOW() WHERE `id` = 99;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0100.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0100.jpg"]', `update_time` = NOW() WHERE `id` = 100;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0101.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0101.jpg"]', `update_time` = NOW() WHERE `id` = 101;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0102.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0102.jpg"]', `update_time` = NOW() WHERE `id` = 102;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0103.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0103.jpg"]', `update_time` = NOW() WHERE `id` = 103;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0104.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0104.jpg"]', `update_time` = NOW() WHERE `id` = 104;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0105.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0105.jpg"]', `update_time` = NOW() WHERE `id` = 105;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0106.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0106.jpg"]', `update_time` = NOW() WHERE `id` = 106;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0107.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0107.jpg"]', `update_time` = NOW() WHERE `id` = 107;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0108.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0108.jpg"]', `update_time` = NOW() WHERE `id` = 108;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0109.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0109.jpg"]', `update_time` = NOW() WHERE `id` = 109;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0110.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0110.jpg"]', `update_time` = NOW() WHERE `id` = 110;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0111.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0111.jpg"]', `update_time` = NOW() WHERE `id` = 111;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0112.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0112.jpg"]', `update_time` = NOW() WHERE `id` = 112;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0113.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0113.jpg"]', `update_time` = NOW() WHERE `id` = 113;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0114.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0114.jpg"]', `update_time` = NOW() WHERE `id` = 114;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0115.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0115.jpg"]', `update_time` = NOW() WHERE `id` = 115;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0116.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0116.jpg"]', `update_time` = NOW() WHERE `id` = 116;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0117.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0117.jpg"]', `update_time` = NOW() WHERE `id` = 117;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0118.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0118.jpg"]', `update_time` = NOW() WHERE `id` = 118;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0119.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0119.jpg"]', `update_time` = NOW() WHERE `id` = 119;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0120.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0120.jpg"]', `update_time` = NOW() WHERE `id` = 120;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0121.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0121.jpg"]', `update_time` = NOW() WHERE `id` = 121;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0122.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0122.jpg"]', `update_time` = NOW() WHERE `id` = 122;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0123.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0123.jpg"]', `update_time` = NOW() WHERE `id` = 123;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0124.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0124.jpg"]', `update_time` = NOW() WHERE `id` = 124;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0125.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0125.jpg"]', `update_time` = NOW() WHERE `id` = 125;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0126.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0126.jpg"]', `update_time` = NOW() WHERE `id` = 126;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0127.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0127.jpg"]', `update_time` = NOW() WHERE `id` = 127;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0128.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0128.jpg"]', `update_time` = NOW() WHERE `id` = 128;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0129.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0129.jpg"]', `update_time` = NOW() WHERE `id` = 129;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0130.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0130.jpg"]', `update_time` = NOW() WHERE `id` = 130;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0131.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0131.jpg"]', `update_time` = NOW() WHERE `id` = 131;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0132.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0132.jpg"]', `update_time` = NOW() WHERE `id` = 132;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0133.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0133.jpg"]', `update_time` = NOW() WHERE `id` = 133;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0134.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0134.jpg"]', `update_time` = NOW() WHERE `id` = 134;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0135.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0135.jpg"]', `update_time` = NOW() WHERE `id` = 135;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0136.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0136.jpg"]', `update_time` = NOW() WHERE `id` = 136;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0137.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0137.jpg"]', `update_time` = NOW() WHERE `id` = 137;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0138.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0138.jpg"]', `update_time` = NOW() WHERE `id` = 138;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0139.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0139.jpg"]', `update_time` = NOW() WHERE `id` = 139;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0140.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0140.jpg"]', `update_time` = NOW() WHERE `id` = 140;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0141.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0141.jpg"]', `update_time` = NOW() WHERE `id` = 141;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0142.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0142.jpg"]', `update_time` = NOW() WHERE `id` = 142;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0143.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0143.jpg"]', `update_time` = NOW() WHERE `id` = 143;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0144.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0144.jpg"]', `update_time` = NOW() WHERE `id` = 144;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0145.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0145.jpg"]', `update_time` = NOW() WHERE `id` = 145;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0146.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0146.jpg"]', `update_time` = NOW() WHERE `id` = 146;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0147.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0147.jpg"]', `update_time` = NOW() WHERE `id` = 147;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0148.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0148.jpg"]', `update_time` = NOW() WHERE `id` = 148;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0149.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0149.jpg"]', `update_time` = NOW() WHERE `id` = 149;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0150.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0150.jpg"]', `update_time` = NOW() WHERE `id` = 150;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0151.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0151.jpg"]', `update_time` = NOW() WHERE `id` = 151;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0152.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0152.jpg"]', `update_time` = NOW() WHERE `id` = 152;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0153.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0153.jpg"]', `update_time` = NOW() WHERE `id` = 153;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0154.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0154.jpg"]', `update_time` = NOW() WHERE `id` = 154;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0155.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0155.jpg"]', `update_time` = NOW() WHERE `id` = 155;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0156.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0156.jpg"]', `update_time` = NOW() WHERE `id` = 156;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0157.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0157.jpg"]', `update_time` = NOW() WHERE `id` = 157;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0158.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0158.jpg"]', `update_time` = NOW() WHERE `id` = 158;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0159.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0159.jpg"]', `update_time` = NOW() WHERE `id` = 159;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0160.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0160.jpg"]', `update_time` = NOW() WHERE `id` = 160;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0161.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0161.jpg"]', `update_time` = NOW() WHERE `id` = 161;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0162.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0162.jpg"]', `update_time` = NOW() WHERE `id` = 162;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0163.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0163.jpg"]', `update_time` = NOW() WHERE `id` = 163;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0164.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0164.jpg"]', `update_time` = NOW() WHERE `id` = 164;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0165.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0165.jpg"]', `update_time` = NOW() WHERE `id` = 165;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0166.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0166.jpg"]', `update_time` = NOW() WHERE `id` = 166;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0167.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0167.jpg"]', `update_time` = NOW() WHERE `id` = 167;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0168.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0168.jpg"]', `update_time` = NOW() WHERE `id` = 168;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0169.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0169.jpg"]', `update_time` = NOW() WHERE `id` = 169;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0170.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0170.jpg"]', `update_time` = NOW() WHERE `id` = 170;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0171.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0171.jpg"]', `update_time` = NOW() WHERE `id` = 171;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0172.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0172.jpg"]', `update_time` = NOW() WHERE `id` = 172;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0173.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0173.jpg"]', `update_time` = NOW() WHERE `id` = 173;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0174.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0174.jpg"]', `update_time` = NOW() WHERE `id` = 174;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0175.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0175.jpg"]', `update_time` = NOW() WHERE `id` = 175;
UPDATE `product` SET `image` = 'https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0176.jpg', `images` = '["https://cyy050722.oss-cn-beijing.aliyuncs.com/products/web-matched/2026/04/30/product-0176.jpg"]', `update_time` = NOW() WHERE `id` = 176;

UPDATE `product_sku` ps JOIN `product` p ON p.id = ps.product_id SET ps.image = p.image WHERE p.id BETWEEN 1 AND 176;
UPDATE `product_spec_value` v JOIN `product_spec_name` n ON n.id = v.spec_name_id JOIN `product` p ON p.id = n.product_id SET v.image = p.image WHERE p.id BETWEEN 1 AND 176;
UPDATE `order_item` oi JOIN `product` p ON p.id = oi.product_id SET oi.product_image = p.image WHERE p.id BETWEEN 1 AND 176;

-- END PRODUCT IMAGE OSS BACKFILL
