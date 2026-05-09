#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
按商品批量生成更贴合的商品图，上传到阿里云 OSS，并产出 SQL 更新文件。

设计目标：
1. 同平台统一电商视觉风格，但每个商品图的构图/材质/场景不完全相同。
2. 图片内容和商品分类、名称一致，避免“同模版换字”。
3. 先小批量验证，再扩大批量。
"""

from __future__ import annotations

import argparse
import json
import os
import random
import re
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Any

import requests
import yaml
from PIL import Image
from PIL import ImageDraw
from PIL import ImageFilter
from PIL import ImageFont
from PIL import ImageStat

from backend.python_analytics.spark_jobs.oss_archive import build_oss_config_from_env


ROOT = Path(__file__).resolve().parents[1]
APP_YML = ROOT / "backend" / "src" / "main" / "resources" / "application.yml"
OUTPUT_DIR = ROOT / "output" / "generated-product-images"
SQL_DIR = ROOT / "backend" / "src" / "main" / "resources" / "sql" / "chunks"
NOW = "2026-04-18 20:30:00"
DEFAULT_OSS_ENDPOINT = "https://oss-cn-your-region.aliyuncs.com"
DEFAULT_BUCKET_NAME = "your-bucket-name"
DEFAULT_PUBLIC_URL = "https://your-bucket-name.oss-cn-your-region.aliyuncs.com"
DEFAULT_IMAGE_SIZE = 1024
DEFAULT_IMAGE_FORMAT = "JPEG"
DEFAULT_JPEG_QUALITY = 88

PRODUCT_INSERT_RE = re.compile(
    r"^\((\d+), '([^']*(?:''[^']*)*)', '([^']*(?:''[^']*)*)', ([0-9.]+), ([0-9.]+), (\d+), (\d+),",
    re.MULTILINE,
)

CATEGORY_HINTS = {
    "手机数码": "高端消费电子产品摄影，纯净背景，金属和玻璃材质细节清晰",
    "电脑办公": "现代办公桌面产品摄影，极简工作台，器材与办公氛围自然",
    "家用电器": "家电棚拍或家居场景图，强调功能和材质，不要出现多余产品",
    "家居家装": "家居软装商业摄影，真实家居空间，柔和光线，生活方式感",
    "服饰鞋包": "电商服饰白底图或轻场景陈列图，版型和材质清楚，像淘宝天猫主图",
    "美妆护肤": "美妆产品广告摄影，瓶身和包装精致，肤感洁净，商业大片感",
    "食品生鲜": "食品商业摄影，食材新鲜自然，颜色真实，不要塑料假感",
    "母婴用品": "母婴用品电商图，安全柔和配色，干净背景，产品主体明确",
    "运动户外": "运动装备商业摄影，轻户外或健身场景，强调功能属性",
    "图书文具": "文创静物摄影，桌面构图，纸张和文具质感清楚",
    "汽车用品": "汽车用品商业摄影，车内或棚拍场景，产品用途明确",
    "宠物生活": "宠物用品电商摄影，产品主体清晰，适量真实宠物元素可点缀",
}

SCENE_LIBRARY = {
    "手机数码": [
        "真实办公桌面场景，旁边有键盘和笔记本电脑虚化背景",
        "现代客厅茶几场景，突出数码产品的质感和屏幕反光",
        "高级电子产品展示台场景，带少量真实生活化道具",
    ],
    "电脑办公": [
        "真实办公桌场景，桌面有显示器、便签、文件夹作为陪体",
        "现代书房工作台场景，呈现高效办公氛围",
        "极简会议室桌面场景，突出办公设备的专业感",
    ],
    "家用电器": [
        "真实家庭厨房或客厅场景，产品自然融入家居环境",
        "现代公寓生活场景，灯光柔和，带日常使用痕迹",
        "干净但真实的家电展示场景，像品牌官网样片",
    ],
    "家居家装": [
        "真实客厅或卧室家居陈列场景，空间有层次",
        "自然居家空间场景，突出软装和材质搭配",
        "生活方式家居摄影场景，像家居品牌画册",
    ],
    "服饰鞋包": [
        "真实服饰静物陈列场景，桌面或挂架环境自然",
        "电商时尚棚拍场景，带少量品牌级场景布置",
        "真实通勤或生活方式场景，但主体仍是商品",
    ],
    "美妆护肤": [
        "梳妆台或浴室台面真实场景，光线干净通透",
        "高级化妆品广告场景，带镜面、水滴、石材等真实材质",
        "生活方式护肤场景，突出包装和使用氛围",
    ],
    "食品生鲜": [
        "真实餐桌或厨房备餐场景，食物状态新鲜自然",
        "生活化食材摆盘场景，强调可食用与真实口感",
        "电商美食摄影场景，避免夸张假模型感",
    ],
    "母婴用品": [
        "真实婴儿房或亲子家庭场景，氛围柔和安全",
        "自然母婴生活场景，突出产品温和和安心感",
        "干净明亮的育儿用品陈列场景",
    ],
    "运动户外": [
        "真实健身房或户外运动场景，突出使用感",
        "轻户外露营或城市骑行场景，画面自然",
        "品牌级运动广告场景，但产品主体明确",
    ],
    "图书文具": [
        "真实书桌阅读场景，纸张和笔触细节清晰",
        "安静学习桌面场景，带台灯、书页、咖啡杯等轻陪体",
        "文创生活方式桌面场景，氛围自然",
    ],
    "汽车用品": [
        "真实车内中控或后备箱场景，体现用途",
        "汽车美容养护场景，产品使用环境明确",
        "品牌级车品棚拍结合车辆环境场景",
    ],
    "宠物生活": [
        "真实宠物家庭场景，产品和宠物互动自然",
        "宠物用品电商场景，背景是客厅或宠物角",
        "生活化宠物喂养或玩耍场景，商品主体清楚",
    ],
}

COMPOSITIONS = [
    "居中主视觉构图",
    "轻微俯拍构图",
    "45度产品展示构图",
    "偏右主体并留少量留白",
    "近景质感特写构图",
]

LIGHTING = [
    "柔和棚拍灯光",
    "干净明亮的商业灯光",
    "自然窗边柔光",
    "高级电商广告布光",
]

BACKDROPS = [
    "真实生活空间背景",
    "品牌级轻场景背景",
    "自然家居或桌面背景",
    "商业摄影真实环境背景",
]

NEGATIVE_PROMPT = (
    "低清晰度, 模糊, 重影, 多个主体, 重复商品, 变形, 扭曲, 多余文字, 水印, logo, 品牌标识, "
    "错误手部, 错误比例, 不相关道具, 过度饱和, AI感过强, 假塑料质感, 同质化模板图"
)

CATEGORY_PALETTES = {
    "手机数码": ((29, 78, 216), (56, 189, 248), (239, 246, 255), (15, 23, 42)),
    "电脑办公": ((15, 118, 110), (20, 184, 166), (240, 253, 250), (17, 24, 39)),
    "家用电器": ((220, 38, 38), (251, 146, 60), (255, 247, 237), (28, 25, 23)),
    "家居家装": ((180, 83, 9), (245, 158, 11), (255, 251, 235), (41, 37, 36)),
    "服饰鞋包": ((124, 58, 237), (236, 72, 153), (250, 245, 255), (36, 24, 57)),
    "美妆护肤": ((219, 39, 119), (244, 114, 182), (253, 242, 248), (63, 32, 48)),
    "食品生鲜": ((22, 163, 74), (163, 230, 53), (247, 254, 231), (20, 38, 28)),
    "母婴用品": ((14, 165, 233), (125, 211, 252), (240, 249, 255), (19, 44, 63)),
    "运动户外": ((234, 88, 12), (251, 191, 36), (255, 247, 237), (67, 20, 7)),
    "图书文具": ((79, 70, 229), (129, 140, 248), (238, 242, 255), (30, 27, 75)),
    "汽车用品": ((75, 85, 99), (148, 163, 184), (248, 250, 252), (15, 23, 42)),
    "宠物生活": ((217, 119, 6), (250, 204, 21), (254, 252, 232), (68, 64, 60)),
    "通用商品": ((71, 85, 105), (148, 163, 184), (248, 250, 252), (15, 23, 42)),
}

FONT_CANDIDATES = [
    Path("C:/Windows/Fonts/msyh.ttc"),
    Path("C:/Windows/Fonts/msyhbd.ttc"),
    Path("C:/Windows/Fonts/simhei.ttf"),
    Path("C:/Windows/Fonts/simsun.ttc"),
]

GALLERY_VARIANTS = ["main", "detail", "scene"]


@dataclass
class ProductRecord:
    product_id: int
    name: str
    description: str
    price: float
    original_price: float
    category_id: int
    merchant_id: int
    main_category: str
    sub_category: str
    subject: str


def read_application_settings() -> dict[str, Any]:
    data = yaml.safe_load(APP_YML.read_text(encoding="utf-8"))
    ai = data.get("ai", {})
    return {
        "api_url": os.getenv("AI_API_URL", ai.get("api-url", "https://dashscope.aliyuncs.com/compatible-mode")),
        "api_key": os.getenv("AI_API_KEY", ai.get("api-key", "")),
        "model": os.getenv("AI_IMAGE_MODEL", os.getenv("AI_MODEL", "wanx2.1-t2i-turbo")),
    }


def parse_products_from_sql(sql_path: Path, start_id: int, count: int) -> list[ProductRecord]:
    text = sql_path.read_text(encoding="utf-8")
    matched: list[ProductRecord] = []
    for groups in PRODUCT_INSERT_RE.findall(text):
        product_id = int(groups[0])
        if product_id < start_id or product_id >= start_id + count:
            continue
        name = groups[1].replace("''", "'")
        description = groups[2].replace("''", "'")
        main_category, sub_category, subject = split_product_fields(name, description)
        matched.append(
            ProductRecord(
                product_id=product_id,
                name=name,
                description=description,
                price=float(groups[3]),
                original_price=float(groups[4]),
                category_id=int(groups[5]),
                merchant_id=int(groups[6]),
                main_category=main_category,
                sub_category=sub_category,
                subject=subject,
            )
        )
    return matched


def split_product_fields(name: str, description: str) -> tuple[str, str, str]:
    description_match = re.search(r"适合(.+?)场景，主打(.+?)人群", description)
    if description_match:
        main_category = description_match.group(1).strip()
        sub_category = description_match.group(2).strip()
    else:
        main_category = "通用商品"
        sub_category = "精选好物"

    parts = [item.strip() for item in name.split("·")]
    if len(parts) >= 3:
        subject = parts[2]
        if " " in subject:
            subject = subject.split(" ")[0]
        return main_category, sub_category, subject
    tokens = [item for item in name.split(" ") if item]
    if len(tokens) >= 3:
        subject = "".join(tokens[1:-1]) or tokens[1]
    elif tokens:
        subject = tokens[-1]
    else:
        subject = "精选好物"
    return main_category, sub_category, subject


def build_prompt(product: ProductRecord) -> str:
    seed = product.product_id
    composition = COMPOSITIONS[seed % len(COMPOSITIONS)]
    lighting = LIGHTING[(seed // 2) % len(LIGHTING)]
    backdrop = BACKDROPS[(seed // 3) % len(BACKDROPS)]
    hint = CATEGORY_HINTS.get(product.main_category, "真实电商商品摄影，产品主体清晰，商业级画面")
    scene_candidates = SCENE_LIBRARY.get(product.main_category, ["真实商品使用场景，环境自然且与商品相关"])
    scene = scene_candidates[seed % len(scene_candidates)]
    unique_trait = [
        "强调材质纹理",
        "强调功能卖点细节",
        "强调包装与主体关系",
        "强调真实陈列层次",
        "强调精致高级感",
    ][seed % 5]
    return (
        f"Use case: product-mockup\n"
        f"Asset type: ecommerce product main image\n"
        f"Primary request: 为商品“{product.name}”生成真实电商商品主图\n"
        f"Scene/backdrop: {scene}，{backdrop}\n"
        f"Subject: {product.subject}，属于{product.main_category}/{product.sub_category}\n"
        f"Style/medium: 高质量中文电商商品摄影，接近淘宝天猫京东专业商品主图，强调真实生活场景\n"
        f"Composition/framing: {composition}，单一主体为主，允许少量相关陪体，画面真实自然\n"
        f"Lighting/mood: {lighting}，商业广告级质感\n"
        f"Color palette: 贴合商品材质和品类，不要所有商品都同色，不要千篇一律白底\n"
        f"Materials/textures: {unique_trait}\n"
        f"Constraints: 图片必须与商品本身吻合；必须按真实使用或真实陈列场景生成；不可出现不相关品类；不可和其他商品图高度同模板化；不要文字；不要水印；不要logo\n"
        f"Avoid: {NEGATIVE_PROMPT}\n"
        f"Additional guidance: {hint}"
    )


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    for candidate in FONT_CANDIDATES:
        if candidate.exists():
            try:
                return ImageFont.truetype(str(candidate), size=size)
            except OSError:
                continue
    return ImageFont.load_default()


def interpolate_color(start: tuple[int, int, int], end: tuple[int, int, int], factor: float) -> tuple[int, int, int]:
    return tuple(int(start[index] + (end[index] - start[index]) * factor) for index in range(3))


def wrap_text(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.ImageFont, max_width: int) -> list[str]:
    lines: list[str] = []
    current = ""
    for char in text:
        if char == "\n":
            if current:
                lines.append(current)
                current = ""
            continue
        trial = current + char
        bbox = draw.textbbox((0, 0), trial, font=font)
        if current and bbox[2] - bbox[0] > max_width:
            lines.append(current)
            current = char
        else:
            current = trial
    if current:
        lines.append(current)
    return lines or [text]


def contains_any(text: str, keywords: list[str]) -> bool:
    return any(keyword in text for keyword in keywords)


def draw_device_shape(draw: ImageDraw.ImageDraw, product: ProductRecord, box: tuple[int, int, int, int], accent: tuple[int, int, int], dark: tuple[int, int, int], light: tuple[int, int, int]):
    left, top, right, bottom = box
    width = right - left
    height = bottom - top
    name = f"{product.name} {product.sub_category}"
    if contains_any(name, ["手表", "穿戴"]):
        cx = (left + right) // 2
        strap_width = max(36, width // 8)
        strap_color = tuple(max(0, channel - 40) for channel in dark)
        draw.rounded_rectangle((cx - strap_width, top, cx + strap_width, bottom), radius=20, fill=strap_color)
        watch_size = min(width, height) * 0.58
        watch_left = int(cx - watch_size / 2)
        watch_top = int(top + height * 0.2)
        watch_right = int(cx + watch_size / 2)
        watch_bottom = int(watch_top + watch_size)
        draw.rounded_rectangle((watch_left, watch_top, watch_right, watch_bottom), radius=42, fill=(24, 31, 45), outline=light, width=8)
        draw.rounded_rectangle((watch_left + 28, watch_top + 28, watch_right - 28, watch_bottom - 28), radius=30, fill=accent)
        return

    if contains_any(name, ["充电宝", "手机"]):
        radius = 44
        draw.rounded_rectangle(box, radius=radius, fill=(15, 23, 42), outline=light, width=8)
        inset = 28
        draw.rounded_rectangle((left + inset, top + inset, right - inset, bottom - inset), radius=28, fill=accent)
        if "手机" in name:
            camera_r = max(14, width // 24)
            draw.ellipse((right - 88, top + 42, right - 88 + camera_r * 2, top + 42 + camera_r * 2), fill=light)
            draw.ellipse((right - 130, top + 42, right - 130 + camera_r * 2, top + 42 + camera_r * 2), fill=light)
        else:
            draw.rounded_rectangle((left + width * 0.22, bottom - 36, right - width * 0.22, bottom - 18), radius=8, fill=light)
        return

    if contains_any(name, ["笔记本", "轻薄本"]):
        base_top = int(top + height * 0.62)
        draw.rounded_rectangle((left + 36, top, right - 36, base_top), radius=26, fill=(30, 41, 59), outline=light, width=8)
        draw.rounded_rectangle((left + 66, top + 28, right - 66, base_top - 30), radius=18, fill=accent)
        draw.polygon([(left + 12, base_top), (right - 12, base_top), (right + 46, bottom), (left - 46, bottom)], fill=dark)
        return

    if contains_any(name, ["打印机"]):
        draw.rounded_rectangle((left + 20, top + 50, right - 20, bottom - 40), radius=34, fill=dark)
        draw.rounded_rectangle((left + 70, top, right - 70, top + 120), radius=18, fill=light)
        draw.rounded_rectangle((left + 90, top + 110, right - 90, top + 200), radius=12, fill=accent)
        return

    if contains_any(name, ["键盘"]):
        draw.rounded_rectangle((left, top + 110, right, bottom - 20), radius=32, fill=dark)
        cols = 8
        rows = 3
        key_w = (width - 80) // cols
        key_h = (height - 210) // rows
        for row in range(rows):
            for col in range(cols):
                x1 = left + 28 + col * key_w
                y1 = top + 135 + row * key_h
                x2 = x1 + key_w - 12
                y2 = y1 + key_h - 12
                key_color = accent if (row + col) % 5 == 0 else light
                draw.rounded_rectangle((x1, y1, x2, y2), radius=8, fill=key_color)
        return

    if contains_any(name, ["破壁机", "料理", "榨汁"]):
        draw.rounded_rectangle((left + 90, top + 20, right - 90, top + int(height * 0.62)), radius=34, fill=light, outline=dark, width=6)
        draw.rectangle((left + 130, top + 70, right - 130, top + int(height * 0.46)), fill=accent)
        draw.rounded_rectangle((left + 120, top + int(height * 0.6), right - 120, bottom), radius=28, fill=dark)
        return

    if contains_any(name, ["加湿器"]):
        draw.rounded_rectangle(box, radius=72, fill=light, outline=accent, width=8)
        draw.ellipse((left + width * 0.36, top - 26, right - width * 0.36, top + 30), fill=light)
        for offset in (0, 26, 52):
            draw.arc((left + width * 0.36, top - 90 - offset, right - width * 0.36, top - 10 - offset), 200, 340, fill=(255, 255, 255), width=6)
        return

    if contains_any(name, ["扫地机器人"]):
        draw.ellipse(box, fill=dark, outline=light, width=10)
        draw.ellipse((left + 36, top + 36, right - 36, bottom - 36), fill=accent)
        draw.ellipse((left + width * 0.42, top + height * 0.18, right - width * 0.42, top + height * 0.34), fill=light)
        return

    if contains_any(name, ["茶几", "桌"]):
        draw.rounded_rectangle((left + 10, top + 140, right - 10, top + 220), radius=28, fill=dark)
        for x in (left + 90, right - 120):
            draw.rectangle((x, top + 220, x + 36, bottom), fill=dark)
        return

    if contains_any(name, ["枕"]):
        draw.rounded_rectangle(box, radius=96, fill=light, outline=accent, width=8)
        draw.arc((left + 80, top + 90, right - 80, bottom - 90), 200, 340, fill=accent, width=8)
        return

    if contains_any(name, ["衣架"]):
        cx = (left + right) // 2
        draw.arc((cx - 70, top, cx + 70, top + 120), 180, 360, fill=dark, width=12)
        draw.line((cx, top + 50, cx, top + 170), fill=dark, width=12)
        draw.line((left + 50, top + 180, cx, bottom), fill=accent, width=16)
        draw.line((right - 50, top + 180, cx, bottom), fill=accent, width=16)
        return

    if contains_any(name, ["衬衫", "风衣", "外套", "服"]):
        shoulder = max(26, width // 5)
        sleeve = max(18, width // 12)
        waist = max(44, width // 4)
        hem = max(70, width // 3)
        collar = max(18, width // 10)
        points = [
            (left + shoulder, top + 40), (left + sleeve, top + 120), (left + shoulder - sleeve // 2, top + 200),
            (left + waist, top + 140), (left + hem, top + 210), (left + hem, bottom),
            (right - hem, bottom), (right - hem, top + 210), (right - waist, top + 140),
            (right - shoulder + sleeve // 2, top + 200), (right - sleeve, top + 120), (right - shoulder, top + 40),
            ((left + right) // 2 + collar, top + 10), ((left + right) // 2 - collar, top + 10),
        ]
        draw.polygon(points, fill=accent)
        draw.line(((left + right) // 2, top + 24, (left + right) // 2, bottom - 20), fill=light, width=8)
        return

    if contains_any(name, ["包"]):
        body_margin = max(28, width // 6)
        handle_margin = max(54, width // 4)
        clasp_margin = max(82, width // 3)
        draw.rounded_rectangle((left + body_margin, top + 110, right - body_margin, bottom), radius=46, fill=accent)
        draw.arc((left + handle_margin, top, right - handle_margin, top + max(180, height // 2)), 180, 360, fill=light, width=18)
        draw.rectangle((left + clasp_margin, top + 230, right - clasp_margin, top + 280), fill=dark)
        return

    if contains_any(name, ["面膜"]):
        pack_margin = max(56, width // 4)
        label_margin = max(90, width // 3)
        text_margin = max(84, width // 3)
        label_top = top + max(54, height // 6)
        label_bottom = label_top + max(56, height // 7)
        copy_top = top + max(180, height // 2)
        copy_bottom = bottom - max(48, height // 5)
        draw.rounded_rectangle((left + pack_margin, top, right - pack_margin, bottom), radius=28, fill=accent)
        draw.rounded_rectangle((left + label_margin, label_top, right - label_margin, label_bottom), radius=16, fill=light)
        draw.rounded_rectangle((left + text_margin, copy_top, right - text_margin, copy_bottom), radius=24, fill=(255, 255, 255))
        return

    if contains_any(name, ["眼影", "彩妆"]):
        draw.rounded_rectangle((left + 40, top + 130, right - 40, bottom), radius=34, fill=dark)
        cell = (width - 180) // 3
        colors = [accent, (244, 114, 182), (251, 191, 36), light, (251, 113, 133), (192, 132, 252)]
        idx = 0
        for row in range(2):
            for col in range(3):
                x1 = left + 70 + col * (cell + 18)
                y1 = top + 170 + row * (cell + 18)
                x2 = x1 + cell
                y2 = y1 + cell
                draw.rounded_rectangle((x1, y1, x2, y2), radius=18, fill=colors[idx % len(colors)])
                idx += 1
        return

    draw.rounded_rectangle(box, radius=64, fill=accent, outline=light, width=8)
    draw.rounded_rectangle((left + 80, top + 80, right - 80, bottom - 80), radius=34, fill=light)


def variant_label(variant: str) -> str:
    return {
        "main": "商详主图",
        "detail": "细节展示",
        "scene": "场景展示",
    }.get(variant, "商品图片")


def generate_local_product_image(product: ProductRecord, local_path: Path, variant: str = "main"):
    generate_local_product_image_with_options(
        product=product,
        local_path=local_path,
        variant=variant,
        image_size=DEFAULT_IMAGE_SIZE,
        image_format=DEFAULT_IMAGE_FORMAT,
        jpeg_quality=DEFAULT_JPEG_QUALITY,
    )


def generate_local_product_image_with_options(
    product: ProductRecord,
    local_path: Path,
    variant: str = "main",
    image_size: int = DEFAULT_IMAGE_SIZE,
    image_format: str = DEFAULT_IMAGE_FORMAT,
    jpeg_quality: int = DEFAULT_JPEG_QUALITY,
):
    width = max(1024, image_size)
    height = max(1024, image_size)
    primary, secondary, soft, dark = CATEGORY_PALETTES.get(product.main_category, CATEGORY_PALETTES["通用商品"])
    image = Image.new("RGB", (width, height), primary)
    draw = ImageDraw.Draw(image)
    seed = product.product_id + {"main": 0, "detail": 17, "scene": 29}.get(variant, 0)

    for y in range(height):
        factor = y / max(1, height - 1)
        draw.line((0, y, width, y), fill=interpolate_color(primary, secondary, factor), width=1)

    overlay = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    overlay_draw = ImageDraw.Draw(overlay)
    left_blob_x = -220 + (seed % 5) * 36
    right_blob_x = width - 440 + (seed % 7) * 22
    stage_top = 680 + (seed % 3) * 10
    overlay_draw.ellipse((left_blob_x, -120, left_blob_x + 620, 360), fill=(*soft, 118))
    overlay_draw.ellipse((right_blob_x, 60, right_blob_x + 520, 540), fill=(255, 255, 255, 92))
    overlay_draw.polygon([(0, height), (0, 280 + (seed % 4) * 18), (width * 0.42, height)], fill=(255, 255, 255, 32))
    overlay_draw.rounded_rectangle((72, stage_top, width - 72, height - 72), radius=42, fill=(255, 255, 255, 208))
    overlay = overlay.filter(ImageFilter.GaussianBlur(radius=2))
    image = Image.alpha_composite(image.convert("RGBA"), overlay)
    draw = ImageDraw.Draw(image)

    shadow = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_draw.ellipse((228, 580, 796, 770), fill=(0, 0, 0, 88))
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=18))
    image = Image.alpha_composite(image, shadow)
    draw = ImageDraw.Draw(image)

    stage_color = tuple(max(0, value - 18) for value in primary)
    if variant == "detail":
        stage_rect = (212, 580, 812, 766)
        shape_box = (268, 136, 756, 676)
    elif variant == "scene":
        stage_rect = (232, 552, 792, 744)
        shape_box = (344, 216, 684, 562)
        draw.rounded_rectangle((108, 242, 242, 520), radius=34, fill=(255, 255, 255, 92))
        draw.rounded_rectangle((792, 266, 920, 542), radius=34, fill=(255, 255, 255, 78))
        draw.ellipse((132, 552, 236, 656), fill=(255, 255, 255, 126))
    else:
        stage_rect = (250, 540, 774, 730)
        shape_box = (328, 184, 696, 590)
    draw.rounded_rectangle(stage_rect, radius=96, fill=(*soft, 235), outline=(255, 255, 255, 220), width=6)
    draw.ellipse((stage_rect[0] + 50, stage_rect[1] + 28, stage_rect[2] - 50, stage_rect[3] - 14), fill=stage_color)
    draw.ellipse((stage_rect[0] + 88, stage_rect[1] + 52, stage_rect[2] - 88, stage_rect[3] - 34), fill=tuple(min(255, value + 35) for value in primary))

    draw_device_shape(draw, product, shape_box, primary, dark, secondary)

    tag_font = load_font(32)
    title_font = load_font(56)
    meta_font = load_font(28)
    price_font = load_font(42)

    draw.rounded_rectangle((72, 72, 286, 136), radius=24, fill=(255, 255, 255, 208))
    draw.text((104, 89), product.main_category, fill=dark, font=tag_font)

    title_lines = wrap_text(draw, product.name, title_font, 800)
    title_y = 744 if variant == "main" else 724
    for line in title_lines[:2]:
        draw.text((96, title_y), line, fill=dark, font=title_font)
        title_y += 70

    sub_text = f"{product.sub_category}  |  {variant_label(variant)}  |  #{product.product_id:04d}"
    draw.text((98, 884), sub_text, fill=(75, 85, 99), font=meta_font)

    price_text = f"¥{product.price:.2f}"
    price_bbox = draw.textbbox((0, 0), price_text, font=price_font)
    price_x = width - 96 - (price_bbox[2] - price_bbox[0])
    draw.text((price_x, 876), price_text, fill=primary, font=price_font)

    save_product_image(image, local_path, image_format=image_format, jpeg_quality=jpeg_quality)


def normalize_image_format(image_format: str) -> str:
    normalized = image_format.strip().upper()
    aliases = {
        "JPG": "JPEG",
        "JPEG": "JPEG",
        "PNG": "PNG",
        "WEBP": "WEBP",
    }
    if normalized not in aliases:
        raise ValueError(f"不支持的图片格式: {image_format}")
    return aliases[normalized]


def image_suffix_for_format(image_format: str) -> str:
    normalized = normalize_image_format(image_format)
    return {
        "JPEG": ".jpg",
        "PNG": ".png",
        "WEBP": ".webp",
    }[normalized]


def local_image_path(batch_dir: Path, product_id: int, variant: str, image_format: str) -> Path:
    suffix = image_suffix_for_format(image_format)
    return batch_dir / f"product-{product_id:04d}-{variant}{suffix}"


def save_product_image(image: Image.Image, local_path: Path, image_format: str, jpeg_quality: int):
    normalized = normalize_image_format(image_format)
    local_path.parent.mkdir(parents=True, exist_ok=True)
    save_kwargs: dict[str, Any] = {"format": normalized}
    if normalized == "JPEG":
        save_kwargs.update({"quality": jpeg_quality, "subsampling": 1, "optimize": False})
    elif normalized == "PNG":
        save_kwargs.update({"optimize": False, "compress_level": 1})
    elif normalized == "WEBP":
        save_kwargs.update({"quality": jpeg_quality, "method": 4})
    image.convert("RGB").save(local_path, **save_kwargs)


def request_image_generation(settings: dict[str, Any], prompt: str) -> str:
    headers = {
        "Authorization": f"Bearer {settings['api_key']}",
        "Content-Type": "application/json",
        "X-DashScope-Async": "enable",
    }
    payload = {
        "model": settings["model"],
        "input": {
            "prompt": prompt,
        },
        "parameters": {
            "size": "1024*1024",
            "n": 1,
        },
    }

    api_url = settings["api_url"].rstrip("/")
    candidates = [
        f"{api_url}/services/aigc/text2image/image-synthesis",
        "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis",
    ]
    last_error = None
    for endpoint in candidates:
        try:
            response = requests.post(endpoint, headers=headers, json=payload, timeout=120)
            if response.status_code >= 400:
                last_error = f"{endpoint} -> HTTP {response.status_code}: {response.text[:400]}"
                continue
            data = response.json()
            task_id = extract_task_id(data)
            if task_id:
                image_url = poll_async_task(headers, task_id)
                if image_url:
                    return image_url
            image_url = extract_image_url(data)
            if image_url:
                return image_url
            last_error = f"{endpoint} -> 未找到图片地址: {json.dumps(data, ensure_ascii=False)[:500]}"
        except Exception as exc:
            last_error = f"{endpoint} -> {exc}"
    raise RuntimeError(last_error or "图片生成失败")


def extract_task_id(payload: dict[str, Any]) -> str | None:
    output = payload.get("output") or {}
    for key in ("task_id", "taskId"):
        if output.get(key):
            return output[key]
    if payload.get("task_id"):
        return payload["task_id"]
    return None


def poll_async_task(headers: dict[str, str], task_id: str) -> str | None:
    endpoint = f"https://dashscope.aliyuncs.com/api/v1/tasks/{task_id}"
    last_payload: dict[str, Any] | None = None
    for _ in range(60):
        response = requests.get(endpoint, headers=headers, timeout=120)
        response.raise_for_status()
        payload = response.json()
        last_payload = payload
        task_status = ((payload.get("output") or {}).get("task_status") or "").upper()
        if task_status == "SUCCEEDED":
            return extract_image_url(payload)
        if task_status in {"FAILED", "CANCELED"}:
            raise RuntimeError(f"文生图异步任务失败: {json.dumps(payload, ensure_ascii=False)[:500]}")
        time.sleep(2)
    raise RuntimeError(f"文生图异步任务超时: {json.dumps(last_payload or {}, ensure_ascii=False)[:500]}")


def extract_image_url(payload: dict[str, Any]) -> str | None:
    output = payload.get("output") or {}
    for key in ("results", "result", "images"):
        value = output.get(key)
        if isinstance(value, list) and value:
            first = value[0]
            if isinstance(first, dict):
                for url_key in ("url", "image_url", "imageUrl"):
                    if first.get(url_key):
                        return first[url_key]
            if isinstance(first, str):
                return first
        if isinstance(value, dict):
            for url_key in ("url", "image_url", "imageUrl"):
                if value.get(url_key):
                    return value[url_key]
    for url_key in ("url", "image_url", "imageUrl"):
        if output.get(url_key):
            return output[url_key]
    return None


def download_image(url: str, path: Path):
    response = requests.get(url, timeout=120)
    response.raise_for_status()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(response.content)


def image_signature(path: Path) -> str:
    image = Image.open(path).convert("L").resize((8, 8))
    pixels = list(image.tobytes())
    average = sum(pixels) / len(pixels)
    bits = "".join("1" if pixel >= average else "0" for pixel in pixels)
    color_image = Image.open(path).convert("RGB").resize((16, 16))
    stat = ImageStat.Stat(color_image)
    rgb = ",".join(str(int(value)) for value in stat.mean)
    return f"{bits}|{rgb}"


def ensure_not_too_similar(current_path: Path, previous_signatures: list[str]):
    signature = image_signature(current_path)
    if signature in previous_signatures:
        print(f"[warn] 图片签名重复: {current_path.name}，继续保留当前图片")
        return
    previous_signatures.append(signature)


@lru_cache(maxsize=1)
def get_oss_bucket():
    config = build_oss_config_from_env("products/generated")
    if config is None:
        access_key_id = os.getenv("ALIYUN_OSS_ACCESS_KEY_ID", "").strip()
        access_key_secret = os.getenv("ALIYUN_OSS_ACCESS_KEY_SECRET", "").strip()
        if not access_key_id or not access_key_secret:
            raise RuntimeError(
                "已检测到使用阿里云 OSS，但当前进程仍未读取到 ALIYUN_OSS_ACCESS_KEY_ID / "
                "ALIYUN_OSS_ACCESS_KEY_SECRET，暂时无法实际上传 OSS"
            )
        from types import SimpleNamespace
        config = SimpleNamespace(
            endpoint=os.getenv("ALIYUN_OSS_ENDPOINT", DEFAULT_OSS_ENDPOINT),
            access_key_id=access_key_id,
            access_key_secret=access_key_secret,
            bucket_name=os.getenv("ALIYUN_OSS_BUCKET_NAME", DEFAULT_BUCKET_NAME),
            public_base_url=os.getenv("ALIYUN_OSS_URL_PREFIX", DEFAULT_PUBLIC_URL),
        )
    import oss2  # type: ignore

    auth = oss2.Auth(config.access_key_id, config.access_key_secret)
    bucket = oss2.Bucket(auth, config.endpoint, config.bucket_name)
    return bucket, config


def upload_file_to_oss(local_path: Path, object_key: str) -> str:
    bucket, config = get_oss_bucket()
    bucket.put_object_from_file(object_key, str(local_path))
    if config.public_base_url:
        return f"{config.public_base_url.rstrip('/')}/{object_key}"
    endpoint_host = config.endpoint.split("://", 1)[-1]
    return f"https://{config.bucket_name}.{endpoint_host}/{object_key}"


def build_object_key(product: ProductRecord, local_path: Path, variant: str) -> str:
    slug_parts = re.findall(r"[a-zA-Z0-9]+", f"{product.name} {product.sub_category}".lower())
    safe_slug = "-".join(slug_parts[:5]).strip("-")
    safe_name = f"{safe_slug}-{product.product_id:04d}" if safe_slug else f"product-{product.product_id:04d}-category-{product.category_id}"
    return f"products/generated/2026/04/18/{safe_name}-{variant}{local_path.suffix.lower()}"


def write_sql_updates(rows: list[tuple[int, str, list[str]]], output_sql: Path):
    output_sql.parent.mkdir(parents=True, exist_ok=True)
    with output_sql.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("-- 商品图片 OSS 回填 SQL\n")
        handle.write(f"-- 生成时间: {NOW}\n\n")
        for product_id, image_url, image_urls in rows:
            images_json = json.dumps(image_urls, ensure_ascii=False).replace("'", "''")
            safe_url = image_url.replace("'", "''")
            handle.write(
                "UPDATE `product` SET `image` = '{url}', `images` = '{images}', `update_time` = '{now}' WHERE `id` = {product_id};\n".format(
                    url=safe_url,
                    images=images_json,
                    now=NOW,
                    product_id=product_id,
                )
            )


def run_batch(
    sql_chunk: Path,
    start_id: int,
    count: int,
    skip_oss: bool,
    gallery_count: int,
    sleep_ms: int,
    upload_workers: int,
    image_size: int,
    image_format: str,
    jpeg_quality: int,
    write_prompts: bool,
    try_ai_main: bool,
):
    settings = read_application_settings()
    products = parse_products_from_sql(sql_chunk, start_id, count)
    if not products:
        raise RuntimeError(f"未在 {sql_chunk} 中找到商品区间 {start_id}-{start_id + count - 1}")

    batch_dir = OUTPUT_DIR / f"{start_id:04d}-{start_id + count - 1:04d}"
    batch_dir.mkdir(parents=True, exist_ok=True)
    update_rows: list[tuple[int, str, list[str]]] = []
    previous_signatures: list[str] = []
    if try_ai_main:
        local_fallback_reason = "未读取到 AI_API_KEY，自动切换本地商品图生成" if not settings["api_key"] else ""
    else:
        local_fallback_reason = "已启用本地极速生成模式"
    warned_fallback = False
    normalized_image_format = normalize_image_format(image_format)
    prompt_needed = write_prompts or (try_ai_main and not local_fallback_reason)

    for index, product in enumerate(products, start=1):
        prompt = build_prompt(product)
        if prompt_needed:
            prompt_path = batch_dir / f"product-{product.product_id:04d}.prompt.txt"
            prompt_path.write_text(prompt, encoding="utf-8")

        gallery_variants = GALLERY_VARIANTS[: max(1, min(gallery_count, len(GALLERY_VARIANTS)))]
        local_paths: list[tuple[str, Path]] = []
        for variant in gallery_variants:
            local_path = local_image_path(batch_dir, product.product_id, variant, normalized_image_format)
            if local_fallback_reason:
                if not warned_fallback:
                    print(f"[warn] {local_fallback_reason}")
                    warned_fallback = True
                generate_local_product_image_with_options(
                    product,
                    local_path,
                    variant=variant,
                    image_size=image_size,
                    image_format=normalized_image_format,
                    jpeg_quality=jpeg_quality,
                )
            else:
                try:
                    if variant == "main" and try_ai_main:
                        remote_image_url = request_image_generation(settings, prompt)
                        download_image(remote_image_url, local_path)
                    else:
                        generate_local_product_image_with_options(
                            product,
                            local_path,
                            variant=variant,
                            image_size=image_size,
                            image_format=normalized_image_format,
                            jpeg_quality=jpeg_quality,
                        )
                except Exception as exc:
                    local_fallback_reason = f"AI 文生图不可用，自动切换本地商品图生成: {exc}"
                    if not warned_fallback:
                        print(f"[warn] {local_fallback_reason}")
                        warned_fallback = True
                    generate_local_product_image_with_options(
                        product,
                        local_path,
                        variant=variant,
                        image_size=image_size,
                        image_format=normalized_image_format,
                        jpeg_quality=jpeg_quality,
                    )
            local_paths.append((variant, local_path))

        if local_paths:
            ensure_not_too_similar(local_paths[0][1], previous_signatures)

        if skip_oss:
            print(f"[{index}/{len(products)}] product={product.product_id} -> local={local_paths[0][1].parent}")
            continue

        image_urls_by_variant: dict[str, str] = {}
        with ThreadPoolExecutor(max_workers=max(1, min(upload_workers, len(local_paths)))) as executor:
            future_map = {
                executor.submit(upload_file_to_oss, local_path, build_object_key(product, local_path, variant)): variant
                for variant, local_path in local_paths
            }
            for future in as_completed(future_map):
                variant = future_map[future]
                image_urls_by_variant[variant] = future.result()

        image_urls = [image_urls_by_variant[variant] for variant, _ in local_paths]
        main_url = image_urls_by_variant.get("main", image_urls[0])
        update_rows.append((product.product_id, main_url or image_urls[0], image_urls))
        print(f"[{index}/{len(products)}] product={product.product_id} -> {main_url or image_urls[0]}")
        if sleep_ms > 0:
            time.sleep(sleep_ms / 1000)

    if skip_oss:
        print(f"[ok] 本地验证图片已生成: {batch_dir}")
        return

    output_sql = SQL_DIR / f"seed-part-products-images-{start_id:04d}-{start_id + count - 1:04d}.sql"
    write_sql_updates(update_rows, output_sql)
    print(f"[ok] 已生成 OSS 图片回填 SQL: {output_sql}")


def parse_args():
    parser = argparse.ArgumentParser(description="生成商品图并上传 OSS")
    parser.add_argument("--sql-chunk", type=Path, default=SQL_DIR / "seed-part-products-0001-1000.sql")
    parser.add_argument("--start-id", type=int, required=True)
    parser.add_argument("--count", type=int, default=10)
    parser.add_argument("--gallery-count", type=int, default=3, help="每个商品生成的图片数量，最多 3 张")
    parser.add_argument("--sleep-ms", type=int, default=0, help="每个商品上传后的等待毫秒数")
    parser.add_argument("--upload-workers", type=int, default=6, help="每个商品并发上传到 OSS 的线程数")
    parser.add_argument("--image-size", type=int, default=DEFAULT_IMAGE_SIZE, help="本地生成图片尺寸，默认 768")
    parser.add_argument("--image-format", choices=["jpg", "jpeg", "png", "webp"], default="jpg", help="本地生成图片格式")
    parser.add_argument("--jpeg-quality", type=int, default=DEFAULT_JPEG_QUALITY, help="JPEG 或 WebP 质量，默认 88")
    parser.add_argument("--write-prompts", action="store_true", help="将每个商品的提示词额外写入本地文件")
    parser.add_argument("--try-ai-main", action="store_true", help="主图优先尝试 AI 生成，失败后再回退本地")
    parser.add_argument("--skip-oss", action="store_true", help="只生成并下载本地图片，不上传 OSS")
    return parser.parse_args()


def main():
    args = parse_args()
    run_batch(
        args.sql_chunk,
        args.start_id,
        args.count,
        args.skip_oss,
        args.gallery_count,
        args.sleep_ms,
        args.upload_workers,
        args.image_size,
        args.image_format,
        args.jpeg_quality,
        args.write_prompts,
        args.try_ai_main,
    )


if __name__ == "__main__":
    main()
