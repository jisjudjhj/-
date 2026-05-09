#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
分段生成电商系统初始化 SQL。

当前支持：
1. base      基础账号/分类/轮播图/优惠券
2. products  商品/规格/SKU（按区间分段）
3. commerce  订单/行为/评价/实时汇总
4. extended  秒杀/客服/消息队列/分析快照/聚类结果
"""

from __future__ import annotations

import argparse
import json
import random
from collections import Counter, defaultdict
from datetime import datetime, timedelta
from functools import lru_cache
from pathlib import Path
from urllib.parse import quote


BASE_DIR = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT_DIR = BASE_DIR / "backend" / "src" / "main" / "resources" / "sql" / "chunks"
PASSWORD_HASH = "$2b$10$rnME1wYnVcL9VtDHing1EuBruIoreT5LwFXJmxqxEIs1/wEye.tXG"
NOW = "2026-04-18 20:00:00"
PLANNED_PRODUCT_TOTAL = 3000
PRODUCT_IMAGE_UPDATE_RE = "seed-part-products-images-*.sql"
COMMERCE_SUMMARY_RE = "seed-part-commerce-*.summary.json"

ADMIN_USERS = [
    ("admin", "平台超管"),
    ("admin_ops", "运营管理员"),
    ("admin_data", "数据管理员"),
    ("admin_support", "客服管理员"),
    ("admin_finance", "财务管理员"),
    ("admin_audit", "风控管理员"),
    ("admin_marketing", "营销管理员"),
    ("admin_goods", "商品管理员"),
    ("admin_im", "客服调度"),
    ("admin_live", "活动管理员"),
    ("admin_stream", "流式分析管理员"),
    ("admin_qa", "质量管理员"),
]

MERCHANT_COUNT = 180
NORMAL_USER_COUNT = 1200
ADMIN_COUNT = len(ADMIN_USERS)
MERCHANT_START_ID = ADMIN_COUNT + 1
NORMAL_USER_START_ID = ADMIN_COUNT + MERCHANT_COUNT + 1

CATEGORY_BLUEPRINTS = [
    {
        "main": "手机数码",
        "color": "1D4ED8",
        "subs": [
            ("手机整机", ["旗舰手机", "折叠手机", "游戏手机", "影像手机"], (1699, 8999), "digital"),
            ("智能穿戴", ["智能手表", "运动手环", "健康手表", "蓝牙耳机"], (129, 2599), "digital"),
            ("数码配件", ["磁吸充电宝", "GaN充电器", "手机壳", "数据线"], (29, 699), "accessory"),
        ],
    },
    {
        "main": "电脑办公",
        "color": "0F766E",
        "subs": [
            ("笔记本电脑", ["轻薄本", "商务本", "游戏本", "创作本"], (3299, 12999), "digital"),
            ("办公设备", ["打印机", "扫描仪", "投影仪", "碎纸机"], (199, 4999), "device"),
            ("办公文具", ["机械键盘", "无线鼠标", "人体工学支架", "桌面扩展坞"], (49, 1999), "accessory"),
        ],
    },
    {
        "main": "家用电器",
        "color": "DC2626",
        "subs": [
            ("厨房电器", ["空气炸锅", "破壁机", "电饭煲", "咖啡机"], (99, 3499), "appliance"),
            ("生活电器", ["吸尘器", "加湿器", "空气净化器", "挂烫机"], (89, 5999), "appliance"),
            ("清洁家电", ["洗地机", "扫地机器人", "除螨仪", "擦窗机器人"], (199, 6999), "appliance"),
        ],
    },
    {
        "main": "家居家装",
        "color": "B45309",
        "subs": [
            ("客厅家具", ["布艺沙发", "岩板茶几", "电视柜", "单人休闲椅"], (199, 7999), "home"),
            ("卧室家纺", ["四件套", "乳胶枕", "床头柜", "遮光窗帘"], (59, 2999), "home"),
            ("收纳日用", ["收纳柜", "衣架套装", "置物架", "垃圾桶"], (19, 999), "home"),
        ],
    },
    {
        "main": "服饰鞋包",
        "color": "7C3AED",
        "subs": [
            ("男装", ["卫衣", "衬衫", "夹克", "休闲裤"], (49, 1299), "fashion"),
            ("女装", ["连衣裙", "针织衫", "风衣", "半身裙"], (59, 1599), "fashion"),
            ("鞋靴箱包", ["运动鞋", "乐福鞋", "双肩包", "托特包"], (79, 2299), "fashion"),
        ],
    },
    {
        "main": "美妆护肤",
        "color": "DB2777",
        "subs": [
            ("面部护肤", ["精华液", "面霜", "面膜", "防晒乳"], (39, 999), "beauty"),
            ("彩妆香氛", ["口红", "粉底液", "眼影盘", "香水"], (29, 1899), "beauty"),
            ("个护清洁", ["电动牙刷", "洗发水", "沐浴露", "身体乳"], (19, 699), "beauty"),
        ],
    },
    {
        "main": "食品生鲜",
        "color": "16A34A",
        "subs": [
            ("休闲零食", ["坚果礼盒", "冻干果脆", "海苔脆", "肉脯组合"], (19, 299), "food"),
            ("粮油速食", ["有机大米", "高蛋白面", "自热米饭", "杂粮礼盒"], (15, 399), "food"),
            ("生鲜冷链", ["原切牛排", "深海三文鱼", "蓝莓鲜果", "草饲羊排"], (39, 999), "fresh"),
        ],
    },
    {
        "main": "母婴用品",
        "color": "EA580C",
        "subs": [
            ("婴童喂养", ["奶瓶套装", "辅食机", "保温碗", "学饮杯"], (29, 899), "mother"),
            ("尿裤洗护", ["拉拉裤", "湿巾", "护臀膏", "婴儿沐浴露"], (19, 699), "mother"),
            ("婴童玩具", ["益智积木", "安抚玩偶", "早教机", "滑步车"], (39, 1999), "mother"),
        ],
    },
    {
        "main": "运动户外",
        "color": "0891B2",
        "subs": [
            ("运动健身", ["瑜伽垫", "筋膜枪", "哑铃套装", "跳绳"], (29, 1599), "sport"),
            ("户外装备", ["露营椅", "帐篷", "登山包", "保温壶"], (49, 2599), "sport"),
            ("骑行出行", ["骑行头盔", "车灯", "骑行服", "折叠自行车"], (39, 4999), "sport"),
        ],
    },
    {
        "main": "图书文具",
        "color": "4F46E5",
        "subs": [
            ("畅销图书", ["商业管理书", "人文社科书", "儿童绘本", "科普读物"], (12, 299), "book"),
            ("学习文具", ["笔记本", "钢笔", "马克笔", "文件夹"], (9, 399), "stationery"),
            ("艺术周边", ["手账礼盒", "贴纸套装", "桌面摆件", "创意明信片"], (9, 599), "stationery"),
        ],
    },
    {
        "main": "汽车用品",
        "color": "334155",
        "subs": [
            ("车载电器", ["行车记录仪", "车载吸尘器", "充气泵", "应急电源"], (69, 2999), "car"),
            ("美容养护", ["玻璃水", "洗车液", "镀晶喷雾", "补漆笔"], (19, 499), "car"),
            ("内饰精品", ["头枕腰靠", "香薰夹", "脚垫", "后备箱收纳箱"], (29, 1999), "car"),
        ],
    },
    {
        "main": "宠物生活",
        "color": "CA8A04",
        "subs": [
            ("宠物主粮", ["猫粮", "犬粮", "冻干主食", "鲜肉罐头"], (19, 899), "pet"),
            ("宠物用品", ["猫砂", "宠物饮水机", "航空箱", "宠物窝"], (15, 1499), "pet"),
            ("宠物玩具", ["逗猫棒", "磨牙玩具", "拾便器", "胸背牵引绳"], (9, 499), "pet"),
        ],
    },
]

STORE_PREFIXES = ["云选", "星选", "潮购", "臻选", "优品", "智享", "悦购", "森活", "鲸选", "橙意"]
STORE_SUFFIXES = ["旗舰店", "严选店", "品牌店", "优选馆", "生活馆", "官方店", "直供店", "臻品馆"]
MERCHANT_CITIES = ["上海", "深圳", "杭州", "广州", "苏州", "成都", "武汉", "南京", "青岛", "长沙", "重庆", "厦门"]
USER_CITY_POOL = [
    ("北京市", "北京市", "朝阳区"),
    ("上海市", "上海市", "浦东新区"),
    ("广东省", "深圳市", "南山区"),
    ("广东省", "广州市", "天河区"),
    ("浙江省", "杭州市", "西湖区"),
    ("江苏省", "南京市", "建邺区"),
    ("四川省", "成都市", "高新区"),
    ("湖北省", "武汉市", "洪山区"),
    ("山东省", "青岛市", "市南区"),
    ("湖南省", "长沙市", "岳麓区"),
]
STREET_SUFFIXES = ["路", "大道", "街", "巷", "里", "广场", "中心", "公寓"]
SURNAMES = list("赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦许何吕张孔曹严华金魏陶姜谢邹喻柏水窦章云苏潘葛奚范彭郎鲁韦昌马苗凤花方俞任袁柳鲍史唐费廉岑薛雷贺倪汤滕殷罗毕郝安常乐于时傅皮卞齐康伍余元顾孟平黄穆萧尹")
GIVEN_NAMES = ["子墨", "沐言", "星野", "若溪", "初夏", "景川", "雨桐", "思远", "知夏", "嘉宁", "安禾", "奕辰", "清妍", "屿川", "书瑶", "言蹊", "景行", "以安", "时雨", "可心"]

SPEC_MODELS = {
    "digital": [("颜色", ["曜石黑", "月光银"]), ("版本", ["标准版", "Pro版"])],
    "accessory": [("颜色", ["深空灰", "珍珠白"]), ("套装", ["单件装", "双件装"])],
    "device": [("版本", ["基础款", "升级款"]), ("服务", ["标准服务", "延保服务"])],
    "appliance": [("颜色", ["云雾白", "星际灰"]), ("容量", ["标准容量", "大容量"])],
    "home": [("规格", ["标准款", "加大款"]), ("颜色", ["原木色", "暖杏色"])],
    "fashion": [("颜色", ["曜石黑", "雾霾灰"]), ("尺码", ["M", "L", "XL"])],
    "beauty": [("规格", ["常规装", "礼盒装"]), ("功效", ["保湿型", "焕亮型"])],
    "food": [("规格", ["家庭装", "分享装"])],
    "fresh": [("规格", ["尝鲜装", "精品装"])],
    "mother": [("规格", ["标准装", "囤货装"])],
    "sport": [("颜色", ["动感黑", "活力橙"]), ("规格", ["标准款", "进阶款"])],
    "book": [("版本", ["精选版", "典藏版"])],
    "stationery": [("规格", ["单品", "套装"])],
    "car": [("型号", ["通用版", "升级版"])],
    "pet": [("规格", ["日常装", "囤货装"])],
}

BRAND_POOLS = {
    "手机数码": ["星极", "曜川", "凌序", "拓影", "未界", "极川", "曜感", "维塔"],
    "电脑办公": ["智序", "拓写", "云极", "航策", "森码", "立维", "栖键", "格域"],
    "家用电器": ["沐川", "澄域", "暖序", "禾川", "净白", "森屿", "朗境", "维沐"],
    "家居家装": ["栖木", "慢舍", "禾屿", "木序", "原寓", "安枝", "川舍", "悦栖"],
    "服饰鞋包": ["序章", "里巷", "简廓", "初尺", "野度", "木上", "隐格", "川本"],
    "美妆护肤": ["沁妍", "若汐", "澄肌", "雾屿", "轻研", "慕光", "初愈", "植序"],
    "食品生鲜": ["谷川集", "鲜作社", "林间味", "禾气仓", "食刻集", "山海味", "初鲜记", "良食局"],
    "母婴用品": ["柔芽", "安蓓", "初稚", "暖芽", "轻宝", "幼羽", "禾贝", "朵芽"],
    "运动户外": ["野跃", "山迹", "速界", "凌峰", "行川", "跃域", "旷野志", "风驰线"],
    "图书文具": ["纸间", "字屿", "木笔社", "知页", "简页", "墨枝", "绘原", "页里"],
    "汽车用品": ["驰野", "途界", "驭光", "巡迹", "拓驰", "行盾", "曜途", "车境"],
    "宠物生活": ["尾巴星球", "喵选社", "汪星仓", "毛球记", "宠在场", "爪印集", "有宠日和", "欢尾社"],
}

SERIES_WORDS = [
    "Air", "Pro", "Max", "Ultra", "Neo", "Prime", "Edge", "X", "One", "S",
    "Lite", "Plus", "Studio", "Flow", "Core", "Zen", "Nova", "Pulse",
]
REVIEW_TAG_POOL = {
    "入门档": ["性价比高", "日常够用", "配送很快", "包装完整"],
    "大众档": ["做工扎实", "价格合适", "体验稳定", "值得回购"],
    "进阶档": ["质感高级", "性能不错", "细节到位", "功能实用"],
    "高端档": ["高端感强", "体验惊喜", "配置优秀", "品牌服务好"],
}
USER_SEGMENTS = [
    {"name": "谨慎囤货型", "weight": 0.22, "orders": (2, 5), "qty": (1, 3), "tier_weights": {"入门档": 0.48, "大众档": 0.34, "进阶档": 0.14, "高端档": 0.04}},
    {"name": "家庭常购型", "weight": 0.33, "orders": (4, 8), "qty": (1, 3), "tier_weights": {"入门档": 0.28, "大众档": 0.46, "进阶档": 0.20, "高端档": 0.06}},
    {"name": "品质升级型", "weight": 0.24, "orders": (3, 6), "qty": (1, 2), "tier_weights": {"入门档": 0.14, "大众档": 0.40, "进阶档": 0.31, "高端档": 0.15}},
    {"name": "旗舰尝鲜型", "weight": 0.11, "orders": (2, 4), "qty": (1, 1), "tier_weights": {"入门档": 0.05, "大众档": 0.22, "进阶档": 0.38, "高端档": 0.35}},
    {"name": "低频浏览型", "weight": 0.10, "orders": (0, 2), "qty": (1, 2), "tier_weights": {"入门档": 0.33, "大众档": 0.40, "进阶档": 0.20, "高端档": 0.07}},
]
SCENE_POOL = ["home", "search", "detail", "guess_you_like", "personal"]

PRICE_TIER_MODELS = {
    "digital": [
        {"name": "入门档", "weight": 0.18, "start": 0.18, "end": 0.36, "stock": (40, 120), "sales": (80, 520), "rating": (4.3, 4.7), "markup": (1.10, 1.18)},
        {"name": "大众档", "weight": 0.42, "start": 0.36, "end": 0.58, "stock": (90, 220), "sales": (180, 980), "rating": (4.4, 4.8), "markup": (1.13, 1.23)},
        {"name": "进阶档", "weight": 0.26, "start": 0.58, "end": 0.78, "stock": (35, 120), "sales": (60, 420), "rating": (4.5, 4.9), "markup": (1.14, 1.26)},
        {"name": "高端档", "weight": 0.14, "start": 0.78, "end": 0.97, "stock": (10, 60), "sales": (8, 160), "rating": (4.6, 5.0), "markup": (1.08, 1.18)},
    ],
    "device": [
        {"name": "入门档", "weight": 0.24, "start": 0.16, "end": 0.34, "stock": (35, 140), "sales": (50, 360), "rating": (4.3, 4.7), "markup": (1.10, 1.18)},
        {"name": "大众档", "weight": 0.40, "start": 0.34, "end": 0.56, "stock": (75, 200), "sales": (120, 720), "rating": (4.4, 4.8), "markup": (1.12, 1.22)},
        {"name": "进阶档", "weight": 0.24, "start": 0.56, "end": 0.76, "stock": (28, 110), "sales": (40, 260), "rating": (4.5, 4.9), "markup": (1.14, 1.24)},
        {"name": "高端档", "weight": 0.12, "start": 0.76, "end": 0.96, "stock": (8, 48), "sales": (5, 110), "rating": (4.6, 5.0), "markup": (1.08, 1.16)},
    ],
    "appliance": [
        {"name": "入门档", "weight": 0.20, "start": 0.12, "end": 0.30, "stock": (40, 160), "sales": (60, 420), "rating": (4.3, 4.7), "markup": (1.10, 1.18)},
        {"name": "大众档", "weight": 0.46, "start": 0.30, "end": 0.54, "stock": (110, 260), "sales": (160, 1100), "rating": (4.4, 4.8), "markup": (1.12, 1.22)},
        {"name": "进阶档", "weight": 0.24, "start": 0.54, "end": 0.76, "stock": (30, 120), "sales": (35, 280), "rating": (4.5, 4.9), "markup": (1.14, 1.24)},
        {"name": "高端档", "weight": 0.10, "start": 0.76, "end": 0.95, "stock": (6, 40), "sales": (4, 90), "rating": (4.6, 5.0), "markup": (1.08, 1.16)},
    ],
    "accessory": [
        {"name": "入门档", "weight": 0.36, "start": 0.08, "end": 0.28, "stock": (120, 420), "sales": (240, 1600), "rating": (4.2, 4.7), "markup": (1.16, 1.28)},
        {"name": "大众档", "weight": 0.40, "start": 0.28, "end": 0.52, "stock": (140, 360), "sales": (200, 1200), "rating": (4.3, 4.8), "markup": (1.14, 1.25)},
        {"name": "进阶档", "weight": 0.18, "start": 0.52, "end": 0.74, "stock": (60, 180), "sales": (60, 420), "rating": (4.4, 4.9), "markup": (1.12, 1.22)},
        {"name": "高端档", "weight": 0.06, "start": 0.74, "end": 0.94, "stock": (16, 70), "sales": (12, 120), "rating": (4.5, 5.0), "markup": (1.08, 1.18)},
    ],
    "home": [
        {"name": "入门档", "weight": 0.28, "start": 0.10, "end": 0.30, "stock": (70, 240), "sales": (100, 760), "rating": (4.2, 4.7), "markup": (1.16, 1.28)},
        {"name": "大众档", "weight": 0.44, "start": 0.30, "end": 0.56, "stock": (80, 260), "sales": (120, 840), "rating": (4.3, 4.8), "markup": (1.14, 1.24)},
        {"name": "进阶档", "weight": 0.20, "start": 0.56, "end": 0.78, "stock": (30, 110), "sales": (35, 280), "rating": (4.4, 4.9), "markup": (1.12, 1.22)},
        {"name": "高端档", "weight": 0.08, "start": 0.78, "end": 0.96, "stock": (8, 36), "sales": (5, 80), "rating": (4.5, 5.0), "markup": (1.08, 1.16)},
    ],
    "fashion": [
        {"name": "入门档", "weight": 0.34, "start": 0.10, "end": 0.26, "stock": (90, 280), "sales": (140, 1100), "rating": (4.1, 4.7), "markup": (1.18, 1.30)},
        {"name": "大众档", "weight": 0.38, "start": 0.26, "end": 0.48, "stock": (110, 320), "sales": (180, 1400), "rating": (4.2, 4.8), "markup": (1.16, 1.28)},
        {"name": "进阶档", "weight": 0.20, "start": 0.48, "end": 0.72, "stock": (45, 140), "sales": (50, 420), "rating": (4.4, 4.9), "markup": (1.12, 1.22)},
        {"name": "高端档", "weight": 0.08, "start": 0.72, "end": 0.95, "stock": (10, 48), "sales": (8, 120), "rating": (4.5, 5.0), "markup": (1.08, 1.18)},
    ],
    "beauty": [
        {"name": "入门档", "weight": 0.30, "start": 0.08, "end": 0.24, "stock": (100, 320), "sales": (180, 1300), "rating": (4.2, 4.7), "markup": (1.20, 1.34)},
        {"name": "大众档", "weight": 0.42, "start": 0.24, "end": 0.48, "stock": (110, 300), "sales": (160, 1180), "rating": (4.3, 4.8), "markup": (1.16, 1.28)},
        {"name": "进阶档", "weight": 0.20, "start": 0.48, "end": 0.72, "stock": (40, 130), "sales": (45, 360), "rating": (4.5, 4.9), "markup": (1.12, 1.22)},
        {"name": "高端档", "weight": 0.08, "start": 0.72, "end": 0.95, "stock": (10, 42), "sales": (8, 110), "rating": (4.6, 5.0), "markup": (1.08, 1.18)},
    ],
    "food": [
        {"name": "入门档", "weight": 0.40, "start": 0.05, "end": 0.18, "stock": (180, 600), "sales": (300, 2200), "rating": (4.2, 4.7), "markup": (1.16, 1.30)},
        {"name": "大众档", "weight": 0.38, "start": 0.18, "end": 0.34, "stock": (160, 520), "sales": (240, 1800), "rating": (4.3, 4.8), "markup": (1.14, 1.26)},
        {"name": "进阶档", "weight": 0.16, "start": 0.34, "end": 0.56, "stock": (80, 240), "sales": (90, 640), "rating": (4.4, 4.9), "markup": (1.12, 1.22)},
        {"name": "高端档", "weight": 0.06, "start": 0.56, "end": 0.82, "stock": (20, 90), "sales": (12, 180), "rating": (4.5, 5.0), "markup": (1.08, 1.18)},
    ],
    "fresh": [
        {"name": "入门档", "weight": 0.24, "start": 0.08, "end": 0.22, "stock": (80, 220), "sales": (120, 760), "rating": (4.2, 4.7), "markup": (1.10, 1.18)},
        {"name": "大众档", "weight": 0.40, "start": 0.22, "end": 0.42, "stock": (110, 260), "sales": (180, 1080), "rating": (4.3, 4.8), "markup": (1.12, 1.20)},
        {"name": "进阶档", "weight": 0.24, "start": 0.42, "end": 0.68, "stock": (50, 130), "sales": (60, 340), "rating": (4.4, 4.9), "markup": (1.10, 1.18)},
        {"name": "高端档", "weight": 0.12, "start": 0.68, "end": 0.94, "stock": (12, 55), "sales": (10, 120), "rating": (4.5, 5.0), "markup": (1.08, 1.14)},
    ],
    "mother": [
        {"name": "入门档", "weight": 0.30, "start": 0.08, "end": 0.24, "stock": (90, 280), "sales": (140, 980), "rating": (4.3, 4.8), "markup": (1.16, 1.28)},
        {"name": "大众档", "weight": 0.42, "start": 0.24, "end": 0.48, "stock": (100, 280), "sales": (160, 1040), "rating": (4.4, 4.8), "markup": (1.14, 1.24)},
        {"name": "进阶档", "weight": 0.20, "start": 0.48, "end": 0.72, "stock": (35, 120), "sales": (40, 300), "rating": (4.5, 4.9), "markup": (1.12, 1.22)},
        {"name": "高端档", "weight": 0.08, "start": 0.72, "end": 0.94, "stock": (8, 45), "sales": (6, 90), "rating": (4.6, 5.0), "markup": (1.08, 1.16)},
    ],
    "sport": [
        {"name": "入门档", "weight": 0.26, "start": 0.10, "end": 0.26, "stock": (80, 240), "sales": (110, 860), "rating": (4.2, 4.7), "markup": (1.16, 1.28)},
        {"name": "大众档", "weight": 0.42, "start": 0.26, "end": 0.50, "stock": (90, 260), "sales": (140, 980), "rating": (4.3, 4.8), "markup": (1.14, 1.24)},
        {"name": "进阶档", "weight": 0.22, "start": 0.50, "end": 0.74, "stock": (30, 120), "sales": (40, 320), "rating": (4.4, 4.9), "markup": (1.12, 1.22)},
        {"name": "高端档", "weight": 0.10, "start": 0.74, "end": 0.95, "stock": (8, 48), "sales": (8, 110), "rating": (4.5, 5.0), "markup": (1.08, 1.18)},
    ],
    "book": [
        {"name": "入门档", "weight": 0.46, "start": 0.05, "end": 0.16, "stock": (180, 620), "sales": (280, 2400), "rating": (4.2, 4.8), "markup": (1.18, 1.32)},
        {"name": "大众档", "weight": 0.34, "start": 0.16, "end": 0.30, "stock": (150, 520), "sales": (220, 1800), "rating": (4.3, 4.8), "markup": (1.16, 1.28)},
        {"name": "进阶档", "weight": 0.14, "start": 0.30, "end": 0.48, "stock": (80, 220), "sales": (70, 620), "rating": (4.4, 4.9), "markup": (1.12, 1.22)},
        {"name": "高端档", "weight": 0.06, "start": 0.48, "end": 0.76, "stock": (16, 70), "sales": (10, 120), "rating": (4.5, 5.0), "markup": (1.08, 1.18)},
    ],
    "stationery": [
        {"name": "入门档", "weight": 0.42, "start": 0.05, "end": 0.18, "stock": (160, 560), "sales": (260, 2200), "rating": (4.2, 4.8), "markup": (1.18, 1.32)},
        {"name": "大众档", "weight": 0.36, "start": 0.18, "end": 0.34, "stock": (140, 460), "sales": (220, 1600), "rating": (4.3, 4.8), "markup": (1.16, 1.28)},
        {"name": "进阶档", "weight": 0.16, "start": 0.34, "end": 0.54, "stock": (70, 180), "sales": (60, 420), "rating": (4.4, 4.9), "markup": (1.12, 1.22)},
        {"name": "高端档", "weight": 0.06, "start": 0.54, "end": 0.82, "stock": (14, 60), "sales": (8, 90), "rating": (4.5, 5.0), "markup": (1.08, 1.18)},
    ],
    "car": [
        {"name": "入门档", "weight": 0.22, "start": 0.10, "end": 0.28, "stock": (60, 180), "sales": (80, 540), "rating": (4.2, 4.7), "markup": (1.14, 1.24)},
        {"name": "大众档", "weight": 0.42, "start": 0.28, "end": 0.52, "stock": (80, 220), "sales": (120, 780), "rating": (4.3, 4.8), "markup": (1.12, 1.22)},
        {"name": "进阶档", "weight": 0.24, "start": 0.52, "end": 0.76, "stock": (30, 110), "sales": (35, 240), "rating": (4.4, 4.9), "markup": (1.10, 1.20)},
        {"name": "高端档", "weight": 0.12, "start": 0.76, "end": 0.96, "stock": (8, 42), "sales": (6, 96), "rating": (4.5, 5.0), "markup": (1.08, 1.16)},
    ],
    "pet": [
        {"name": "入门档", "weight": 0.34, "start": 0.08, "end": 0.24, "stock": (120, 380), "sales": (180, 1300), "rating": (4.2, 4.7), "markup": (1.16, 1.28)},
        {"name": "大众档", "weight": 0.38, "start": 0.24, "end": 0.46, "stock": (110, 320), "sales": (160, 1100), "rating": (4.3, 4.8), "markup": (1.14, 1.25)},
        {"name": "进阶档", "weight": 0.20, "start": 0.46, "end": 0.70, "stock": (45, 140), "sales": (45, 340), "rating": (4.4, 4.9), "markup": (1.12, 1.22)},
        {"name": "高端档", "weight": 0.08, "start": 0.70, "end": 0.94, "stock": (10, 48), "sales": (8, 110), "rating": (4.5, 5.0), "markup": (1.08, 1.18)},
    ],
}


def sql_value(value):
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float)):
        return str(value)
    text = str(value).replace("\\", "\\\\").replace("'", "''")
    return f"'{text}'"


def json_text(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def placeholder_image(text_a: str, text_b: str, color: str, width: int = 800, height: int = 800) -> str:
    content = quote(f"{text_a}\n{text_b}")
    return f"https://placehold.co/{width}x{height}/{color}/FFFFFF.png?text={content}"


@lru_cache(maxsize=1)
def load_product_image_overrides() -> dict[int, str]:
    update_files = sorted(DEFAULT_OUTPUT_DIR.glob(PRODUCT_IMAGE_UPDATE_RE))
    if not update_files:
        return {}

    result: dict[int, str] = {}
    for update_file in update_files:
        for line in update_file.read_text(encoding="utf-8").splitlines():
            if "UPDATE `product` SET `image` =" not in line:
                continue
            parts = line.split("'")
            if len(parts) < 4:
                continue
            image_url = parts[1]
            try:
                product_id = int(line.rsplit("=", 1)[-1].rstrip("; ").strip())
            except ValueError:
                continue
            result[product_id] = image_url
    return result


def product_images_for(product_id: int, leaf: dict, name: str) -> tuple[str, list[str]]:
    overrides = load_product_image_overrides()
    if product_id in overrides:
        main_image = overrides[product_id]
        return main_image, [main_image]

    main_image = placeholder_image(leaf["sub_name"], f"{product_id:04d}", leaf["color"])
    return main_image, [
        main_image,
        placeholder_image(name[:10], "细节图", leaf["color"]),
        placeholder_image(leaf["main_name"], "场景图", leaf["color"]),
    ]


def chunk_writer(path: Path, title: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    handle = path.open("w", encoding="utf-8", newline="\n")
    handle.write(f"-- {title}\n")
    handle.write("-- 自动生成，请勿手工维护\n")
    handle.write(f"-- 生成时间: {NOW}\n")
    handle.write("-- 所有账号默认密码: 123456 (BCrypt)\n\n")
    return handle


def write_insert(handle, table: str, columns: list[str], rows: list[tuple], batch_size: int = 200):
    if not rows:
        return
    column_sql = ", ".join(f"`{col}`" for col in columns)
    for index in range(0, len(rows), batch_size):
        batch = rows[index:index + batch_size]
        handle.write(f"INSERT INTO `{table}` ({column_sql}) VALUES\n")
        values_sql = []
        for row in batch:
            values_sql.append("(" + ", ".join(sql_value(item) for item in row) + ")")
        handle.write(",\n".join(values_sql))
        handle.write(";\n\n")


def build_categories():
    main_rows = []
    sub_rows = []
    leaves = []
    main_id = 1
    sub_id = 101
    for blueprint in CATEGORY_BLUEPRINTS:
        main_rows.append((
            main_id,
            blueprint["main"],
            0,
            placeholder_image(blueprint["main"], "主分类", blueprint["color"], 320, 320),
            main_id,
            0,
            None,
            None,
            f"{blueprint['main']}主分类",
            NOW,
        ))
        for idx, (sub_name, nouns, price_range, spec_type) in enumerate(blueprint["subs"], start=1):
            sub_rows.append((
                sub_id,
                sub_name,
                main_id,
                placeholder_image(sub_name, blueprint["main"], blueprint["color"], 320, 320),
                idx,
                0,
                None,
                None,
                f"{blueprint['main']}-{sub_name}",
                NOW,
            ))
            leaves.append({
                "id": sub_id,
                "main_id": main_id,
                "main_name": blueprint["main"],
                "sub_name": sub_name,
                "nouns": nouns,
                "price_range": price_range,
                "spec_type": spec_type,
                "color": blueprint["color"],
            })
            sub_id += 1
        main_id += 1
    return main_rows, sub_rows, leaves


def build_base_chunk(output: Path):
    main_rows, sub_rows, leaves = build_categories()
    user_rows = []
    banner_rows = []
    coupon_rows = []

    user_id = 1

    for index, (username, nickname) in enumerate(ADMIN_USERS, start=1):
        user_rows.append((
            user_id,
            username,
            PASSWORD_HASH,
            f"{username}@demo.local",
            f"1386000{index:04d}",
            placeholder_image(nickname, "ADMIN", "111827", 320, 320),
            nickname,
            "admin",
            1,
            999999.00,
            1,
            NOW,
            0,
            NOW,
            NOW,
            0,
        ))
        user_id += 1

    for index in range(1, MERCHANT_COUNT + 1):
        leaf = leaves[(index - 1) % len(leaves)]
        store_name = f"{STORE_PREFIXES[(index - 1) % len(STORE_PREFIXES)]}{leaf['main_name']}{STORE_SUFFIXES[(index - 1) % len(STORE_SUFFIXES)]}"
        username = f"merchant{index:03d}"
        city = MERCHANT_CITIES[(index - 1) % len(MERCHANT_CITIES)]
        user_rows.append((
            user_id,
            username,
            PASSWORD_HASH,
            f"{username}@shop.local",
            f"139700{index:05d}"[:11],
            placeholder_image(store_name[:6], city, leaf["color"], 320, 320),
            store_name,
            "merchant",
            1,
            20000 + index * 88,
            1,
            NOW,
            0,
            NOW,
            NOW,
            0,
        ))
        user_id += 1

    for index in range(1, NORMAL_USER_COUNT + 1):
        surname = SURNAMES[(index - 1) % len(SURNAMES)]
        given = GIVEN_NAMES[(index * 3) % len(GIVEN_NAMES)]
        nickname = f"{surname}{given}"
        username = f"user{index:04d}"
        user_rows.append((
            user_id,
            username,
            PASSWORD_HASH,
            f"{username}@user.local",
            f"137500{index:05d}"[:11],
            placeholder_image(nickname, "USER", "2563EB", 320, 320),
            nickname,
            "user",
            1,
            300 + (index % 50) * 25,
            1,
            NOW,
            0,
            NOW,
            NOW,
            0,
        ))
        user_id += 1

    for index in range(1, 25):
        leaf = leaves[(index - 1) % len(leaves)]
        link_type = "category" if index % 3 else "none"
        link_value = str(leaf["id"]) if link_type == "category" else None
        banner_rows.append((
            index,
            f"{leaf['main_name']}热销会场 {index:02d}",
            placeholder_image(leaf["main_name"], leaf["sub_name"], leaf["color"], 1200, 420),
            link_type,
            link_value,
            index,
            1,
            NOW,
        ))

    coupon_id = 1
    for leaf in leaves:
        coupon_rows.append((
            coupon_id,
            f"{leaf['main_name']}{leaf['sub_name']}满减券",
            1,
            30 + (coupon_id % 4) * 10,
            199,
            None,
            5000,
            0,
            "2026-04-01 00:00:00",
            "2026-12-31 23:59:59",
            1,
            0,
            None,
            0,
            "",
            "",
            "平台公开领取",
            NOW,
        ))
        coupon_id += 1
        merchant_owner = len(ADMIN_USERS) + ((coupon_id * 7) % MERCHANT_COUNT) + 1
        coupon_rows.append((
            coupon_id,
            f"{leaf['sub_name']}店铺券",
            1,
            20 + (coupon_id % 3) * 5,
            99,
            None,
            2000,
            0,
            "2026-04-01 00:00:00",
            "2026-12-31 23:59:59",
            1,
            1,
            merchant_owner,
            0,
            "",
            "",
            "商家活动券",
            NOW,
        ))
        coupon_id += 1

    with chunk_writer(output, "Seed Chunk 01 - 基础账号/分类/轮播图/优惠券") as handle:
        handle.write("-- 推荐先执行本文件，再继续导入商品块、订单块、行为块\n\n")
        handle.write("SET NAMES utf8mb4;\n\n")
        write_insert(handle, "user", [
            "id", "username", "password", "email", "phone", "avatar", "nickname", "role",
            "status", "balance", "email_verified", "last_profile_change", "token_version",
            "create_time", "update_time", "deleted",
        ], user_rows)
        write_insert(handle, "category", [
            "id", "name", "parent_id", "icon", "sort_order", "audience_type",
            "target_segment_codes", "target_user_ids", "audience_note", "create_time",
        ], main_rows + sub_rows)
        write_insert(handle, "banner", [
            "id", "title", "image", "link_type", "link_value", "sort_order", "status", "create_time",
        ], banner_rows)
        write_insert(handle, "coupon", [
            "id", "name", "type", "value", "min_amount", "max_discount", "total_count", "used_count",
            "start_time", "end_time", "status", "scope_type", "merchant_id", "audience_type",
            "target_segment_codes", "target_user_ids", "audience_note", "create_time",
        ], coupon_rows)

    print(f"[ok] 已生成基础块: {output}")
    print(f"     账号: {len(user_rows)} 条, 分类: {len(main_rows) + len(sub_rows)} 条, 轮播图: {len(banner_rows)} 条, 优惠券: {len(coupon_rows)} 条")


def pick_leaf(product_id: int):
    _, _, leaves = build_categories()
    return leaves[(product_id - 1) % len(leaves)]


def product_name(product_id: int, leaf: dict) -> str:
    noun = leaf["nouns"][(product_id // 7) % len(leaf["nouns"])]
    brand_pool = BRAND_POOLS.get(leaf["main_name"], ["云选", "星选", "臻选", "序章"])
    brand = brand_pool[(product_id - 1) % len(brand_pool)]
    series = SERIES_WORDS[(product_id * 3) % len(SERIES_WORDS)]
    model_code = f"{(product_id * 7) % 90 + 10}{chr(65 + (product_id % 6))}"
    return f"{brand} {series} {noun} {model_code}"


def deterministic_ratio(seed: int, salt: int = 0) -> float:
    return ((seed * 97 + salt * 53) % 1000) / 1000.0


def choose_price_tier(product_id: int, spec_type: str) -> dict:
    tiers = PRICE_TIER_MODELS.get(spec_type, PRICE_TIER_MODELS["home"])
    cursor = deterministic_ratio(product_id, 1)
    cumulative = 0.0
    for tier in tiers:
        cumulative += tier["weight"]
        if cursor <= cumulative:
            return tier
    return tiers[-1]


def product_metrics_for(product_id: int, leaf: dict) -> tuple[dict, float, float, int, int, float]:
    tier = choose_price_tier(product_id, leaf["spec_type"])
    low, high = leaf["price_range"]
    span = max(high - low, 1)
    tier_start = low + span * tier["start"]
    tier_end = low + span * tier["end"]
    if tier_end <= tier_start:
        tier_end = tier_start + max(span * 0.06, 1)

    price_ratio = deterministic_ratio(product_id, 2)
    price = round(tier_start + (tier_end - tier_start) * price_ratio, 2)

    markup_min, markup_max = tier["markup"]
    markup = markup_min + (markup_max - markup_min) * deterministic_ratio(product_id, 3)
    original_price = round(price * markup, 2)

    stock_min, stock_max = tier["stock"]
    stock = int(round(stock_min + (stock_max - stock_min) * deterministic_ratio(product_id, 4)))

    sales_min, sales_max = tier["sales"]
    sales_count = int(round(sales_min + (sales_max - sales_min) * deterministic_ratio(product_id, 5)))

    rating_min, rating_max = tier["rating"]
    rating = round(rating_min + (rating_max - rating_min) * deterministic_ratio(product_id, 6), 1)
    rating = min(max(rating, 4.0), 5.0)
    return tier, price, original_price, stock, sales_count, rating


def merchant_id_for(product_id: int) -> int:
    return len(ADMIN_USERS) + ((product_id * 13) % 180) + 1


def build_product_catalog(start_id: int, count: int):
    catalog = []
    for product_id in range(start_id, start_id + count):
        leaf = pick_leaf(product_id)
        name = product_name(product_id, leaf)
        tier, price, original_price, stock, sales_count, rating = product_metrics_for(product_id, leaf)
        main_image, image_list = product_images_for(product_id, leaf, name)
        catalog.append({
            "id": product_id,
            "name": name,
            "description": f"{name}，适合{leaf['main_name']}场景，主打{leaf['sub_name']}人群，定位{tier['name']}，支持平台推荐、店铺活动和复购营销。",
            "price": price,
            "original_price": original_price,
            "category_id": leaf["id"],
            "merchant_id": merchant_id_for(product_id),
            "image": main_image,
            "images": image_list,
            "tags": [leaf["main_name"], leaf["sub_name"], tier["name"], "平台热卖", "商家直供", "推荐优选"],
            "stock": stock,
            "sales_count": sales_count,
            "rating": rating,
            "tier_name": tier["name"],
            "main_name": leaf["main_name"],
            "sub_name": leaf["sub_name"],
            "spec_type": leaf["spec_type"],
        })
    return catalog


def build_product_rows(start_id: int, count: int):
    product_rows = []
    spec_name_rows = []
    spec_value_rows = []
    sku_rows = []

    spec_name_id = start_id * 10
    spec_value_id = start_id * 20
    sku_id = start_id * 10

    for product_id in range(start_id, start_id + count):
        leaf = pick_leaf(product_id)
        name = product_name(product_id, leaf)
        tier, price, original_price, stock, sales_count, rating = product_metrics_for(product_id, leaf)
        main_image, image_list = product_images_for(product_id, leaf, name)
        tags = [
            leaf["main_name"],
            leaf["sub_name"],
            tier["name"],
            "平台热卖",
            "商家直供",
            "推荐优选",
        ]
        merchant_id = merchant_id_for(product_id)
        product_rows.append((
            product_id,
            name,
            f"{name}，适合{leaf['main_name']}场景，主打{leaf['sub_name']}人群，定位{tier['name']}，支持平台推荐、店铺活动和复购营销。",
            price,
            original_price,
            leaf["id"],
            merchant_id,
            main_image,
            json_text(image_list),
            json_text(tags),
            stock,
            sales_count,
            rating,
            1,
            NOW,
            NOW,
            0,
        ))

        specs = SPEC_MODELS[leaf["spec_type"]]
        local_spec_values = []
        for spec_sort, (spec_name, values) in enumerate(specs, start=1):
            current_spec_name_id = spec_name_id
            spec_name_rows.append((current_spec_name_id, product_id, spec_name, spec_sort, NOW))
            spec_name_id += 1
            for value_sort, spec_value in enumerate(values, start=1):
                spec_value_rows.append((
                    spec_value_id,
                    current_spec_name_id,
                    spec_value,
                    placeholder_image(spec_name, spec_value, leaf["color"], 320, 320),
                    value_sort,
                    NOW,
                ))
                local_spec_values.append((spec_name, spec_value))
                spec_value_id += 1

        if len(specs) == 1:
            for option in specs[0][1]:
                sku_rows.append((
                    sku_id,
                    product_id,
                    f"SKU{product_id:05d}{sku_id % 100:02d}",
                    f"{name} {option}",
                    price,
                    original_price,
                    max(stock // 2, 20),
                    sales_count // 2,
                    main_image,
                    json_text({specs[0][0]: option}),
                    1,
                    NOW,
                    NOW,
                ))
                sku_id += 1
        else:
            first_name, first_values = specs[0]
            second_name, second_values = specs[1]
            for first in first_values:
                for second in second_values:
                    sku_rows.append((
                        sku_id,
                        product_id,
                        f"SKU{product_id:05d}{sku_id % 100:02d}",
                        f"{first}-{second}",
                        round(price + (sku_id % 3) * 9.9, 2),
                        round(original_price + (sku_id % 3) * 15.9, 2),
                        max(stock // (len(first_values) * len(second_values)), 8),
                        max(sales_count // (len(first_values) * len(second_values)), 1),
                        main_image,
                        json_text({first_name: first, second_name: second}),
                        1,
                        NOW,
                        NOW,
                    ))
                    sku_id += 1

    return product_rows, spec_name_rows, spec_value_rows, sku_rows


def choose_segment(user_id: int) -> dict:
    cursor = deterministic_ratio(user_id, 17)
    cumulative = 0.0
    for segment in USER_SEGMENTS:
        cumulative += segment["weight"]
        if cursor <= cumulative:
            return segment
    return USER_SEGMENTS[-1]


def choose_weighted(rnd: random.Random, items: list, weight_getter):
    total = 0.0
    weights = []
    for item in items:
        weight = max(float(weight_getter(item)), 0.0001)
        weights.append(weight)
        total += weight
    cursor = rnd.random() * total
    current = 0.0
    for item, weight in zip(items, weights):
        current += weight
        if cursor <= current:
            return item
    return items[-1]


def category_preferences_for_user(user_id: int, categories: list[str]) -> list[str]:
    start = user_id % len(categories)
    return [categories[start], categories[(start + 3) % len(categories)], categories[(start + 7) % len(categories)]]


def build_address_detail(user_id: int, index: int) -> str:
    block = (user_id * 7 + index * 13) % 180 + 1
    building = (user_id * 5 + index * 3) % 28 + 1
    room = (user_id * 11 + index * 17) % 1808 + 101
    street_name = f"云栖{STREET_SUFFIXES[(user_id + index) % len(STREET_SUFFIXES)]}"
    return f"{street_name}{block}号{building}栋{room}"


def build_review_content(product: dict, rating: int) -> str:
    if rating >= 5:
        prefix = "整体非常满意"
    elif rating == 4:
        prefix = "整体体验不错"
    else:
        prefix = "基本符合预期"
    return f"{prefix}，{product['name']}和页面描述一致，{product['sub_name']}相关体验比较稳定，物流和包装也比较到位。"


def commerce_users_for_chunk(start_id: int, count: int, all_user_ids: list[int]) -> list[int]:
    total_users = len(all_user_ids)
    if total_users == 0:
        return []

    range_start = max(start_id - 1, 0)
    range_end = range_start + max(count, 1)
    slice_start = min(total_users - 1, (range_start * total_users) // PLANNED_PRODUCT_TOTAL)
    slice_end = min(total_users, max(slice_start + 1, (range_end * total_users + PLANNED_PRODUCT_TOTAL - 1) // PLANNED_PRODUCT_TOTAL))
    return all_user_ids[slice_start:slice_end]


def keep_latest_time(current: str | None, candidate: str) -> str:
    if current is None or candidate > current:
        return candidate
    return current


def keep_earliest_time(current: str | None, candidate: str) -> str:
    if current is None or candidate < current:
        return candidate
    return current


def normalize_counter_to_points(counter: Counter, limit: int, total_points: int = 100) -> dict[str, int]:
    top_items = counter.most_common(limit)
    if not top_items:
        return {}

    total = sum(value for _, value in top_items)
    if total <= 0:
        return {}

    raw_points = [(key, value * total_points / total) for key, value in top_items]
    base_points = {key: int(score) for key, score in raw_points}
    allocated = sum(base_points.values())
    remainder = total_points - allocated
    if remainder > 0:
        ranked_remainders = sorted(raw_points, key=lambda item: (item[1] - int(item[1]), item[0]), reverse=True)
        for index in range(remainder):
            key = ranked_remainders[index % len(ranked_remainders)][0]
            base_points[key] += 1
    return {key: value for key, value in base_points.items() if value > 0}


def summary_path_for_chunk(output: Path) -> Path:
    return output.with_suffix(".summary.json")


def write_json_file(path: Path, payload: dict):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True),
        encoding="utf-8",
        newline="\n",
    )


def load_commerce_summaries() -> dict[int, dict]:
    summaries: dict[int, dict] = {}
    for path in sorted(DEFAULT_OUTPUT_DIR.glob(COMMERCE_SUMMARY_RE)):
        payload = json.loads(path.read_text(encoding="utf-8"))
        for raw_user_id, summary in payload.get("users", {}).items():
            normalized = dict(summary)
            normalized["user_id"] = int(normalized.get("user_id", raw_user_id))
            summaries[int(raw_user_id)] = normalized
    return summaries


def build_products_chunk(output: Path, start_id: int, count: int):
    product_rows, spec_name_rows, spec_value_rows, sku_rows = build_product_rows(start_id, count)
    with chunk_writer(output, f"Seed Chunk Product - 商品 {start_id} 到 {start_id + count - 1}") as handle:
        handle.write("SET NAMES utf8mb4;\n\n")
        write_insert(handle, "product", [
            "id", "name", "description", "price", "original_price", "category_id", "merchant_id",
            "image", "images", "tags", "stock", "sales_count", "rating", "status",
            "create_time", "update_time", "deleted",
        ], product_rows)
        write_insert(handle, "product_spec_name", [
            "id", "product_id", "spec_name", "sort_order", "create_time",
        ], spec_name_rows)
        write_insert(handle, "product_spec_value", [
            "id", "spec_name_id", "spec_value", "image", "sort_order", "create_time",
        ], spec_value_rows)
        write_insert(handle, "product_sku", [
            "id", "product_id", "sku_code", "sku_name", "price", "original_price", "stock",
            "sales_count", "image", "spec_values", "status", "create_time", "update_time",
        ], sku_rows)

    print(f"[ok] 已生成商品块: {output}")
    print(f"     商品: {len(product_rows)} 条, 规格名: {len(spec_name_rows)} 条, 规格值: {len(spec_value_rows)} 条, SKU: {len(sku_rows)} 条")


def build_commerce_chunk(output: Path, start_id: int, count: int):
    rnd = random.Random(20260418 + start_id * 11 + count)
    now_ref = datetime(2026, 4, 18, 12, 0, 0)
    cutoff_30d = now_ref - timedelta(days=30)
    cutoff_90d = now_ref - timedelta(days=90)
    products = build_product_catalog(start_id, count)
    categories = sorted({product["main_name"] for product in products})
    products_by_category = defaultdict(list)
    for product in products:
        products_by_category[product["main_name"]].append(product)

    address_rows = []
    order_rows = []
    order_item_rows = []
    behavior_rows = []
    event_rows = []
    favorite_rows = []
    preference_rows = []
    wallet_rows = []
    review_rows = []
    vote_rows = []
    cart_rows = []
    search_rows = []
    message_rows = []
    refund_rows = []
    stream_behavior_rows = []
    stream_category_rows = []
    stream_hot_rows = []
    user_balance_updates = []

    all_user_ids = list(range(NORMAL_USER_START_ID, NORMAL_USER_START_ID + NORMAL_USER_COUNT))
    user_ids = commerce_users_for_chunk(start_id, count, all_user_ids)
    user_balances = {user_id: round(300 + (user_id % 50) * 25, 2) for user_id in user_ids}
    user_address_refs = {}
    user_primary_address = {}
    favorite_set = set()
    cart_set = set()
    search_counter = defaultdict(Counter)
    search_first_time = {}
    search_last_time = {}
    user_category_counter = defaultdict(Counter)
    user_tag_counter = defaultdict(Counter)
    user_price_points = defaultdict(list)
    user_product_interest = defaultdict(Counter)
    user_seen_products = defaultdict(set)
    user_purchased_products = defaultdict(set)
    user_order_records = defaultdict(list)
    user_last_paid_time = {}
    user_behavior_days = defaultdict(set)
    user_recent_active_days_30d = defaultdict(set)
    user_recent_behavior_count_30d = Counter()
    user_recent_behavior_by_type_30d = defaultdict(Counter)
    user_recent_duration_sum_30d = Counter()
    user_recent_duration_count_30d = Counter()
    user_order_count_90d = Counter()
    user_order_amount_90d = defaultdict(float)
    user_distinct_category_90d = defaultdict(set)
    behavior_counter = defaultdict(Counter)
    behavior_last_time = defaultdict(dict)
    category_behavior_counter = Counter()
    category_behavior_last_time = {}
    product_behavior_counter = defaultdict(lambda: {"behavior_count": 0, "purchase_count": 0, "category_id": 0, "last_event_time": NOW})

    pk_base = start_id * 1_000_000
    address_id = pk_base + 1
    order_id = pk_base + 1
    order_item_id = pk_base + 1
    behavior_id = pk_base + 1
    event_id = pk_base + 1
    favorite_id = pk_base + 1
    preference_id = pk_base + 1
    wallet_id = pk_base + 1
    review_id = pk_base + 1
    vote_id = pk_base + 1
    cart_id = pk_base + 1
    search_id = pk_base + 1
    message_id = pk_base + 1
    refund_id = pk_base + 1

    for user_id in user_ids:
        city_tuple = USER_CITY_POOL[(user_id - user_ids[0]) % len(USER_CITY_POOL)]
        receiver_name = f"{SURNAMES[user_id % len(SURNAMES)]}{GIVEN_NAMES[(user_id * 3) % len(GIVEN_NAMES)]}"
        phone = f"137500{user_id - user_ids[0] + 1:05d}"[:11]
        address_count = 1 + (1 if deterministic_ratio(user_id, 20) > 0.62 else 0)
        user_address_refs[user_id] = []
        for idx in range(address_count):
            address_rows.append((
                address_id,
                user_id,
                receiver_name,
                phone,
                city_tuple[0],
                city_tuple[1],
                city_tuple[2],
                build_address_detail(user_id, idx),
                1 if idx == 0 else 0,
                NOW,
                NOW,
            ))
            if idx == 0:
                user_primary_address[user_id] = (
                    receiver_name,
                    phone,
                    city_tuple[0],
                    city_tuple[1],
                    city_tuple[2],
                    build_address_detail(user_id, idx),
                )
            user_address_refs[user_id].append(address_id)
            address_id += 1

    for user_id in user_ids:
        segment = choose_segment(user_id)
        preferred_categories = category_preferences_for_user(user_id, categories)
        order_min, order_max = segment["orders"]
        order_count = rnd.randint(order_min, order_max)
        if order_count == 0 and deterministic_ratio(user_id, 31) > 0.78:
            order_count = 1

        for order_index in range(order_count):
            order_time = datetime(2026, 4, 18, 12, 0, 0) - timedelta(
                days=rnd.randint(0, 210),
                hours=rnd.randint(0, 23),
                minutes=rnd.randint(0, 59),
            )
            status_roll = rnd.random()
            age_days = (now_ref - order_time).days
            if age_days <= 2:
                status = 0 if status_roll < 0.35 else 1 if status_roll < 0.72 else 2
            elif age_days <= 7:
                status = 1 if status_roll < 0.18 else 2 if status_roll < 0.55 else 3 if status_roll < 0.88 else 4
            else:
                status = 3 if status_roll < 0.68 else 2 if status_roll < 0.82 else 4 if status_roll < 0.94 else 1

            item_count = rnd.randint(1, 2 if segment["name"] in ["旗舰尝鲜型", "低频浏览型"] else 3)
            chosen_products = []
            seen_product_ids = set()
            for _ in range(item_count):
                category_name = preferred_categories[0] if rnd.random() < 0.46 else preferred_categories[rnd.randint(0, len(preferred_categories) - 1)] if rnd.random() < 0.78 else categories[rnd.randint(0, len(categories) - 1)]
                pool = products_by_category[category_name]
                product = choose_weighted(
                    rnd,
                    pool,
                    lambda item: segment["tier_weights"].get(item["tier_name"], 0.05) * (
                        1.65 if item["tier_name"] == "入门档" else 1.28 if item["tier_name"] == "大众档" else 0.82 if item["tier_name"] == "进阶档" else 0.45
                    ),
                )
                retry = 0
                while product["id"] in seen_product_ids and retry < 5:
                    product = pool[rnd.randint(0, len(pool) - 1)]
                    retry += 1
                seen_product_ids.add(product["id"])
                chosen_products.append(product)

            order_items = []
            original_amount = 0.0
            for product in chosen_products:
                if product["tier_name"] == "入门档":
                    quantity = rnd.randint(1, segment["qty"][1])
                elif product["tier_name"] == "大众档":
                    quantity = rnd.randint(1, min(segment["qty"][1], 2))
                else:
                    quantity = 1
                subtotal = round(product["price"] * quantity, 2)
                original_amount = round(original_amount + subtotal, 2)
                order_items.append((product, quantity, subtotal))

            discount_rate = 0.0
            if original_amount >= 300 and rnd.random() < 0.32:
                discount_rate = 0.03 + rnd.random() * 0.09
            elif original_amount >= 100 and rnd.random() < 0.18:
                discount_rate = 0.02 + rnd.random() * 0.05
            discount_amount = round(min(original_amount * discount_rate, original_amount * 0.18), 2)
            total_amount = round(original_amount - discount_amount, 2)
            pay_dt = None if status in [0, 4] else order_time + timedelta(minutes=rnd.randint(8, 180))
            pay_time = pay_dt.strftime("%Y-%m-%d %H:%M:%S") if pay_dt else None
            if status == 0:
                order_update_time = order_time.strftime("%Y-%m-%d %H:%M:%S")
            elif status == 1 and pay_dt:
                order_update_time = pay_dt.strftime("%Y-%m-%d %H:%M:%S")
            elif status == 2 and pay_dt:
                ship_dt = pay_dt + timedelta(hours=rnd.randint(6, 36))
                order_update_time = ship_dt.strftime("%Y-%m-%d %H:%M:%S")
            elif status == 3 and pay_dt:
                ship_dt = pay_dt + timedelta(hours=rnd.randint(6, 30))
                complete_dt = ship_dt + timedelta(days=rnd.randint(2, 8), hours=rnd.randint(0, 12))
                order_update_time = complete_dt.strftime("%Y-%m-%d %H:%M:%S")
            else:
                cancel_dt = order_time + timedelta(minutes=rnd.randint(10, 240))
                order_update_time = cancel_dt.strftime("%Y-%m-%d %H:%M:%S")
            order_no = f"ORD{user_id:04d}{order_id:06d}"
            address_ref = user_primary_address[user_id]
            order_rows.append((
                order_id,
                user_id,
                order_no,
                total_amount,
                original_amount,
                discount_amount,
                None,
                None,
                None,
                status,
                f"{address_ref[2]}{address_ref[3]}{address_ref[4]}{address_ref[5]}",
                address_ref[0],
                address_ref[1],
                f"订单备注：用户分层={segment['name']}，商品数={len(order_items)}",
                pay_time,
                order_time.strftime("%Y-%m-%d %H:%M:%S"),
                order_update_time,
            ))
            order_create_time_str = order_time.strftime("%Y-%m-%d %H:%M:%S")
            order_record = {
                "order_id": order_id,
                "order_no": order_no,
                "status": status,
                "create_time": order_create_time_str,
                "pay_time": pay_time,
                "total_amount": total_amount,
                "original_amount": original_amount,
                "discount_amount": discount_amount,
                "product_ids": [product["id"] for product, _, _ in order_items],
                "category_names": sorted({product["main_name"] for product, _, _ in order_items}),
            }
            user_order_records[user_id].append(order_record)
            if status in [1, 2, 3]:
                paid_ref_time = pay_dt or order_time
                paid_ref_time_str = paid_ref_time.strftime("%Y-%m-%d %H:%M:%S")
                user_last_paid_time[user_id] = keep_latest_time(user_last_paid_time.get(user_id), paid_ref_time_str)
                if paid_ref_time >= cutoff_90d:
                    user_order_count_90d[user_id] += 1
                    user_order_amount_90d[user_id] = round(user_order_amount_90d[user_id] + total_amount, 2)
                    for product, _, _ in order_items:
                        user_distinct_category_90d[user_id].add(product["main_name"])

            if status not in [0, 4]:
                if user_balances[user_id] < total_amount:
                    recharge_amount = round(max(total_amount * 1.3, 200 + rnd.randint(0, 600)), 2)
                    balance_before = user_balances[user_id]
                    balance_after = round(balance_before + recharge_amount, 2)
                    wallet_rows.append((
                        wallet_id,
                        user_id,
                        "recharge",
                        recharge_amount,
                        balance_before,
                        balance_after,
                        None,
                        "余额不足自动充值",
                        (order_time - timedelta(minutes=rnd.randint(20, 300))).strftime("%Y-%m-%d %H:%M:%S"),
                    ))
                    wallet_id += 1
                    user_balances[user_id] = balance_after
                balance_before = user_balances[user_id]
                balance_after = round(balance_before - total_amount, 2)
                wallet_rows.append((
                    wallet_id,
                    user_id,
                    "pay",
                    -total_amount,
                    balance_before,
                    balance_after,
                    order_no,
                    "订单支付",
                    pay_time,
                ))
                wallet_id += 1
                user_balances[user_id] = balance_after

            order_has_refund = status == 3 and rnd.random() < 0.035
            refund_amount = 0.0
            if order_has_refund:
                refund_amount = round(total_amount * (0.25 + rnd.random() * 0.75), 2)
                refund_time = order_time + timedelta(days=rnd.randint(3, 20))
                refund_time_str = refund_time.strftime("%Y-%m-%d %H:%M:%S")
                refund_rows.append((
                    refund_id,
                    order_id,
                    user_id,
                    "商品与预期不符",
                    "用户申请售后，系统已模拟完成退款流程。",
                    json_text([]),
                    refund_amount,
                    3,
                    None,
                    refund_time_str,
                    refund_time_str,
                ))
                refund_id += 1
                balance_before = user_balances[user_id]
                balance_after = round(balance_before + refund_amount, 2)
                wallet_rows.append((
                    wallet_id,
                    user_id,
                    "refund",
                    refund_amount,
                    balance_before,
                    balance_after,
                    order_no,
                    "订单退款",
                    refund_time_str,
                ))
                wallet_id += 1
                user_balances[user_id] = balance_after
                order_update_time = keep_latest_time(order_update_time, refund_time_str)

            review_candidates = []
            for product, quantity, subtotal in order_items:
                order_item_rows.append((
                    order_item_id,
                    order_id,
                    product["id"],
                    None,
                    None,
                    product["name"],
                    product["image"],
                    product["price"],
                    quantity,
                    subtotal,
                ))

                for view_index in range(rnd.randint(2, 5)):
                    behavior_time = order_time - timedelta(days=rnd.randint(0, 9), hours=rnd.randint(0, 20), minutes=rnd.randint(0, 50))
                    behavior_time_str = behavior_time.strftime("%Y-%m-%d %H:%M:%S")
                    duration = rnd.randint(12, 180)
                    trace_id = f"trace-{user_id}-{order_id}-{product['id']}-{view_index}"
                    token = f"token-{user_id}-{product['id']}-{order_id}-{view_index}"
                    scene = SCENE_POOL[(view_index + user_id) % len(SCENE_POOL)]
                    behavior_rows.append((behavior_id, user_id, product["id"], "view", None, duration, behavior_time_str))
                    behavior_id += 1
                    behavior_counter[user_id]["view"] += 1
                    behavior_last_time[user_id]["view"] = keep_latest_time(behavior_last_time[user_id].get("view"), behavior_time_str)
                    user_behavior_days[user_id].add(behavior_time_str[:10])
                    category_behavior_counter[(user_id, product["category_id"], product["main_name"])] += 1
                    category_behavior_last_time[(user_id, product["category_id"], product["main_name"])] = keep_latest_time(
                        category_behavior_last_time.get((user_id, product["category_id"], product["main_name"])),
                        behavior_time_str,
                    )
                    user_category_counter[user_id][product["main_name"]] += 1
                    user_tag_counter[user_id][product["sub_name"]] += 1
                    user_price_points[user_id].append(product["price"])
                    user_product_interest[user_id][product["id"]] += 1
                    user_seen_products[user_id].add(product["id"])
                    product_behavior_counter[product["id"]]["behavior_count"] += 1
                    product_behavior_counter[product["id"]]["category_id"] = product["category_id"]
                    product_behavior_counter[product["id"]]["last_event_time"] = behavior_time_str
                    if behavior_time >= cutoff_30d:
                        user_recent_behavior_count_30d[user_id] += 1
                        user_recent_behavior_by_type_30d[user_id]["view"] += 1
                        user_recent_active_days_30d[user_id].add(behavior_time_str[:10])
                        user_recent_duration_sum_30d[user_id] += duration
                        user_recent_duration_count_30d[user_id] += 1
                    event_rows.append((
                        event_id,
                        user_id,
                        product["id"],
                        "exposure",
                        scene,
                        trace_id,
                        token,
                        "A",
                        duration,
                        None,
                        None,
                        behavior_time_str,
                        json_text({"tier": product["tier_name"], "category": product["main_name"]}),
                        behavior_time_str,
                    ))
                    event_id += 1

                    click_rate = 0.18 if product["tier_name"] == "高端档" else 0.24 if product["tier_name"] == "进阶档" else 0.31 if product["tier_name"] == "大众档" else 0.27
                    if rnd.random() < click_rate:
                        click_time = behavior_time + timedelta(seconds=rnd.randint(2, 45))
                        click_time_str = click_time.strftime("%Y-%m-%d %H:%M:%S")
                        event_rows.append((
                            event_id,
                            user_id,
                            product["id"],
                            "click",
                            scene,
                            trace_id,
                            token,
                            "A",
                            None,
                            None,
                            None,
                            click_time_str,
                            json_text({"tier": product["tier_name"], "category": product["main_name"]}),
                            click_time_str,
                        ))
                        event_id += 1
                        if duration >= 25 and rnd.random() < 0.72:
                            dwell_time = click_time + timedelta(seconds=rnd.randint(8, min(duration, 180)))
                            dwell_time_str = dwell_time.strftime("%Y-%m-%d %H:%M:%S")
                            event_rows.append((
                                event_id,
                                user_id,
                                product["id"],
                                "dwell",
                                scene,
                                trace_id,
                                token,
                                "A",
                                duration,
                                None,
                                None,
                                dwell_time_str,
                                json_text({"tier": product["tier_name"], "category": product["main_name"]}),
                                dwell_time_str,
                            ))
                            event_id += 1

                if rnd.random() < 0.75:
                    cart_time = order_time - timedelta(days=rnd.randint(0, 4), hours=rnd.randint(0, 10))
                    cart_time_str = cart_time.strftime("%Y-%m-%d %H:%M:%S")
                    behavior_rows.append((behavior_id, user_id, product["id"], "cart", None, None, cart_time_str))
                    behavior_id += 1
                    behavior_counter[user_id]["cart"] += 1
                    behavior_last_time[user_id]["cart"] = keep_latest_time(behavior_last_time[user_id].get("cart"), cart_time_str)
                    user_behavior_days[user_id].add(cart_time_str[:10])
                    user_product_interest[user_id][product["id"]] += 3
                    user_seen_products[user_id].add(product["id"])
                    product_behavior_counter[product["id"]]["behavior_count"] += 1
                    if cart_time >= cutoff_30d:
                        user_recent_behavior_count_30d[user_id] += 1
                        user_recent_behavior_by_type_30d[user_id]["cart"] += 1
                        user_recent_active_days_30d[user_id].add(cart_time_str[:10])
                    event_rows.append((
                        event_id,
                        user_id,
                        product["id"],
                        "add_cart",
                        "detail",
                        f"trace-{user_id}-{order_id}-cart",
                        f"token-{user_id}-{product['id']}-cart",
                        "A",
                        None,
                        None,
                        None,
                        cart_time_str,
                        json_text({"tier": product["tier_name"]}),
                        cart_time_str,
                    ))
                    event_id += 1

                if rnd.random() < 0.32:
                    favorite_key = (user_id, product["id"])
                    if favorite_key not in favorite_set:
                        favorite_set.add(favorite_key)
                        favorite_time = order_time - timedelta(days=rnd.randint(0, 12), hours=rnd.randint(0, 22))
                        favorite_time_str = favorite_time.strftime("%Y-%m-%d %H:%M:%S")
                        favorite_rows.append((favorite_id, user_id, product["id"], favorite_time_str))
                        favorite_id += 1
                        behavior_rows.append((behavior_id, user_id, product["id"], "favorite", None, None, favorite_time_str))
                        behavior_id += 1
                        behavior_counter[user_id]["favorite"] += 1
                        behavior_last_time[user_id]["favorite"] = keep_latest_time(behavior_last_time[user_id].get("favorite"), favorite_time_str)
                        user_behavior_days[user_id].add(favorite_time_str[:10])
                        user_product_interest[user_id][product["id"]] += 4
                        user_seen_products[user_id].add(product["id"])
                        if favorite_time >= cutoff_30d:
                            user_recent_behavior_count_30d[user_id] += 1
                            user_recent_behavior_by_type_30d[user_id]["favorite"] += 1
                            user_recent_active_days_30d[user_id].add(favorite_time_str[:10])

                search_keyword = product["sub_name"] if rnd.random() < 0.65 else product["main_name"]
                search_counter[user_id][search_keyword] += 1
                search_time = order_time - timedelta(days=rnd.randint(0, 15), hours=rnd.randint(0, 23))
                search_time_str = search_time.strftime("%Y-%m-%d %H:%M:%S")
                search_first_time[(user_id, search_keyword)] = keep_earliest_time(search_first_time.get((user_id, search_keyword)), search_time_str)
                search_last_time[(user_id, search_keyword)] = keep_latest_time(search_last_time.get((user_id, search_keyword)), search_time_str)
                behavior_rows.append((behavior_id, user_id, None, "search", search_keyword, None, search_time_str))
                behavior_id += 1
                behavior_counter[user_id]["search"] += 1
                behavior_last_time[user_id]["search"] = keep_latest_time(behavior_last_time[user_id].get("search"), search_time_str)
                user_behavior_days[user_id].add(search_time_str[:10])
                if search_time >= cutoff_30d:
                    user_recent_behavior_count_30d[user_id] += 1
                    user_recent_behavior_by_type_30d[user_id]["search"] += 1
                    user_recent_active_days_30d[user_id].add(search_time_str[:10])

                if status == 3:
                    purchase_time = (order_time + timedelta(minutes=rnd.randint(20, 260))).strftime("%Y-%m-%d %H:%M:%S")
                    behavior_rows.append((behavior_id, user_id, product["id"], "purchase", None, None, purchase_time))
                    behavior_id += 1
                    behavior_counter[user_id]["purchase"] += 1
                    behavior_last_time[user_id]["purchase"] = keep_latest_time(behavior_last_time[user_id].get("purchase"), purchase_time)
                    user_behavior_days[user_id].add(purchase_time[:10])
                    user_product_interest[user_id][product["id"]] += 8
                    user_seen_products[user_id].add(product["id"])
                    user_purchased_products[user_id].add(product["id"])
                    product_behavior_counter[product["id"]]["behavior_count"] += quantity
                    product_behavior_counter[product["id"]]["purchase_count"] += quantity
                    product_behavior_counter[product["id"]]["last_event_time"] = keep_latest_time(
                        product_behavior_counter[product["id"]]["last_event_time"],
                        purchase_time,
                    )
                    purchase_dt = datetime.strptime(purchase_time, "%Y-%m-%d %H:%M:%S")
                    if purchase_dt >= cutoff_30d:
                        user_recent_behavior_count_30d[user_id] += 1
                        user_recent_behavior_by_type_30d[user_id]["purchase"] += 1
                        user_recent_active_days_30d[user_id].add(purchase_time[:10])
                    event_rows.append((
                        event_id,
                        user_id,
                        product["id"],
                        "order",
                        "order_pay",
                        f"trace-{user_id}-{order_id}-pay",
                        f"token-{user_id}-{product['id']}-pay",
                        "A",
                        None,
                        order_id,
                        subtotal,
                        purchase_time,
                        json_text({"quantity": quantity, "tier": product["tier_name"]}),
                        purchase_time,
                    ))
                    event_id += 1
                    review_candidates.append(product)

                order_item_id += 1

            if status == 3 and review_candidates:
                for product in review_candidates:
                    if rnd.random() < 0.34:
                        review_rating = 5 if rnd.random() < 0.55 else 4 if rnd.random() < 0.88 else 3
                        helpful_count = rnd.randint(0, 8)
                        review_tags = REVIEW_TAG_POOL[product["tier_name"]][:2 + rnd.randint(0, 1)]
                        review_time = order_time + timedelta(days=rnd.randint(3, 25))
                        reply_text = "感谢支持，欢迎下次再来。" if rnd.random() < 0.52 else None
                        review_rows.append((
                            review_id,
                            user_id,
                            product["id"],
                            order_id,
                            review_rating,
                            build_review_content(product, review_rating),
                            json_text([]),
                            json_text([]),
                            json_text(review_tags),
                            None,
                            json_text([]),
                            json_text([]),
                            None,
                            helpful_count,
                            reply_text,
                            review_time.strftime("%Y-%m-%d %H:%M:%S") if reply_text else None,
                            1,
                            review_time.strftime("%Y-%m-%d %H:%M:%S"),
                        ))
                        vote_pool = [candidate for candidate in user_ids if candidate != user_id]
                        vote_user_candidates = rnd.sample(vote_pool, k=min(helpful_count, len(vote_pool)))
                        for vote_user in vote_user_candidates:
                            vote_rows.append((
                                vote_id,
                                review_id,
                                vote_user,
                                f"device-{vote_user % 97}-{review_id % 31}",
                                review_time.strftime("%Y-%m-%d %H:%M:%S"),
                            ))
                            vote_id += 1
                        review_id += 1

            order_rows[-1] = order_rows[-1][:-1] + (order_update_time,)
            message_rows.append((
                message_id,
                user_id,
                "订单状态更新",
                f"订单 {order_no} 当前状态已更新，实付金额 {total_amount:.2f} 元。",
                "order",
                order_id,
                1 if rnd.random() < 0.38 else 0,
                order_update_time,
            ))
            message_id += 1
            if rnd.random() < 0.42:
                promo_time = (order_time - timedelta(days=1, hours=rnd.randint(0, 10))).strftime("%Y-%m-%d %H:%M:%S")
                message_rows.append((
                    message_id,
                    user_id,
                    "限时优惠提醒",
                    f"{preferred_categories[0]}频道有新的活动和优惠券，推荐你关注。",
                    "promotion",
                    None,
                    1 if rnd.random() < 0.46 else 0,
                    promo_time,
                ))
                message_id += 1

            order_id += 1

        for cart_index in range(rnd.randint(0, 3)):
            category_name = preferred_categories[cart_index % len(preferred_categories)]
            product = products_by_category[category_name][(user_id + cart_index * 7) % len(products_by_category[category_name])]
            cart_key = (user_id, product["id"])
            if cart_key in cart_set:
                continue
            cart_set.add(cart_key)
            cart_rows.append((
                cart_id,
                user_id,
                product["id"],
                None,
                None,
                1 + (1 if product["tier_name"] in ["入门档", "大众档"] and rnd.random() < 0.28 else 0),
                1,
                NOW,
                NOW,
            ))
            cart_id += 1

    for user_id in user_ids:
        category_pref = normalize_counter_to_points(user_category_counter[user_id], 5)
        tag_pref = normalize_counter_to_points(user_tag_counter[user_id], 6)
        min_price = round(min(user_price_points[user_id]), 2) if user_price_points[user_id] else 0.0
        max_price = round(max(user_price_points[user_id]), 2) if user_price_points[user_id] else 0.0
        preference_rows.append((
            preference_id,
            user_id,
            json_text(category_pref),
            json_text(tag_pref),
            min_price,
            max_price,
            NOW,
        ))
        preference_id += 1

        for keyword, count_value in search_counter[user_id].most_common(5):
            search_rows.append((
                search_id,
                user_id,
                keyword,
                count_value,
                search_first_time.get((user_id, keyword), NOW),
                search_last_time.get((user_id, keyword), NOW),
            ))
            search_id += 1

        for behavior_type, count_value in behavior_counter[user_id].items():
            stream_behavior_rows.append((
                user_id,
                behavior_type,
                count_value,
                behavior_last_time[user_id].get(behavior_type, NOW),
                NOW,
            ))

        for (uid, category_id, category_name), score in category_behavior_counter.items():
            if uid != user_id:
                continue
            stream_category_rows.append((
                user_id,
                category_id,
                category_name,
                round(score * 1.0, 2),
                score,
                category_behavior_last_time.get((uid, category_id, category_name), NOW),
                NOW,
            ))

        user_balance_updates.append((round(user_balances[user_id], 2), user_id))

    for product_id, metrics in product_behavior_counter.items():
        stream_hot_rows.append((
            product_id,
            metrics["category_id"],
            round(metrics["behavior_count"] * 1.2 + metrics["purchase_count"] * 6.5, 2),
            metrics["behavior_count"],
            metrics["purchase_count"],
            metrics["last_event_time"],
            NOW,
        ))

    commerce_summary = {
        "generated_at": NOW,
        "start_id": start_id,
        "count": count,
        "users": {},
    }
    for user_id in user_ids:
        last_behavior_time = max(behavior_last_time[user_id].values()) if behavior_last_time[user_id] else None
        recency_order_days = 9999
        if user_last_paid_time.get(user_id):
            recency_order_days = max(
                0,
                (now_ref - datetime.strptime(user_last_paid_time[user_id], "%Y-%m-%d %H:%M:%S")).days,
            )
        recency_behavior_days = 9999
        if last_behavior_time:
            recency_behavior_days = max(
                0,
                (now_ref - datetime.strptime(last_behavior_time, "%Y-%m-%d %H:%M:%S")).days,
            )
        price_min = round(min(user_price_points[user_id]), 2) if user_price_points[user_id] else 0.0
        price_max = round(max(user_price_points[user_id]), 2) if user_price_points[user_id] else 0.0
        avg_duration_30d = 0.0
        if user_recent_duration_count_30d[user_id] > 0:
            avg_duration_30d = round(user_recent_duration_sum_30d[user_id] / user_recent_duration_count_30d[user_id], 2)
        commerce_summary["users"][str(user_id)] = {
            "user_id": user_id,
            "segment_name": choose_segment(user_id)["name"],
            "order_refs": user_order_records[user_id],
            "category_preferences": normalize_counter_to_points(user_category_counter[user_id], 5),
            "tag_preferences": normalize_counter_to_points(user_tag_counter[user_id], 6),
            "search_keywords": [key for key, _ in search_counter[user_id].most_common(5)],
            "price_range_min": price_min,
            "price_range_max": price_max,
            "top_categories": [key for key, _ in user_category_counter[user_id].most_common(5)],
            "top_tags": [key for key, _ in user_tag_counter[user_id].most_common(6)],
            "interest_product_ids": [product_id for product_id, _ in user_product_interest[user_id].most_common(30)],
            "seen_product_ids": sorted(user_seen_products[user_id]),
            "purchased_product_ids": sorted(user_purchased_products[user_id]),
            "behavior_counts": dict(behavior_counter[user_id]),
            "recent_behavior_counts_30d": dict(user_recent_behavior_by_type_30d[user_id]),
            "total_behaviors": sum(behavior_counter[user_id].values()),
            "behavior_count_30d": user_recent_behavior_count_30d[user_id],
            "active_days_total": len(user_behavior_days[user_id]),
            "active_days_30d": len(user_recent_active_days_30d[user_id]),
            "avg_duration_30d": avg_duration_30d,
            "order_count_90d": user_order_count_90d[user_id],
            "order_amount_90d": round(user_order_amount_90d[user_id], 2),
            "distinct_category_count_90d": len(user_distinct_category_90d[user_id]),
            "recency_order_days": recency_order_days,
            "recency_behavior_days": recency_behavior_days,
            "last_paid_time": user_last_paid_time.get(user_id),
            "last_behavior_time": last_behavior_time,
        }
    write_json_file(summary_path_for_chunk(output), commerce_summary)

    with chunk_writer(output, f"Seed Chunk Commerce - 订单行为评价 {start_id} 到 {start_id + count - 1}") as handle:
        handle.write("SET NAMES utf8mb4;\n\n")
        write_insert(handle, "address", [
            "id", "user_id", "receiver_name", "receiver_phone", "province", "city", "district",
            "detail", "is_default", "create_time", "update_time",
        ], address_rows)
        write_insert(handle, "order", [
            "id", "user_id", "order_no", "total_amount", "original_amount", "discount_amount",
            "user_coupon_id", "seckill_activity_id", "seckill_apply_id", "status", "address",
            "receiver_name", "receiver_phone", "remark", "pay_time", "create_time", "update_time",
        ], order_rows)
        write_insert(handle, "order_item", [
            "id", "order_id", "product_id", "sku_id", "sku_name", "product_name", "product_image",
            "price", "quantity", "subtotal",
        ], order_item_rows)
        write_insert(handle, "wallet_transaction", [
            "id", "user_id", "type", "amount", "balance_before", "balance_after", "order_no", "description", "create_time",
        ], wallet_rows)
        write_insert(handle, "user_behavior", [
            "id", "user_id", "product_id", "behavior_type", "search_keyword", "duration", "create_time",
        ], behavior_rows, batch_size=500)
        write_insert(handle, "recommendation_event", [
            "id", "user_id", "product_id", "event_type", "scene", "trace_id", "recommendation_token",
            "experiment_group", "duration", "order_id", "amount", "event_time", "metadata", "create_time",
        ], event_rows, batch_size=500)
        write_insert(handle, "user_favorite", [
            "id", "user_id", "product_id", "create_time",
        ], favorite_rows)
        write_insert(handle, "user_preference", [
            "id", "user_id", "category_preferences", "tag_preferences", "price_range_min", "price_range_max", "update_time",
        ], preference_rows)
        write_insert(handle, "cart_item", [
            "id", "user_id", "product_id", "sku_id", "sku_name", "quantity", "selected", "create_time", "update_time",
        ], cart_rows)
        write_insert(handle, "product_review", [
            "id", "user_id", "product_id", "order_id", "rating", "content", "images", "video_urls", "tags",
            "append_content", "append_images", "append_video_urls", "append_time", "helpful_count",
            "reply", "reply_time", "status", "create_time",
        ], review_rows)
        write_insert(handle, "product_review_vote", [
            "id", "review_id", "user_id", "device_fingerprint", "create_time",
        ], vote_rows)
        write_insert(handle, "refund_request", [
            "id", "order_id", "user_id", "reason", "description", "images", "amount", "status",
            "reject_reason", "create_time", "update_time",
        ], refund_rows)
        write_insert(handle, "search_history", [
            "id", "user_id", "keyword", "search_count", "create_time", "update_time",
        ], search_rows)
        write_insert(handle, "message", [
            "id", "user_id", "title", "content", "type", "related_id", "is_read", "create_time",
        ], message_rows)
        write_insert(handle, "stream_user_behavior_distribution", [
            "user_id", "behavior_type", "behavior_count", "last_event_time", "update_time",
        ], stream_behavior_rows)
        write_insert(handle, "stream_user_category_preference", [
            "user_id", "category_id", "category_name", "preference_score", "behavior_count", "last_event_time", "update_time",
        ], stream_category_rows)
        write_insert(handle, "stream_product_hotness_realtime", [
            "product_id", "category_id", "hot_score", "behavior_count", "purchase_count", "last_event_time", "update_time",
        ], stream_hot_rows)
        for balance, user_id in user_balance_updates:
            handle.write(f"UPDATE `user` SET `balance` = {balance}, `update_time` = '{NOW}' WHERE `id` = {user_id};\n")

    print(f"[ok] 已生成交易行为块: {output}")
    print(f"     地址: {len(address_rows)} 条, 订单: {len(order_rows)} 条, 订单明细: {len(order_item_rows)} 条")
    print(f"     行为: {len(behavior_rows)} 条, 事件: {len(event_rows)} 条, 评价: {len(review_rows)} 条, 收藏: {len(favorite_rows)} 条")


def build_extended_chunk(output: Path):
    rnd = random.Random(20260418 + 4096)
    now_dt = datetime(2026, 4, 18, 20, 0, 0)
    snapshot_date = now_dt.strftime("%Y-%m-%d")
    snapshot_date_obj = now_dt.date()
    main_rows, _, leaves = build_categories()
    categories = [row[1] for row in main_rows]
    leaf_by_main = defaultdict(list)
    for leaf in leaves:
        leaf_by_main[leaf["main_name"]].append(leaf)

    products = build_product_catalog(1, PLANNED_PRODUCT_TOTAL)
    products_by_category = defaultdict(list)
    hot_products = sorted(products, key=lambda item: (item["sales_count"], item["rating"]), reverse=True)
    for product in products:
        products_by_category[product["main_name"]].append(product)

    admin_ids = list(range(1, ADMIN_COUNT + 1))
    merchant_ids = list(range(MERCHANT_START_ID, MERCHANT_START_ID + MERCHANT_COUNT))
    normal_user_ids = list(range(NORMAL_USER_START_ID, NORMAL_USER_START_ID + NORMAL_USER_COUNT))
    coupon_ids = list(range(1, len(leaves) * 2 + 1))

    def username_for(user_id: int) -> str:
        if user_id <= ADMIN_COUNT:
            return ADMIN_USERS[user_id - 1][0]
        if user_id < NORMAL_USER_START_ID:
            return f"merchant{user_id - ADMIN_COUNT:03d}"
        return f"user{user_id - NORMAL_USER_START_ID + 1:04d}"

    def nickname_for(user_id: int) -> str:
        if user_id <= ADMIN_COUNT:
            return ADMIN_USERS[user_id - 1][1]
        if user_id < NORMAL_USER_START_ID:
            return f"商家{user_id - ADMIN_COUNT:03d}"
        offset = user_id - NORMAL_USER_START_ID + 1
        return f"{SURNAMES[(offset - 1) % len(SURNAMES)]}{GIVEN_NAMES[(offset * 3) % len(GIVEN_NAMES)]}"

    def segment_to_kmeans(user_id: int) -> tuple[str, str, str]:
        segment_name = choose_segment(user_id)["name"]
        mapping = {
            "家庭常购型": ("S1", "高频家庭复购", "订单频次高、购物稳定、偏好家庭常购和实用型商品。"),
            "品质升级型": ("S2", "品质升级用户", "客单价较高，愿意为品牌、质感和体验买单。"),
            "谨慎囤货型": ("S3", "价格敏感囤货", "擅长囤货和优惠券驱动，偏向高性价比商品。"),
            "旗舰尝鲜型": ("S4", "旗舰尝鲜用户", "偏好新品和高端款，决策快但订单频次不高。"),
            "低频浏览型": ("S5", "低频观望用户", "浏览多于成交，需要唤醒和活动触达。"),
        }
        return mapping[segment_name]

    commerce_summaries = load_commerce_summaries()
    if not commerce_summaries:
        raise RuntimeError("未找到 commerce summary，请先生成 commerce 分块。")

    platform_coupon_ids_by_main = defaultdict(list)
    for leaf_index, leaf in enumerate(leaves, start=1):
        platform_coupon_ids_by_main[leaf["main_name"]].append((leaf_index - 1) * 2 + 1)

    def safe_top_categories(summary: dict) -> list[str]:
        values = list(summary.get("top_categories", []))
        return values if values else categories[:3]

    def safe_top_tags(summary: dict) -> list[str]:
        values = list(summary.get("top_tags", []))
        return values if values else ["平台热卖", "推荐优选"]

    def is_cold_start_user(summary: dict) -> bool:
        return summary.get("order_count_90d", 0) == 0 and summary.get("behavior_count_30d", 0) < 12

    def classify_summary(summary: dict) -> tuple[str, str, str]:
        if is_cold_start_user(summary):
            return "S0", "冷启动观察池", "近 90 天订单少且行为样本不足，需依赖热门与规则兜底。"
        order_count_90d = summary.get("order_count_90d", 0)
        order_amount_90d = float(summary.get("order_amount_90d", 0) or 0)
        behavior_count_30d = summary.get("behavior_count_30d", 0)
        avg_order_amount_90d = order_amount_90d / max(order_count_90d, 1)
        recency_order_days = summary.get("recency_order_days", 9999)
        if order_count_90d >= 6 and avg_order_amount_90d < 650:
            return "S1", "高频家庭复购", "订单频次高、价格带稳定，适合家庭刚需复购策略。"
        if avg_order_amount_90d >= 850 and order_amount_90d >= 2400:
            return "S2", "品质升级用户", "客单价较高，愿意为品牌、质感和体验买单。"
        if behavior_count_30d >= 18 and order_count_90d <= 2 and recency_order_days >= 20:
            return "S5", "低频观望用户", "浏览活跃但转化偏低，需要唤醒和触达。"
        if avg_order_amount_90d >= 1000 and order_count_90d <= 3:
            return "S4", "旗舰尝鲜用户", "愿意购买高价单品，偏好新品和旗舰商品。"
        return "S3", "价格敏感囤货", "客单价适中，常受优惠与囤货活动驱动。"

    seckill_rows = []
    seckill_apply_rows = []
    user_coupon_rows = []
    mq_outbox_rows = []
    mq_consume_rows = []
    profile_change_rows = []
    operation_rows = []
    support_agent_rows = []
    conversation_rows = []
    im_message_rows = []
    ticket_rows = []
    analytics_job_rows = []
    behavior_daily_rows = []
    funnel_rows = []
    heatmap_rows = []
    sales_daily_rows = []
    rfm_user_rows = []
    rfm_segment_rows = []
    profile_snapshot_rows = []
    recommendation_rows = []
    exposure_rows = []
    similarity_rows = []
    association_rows = []
    report_rows = []
    kmeans_task_rows = []
    kmeans_segment_rows = []
    kmeans_user_rows = []
    kmeans_feature_rows = []
    order_coupon_updates = []

    coupon_issue_counter = Counter()
    coupon_bound_order_ids = set()

    activity_specs = [
        ("今夜爆款秒杀", now_dt - timedelta(days=1, hours=4), now_dt - timedelta(days=1, hours=-4), 1),
        ("家电焕新专场", now_dt - timedelta(hours=10), now_dt + timedelta(hours=14), 1),
        ("数码尖货抢购", now_dt + timedelta(hours=8), now_dt + timedelta(hours=20), 1),
        ("周末母婴福利场", now_dt + timedelta(days=1, hours=9), now_dt + timedelta(days=1, hours=23), 1),
        ("运动户外限量场", now_dt + timedelta(days=2, hours=10), now_dt + timedelta(days=2, hours=22), 1),
        ("宠物生活品牌日", now_dt + timedelta(days=3, hours=9), now_dt + timedelta(days=3, hours=23), 0),
        ("图书文具精选秒杀", now_dt - timedelta(days=3, hours=8), now_dt - timedelta(days=3, hours=-6), 0),
        ("家居家装半价夜", now_dt + timedelta(days=4, hours=10), now_dt + timedelta(days=4, hours=23), 0),
    ]
    seckill_apply_id = 1
    for activity_id, (name, start_time, end_time, publish_status) in enumerate(activity_specs, start=1):
        seckill_rows.append((
            activity_id,
            name,
            placeholder_image("秒杀", name[:8], "DC2626", 1200, 420),
            f"{name}，覆盖品牌爆款、库存限量与实时转化演示场景。",
            start_time.strftime("%Y-%m-%d %H:%M:%S"),
            end_time.strftime("%Y-%m-%d %H:%M:%S"),
            publish_status,
            activity_id,
            NOW,
            NOW,
        ))
        base_index = (activity_id - 1) * 37
        for offset in range(24):
            product = products[(base_index + offset * 7) % len(products)]
            audit_status = 1 if offset % 8 not in [5, 7] else 0 if offset % 8 == 5 else 2
            seckill_stock = 20 + (offset % 6) * 15
            sold_count = min(seckill_stock, 2 + (offset * 3) % max(seckill_stock, 3)) if audit_status == 1 and end_time <= now_dt else 0
            reject_reason = "图片素材不符合活动规范" if audit_status == 2 else None
            audit_time = (start_time - timedelta(hours=12)).strftime("%Y-%m-%d %H:%M:%S") if audit_status in [1, 2] else None
            seckill_apply_rows.append((
                seckill_apply_id,
                activity_id,
                product["merchant_id"],
                product["id"],
                product["price"],
                round(product["price"] * (0.58 + (offset % 5) * 0.05), 2),
                seckill_stock,
                sold_count,
                1 + (offset % 2),
                audit_status,
                reject_reason,
                audit_time,
                (start_time - timedelta(days=2)).strftime("%Y-%m-%d %H:%M:%S"),
                NOW,
            ))
            seckill_apply_id += 1

    user_coupon_id = 80_000_001
    for user_id in normal_user_ids:
        summary = commerce_summaries.get(user_id, {})
        top_categories = safe_top_categories(summary)
        issued_coupon_ids = set()
        paid_orders = [
            order_ref for order_ref in summary.get("order_refs", [])
            if order_ref.get("status") in [1, 2, 3] and float(order_ref.get("discount_amount", 0) or 0) > 0
        ]
        paid_orders.sort(key=lambda item: item.get("create_time", ""), reverse=True)
        for order_ref in paid_orders[: min(3, len(top_categories))]:
            order_id = order_ref["order_id"]
            if order_id in coupon_bound_order_ids:
                continue
            candidate_categories = order_ref.get("category_names", []) or top_categories
            coupon_id = None
            for category_name in candidate_categories:
                for candidate_coupon_id in platform_coupon_ids_by_main.get(category_name, []):
                    if candidate_coupon_id not in issued_coupon_ids:
                        coupon_id = candidate_coupon_id
                        break
                if coupon_id is not None:
                    break
            if coupon_id is None:
                continue
            issued_coupon_ids.add(coupon_id)
            use_time = order_ref.get("pay_time") or order_ref.get("create_time")
            create_time = datetime.strptime(use_time, "%Y-%m-%d %H:%M:%S") - timedelta(days=2)
            user_coupon_rows.append((
                user_coupon_id,
                user_id,
                coupon_id,
                1,
                order_id,
                use_time,
                create_time.strftime("%Y-%m-%d %H:%M:%S"),
            ))
            order_coupon_updates.append((user_coupon_id, order_id))
            coupon_bound_order_ids.add(order_id)
            coupon_issue_counter[coupon_id] += 1
            user_coupon_id += 1

        extra_coupon_targets = top_categories[:3]
        for offset, category_name in enumerate(extra_coupon_targets, start=1):
            candidate_coupon_id = None
            for value in platform_coupon_ids_by_main.get(category_name, []):
                if value not in issued_coupon_ids:
                    candidate_coupon_id = value
                    break
            if candidate_coupon_id is None:
                continue
            issued_coupon_ids.add(candidate_coupon_id)
            status = 2 if offset == 3 and summary.get("recency_order_days", 9999) > 45 else 0
            create_time = now_dt - timedelta(days=(user_id + candidate_coupon_id + offset) % 45, hours=offset * 2)
            user_coupon_rows.append((
                user_coupon_id,
                user_id,
                candidate_coupon_id,
                status,
                None,
                None,
                create_time.strftime("%Y-%m-%d %H:%M:%S"),
            ))
            coupon_issue_counter[candidate_coupon_id] += 1
            user_coupon_id += 1

    outbox_id = 1
    consume_id = 1
    event_types = [
        ("order.paid", "ecommerce.order.exchange", "order.paid", "OrderPaidConsumer"),
        ("refund.completed", "ecommerce.order.exchange", "refund.done", "RefundCompletedConsumer"),
        ("coupon.issued", "ecommerce.marketing.exchange", "coupon.issued", "CouponIssuedConsumer"),
        ("recommend.exposure.sync", "ecommerce.recommend.exchange", "recommend.exposure", "RecommendationExposureConsumer"),
        ("seckill.apply.audit", "ecommerce.seckill.exchange", "seckill.audit", "SeckillAuditConsumer"),
    ]
    for index in range(1, 241):
        event_type, exchange_name, routing_key, consumer_name = event_types[(index - 1) % len(event_types)]
        created_time = now_dt - timedelta(days=index % 28, hours=index % 17, minutes=index % 43)
        status = "SENT" if index % 6 not in [0, 5] else "FAILED" if index % 6 == 5 else "NEW"
        next_retry_time = None
        sent_time = None
        error_message = None
        retry_count = 0
        if status == "SENT":
            sent_time = (created_time + timedelta(minutes=2 + index % 9)).strftime("%Y-%m-%d %H:%M:%S")
        elif status == "FAILED":
            retry_count = 1 + index % 3
            next_retry_time = (created_time + timedelta(hours=2 + index % 5)).strftime("%Y-%m-%d %H:%M:%S")
            error_message = "Broker timeout, waiting for retry"
        else:
            next_retry_time = (created_time + timedelta(hours=1 + index % 4)).strftime("%Y-%m-%d %H:%M:%S")
        biz_id = str(1_000_000 + index * 9)
        payload = json_text({
            "bizId": biz_id,
            "eventType": event_type,
            "userId": normal_user_ids[index % len(normal_user_ids)],
            "productId": products[(index * 11) % len(products)]["id"],
        })
        event_key = f"evt-20260418-{index:05d}"
        mq_outbox_rows.append((
            outbox_id,
            event_key,
            event_type,
            exchange_name,
            routing_key,
            biz_id,
            payload,
            status,
            retry_count,
            next_retry_time,
            error_message,
            sent_time,
            created_time.strftime("%Y-%m-%d %H:%M:%S"),
            NOW,
        ))
        if status == "SENT":
            mq_consume_rows.append((
                consume_id,
                event_key,
                consumer_name,
                sent_time,
            ))
            consume_id += 1
            if index % 5 == 0:
                mq_consume_rows.append((
                    consume_id,
                    event_key,
                    "AuditTrailConsumer",
                    sent_time,
                ))
                consume_id += 1
        outbox_id += 1

    profile_change_id = 1
    for user_id in normal_user_ids[:120]:
        old_nickname = nickname_for(user_id)
        create_time = now_dt - timedelta(days=user_id % 36, hours=user_id % 15)
        status = 1 if user_id % 6 in [1, 2, 3] else 0 if user_id % 6 == 4 else 2
        review_time = None if status == 0 else (create_time + timedelta(hours=6 + user_id % 18)).strftime("%Y-%m-%d %H:%M:%S")
        reject_reason = "昵称含营销导流信息" if status == 2 else None
        reviewer_id = admin_ids[user_id % len(admin_ids)] if status != 0 else None
        profile_change_rows.append((
            profile_change_id,
            user_id,
            f"{old_nickname}优选",
            placeholder_image(old_nickname, "NEW", "2563EB", 320, 320),
            old_nickname,
            placeholder_image(old_nickname, "USER", "2563EB", 320, 320),
            status,
            reject_reason,
            review_time,
            reviewer_id,
            create_time.strftime("%Y-%m-%d %H:%M:%S"),
        ))
        profile_change_id += 1

    operation_id = 1
    operation_templates = [
        ("商品管理", "新增商品", "POST", "/admin/products"),
        ("订单管理", "发货处理", "POST", "/admin/orders/ship"),
        ("优惠券", "批量发券", "POST", "/admin/coupons/issue"),
        ("推荐配置", "更新策略", "PUT", "/admin/recommendation/config"),
        ("秒杀管理", "审核报名", "POST", "/admin/seckill/applications/audit"),
        ("客服中心", "转派工单", "POST", "/admin/im/tickets/assign"),
        ("用户管理", "审核资料", "POST", "/admin/users/profile-review"),
        ("分析看板", "导出报表", "GET", "/admin/analytics/export"),
    ]
    for index in range(1, 421):
        user_id = admin_ids[(index - 1) % len(admin_ids)] if index % 4 != 0 else merchant_ids[index % len(merchant_ids)]
        role = "admin" if user_id <= ADMIN_COUNT else "merchant"
        module, action, method, url = operation_templates[(index - 1) % len(operation_templates)]
        status = 0 if index % 19 == 0 else 1
        operation_rows.append((
            operation_id,
            user_id,
            username_for(user_id),
            role,
            module,
            action,
            method,
            url,
            json_text({"traceId": f"trace-op-{index:04d}", "index": index}),
            f"10.10.{(index % 24) + 1}.{(index % 220) + 10}",
            status,
            None if status == 1 else "接口返回 500，已记录告警",
            40 + (index * 7) % 380,
            (now_dt - timedelta(days=index % 14, hours=index % 12, minutes=index % 50)).strftime("%Y-%m-%d %H:%M:%S"),
        ))
        operation_id += 1

    support_user_ids = [4, 9, 2, 12]
    for agent_id, user_id in enumerate(support_user_ids, start=1):
        support_agent_rows.append((
            agent_id,
            user_id,
            f"{nickname_for(user_id)}客服",
            placeholder_image(nickname_for(user_id), "CS", "0F766E", 320, 320),
            "official",
            1 if agent_id != 3 else 0,
            1,
        ))

    conversation_id = 1
    im_message_id = 1
    ticket_id = 1
    for index in range(1, 241):
        product = products[(index * 17) % len(products)]
        user_id = normal_user_ids[(index * 7) % len(normal_user_ids)]
        merchant_id = product["merchant_id"]
        support_user_id = support_user_ids[index % len(support_user_ids)]
        create_time = now_dt - timedelta(days=index % 30, hours=index % 18, minutes=index % 50)
        is_escalated = 1 if index % 5 in [0, 3] else 0
        priority = "urgent" if index % 17 == 0 else "high" if is_escalated else "normal"
        status = "resolved" if index % 6 == 0 else "processing" if is_escalated else "open"
        message_total = 3 + index % 4 + (1 if is_escalated else 0)
        unread_user = 0
        unread_merchant = 0
        unread_support = 0
        last_message = None
        last_message_type = None
        last_sender_role = None
        last_sender_id = None
        last_message_time = None

        for msg_offset in range(message_total):
            msg_time = create_time + timedelta(minutes=msg_offset * (5 + index % 4))
            if msg_offset == 0:
                sender_role = "user"
                sender_id = user_id
                message_type = "text"
                content = f"你好，想咨询一下 {product['name']} 的发货和售后政策。"
                payload_json = None
            elif is_escalated and msg_offset == message_total - 1:
                sender_role = "admin"
                sender_id = support_user_id
                message_type = "system"
                content = "平台客服已介入，请双方在工单内补充信息。"
                payload_json = json_text({"ticketMode": True, "priority": priority})
            elif msg_offset == 1 and index % 4 == 0:
                sender_role = "merchant"
                sender_id = merchant_id
                message_type = "product_card"
                content = f"已为你推荐商品：{product['name']}"
                payload_json = json_text({"productId": product["id"], "productName": product["name"]})
            elif msg_offset == 2 and index % 6 == 0:
                sender_role = "merchant"
                sender_id = merchant_id
                message_type = "order_card"
                content = f"这是你关注商品的订单进度卡片：{product['name']}"
                payload_json = json_text({"orderNo": f"ORDIM{index:06d}", "productId": product["id"]})
            else:
                sender_role = "merchant" if msg_offset % 2 == 1 else "user"
                sender_id = merchant_id if sender_role == "merchant" else user_id
                message_type = "text"
                content = "库存充足，支持 48 小时内发货。" if sender_role == "merchant" else "可以的，我再确认一下规格和活动价。"
                payload_json = None

            if sender_role == "user":
                unread_merchant += 1
                unread_support += 1 if is_escalated else 0
            elif sender_role == "merchant":
                unread_user += 1
            else:
                unread_user += 1
                unread_merchant += 1

            last_message = content[:1000]
            last_message_type = message_type
            last_sender_role = sender_role
            last_sender_id = sender_id
            last_message_time = msg_time.strftime("%Y-%m-%d %H:%M:%S")
            im_message_rows.append((
                im_message_id,
                conversation_id,
                sender_role,
                sender_id,
                message_type,
                content,
                payload_json,
                1 if sender_role == "admin" and message_type == "system" else 0,
                last_message_time,
            ))
            im_message_id += 1

        if status == "resolved":
            unread_user = 0
            unread_merchant = 0
            unread_support = 0
        closed_time = (create_time + timedelta(hours=8 + index % 24)).strftime("%Y-%m-%d %H:%M:%S") if status == "resolved" and index % 3 == 0 else None
        conversation_rows.append((
            conversation_id,
            f"IM20260418{conversation_id:05d}",
            "support" if is_escalated else "merchant",
            user_id,
            merchant_id,
            support_user_id if is_escalated else None,
            1_000_000 + index * 9 if index % 4 == 0 else None,
            product["id"],
            "closed" if closed_time else status,
            is_escalated,
            priority,
            last_message,
            last_message_type,
            last_sender_role,
            last_sender_id,
            last_message_time,
            unread_user,
            unread_merchant,
            unread_support,
            closed_time,
            create_time.strftime("%Y-%m-%d %H:%M:%S"),
            NOW,
        ))
        if is_escalated or priority == "urgent":
            ticket_status = "resolved" if index % 4 == 0 else "processing"
            assigned_time = (create_time + timedelta(minutes=12)).strftime("%Y-%m-%d %H:%M:%S")
            resolved_time = (create_time + timedelta(hours=5 + index % 18)).strftime("%Y-%m-%d %H:%M:%S") if ticket_status == "resolved" else None
            ticket_rows.append((
                ticket_id,
                conversation_id,
                1_000_000 + index * 3 if index % 9 == 0 else None,
                f"TK20260418{ticket_id:05d}",
                ticket_status,
                "ai_transfer" if index % 7 == 0 else "user_support",
                "refund" if index % 4 == 0 else "logistics" if index % 3 == 0 else "product",
                f"{product['sub_name']}咨询升级处理",
                f"用户围绕 {product['name']} 提交升级诉求，需要平台客服跟进。",
                user_id,
                support_user_id,
                support_user_id if ticket_status == "resolved" else None,
                assigned_time,
                resolved_time,
                (create_time + timedelta(hours=4)).strftime("%Y-%m-%d %H:%M:%S"),
                1 if priority in ["high", "urgent"] else 0,
                (create_time + timedelta(hours=2)).strftime("%Y-%m-%d %H:%M:%S") if priority == "urgent" else None,
                create_time.strftime("%Y-%m-%d %H:%M:%S"),
                NOW,
            ))
            ticket_id += 1
        conversation_id += 1

    # analytics_job_rows are populated after analytical rows are derived so the counts stay self-consistent.

    behavior_types = ["view", "cart", "favorite", "purchase", "search"]
    behavior_daily_id = 1
    funnel_id = 1
    heatmap_id = 1
    sales_daily_id = 1
    for day_offset in range(30):
        day = snapshot_date_obj - timedelta(days=29 - day_offset)
        weekday = day.isoweekday()
        base_view = 4200 + weekday * 130 + day_offset * 26
        for behavior_type in behavior_types:
            if behavior_type == "view":
                event_count = base_view
                user_count = int(base_view * 0.29)
                product_count = int(base_view * 0.18)
                avg_duration = round(35 + weekday * 1.6 + day_offset * 0.2, 2)
            elif behavior_type == "cart":
                event_count = int(base_view * 0.23)
                user_count = int(base_view * 0.11)
                product_count = int(base_view * 0.09)
                avg_duration = round(18 + weekday * 0.9, 2)
            elif behavior_type == "favorite":
                event_count = int(base_view * 0.13)
                user_count = int(base_view * 0.07)
                product_count = int(base_view * 0.06)
                avg_duration = round(14 + weekday * 0.6, 2)
            elif behavior_type == "purchase":
                event_count = int(base_view * 0.09)
                user_count = int(base_view * 0.05)
                product_count = int(base_view * 0.05)
                avg_duration = None
            else:
                event_count = int(base_view * 0.17)
                user_count = int(base_view * 0.08)
                product_count = int(base_view * 0.07)
                avg_duration = None
            behavior_daily_rows.append((
                behavior_daily_id,
                day.strftime("%Y-%m-%d"),
                behavior_type,
                user_count,
                event_count,
                product_count,
                avg_duration,
                NOW,
                NOW,
            ))
            behavior_daily_id += 1

        view_user_count = int(base_view * 0.29)
        cart_user_count = int(view_user_count * 0.42)
        favorite_user_count = int(view_user_count * 0.25)
        purchase_user_count = int(cart_user_count * 0.48)
        funnel_rows.append((
            funnel_id,
            day.strftime("%Y-%m-%d"),
            view_user_count,
            cart_user_count,
            favorite_user_count,
            purchase_user_count,
            round(cart_user_count * 100 / max(view_user_count, 1), 2),
            round(purchase_user_count * 100 / max(cart_user_count, 1), 2),
            round(purchase_user_count * 100 / max(view_user_count, 1), 2),
            NOW,
            NOW,
        ))
        funnel_id += 1

    heatmap_types = ["all", "view", "cart", "favorite", "purchase", "search"]
    for day_offset in range(14):
        day = snapshot_date_obj - timedelta(days=13 - day_offset)
        weekday = day.isoweekday()
        for hour in range(24):
            hour_boost = 1.7 if hour in [11, 15, 21] else 1.25 if hour in [9, 10, 14, 20] else 0.8 if hour in [0, 1, 2, 3, 4, 5] else 1.0
            base_count = int((180 + weekday * 18 + day_offset * 6) * hour_boost)
            for behavior_type in heatmap_types:
                ratio = 1.0 if behavior_type == "all" else 0.42 if behavior_type == "view" else 0.18 if behavior_type == "cart" else 0.11 if behavior_type == "favorite" else 0.07 if behavior_type == "purchase" else 0.15
                event_count = int(base_count * ratio)
                user_count = max(1, int(event_count * (0.32 if behavior_type in ["all", "view"] else 0.5)))
                heatmap_rows.append((
                    heatmap_id,
                    day.strftime("%Y-%m-%d"),
                    weekday,
                    hour,
                    behavior_type,
                    event_count,
                    user_count,
                    NOW,
                    NOW,
                ))
                heatmap_id += 1

    for day_offset in range(45):
        day = snapshot_date_obj - timedelta(days=44 - day_offset)
        paid_order_count = 320 + day_offset * 4 + (day.isoweekday() % 3) * 16
        paid_user_count = int(paid_order_count * 0.78)
        revenue = round(128000 + day_offset * 2350 + day.isoweekday() * 1860, 2)
        refund_amount = round(revenue * (0.018 + (day_offset % 5) * 0.002), 2)
        avg_order_value = round(revenue / max(paid_order_count, 1), 2)
        moving_avg_7d = round(revenue - 6800 + (day_offset % 7) * 420, 2)
        sales_daily_rows.append((
            sales_daily_id,
            day.strftime("%Y-%m-%d"),
            0,
            paid_order_count,
            paid_user_count,
            revenue,
            refund_amount,
            avg_order_value,
            moving_avg_7d,
            round(((revenue - 118000) / 118000) * 100, 2),
            None,
            "forecast-v2.1",
            NOW,
            NOW,
        ))
        sales_daily_id += 1
    for day_offset in range(1, 8):
        day = snapshot_date_obj + timedelta(days=day_offset)
        revenue = round(236000 + day_offset * 4200, 2)
        paid_order_count = 510 + day_offset * 18
        paid_user_count = int(paid_order_count * 0.8)
        sales_daily_rows.append((
            sales_daily_id,
            day.strftime("%Y-%m-%d"),
            1,
            paid_order_count,
            paid_user_count,
            revenue,
            round(revenue * 0.021, 2),
            round(revenue / max(paid_order_count, 1), 2),
            round(revenue - 5200, 2),
            round(4.2 + day_offset * 0.35, 2),
            round(82 + day_offset * 1.8, 2),
            "forecast-v2.1",
            NOW,
            NOW,
        ))
        sales_daily_id += 1

    rfm_segment_stats = defaultdict(lambda: {"count": 0, "recency": 0.0, "frequency": 0.0, "monetary": 0.0})
    kmeans_segment_stats = defaultdict(lambda: {
        "count": 0,
        "order_count": 0.0,
        "order_amount": 0.0,
        "behavior_count": 0.0,
        "active_days": 0.0,
        "recency": 0.0,
        "price_per_order": 0.0,
        "categories": Counter(),
        "tags": Counter(),
        "feature_center": defaultdict(float),
    })
    segment_meta = {
        "S0": ("冷启动观察池", "近 90 天订单与行为较少，先使用规则和热门兜底。"),
        "S1": ("高频家庭复购", "以家庭刚需和复购品类为主，响应促销和套餐活动。"),
        "S2": ("品质升级用户", "注重品牌和体验，适合新品、精品和会员权益。"),
        "S3": ("价格敏感囤货", "重视满减和优惠券，适合大包装和高性价比活动。"),
        "S4": ("旗舰尝鲜用户", "对新品、高端旗舰和趋势单品有明显偏好。"),
        "S5": ("低频观望用户", "浏览多、成交少，适合唤醒券和客服触达。"),
    }
    user_segment_lookup = {}
    kmeans_task_id = 1
    cold_start_user_count = 0

    rfm_id = 1
    profile_snapshot_id = 1
    kmeans_user_id = 1
    kmeans_feature_id = 1
    for index, user_id in enumerate(normal_user_ids, start=1):
        summary = commerce_summaries.get(user_id, {})
        paid_orders = [order_ref for order_ref in summary.get("order_refs", []) if order_ref.get("status") in [1, 2, 3]]
        order_count_90d = int(summary.get("order_count_90d", 0))
        order_amount_90d = round(float(summary.get("order_amount_90d", 0) or 0), 2)
        frequency_count = max(order_count_90d, len(paid_orders))
        monetary_amount = round(sum(float(order_ref.get("total_amount", 0) or 0) for order_ref in paid_orders), 2)
        recency_days = int(summary.get("recency_order_days", 9999))
        behavior_count_30d = int(summary.get("behavior_count_30d", 0))
        active_days_30d = int(summary.get("active_days_30d", 0))
        avg_duration_30d = round(float(summary.get("avg_duration_30d", 0) or 0), 2)
        price_min = round(float(summary.get("price_range_min", 0) or 0), 2)
        price_max = round(float(summary.get("price_range_max", 0) or 0), 2)
        view_count_30d = int(summary.get("recent_behavior_counts_30d", {}).get("view", 0))
        cart_count_30d = int(summary.get("recent_behavior_counts_30d", {}).get("cart", 0))
        favorite_count_30d = int(summary.get("recent_behavior_counts_30d", {}).get("favorite", 0))
        purchase_count_30d = int(summary.get("recent_behavior_counts_30d", {}).get("purchase", 0))
        cold_start = is_cold_start_user(summary)
        if cold_start:
            cold_start_user_count += 1

        if recency_days <= 7:
            r_score = 5
        elif recency_days <= 15:
            r_score = 4
        elif recency_days <= 30:
            r_score = 3
        elif recency_days <= 60:
            r_score = 2
        else:
            r_score = 1
        if frequency_count >= 8:
            f_score = 5
        elif frequency_count >= 6:
            f_score = 4
        elif frequency_count >= 4:
            f_score = 3
        elif frequency_count >= 2:
            f_score = 2
        else:
            f_score = 1
        if monetary_amount >= 3200:
            m_score = 5
        elif monetary_amount >= 2200:
            m_score = 4
        elif monetary_amount >= 1200:
            m_score = 3
        elif monetary_amount >= 500:
            m_score = 2
        else:
            m_score = 1

        segment_code, segment_name, segment_desc = classify_summary(summary)
        rfm_code = f"{r_score}{f_score}{m_score}"
        rfm_user_rows.append((
            rfm_id,
            snapshot_date,
            user_id,
            recency_days,
            frequency_count,
            monetary_amount,
            r_score,
            f_score,
            m_score,
            rfm_code,
            segment_name,
            NOW,
        ))
        rfm_id += 1
        rfm_segment_stats[segment_name]["count"] += 1
        rfm_segment_stats[segment_name]["recency"] += recency_days
        rfm_segment_stats[segment_name]["frequency"] += frequency_count
        rfm_segment_stats[segment_name]["monetary"] += monetary_amount

        category_preferences = summary.get("category_preferences", {}) or {}
        tag_preferences = summary.get("tag_preferences", {}) or {}
        total_behaviors = int(summary.get("total_behaviors", 0))
        profile_snapshot_rows.append((
            profile_snapshot_id,
            snapshot_date,
            user_id,
            total_behaviors,
            json_text(category_preferences),
            json_text(tag_preferences),
            price_min,
            price_max,
            1 if cold_start else 0,
            "profile-v4.0",
            NOW,
            NOW,
        ))
        profile_snapshot_id += 1

        user_segment_lookup[user_id] = (segment_code, segment_name)
        avg_order_amount_90d = round(order_amount_90d / max(order_count_90d, 1), 2)
        raw_features = {
            "orderCount90d": order_count_90d,
            "orderAmount90d": order_amount_90d,
            "behaviorCount30d": behavior_count_30d,
            "activeDays30d": active_days_30d,
            "avgDuration30d": avg_duration_30d,
            "recencyOrderDays": recency_days,
        }
        normalized_features = {
            "orderCount90d": round(min(order_count_90d / 12, 1), 4),
            "orderAmount90d": round(min(order_amount_90d / 5000, 1), 4),
            "behaviorCount30d": round(min(behavior_count_30d / 220, 1), 4),
            "activeDays30d": round(min(active_days_30d / 30, 1), 4),
            "avgDuration30d": round(min(avg_duration_30d / 80, 1), 4),
            "recencyOrderDays": round(max(0, 1 - recency_days / 60), 4),
        }
        kmeans_user_rows.append((
            kmeans_user_id,
            kmeans_task_id,
            snapshot_date,
            user_id,
            segment_code,
            segment_name,
            None if segment_code == "S0" else int(segment_code[-1]) - 1,
            None if segment_code == "S0" else round(0.12 + (index % 19) * 0.017, 6),
            None if segment_code == "S0" else round(0.68 + ((index * 5) % 18) * 0.012, 4),
            1 if cold_start else 0,
            index,
            f"{segment_desc[:24]}，近期偏好{safe_top_categories(summary)[0]}。",
            NOW,
            NOW,
        ))
        kmeans_feature_rows.append((
            kmeans_feature_id,
            kmeans_task_id,
            snapshot_date,
            user_id,
            order_count_90d,
            order_amount_90d,
            avg_order_amount_90d,
            int(summary.get("distinct_category_count_90d", 0)),
            behavior_count_30d,
            view_count_30d,
            cart_count_30d,
            favorite_count_30d,
            purchase_count_30d,
            active_days_30d,
            avg_duration_30d,
            recency_days,
            int(summary.get("recency_behavior_days", 9999)),
            120 + index % 680,
            json_text(raw_features),
            json_text(normalized_features),
            NOW,
            NOW,
        ))
        kmeans_user_id += 1
        kmeans_feature_id += 1

        stat = kmeans_segment_stats[segment_code]
        stat["count"] += 1
        stat["order_count"] += order_count_90d
        stat["order_amount"] += order_amount_90d
        stat["behavior_count"] += behavior_count_30d
        stat["active_days"] += active_days_30d
        stat["recency"] += recency_days
        stat["price_per_order"] += avg_order_amount_90d
        stat["categories"].update(safe_top_categories(summary))
        stat["tags"].update(safe_top_tags(summary))
        stat["feature_center"]["orderCount90d"] += order_count_90d
        stat["feature_center"]["orderAmount90d"] += order_amount_90d
        stat["feature_center"]["behaviorCount30d"] += behavior_count_30d
        stat["feature_center"]["activeDays30d"] += active_days_30d

    rfm_segment_id = 1
    for segment_name, metrics in rfm_segment_stats.items():
        count = metrics["count"]
        rfm_segment_rows.append((
            rfm_segment_id,
            snapshot_date,
            segment_name,
            count,
            round(count * 100 / len(normal_user_ids), 2),
            round(metrics["recency"] / max(count, 1), 2),
            round(metrics["frequency"] / max(count, 1), 2),
            round(metrics["monetary"] / max(count, 1), 2),
            NOW,
        ))
        rfm_segment_id += 1

    for index, segment_code in enumerate(sorted(kmeans_segment_stats.keys()), start=1):
        metrics = kmeans_segment_stats[segment_code]
        count = metrics["count"]
        segment_name, segment_desc = segment_meta[segment_code]
        feature_center = {
            key: round(value / max(count, 1), 4)
            for key, value in metrics["feature_center"].items()
        }
        kmeans_segment_rows.append((
            index,
            kmeans_task_id,
            snapshot_date,
            segment_code,
            segment_name,
            segment_desc,
            f"{segment_name}在本周期表现稳定，适合作为精细化运营重点人群。",
            f"建议对{segment_name}投放分层权益、差异化消息与个性化推荐。",
            count,
            round(count * 100 / len(normal_user_ids), 2),
            round(metrics["order_count"] / max(count, 1), 2),
            round(metrics["order_amount"] / max(count, 1), 2),
            round(metrics["behavior_count"] / max(count, 1), 2),
            round(metrics["active_days"] / max(count, 1), 2),
            round(metrics["recency"] / max(count, 1), 2),
            round(metrics["price_per_order"] / max(count, 1), 2),
            json_text(feature_center),
            json_text([key for key, _ in metrics["categories"].most_common(3)]),
            json_text([key for key, _ in metrics["tags"].most_common(5)]),
            NOW,
            NOW,
        ))

    clustered_user_count = len(normal_user_ids) - cold_start_user_count
    kmeans_task_rows.append((
        kmeans_task_id,
        "kmeans-20260418-01",
        snapshot_date,
        "success",
        "kmeans",
        "cluster-v2.0",
        "feature-v4.0",
        5,
        len(normal_user_ids),
        clustered_user_count,
        cold_start_user_count,
        round(0.6284, 4),
        round(1726.4182, 6),
        json_text(["orderCount90d", "orderAmount90d", "behaviorCount30d", "activeDays30d", "avgDuration30d", "recencyOrderDays"]),
        json_text({"segments": 5, "topSegment": "S1", "coverageUsers": clustered_user_count}),
        json_text({
            "summary": "聚类结果基于真实交易与行为汇总生成，冷启动用户已单独归入观察池。",
            "suggestions": ["加强家庭复购包", "针对品质升级人群投放新品权益", "对冷启动用户使用热门商品与客服引导"],
        }),
        None,
        (now_dt - timedelta(hours=2)).strftime("%Y-%m-%d %H:%M:%S"),
        (now_dt - timedelta(hours=1, minutes=18)).strftime("%Y-%m-%d %H:%M:%S"),
        NOW,
        NOW,
    ))

    recommendation_id = 1
    for user_id in normal_user_ids:
        summary = commerce_summaries.get(user_id, {})
        preferred_categories = safe_top_categories(summary)
        search_keywords = list(summary.get("search_keywords", []))
        purchased_product_ids = set(summary.get("purchased_product_ids", []))
        interest_product_ids = list(summary.get("interest_product_ids", []))
        cold_start = is_cold_start_user(summary)
        for scene in ["guess_you_like", "personal"]:
            selected_products = []
            selected_product_ids = set()
            if scene == "personal" and not cold_start:
                for product_id in interest_product_ids:
                    if product_id in purchased_product_ids:
                        continue
                    product = products[product_id - 1]
                    if product["id"] not in selected_product_ids:
                        selected_products.append(product)
                        selected_product_ids.add(product["id"])
                    if len(selected_products) >= 8:
                        break
            for category_name in preferred_categories:
                pool = products_by_category[category_name]
                for step in range(len(pool)):
                    candidate = pool[(user_id + step * 17 + len(selected_products) * 7) % len(pool)]
                    if scene == "personal" and candidate["id"] in purchased_product_ids:
                        continue
                    if candidate["id"] not in selected_product_ids:
                        selected_products.append(candidate)
                        selected_product_ids.add(candidate["id"])
                    if len(selected_products) >= 8:
                        break
                if len(selected_products) >= 8:
                    break
            while len(selected_products) < 8:
                candidate = hot_products[(user_id + len(selected_products) * 13) % len(hot_products)]
                if candidate["id"] not in selected_product_ids:
                    selected_products.append(candidate)
                    selected_product_ids.add(candidate["id"])
            for rank_no, product in enumerate(selected_products[:8], start=1):
                if cold_start:
                    reason = f"你当前行为样本较少，先推荐平台热门的 {product['main_name']} 商品。"
                elif search_keywords:
                    reason = f"你最近搜索过“{search_keywords[0]}”，系统优先推荐相关商品。"
                else:
                    reason = f"你近期偏好 {product['main_name']} / {product['sub_name']}，系统优先推荐同类高热商品。"
                recommendation_rows.append((
                    recommendation_id,
                    snapshot_date,
                    scene,
                    user_id,
                    product["id"],
                    rank_no,
                    round(0.97 - rank_no * 0.061 + (user_id % 11) * 0.002, 6),
                    "hot" if cold_start else "hybrid" if scene == "personal" else "rules",
                    reason,
                    "rec-v6.0",
                    NOW,
                ))
                recommendation_id += 1
    for rank_no, product in enumerate(hot_products[:20], start=1):
        recommendation_rows.append((
            recommendation_id,
            snapshot_date,
            "hot",
            0,
            product["id"],
            rank_no,
            round(0.99 - rank_no * 0.021, 6),
            "hot",
            f"{product['main_name']} 当前热度与成交持续上升。",
            "rec-v6.0",
            NOW,
        ))
        recommendation_id += 1

    exposure_id = 1
    for index, user_id in enumerate(normal_user_ids[:300], start=1):
        summary = commerce_summaries.get(user_id, {})
        preferred_categories = safe_top_categories(summary)
        segment_code, segment_name = user_segment_lookup[user_id]
        paid_orders = [order_ref for order_ref in summary.get("order_refs", []) if order_ref.get("status") in [1, 2, 3]]
        request_token = f"req-20260418-{user_id:05d}"
        for rank_no in range(1, 7):
            category_name = preferred_categories[(rank_no - 1) % len(preferred_categories)]
            product = products_by_category[category_name][(user_id + rank_no * 19) % len(products_by_category[category_name])]
            exposure_time = now_dt - timedelta(days=index % 18, hours=rank_no * 2, minutes=index % 41)
            click_time = exposure_time + timedelta(minutes=3 + rank_no) if rank_no <= 4 or index % 5 == 0 else None
            favorite_time = click_time + timedelta(minutes=8) if click_time and rank_no in [2, 3] and index % 4 == 0 else None
            cart_time = click_time + timedelta(minutes=15) if click_time and rank_no <= 3 and index % 3 == 0 else None
            order_ref = paid_orders[(rank_no - 1) % len(paid_orders)] if cart_time and paid_orders and rank_no <= 2 and index % 7 == 0 else None
            purchase_time = order_ref.get("pay_time") if order_ref else None
            order_id = order_ref.get("order_id") if order_ref else None
            exposure_rows.append((
                exposure_id,
                f"exp-20260418-{exposure_id:06d}",
                request_token,
                user_id,
                product["id"],
                "guess_you_like" if rank_no % 2 == 0 else "personal",
                rank_no,
                "hot" if segment_code == "S0" else "hybrid",
                "live",
                "BEHAVIOR_MATCH" if rank_no <= 3 else "HOT_TREND",
                "rec-v6.0",
                "A" if index % 2 == 0 else "B",
                segment_code,
                segment_name,
                exposure_time.strftime("%Y-%m-%d %H:%M:%S"),
                click_time.strftime("%Y-%m-%d %H:%M:%S") if click_time else None,
                favorite_time.strftime("%Y-%m-%d %H:%M:%S") if favorite_time else None,
                cart_time.strftime("%Y-%m-%d %H:%M:%S") if cart_time else None,
                purchase_time,
                order_id,
                exposure_time.strftime("%Y-%m-%d %H:%M:%S"),
                NOW,
            ))
            exposure_id += 1

    similarity_id = 1
    for index, product in enumerate(products[:240], start=1):
        pool = products_by_category[product["main_name"]]
        rank_no = 1
        candidate_index = 1
        while rank_no <= 5:
            candidate = pool[(index + candidate_index * 9) % len(pool)]
            candidate_index += 1
            if candidate["id"] == product["id"]:
                continue
            similarity_rows.append((
                similarity_id,
                snapshot_date,
                product["id"],
                candidate["id"],
                round(0.92 - rank_no * 0.08 + (index % 5) * 0.01, 6),
                "item_cf",
                rank_no,
                NOW,
            ))
            similarity_id += 1
            rank_no += 1

    association_id = 1
    for index, product in enumerate(products[:180], start=1):
        pool = products_by_category[product["main_name"]]
        rhs_product = pool[(index * 5) % len(pool)]
        if rhs_product["id"] == product["id"]:
            rhs_product = pool[(index * 5 + 3) % len(pool)]
        association_rows.append((
            association_id,
            snapshot_date,
            product["id"],
            rhs_product["id"],
            80 + index % 40,
            round(0.012 + (index % 8) * 0.003, 6),
            round(0.18 + (index % 5) * 0.04, 6),
            round(1.12 + (index % 6) * 0.18, 4),
            index,
            NOW,
        ))
        association_id += 1
        second_rhs = pool[(index * 7 + 11) % len(pool)]
        if second_rhs["id"] == product["id"]:
            second_rhs = pool[(index * 7 + 17) % len(pool)]
        association_rows.append((
            association_id,
            snapshot_date,
            product["id"],
            second_rhs["id"],
            60 + index % 30,
            round(0.01 + (index % 6) * 0.002, 6),
            round(0.14 + (index % 4) * 0.035, 6),
            round(1.05 + (index % 5) * 0.16, 4),
            index + 500,
            NOW,
        ))
        association_id += 1

    largest_segment_code, largest_segment_metrics = max(
        kmeans_segment_stats.items(),
        key=lambda item: item[1]["count"],
    )
    largest_segment_name = segment_meta[largest_segment_code][0]
    recommendation_click_count = sum(1 for row in exposure_rows if row[15] is not None)
    recommendation_cart_count = sum(1 for row in exposure_rows if row[17] is not None)
    recommendation_purchase_count = sum(1 for row in exposure_rows if row[18] is not None)
    recommendation_attribution_rate = round(
        recommendation_purchase_count * 100 / max(len(exposure_rows), 1),
        2,
    )

    analytics_jobs = [
        ("rfm_daily", "success", len(normal_user_ids), len(rfm_user_rows), {"segments": len(rfm_segment_rows)}),
        ("profile_snapshot_daily", "success", len(normal_user_ids), len(profile_snapshot_rows), {"coldStartUsers": cold_start_user_count}),
        ("recommend_train", "success", PLANNED_PRODUCT_TOTAL, len(recommendation_rows), {"scenes": ["guess_you_like", "personal", "hot"], "userCoverage": len(normal_user_ids)}),
        ("recommend_exposure_sync", "success", len(exposure_rows), recommendation_purchase_count, {"attributionRate": recommendation_attribution_rate}),
        ("association_rule_train", "success", len(products[:180]), len(association_rows), {"minSupport": 0.012}),
        ("product_similarity_build", "success", PLANNED_PRODUCT_TOTAL, len(similarity_rows), {"algorithm": "item_cf"}),
        ("kmeans_daily", "success", len(kmeans_feature_rows), len(kmeans_user_rows), {"clusterCount": 5, "clusteredUsers": clustered_user_count, "coldStartUsers": cold_start_user_count}),
        ("sales_forecast_daily", "success", 45, 52, {"forecastDays": 7}),
    ]
    for job_id, (job_name, status, processed_count, output_count, summary) in enumerate(analytics_jobs, start=1):
        start_time = now_dt - timedelta(hours=job_id * 3)
        end_time = start_time + timedelta(minutes=15 + job_id * 4)
        analytics_job_rows.append((
            job_id,
            job_name,
            f"{job_name}-20260418-{job_id:02d}",
            "python_offline",
            status,
            snapshot_date,
            processed_count,
            output_count,
            json_text(summary),
            None,
            start_time.strftime("%Y-%m-%d %H:%M:%S"),
            end_time.strftime("%Y-%m-%d %H:%M:%S"),
            start_time.strftime("%Y-%m-%d %H:%M:%S"),
            end_time.strftime("%Y-%m-%d %H:%M:%S"),
        ))

    report_rows.extend([
        (
            1,
            snapshot_date,
            "dashboard_overview",
            "平台经营总览",
            json_text({"gmv": 236000.0, "paidOrderCount": 548, "activeUsers": 1892, "topCategory": "手机数码"}),
            "analytics_sales_daily,analytics_funnel_daily,analytics_behavior_daily",
            NOW,
            NOW,
        ),
        (
            2,
            snapshot_date,
            "recommend_summary",
            "推荐效果概览",
            json_text({
                "exposure": len(exposure_rows),
                "click": recommendation_click_count,
                "cart": recommendation_cart_count,
                "purchase": recommendation_purchase_count,
                "topScene": "guess_you_like",
            }),
            "analytics_recommendation_result,analytics_recommendation_exposure",
            NOW,
            NOW,
        ),
        (
            3,
            snapshot_date,
            "segment_board",
            "用户分群概览",
            json_text({
                "segments": 5,
                "largestSegment": largest_segment_code,
                "largestSegmentName": largest_segment_name,
                "largestCount": largest_segment_metrics["count"],
                "coldStartUsers": cold_start_user_count,
            }),
            "analytics_kmeans_task,analytics_kmeans_segment,analytics_kmeans_user_result",
            NOW,
            NOW,
        ),
        (
            4,
            snapshot_date,
            "seckill_monitor",
            "秒杀活动监控",
            json_text({"activities": len(seckill_rows), "approvedApplications": sum(1 for row in seckill_apply_rows if row[9] == 1), "ongoingActivities": 3}),
            "seckill_activity,seckill_activity_apply",
            NOW,
            NOW,
        ),
        (
            5,
            snapshot_date,
            "service_center",
            "客服运营快照",
            json_text({"conversations": len(conversation_rows), "tickets": len(ticket_rows), "avgHandleMinutes": 47}),
            "im_conversation,im_ticket,im_message",
            NOW,
            NOW,
        ),
    ])

    with chunk_writer(output, "Seed Chunk Extended - 秒杀/客服/运营/分析扩展数据") as handle:
        handle.write("-- 覆盖秒杀、客服、运营日志、消息队列、分析看板、K-means 聚类等扩展表\n\n")
        handle.write("SET NAMES utf8mb4;\n\n")
        write_insert(handle, "seckill_activity", [
            "id", "name", "cover_image", "description", "start_time", "end_time", "publish_status",
            "sort_order", "create_time", "update_time",
        ], seckill_rows)
        write_insert(handle, "seckill_activity_apply", [
            "id", "activity_id", "merchant_id", "product_id", "product_price", "seckill_price", "seckill_stock",
            "sold_count", "limit_per_user", "audit_status", "reject_reason", "audit_time", "create_time", "update_time",
        ], seckill_apply_rows)
        write_insert(handle, "user_coupon", [
            "id", "user_id", "coupon_id", "status", "order_id", "use_time", "create_time",
        ], user_coupon_rows)
        write_insert(handle, "mq_outbox_event", [
            "id", "event_id", "event_type", "exchange_name", "routing_key", "biz_id", "payload", "status",
            "retry_count", "next_retry_time", "error_message", "sent_time", "create_time", "update_time",
        ], mq_outbox_rows)
        write_insert(handle, "mq_consume_log", [
            "id", "event_id", "consumer_name", "create_time",
        ], mq_consume_rows)
        write_insert(handle, "profile_change_request", [
            "id", "user_id", "new_nickname", "new_avatar", "old_nickname", "old_avatar", "status",
            "reject_reason", "review_time", "reviewer_id", "create_time",
        ], profile_change_rows)
        write_insert(handle, "operation_log", [
            "id", "user_id", "username", "role", "module", "action", "method", "url", "params",
            "ip", "status", "error_msg", "cost_time", "create_time",
        ], operation_rows)
        write_insert(handle, "im_support_agent", [
            "id", "user_id", "display_name", "avatar", "agent_type", "online_status", "enabled",
        ], support_agent_rows)
        write_insert(handle, "im_conversation", [
            "id", "conversation_no", "conversation_type", "user_id", "merchant_id", "support_agent_id",
            "order_id", "product_id", "status", "is_escalated", "priority", "last_message", "last_message_type",
            "last_sender_role", "last_sender_id", "last_message_time", "unread_user", "unread_merchant",
            "unread_support", "closed_time", "create_time", "update_time",
        ], conversation_rows)
        write_insert(handle, "im_message", [
            "id", "conversation_id", "sender_role", "sender_id", "message_type", "content", "payload_json",
            "is_system", "create_time",
        ], im_message_rows, batch_size=500)
        write_insert(handle, "im_ticket", [
            "id", "conversation_id", "review_id", "ticket_no", "ticket_status", "source_type", "issue_type",
            "issue_summary", "issue_detail", "created_by_user_id", "assigned_support_id", "resolved_by_id",
            "assigned_time", "resolved_time", "sla_deadline_time", "sla_escalation_level",
            "last_escalation_time", "create_time", "update_time",
        ], ticket_rows)
        write_insert(handle, "analytics_job_log", [
            "id", "job_name", "batch_no", "job_type", "status", "snapshot_date", "processed_count", "output_count",
            "result_summary", "error_message", "start_time", "end_time", "create_time", "update_time",
        ], analytics_job_rows)
        write_insert(handle, "analytics_behavior_daily", [
            "id", "stat_date", "behavior_type", "user_count", "event_count", "product_count", "avg_duration",
            "create_time", "update_time",
        ], behavior_daily_rows)
        write_insert(handle, "analytics_funnel_daily", [
            "id", "stat_date", "view_user_count", "cart_user_count", "favorite_user_count", "purchase_user_count",
            "view_to_cart_rate", "cart_to_purchase_rate", "view_to_purchase_rate", "create_time", "update_time",
        ], funnel_rows)
        write_insert(handle, "analytics_behavior_heatmap", [
            "id", "stat_date", "day_of_week", "hour_of_day", "behavior_type", "event_count", "user_count",
            "create_time", "update_time",
        ], heatmap_rows, batch_size=500)
        write_insert(handle, "analytics_sales_daily", [
            "id", "stat_date", "is_forecast", "paid_order_count", "paid_user_count", "revenue", "refund_amount",
            "avg_order_value", "moving_avg_7d", "week_over_week", "forecast_confidence", "model_version",
            "create_time", "update_time",
        ], sales_daily_rows)
        write_insert(handle, "analytics_rfm_user_snapshot", [
            "id", "snapshot_date", "user_id", "recency_days", "frequency_count", "monetary_amount", "r_score",
            "f_score", "m_score", "rfm_code", "segment_name", "create_time",
        ], rfm_user_rows, batch_size=500)
        write_insert(handle, "analytics_rfm_segment_snapshot", [
            "id", "snapshot_date", "segment_name", "user_count", "percentage", "avg_recency_days",
            "avg_frequency", "avg_monetary", "create_time",
        ], rfm_segment_rows)
        write_insert(handle, "analytics_user_profile_snapshot", [
            "id", "snapshot_date", "user_id", "total_behaviors", "category_preferences", "tag_preferences",
            "price_range_min", "price_range_max", "cold_start", "model_version", "create_time", "update_time",
        ], profile_snapshot_rows, batch_size=500)
        write_insert(handle, "analytics_recommendation_result", [
            "id", "snapshot_date", "scene", "user_id", "product_id", "rank_no", "score", "algorithm",
            "reason", "model_version", "create_time",
        ], recommendation_rows, batch_size=500)
        write_insert(handle, "analytics_recommendation_exposure", [
            "id", "exposure_token", "request_token", "user_id", "product_id", "scene", "rank_no", "algorithm",
            "source_type", "reason_type", "model_version", "experiment_group", "segment_code", "segment_name",
            "exposure_time", "click_time", "favorite_time", "cart_time", "purchase_time", "order_id",
            "create_time", "update_time",
        ], exposure_rows, batch_size=500)
        write_insert(handle, "analytics_product_similarity", [
            "id", "snapshot_date", "product_id", "similar_product_id", "similarity", "source_algorithm",
            "rank_no", "create_time",
        ], similarity_rows, batch_size=500)
        write_insert(handle, "analytics_association_rule", [
            "id", "snapshot_date", "lhs_product_id", "rhs_product_id", "support_count", "support_rate",
            "confidence", "lift", "rank_no", "create_time",
        ], association_rows, batch_size=500)
        write_insert(handle, "analytics_report_snapshot", [
            "id", "snapshot_date", "report_code", "report_name", "report_data", "source_tables",
            "create_time", "update_time",
        ], report_rows)
        write_insert(handle, "analytics_kmeans_task", [
            "id", "batch_no", "snapshot_date", "status", "algorithm_name", "model_version", "feature_version",
            "cluster_count", "sample_user_count", "clustered_user_count", "cold_start_user_count",
            "silhouette_score", "inertia_score", "feature_columns", "result_summary", "llm_overview",
            "error_message", "start_time", "end_time", "create_time", "update_time",
        ], kmeans_task_rows)
        write_insert(handle, "analytics_kmeans_segment", [
            "id", "task_id", "snapshot_date", "segment_code", "segment_name", "segment_description",
            "llm_summary", "operation_suggestion", "user_count", "percentage", "avg_order_count_90d",
            "avg_order_amount_90d", "avg_behavior_count_30d", "avg_active_days_30d", "avg_recency_days",
            "avg_price_per_order", "feature_center", "top_categories", "top_tags", "create_time", "update_time",
        ], kmeans_segment_rows)
        write_insert(handle, "analytics_kmeans_user_result", [
            "id", "task_id", "snapshot_date", "user_id", "segment_code", "segment_name", "cluster_index",
            "distance_to_center", "confidence_score", "is_cold_start", "sort_order", "persona_summary",
            "create_time", "update_time",
        ], kmeans_user_rows, batch_size=500)
        write_insert(handle, "analytics_kmeans_feature_snapshot", [
            "id", "task_id", "snapshot_date", "user_id", "order_count_90d", "order_amount_90d",
            "avg_order_amount_90d", "distinct_category_count_90d", "behavior_count_30d", "view_count_30d",
            "cart_count_30d", "favorite_count_30d", "purchase_behavior_count_30d", "active_days_30d",
            "avg_duration_30d", "recency_order_days", "recency_behavior_days", "tenure_days",
            "raw_features", "normalized_features", "create_time", "update_time",
        ], kmeans_feature_rows, batch_size=500)
        for user_coupon_id, order_id in order_coupon_updates:
            handle.write(
                f"UPDATE `order` SET `user_coupon_id` = {user_coupon_id} WHERE `id` = {order_id};\n"
            )
        if order_coupon_updates:
            handle.write("\n")
        for coupon_id, issue_count in sorted(coupon_issue_counter.items()):
            handle.write(f"UPDATE `coupon` SET `used_count` = {issue_count} WHERE `id` = {coupon_id};\n")
        handle.write("\n")

    print(f"[ok] 已生成扩展块: {output}")
    print(f"     秒杀活动: {len(seckill_rows)} 条, 秒杀报名: {len(seckill_apply_rows)} 条, 用户优惠券: {len(user_coupon_rows)} 条")
    print(f"     客服会话: {len(conversation_rows)} 条, IM 消息: {len(im_message_rows)} 条, 工单: {len(ticket_rows)} 条")
    print(f"     分析快照: {len(behavior_daily_rows) + len(funnel_rows) + len(heatmap_rows) + len(sales_daily_rows)} 条")


def parse_args():
    parser = argparse.ArgumentParser(description="分段生成电商系统 seed SQL")
    parser.add_argument("--chunk", choices=["base", "products", "commerce", "extended"], required=True, help="要生成的分段")
    parser.add_argument("--start-id", type=int, default=1, help="商品起始 ID，仅 products 使用")
    parser.add_argument("--count", type=int, default=1000, help="生成数量，仅 products 使用")
    parser.add_argument("--output", type=Path, default=None, help="输出文件路径")
    return parser.parse_args()


def main():
    args = parse_args()
    if args.chunk == "base":
        output = args.output or (DEFAULT_OUTPUT_DIR / "seed-part-01-base.sql")
        build_base_chunk(output)
        return
    if args.chunk == "commerce":
        output = args.output or (DEFAULT_OUTPUT_DIR / f"seed-part-commerce-{args.start_id:04d}-{args.start_id + args.count - 1:04d}.sql")
        build_commerce_chunk(output, args.start_id, args.count)
        return
    if args.chunk == "extended":
        output = args.output or (DEFAULT_OUTPUT_DIR / "seed-part-extended.sql")
        build_extended_chunk(output)
        return
    output = args.output or (DEFAULT_OUTPUT_DIR / f"seed-part-products-{args.start_id:04d}-{args.start_id + args.count - 1:04d}.sql")
    build_products_chunk(output, args.start_id, args.count)


if __name__ == "__main__":
    main()
