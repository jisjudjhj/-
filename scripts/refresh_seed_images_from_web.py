#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从网络检索商品/基础资源图片，上传到 OSS，并生成初始化 SQL 更新分片。

约束：
1. 不走任何文生图或本地生成图逻辑。
2. 优先按商品名、子类目和使用场景构造关键词。
3. 用感知哈希做基础去重，尽量规避重复或过于相似的图片。
"""

from __future__ import annotations

import argparse
import html
import io
import json
import random
import re
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import requests
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from backend.python_analytics.spark_jobs.oss_archive import build_oss_config_from_env
from scripts.generate_base_images_to_oss import parse_base_assets
from scripts.generate_product_images_to_oss import DEFAULT_BUCKET_NAME, DEFAULT_OSS_ENDPOINT, DEFAULT_PUBLIC_URL, ProductRecord, parse_products_from_sql


CHUNK_DIR = ROOT / "backend" / "src" / "main" / "resources" / "sql" / "chunks"
OUTPUT_DIR = ROOT / "output" / "web-seed-images"
NOW = "2026-04-19 02:40:00"
REQUEST_TIMEOUT = 25
IMAGE_SIZE = 960
JPEG_QUALITY = 88
SIMILARITY_THRESHOLD = 0
PRODUCT_PREFIX = "products/generated/2026/04/19"
ASSET_PREFIX = "assets/generated/2026/04/19"
DELETE_PREFIXES = [
    "products/generated/",
    "assets/generated/",
]
THREAD_LOCAL = threading.local()
SEARCH_CACHE: dict[str, list[str]] = {}
SEARCH_CACHE_LOCK = threading.Lock()

PRODUCT_NAME_RE = re.compile(r"[A-Za-z0-9]+")
PRODUCT_INSERT_RANGE_RE = re.compile(r"seed-part-products-(\d+)-(\d+)\.sql$")

CATEGORY_TAGS = {
    "手机数码": ["electronics", "gadget", "device"],
    "电脑办公": ["office", "workspace", "desk"],
    "家用电器": ["home", "appliance", "interior"],
    "家居家装": ["home", "furniture", "interior"],
    "服饰鞋包": ["fashion", "apparel", "lifestyle"],
    "美妆护肤": ["beauty", "cosmetics", "skincare"],
    "食品生鲜": ["food", "grocery", "fresh"],
    "母婴用品": ["baby", "kids", "family"],
    "运动户外": ["fitness", "outdoor", "sports"],
    "图书文具": ["books", "stationery", "desk"],
    "汽车用品": ["car", "auto", "vehicle"],
    "宠物生活": ["pet", "animal", "home"],
}

SCENE_TAGS = {
    "手机数码": ["desk", "technology", "modern"],
    "电脑办公": ["workspace", "office", "minimal"],
    "家用电器": ["kitchen", "livingroom", "home"],
    "家居家装": ["interior", "livingroom", "cozy"],
    "服饰鞋包": ["fashion", "closet", "style"],
    "美妆护肤": ["bathroom", "vanity", "clean"],
    "食品生鲜": ["table", "kitchen", "meal"],
    "母婴用品": ["nursery", "family", "soft"],
    "运动户外": ["gym", "outdoor", "active"],
    "图书文具": ["desk", "study", "reading"],
    "汽车用品": ["car", "garage", "detail"],
    "宠物生活": ["pet", "home", "play"],
}

KEYWORD_RULES = [
    (r"折叠手机", ["foldable", "smartphone", "mobile"]),
    (r"游戏手机", ["gaming", "smartphone", "mobile"]),
    (r"影像手机", ["camera", "smartphone", "mobile"]),
    (r"旗舰手机", ["premium", "smartphone", "mobile"]),
    (r"智能手表|健康手表", ["smartwatch", "wearable", "watch"]),
    (r"蓝牙耳机", ["wireless", "earbuds", "headphones"]),
    (r"运动手环", ["fitness", "tracker", "smartband"]),
    (r"磁吸充电宝|充电宝", ["powerbank", "battery", "charger"]),
    (r"GaN充电器|充电器", ["charger", "adapter", "electronics"]),
    (r"手机壳", ["phonecase", "smartphone", "accessory"]),
    (r"数据线", ["usb", "cable", "charger"]),
    (r"轻薄本|商务本|笔记本电脑", ["laptop", "notebook", "computer"]),
    (r"显示器", ["monitor", "screen", "desktop"]),
    (r"打印机", ["printer", "office", "device"]),
    (r"键盘", ["keyboard", "mechanical", "desk"]),
    (r"文件夹", ["folder", "stationery", "office"]),
    (r"钢笔", ["fountainpen", "pen", "stationery"]),
    (r"马克笔", ["marker", "art", "stationery"]),
    (r"笔记本", ["notebook", "journal", "stationery"]),
    (r"人文社科书", ["book", "hardcover", "reading"]),
    (r"儿童绘本", ["picturebook", "children", "book"]),
    (r"商业管理书", ["businessbook", "management", "reading"]),
    (r"科普读物", ["sciencebook", "education", "reading"]),
    (r"精华液", ["serum", "skincare", "bottle"]),
    (r"防晒乳", ["sunscreen", "skincare", "cosmetics"]),
    (r"面膜", ["facemask", "skincare", "beauty"]),
    (r"面霜", ["facecream", "skincare", "beauty"]),
    (r"口红", ["lipstick", "makeup", "beauty"]),
    (r"香水", ["perfume", "fragrance", "beauty"]),
    (r"洗发", ["shampoo", "haircare", "bottle"]),
    (r"沐浴", ["bodywash", "bath", "bottle"]),
    (r"冻干果脆", ["fruitsnack", "driedfruit", "food"]),
    (r"坚果礼盒", ["nuts", "giftbox", "snack"]),
    (r"海苔脆", ["seaweed", "snack", "food"]),
    (r"肉脯", ["jerky", "snack", "food"]),
    (r"方便面|速食", ["instantfood", "noodles", "meal"]),
    (r"大米|食用油", ["grocery", "rice", "kitchen"]),
    (r"牛排|虾仁|水果", ["freshfood", "ingredient", "meal"]),
    (r"奶瓶|辅食", ["baby", "feeding", "product"]),
    (r"纸尿裤|尿裤", ["diaper", "baby", "care"]),
    (r"积木|玩具", ["toy", "kids", "colorful"]),
    (r"跑步鞋|运动鞋", ["sneakers", "running", "shoes"]),
    (r"瑜伽垫", ["yogamat", "fitness", "exercise"]),
    (r"哑铃", ["dumbbell", "fitness", "gym"]),
    (r"帐篷|露营", ["camping", "tent", "outdoor"]),
    (r"骑行", ["cycling", "bike", "helmet"]),
    (r"行李箱|双肩包|挎包", ["bag", "luggage", "fashion"]),
    (r"衬衫|T恤|卫衣|夹克", ["fashion", "clothing", "apparel"]),
    (r"连衣裙|半裙|女装", ["dress", "fashion", "apparel"]),
    (r"沙发", ["sofa", "furniture", "interior"]),
    (r"茶几", ["coffeetable", "furniture", "interior"]),
    (r"电视柜", ["tvstand", "furniture", "interior"]),
    (r"床品|四件套", ["bedding", "bedroom", "home"]),
    (r"枕", ["pillow", "bedroom", "home"]),
    (r"收纳箱|整理盒", ["storage", "organizer", "home"]),
    (r"扫地机器人", ["robotvacuum", "cleaning", "appliance"]),
    (r"加湿器", ["humidifier", "appliance", "home"]),
    (r"空气炸锅", ["airfryer", "kitchen", "appliance"]),
    (r"破壁机|榨汁", ["blender", "kitchen", "appliance"]),
    (r"车载充电器", ["carcharger", "auto", "accessory"]),
    (r"行车记录仪", ["dashcam", "car", "device"]),
    (r"脚垫|坐垫", ["carinterior", "auto", "accessory"]),
    (r"洗车液|车蜡", ["carcare", "auto", "detail"]),
    (r"猫粮|狗粮", ["petfood", "pet", "animal"]),
    (r"猫砂", ["catlitter", "pet", "home"]),
    (r"逗猫棒|磨牙", ["pettoy", "pet", "play"]),
]

BASE_CATEGORY_RULES = [
    ("手机数码", ["electronics", "store", "tech"]),
    ("电脑办公", ["office", "workspace", "desk"]),
    ("家用电器", ["appliance", "home", "interior"]),
    ("家居家装", ["furniture", "interior", "home"]),
    ("服饰鞋包", ["fashion", "apparel", "style"]),
    ("美妆护肤", ["beauty", "skincare", "cosmetics"]),
    ("食品生鲜", ["grocery", "food", "fresh"]),
    ("母婴用品", ["baby", "kids", "family"]),
    ("运动户外", ["fitness", "sports", "outdoor"]),
    ("图书文具", ["books", "stationery", "study"]),
    ("汽车用品", ["car", "auto", "garage"]),
    ("宠物生活", ["pet", "animal", "home"]),
]


@dataclass
class ProductImageRow:
    product_id: int
    image: str
    images: list[str]


@dataclass
class BaseImageRows:
    users: list[tuple[int, str]]
    categories: list[tuple[int, str]]
    banners: list[tuple[int, str]]


class DedupeStore:
    def __init__(self, threshold: int):
        self.threshold = threshold
        self._hashes: list[int] = []
        self._lock = threading.Lock()

    def accept(self, image: Image.Image) -> bool:
        image_hash = dhash(image)
        with self._lock:
            for existing in self._hashes:
                if hamming_distance(image_hash, existing) <= self.threshold:
                    return False
            self._hashes.append(image_hash)
        return True


def sanitize_tags(tags: Iterable[str]) -> list[str]:
    cleaned: list[str] = []
    for tag in tags:
        normalized = re.sub(r"[^a-z0-9]+", "", tag.lower())
        if normalized and normalized not in cleaned:
            cleaned.append(normalized)
    return cleaned[:8]


def keyword_tags_for_text(text: str, main_category: str) -> list[str]:
    for pattern, tags in KEYWORD_RULES:
        if re.search(pattern, text):
            return sanitize_tags([*tags, *CATEGORY_TAGS.get(main_category, [])])
    return sanitize_tags([*CATEGORY_TAGS.get(main_category, []), "product"])


def product_variant_tags(product: ProductRecord, variant: str) -> list[str]:
    base = keyword_tags_for_text(product.name, product.main_category)
    slug_tags = PRODUCT_NAME_RE.findall(product.name.lower())
    extra = [token for token in slug_tags if token not in {"ultra", "edge", "studio", "zen", "air", "pro", "s"}]
    if variant == "detail":
        return sanitize_tags([*base, "detail", "closeup", *extra[:2]])
    if variant == "scene":
        return sanitize_tags([*base, *SCENE_TAGS.get(product.main_category, []), *extra[:2]])
    return sanitize_tags([*base, "product", "studio", *extra[:2]])


def query_terms_for_product(product: ProductRecord, variant: str) -> list[str]:
    chinese_subject = re.sub(r"[A-Za-z0-9 ]+", "", product.subject).strip()
    chinese_sub_category = re.sub(r"[A-Za-z0-9 ]+", "", product.sub_category).strip()
    topic = chinese_subject or chinese_sub_category or product.main_category
    if variant == "detail":
        return [
            f"{topic} 细节图",
            f"{topic} 商品细节图",
            f"{topic} 电商实物图",
        ]
    if variant == "scene":
        return [
            f"{topic} 场景图",
            f"{product.main_category} {topic} 场景图",
            f"{topic} 使用场景图",
        ]
    return [
        f"{topic} 商品图",
        f"{topic} 电商图",
        f"{topic} 实物图",
    ]


def query_terms_for_avatar(role: str, nickname: str) -> list[str]:
    tags = merchant_or_user_tags(role, nickname)
    if role == "merchant":
        return [f"{' '.join(tags[:4])} store owner portrait", f"{' '.join(tags[:4])} lifestyle portrait"]
    if role == "admin":
        return [f"{' '.join(tags[:4])} executive portrait", f"{' '.join(tags[:4])} office portrait"]
    return [f"{' '.join(tags[:4])} portrait", f"{' '.join(tags[:4])} headshot"]


def query_terms_for_category(name: str) -> list[str]:
    return [f"{name} 商品图", f"{name} 配图"]


def query_terms_for_banner(title: str) -> list[str]:
    return [f"{title} 场景图", f"{title} 海报图"]


def merchant_or_user_tags(role: str, nickname: str) -> list[str]:
    if role == "admin":
        return ["business", "portrait", "office", "professional"]
    if role == "merchant":
        for key, tags in BASE_CATEGORY_RULES:
            if key in nickname:
                return sanitize_tags([*tags, "portrait", "owner"])
        return ["business", "portrait", "owner", "shop"]
    return ["portrait", "person", "lifestyle", "face"]


def category_tags(name: str) -> list[str]:
    for key, tags in BASE_CATEGORY_RULES:
        if key in name:
            return sanitize_tags([*tags, "product"])
    return ["lifestyle", "product", "detail"]


def banner_tags(title: str) -> list[str]:
    for key, tags in BASE_CATEGORY_RULES:
        if key in title:
            return sanitize_tags([*tags, "banner", "hero"])
    return ["shopping", "banner", "hero", "lifestyle"]


def get_oss_bucket():
    config = build_oss_config_from_env("products/generated")
    if config is None:
        from types import SimpleNamespace
        import os

        config = SimpleNamespace(
            endpoint=os.getenv("ALIYUN_OSS_ENDPOINT", DEFAULT_OSS_ENDPOINT),
            access_key_id=os.getenv("ALIYUN_OSS_ACCESS_KEY_ID", ""),
            access_key_secret=os.getenv("ALIYUN_OSS_ACCESS_KEY_SECRET", ""),
            bucket_name=os.getenv("ALIYUN_OSS_BUCKET_NAME", DEFAULT_BUCKET_NAME),
            public_base_url=os.getenv("ALIYUN_OSS_URL_PREFIX", DEFAULT_PUBLIC_URL),
        )
    import oss2  # type: ignore

    auth = oss2.Auth(config.access_key_id, config.access_key_secret)
    bucket = oss2.Bucket(auth, config.endpoint, config.bucket_name)
    return bucket, config


def public_url(config, object_key: str) -> str:
    if getattr(config, "public_base_url", ""):
        return f"{config.public_base_url.rstrip('/')}/{object_key}"
    endpoint_host = config.endpoint.split("://", 1)[-1]
    return f"https://{config.bucket_name}.{endpoint_host}/{object_key}"


def delete_prefixes(prefixes: Iterable[str]):
    import oss2  # type: ignore

    bucket, _ = get_oss_bucket()
    for prefix in prefixes:
        object_keys = [obj.key for obj in oss2.ObjectIterator(bucket, prefix=prefix)]
        if not object_keys:
            continue
        for index in range(0, len(object_keys), 1000):
            bucket.batch_delete_objects(object_keys[index:index + 1000])
        print(f"[cleanup] deleted {len(object_keys)} objects under {prefix}")


def session() -> requests.Session:
    existing = getattr(THREAD_LOCAL, "session", None)
    if existing is not None:
        return existing
    s = requests.Session()
    s.headers.update(
        {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            "(KHTML, like Gecko) Chrome/135.0 Safari/537.36",
            "Accept": "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
        }
    )
    THREAD_LOCAL.session = s
    return s


def fetch_loremflickr(tags: list[str], lock_value: int, width: int, height: int) -> bytes:
    tag_string = ",".join(sanitize_tags(tags)) or "product"
    url = f"https://loremflickr.com/{width}/{height}/{tag_string}?lock={lock_value}"
    last_error: Exception | None = None
    for attempt in range(3):
        try:
            response = session().get(url, timeout=REQUEST_TIMEOUT, allow_redirects=True)
            response.raise_for_status()
            return response.content
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            time.sleep(0.6 * (attempt + 1))
    raise RuntimeError(f"loremflickr 请求失败: {url}") from last_error


def search_bing_image_urls(query: str) -> list[str]:
    with SEARCH_CACHE_LOCK:
        cached = SEARCH_CACHE.get(query)
    if cached is not None:
        return cached

    response = session().get(
        "https://www.bing.com/images/search",
        params={"q": query, "form": "HDRSC3", "first": "1"},
        timeout=REQUEST_TIMEOUT,
    )
    response.raise_for_status()
    urls = [html.unescape(url) for url in re.findall(r"murl&quot;:&quot;(.*?)&quot;", response.text)]
    unique_urls: list[str] = []
    for url in urls:
        if url.startswith("http") and url not in unique_urls:
            unique_urls.append(url)
    with SEARCH_CACHE_LOCK:
        SEARCH_CACHE[query] = unique_urls
    return unique_urls


def fetch_direct_image(url: str) -> bytes:
    response = session().get(url, timeout=REQUEST_TIMEOUT, allow_redirects=True)
    response.raise_for_status()
    return response.content


def fetch_randomuser_portrait(index: int) -> bytes:
    gender = "men" if index % 2 else "women"
    portrait_id = index % 100
    url = f"https://randomuser.me/api/portraits/{gender}/{portrait_id}.jpg"
    last_error: Exception | None = None
    for attempt in range(3):
        try:
            response = session().get(url, timeout=REQUEST_TIMEOUT)
            response.raise_for_status()
            return response.content
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            time.sleep(0.5 * (attempt + 1))
    raise RuntimeError(f"randomuser 请求失败: {url}") from last_error


def image_from_bytes(content: bytes, size: int) -> Image.Image:
    image = Image.open(io.BytesIO(content)).convert("RGB")
    return ImageOps.fit(image, (size, size), method=Image.Resampling.LANCZOS)


def save_jpeg(image: Image.Image, path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="JPEG", quality=JPEG_QUALITY, optimize=True)


def dhash(image: Image.Image) -> int:
    grayscale = image.convert("L").resize((9, 8), Image.Resampling.LANCZOS)
    pixels = list(grayscale.getdata())
    value = 0
    bit = 0
    for row in range(8):
        offset = row * 9
        for col in range(8):
            left = pixels[offset + col]
            right = pixels[offset + col + 1]
            if left > right:
                value |= 1 << bit
            bit += 1
    return value


def hamming_distance(a: int, b: int) -> int:
    return (a ^ b).bit_count()


def upload_bytes(local_path: Path, object_key: str) -> str:
    bucket, config = get_oss_bucket()
    bucket.put_object_from_file(object_key, str(local_path))
    return public_url(config, object_key)


def product_object_key(product: ProductRecord, variant: str) -> str:
    slug_parts = PRODUCT_NAME_RE.findall(product.name.lower())
    slug = "-".join(slug_parts[:6]).strip("-") or f"product-{product.product_id}"
    return f"{PRODUCT_PREFIX}/{slug}-{product.product_id:04d}-{variant}.jpg"


def asset_object_key(kind: str, asset_id: int) -> str:
    return f"{ASSET_PREFIX}/{kind}-{asset_id:04d}.jpg"


def download_unique_product_image(
    product: ProductRecord,
    variant: str,
    lock_seed: int,
    dedupe: DedupeStore,
    output_dir: Path,
) -> Path:
    tags = product_variant_tags(product, variant)
    candidate_urls: list[str] = []
    last_fallback_image: Image.Image | None = None
    for query in query_terms_for_product(product, variant):
        candidate_urls.extend(search_bing_image_urls(query)[:10])
    seen_urls: set[str] = set()
    for url in candidate_urls:
        if url in seen_urls:
            continue
        seen_urls.add(url)
        try:
            content = fetch_direct_image(url)
            image = image_from_bytes(content, IMAGE_SIZE)
            last_fallback_image = image
            if not dedupe.accept(image):
                continue
            path = output_dir / f"product-{product.product_id:04d}-{variant}.jpg"
            save_jpeg(image, path)
            return path
        except Exception:  # noqa: BLE001
            continue

    attempt_suffixes = [
        [],
        ["clean"],
        ["modern"],
        ["minimal"],
        ["lifestyle"],
        ["retail"],
        ["closeup"],
        ["detail"],
        ["premium"],
        ["table"],
        ["indoor"],
        ["showcase"],
    ]
    for offset, suffixes in enumerate(attempt_suffixes):
        candidate_tags = sanitize_tags([*tags, *suffixes])
        content = fetch_loremflickr(candidate_tags, lock_seed + offset * 7919, IMAGE_SIZE, IMAGE_SIZE)
        image = image_from_bytes(content, IMAGE_SIZE)
        last_fallback_image = image
        if not dedupe.accept(image):
            continue
        path = output_dir / f"product-{product.product_id:04d}-{variant}.jpg"
        save_jpeg(image, path)
        return path
    if last_fallback_image is not None:
        path = output_dir / f"product-{product.product_id:04d}-{variant}.jpg"
        save_jpeg(last_fallback_image, path)
        return path
    raise RuntimeError(f"商品 {product.product_id} 在变体 {variant} 上未找到足够不相似的网络图片")


def build_product_row(product: ProductRecord, dedupe: DedupeStore, output_dir: Path) -> ProductImageRow:
    local_path = download_unique_product_image(
        product,
        "main",
        lock_seed=product.product_id * 17 + 1,
        dedupe=dedupe,
        output_dir=output_dir,
    )
    main_url = upload_bytes(local_path, product_object_key(product, "main"))
    return ProductImageRow(product.product_id, main_url, [main_url])


def build_user_avatar_row(asset, dedupe: DedupeStore, output_dir: Path) -> tuple[int, str]:
    path = output_dir / f"user-{asset.user_id:04d}.jpg"
    candidate_queries = query_terms_for_avatar(asset.role, asset.nickname)
    last_image: Image.Image | None = None
    for attempt in range(4):
        if attempt == 0 and asset.role == "user":
            content = fetch_randomuser_portrait(asset.user_id + attempt)
        else:
            fetched = False
            content = b""
            for query in candidate_queries:
                for url in search_bing_image_urls(query)[:8]:
                    try:
                        content = fetch_direct_image(url)
                        fetched = True
                        break
                    except Exception:  # noqa: BLE001
                        continue
                if fetched:
                    break
            if not fetched:
                tags = merchant_or_user_tags(asset.role, asset.nickname)
                content = fetch_loremflickr(tags, asset.user_id * 13 + attempt * 37, 640, 640)
        image = image_from_bytes(content, 640)
        last_image = image
        if not dedupe.accept(image):
            continue
        save_jpeg(image, path)
        return asset.user_id, upload_bytes(path, asset_object_key("avatar", asset.user_id))
    if last_image is not None:
        save_jpeg(last_image, path)
        return asset.user_id, upload_bytes(path, asset_object_key("avatar", asset.user_id))
    raise RuntimeError(f"用户头像 {asset.user_id} 未找到可用网络图片")


def build_category_row(asset, dedupe: DedupeStore, output_dir: Path) -> tuple[int, str]:
    path = output_dir / f"category-{asset.category_id:04d}.jpg"
    last_image: Image.Image | None = None
    for query in query_terms_for_category(asset.name):
        for url in search_bing_image_urls(query)[:10]:
            try:
                content = fetch_direct_image(url)
                image = image_from_bytes(content, 640)
                last_image = image
                if not dedupe.accept(image):
                    continue
                save_jpeg(image, path)
                return asset.category_id, upload_bytes(path, asset_object_key("category", asset.category_id))
            except Exception:  # noqa: BLE001
                continue
    for attempt in range(4):
        content = fetch_loremflickr(category_tags(asset.name), asset.category_id * 19 + attempt * 53, 640, 640)
        image = image_from_bytes(content, 640)
        last_image = image
        if not dedupe.accept(image):
            continue
        save_jpeg(image, path)
        return asset.category_id, upload_bytes(path, asset_object_key("category", asset.category_id))
    if last_image is not None:
        save_jpeg(last_image, path)
        return asset.category_id, upload_bytes(path, asset_object_key("category", asset.category_id))
    raise RuntimeError(f"分类图 {asset.category_id} 未找到可用网络图片")


def build_banner_row(asset, dedupe: DedupeStore, output_dir: Path) -> tuple[int, str]:
    path = output_dir / f"banner-{asset.banner_id:04d}.jpg"
    last_image: Image.Image | None = None
    for query in query_terms_for_banner(asset.title):
        for url in search_bing_image_urls(query)[:10]:
            try:
                content = fetch_direct_image(url)
                image = Image.open(io.BytesIO(content)).convert("RGB")
                fitted = ImageOps.fit(image, (1200, 420), method=Image.Resampling.LANCZOS)
                last_image = fitted
                if not dedupe.accept(fitted.copy().resize((640, 224), Image.Resampling.LANCZOS)):
                    continue
                path.parent.mkdir(parents=True, exist_ok=True)
                fitted.save(path, format="JPEG", quality=JPEG_QUALITY, optimize=True)
                return asset.banner_id, upload_bytes(path, asset_object_key("banner", asset.banner_id))
            except Exception:  # noqa: BLE001
                continue
    for attempt in range(4):
        content = fetch_loremflickr(banner_tags(asset.title), asset.banner_id * 23 + attempt * 71, 1200, 420)
        image = Image.open(io.BytesIO(content)).convert("RGB")
        fitted = ImageOps.fit(image, (1200, 420), method=Image.Resampling.LANCZOS)
        last_image = fitted
        if not dedupe.accept(fitted.copy().resize((640, 224), Image.Resampling.LANCZOS)):
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        fitted.save(path, format="JPEG", quality=JPEG_QUALITY, optimize=True)
        return asset.banner_id, upload_bytes(path, asset_object_key("banner", asset.banner_id))
    if last_image is not None:
        path.parent.mkdir(parents=True, exist_ok=True)
        last_image.save(path, format="JPEG", quality=JPEG_QUALITY, optimize=True)
        return asset.banner_id, upload_bytes(path, asset_object_key("banner", asset.banner_id))
    raise RuntimeError(f"Banner {asset.banner_id} 未找到可用网络图片")


def write_product_sql(rows: list[ProductImageRow], output_sql: Path):
    output_sql.parent.mkdir(parents=True, exist_ok=True)
    with output_sql.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("-- 商品网络图片回填 SQL\n")
        handle.write(f"-- 生成时间: {NOW}\n\n")
        for row in sorted(rows, key=lambda item: item.product_id):
            images_json = json.dumps(row.images, ensure_ascii=False).replace("'", "''")
            handle.write(
                "UPDATE `product` SET `image` = '{image}', `images` = '{images}', `update_time` = '{now}' WHERE `id` = {product_id};\n".format(
                    image=row.image.replace("'", "''"),
                    images=images_json,
                    now=NOW,
                    product_id=row.product_id,
                )
            )


def write_base_sql(rows: BaseImageRows, output_sql: Path):
    output_sql.parent.mkdir(parents=True, exist_ok=True)
    with output_sql.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("-- 基础资源网络图片回填 SQL\n")
        handle.write(f"-- 生成时间: {NOW}\n\n")
        for user_id, url in sorted(rows.users):
            handle.write(f"UPDATE `user` SET `avatar` = '{url}', `update_time` = '{NOW}' WHERE `id` = {user_id};\n")
        handle.write("\n")
        for category_id, url in sorted(rows.categories):
            handle.write(f"UPDATE `category` SET `icon` = '{url}' WHERE `id` = {category_id};\n")
        handle.write("\n")
        for banner_id, url in sorted(rows.banners):
            handle.write(f"UPDATE `banner` SET `image` = '{url}' WHERE `id` = {banner_id};\n")


def process_product_chunk(chunk_path: Path, dedupe: DedupeStore, workers: int, limit: int | None = None):
    range_match = PRODUCT_INSERT_RANGE_RE.search(chunk_path.name)
    if not range_match:
        raise RuntimeError(f"无法从文件名推断商品区间: {chunk_path.name}")
    start_id = int(range_match.group(1))
    end_id = int(range_match.group(2))
    products = parse_products_from_sql(chunk_path, start_id, end_id - start_id + 1)
    if limit is not None:
        products = products[:limit]
    output_dir = OUTPUT_DIR / chunk_path.stem
    rows: list[ProductImageRow] = []
    with ThreadPoolExecutor(max_workers=workers) as executor:
        future_map = {executor.submit(build_product_row, product, dedupe, output_dir): product.product_id for product in products}
        for index, future in enumerate(as_completed(future_map), start=1):
            row = future.result()
            rows.append(row)
            if index % 25 == 0 or index == len(future_map):
                print(f"[products] {chunk_path.name} {index}/{len(future_map)}")
    sql_path = CHUNK_DIR / f"seed-part-products-images-{start_id:04d}-{end_id:04d}.sql"
    write_product_sql(rows, sql_path)
    return sql_path


def process_base_assets(dedupe: DedupeStore, workers: int):
    users, categories, banners = parse_base_assets()
    base_dir = OUTPUT_DIR / "base-assets"
    user_rows: list[tuple[int, str]] = []
    category_rows: list[tuple[int, str]] = []
    banner_rows: list[tuple[int, str]] = []

    with ThreadPoolExecutor(max_workers=workers) as executor:
        user_futures = {executor.submit(build_user_avatar_row, asset, dedupe, base_dir / "users"): asset.user_id for asset in users}
        for index, future in enumerate(as_completed(user_futures), start=1):
            user_rows.append(future.result())
            if index % 100 == 0 or index == len(user_futures):
                print(f"[base-users] {index}/{len(user_futures)}")

    with ThreadPoolExecutor(max_workers=max(4, min(workers, 12))) as executor:
        category_futures = {executor.submit(build_category_row, asset, dedupe, base_dir / "categories"): asset.category_id for asset in categories}
        for index, future in enumerate(as_completed(category_futures), start=1):
            category_rows.append(future.result())
            if index % 20 == 0 or index == len(category_futures):
                print(f"[base-categories] {index}/{len(category_futures)}")

    with ThreadPoolExecutor(max_workers=max(4, min(workers, 10))) as executor:
        banner_futures = {executor.submit(build_banner_row, asset, dedupe, base_dir / "banners"): asset.banner_id for asset in banners}
        for index, future in enumerate(as_completed(banner_futures), start=1):
            banner_rows.append(future.result())
            if index % 10 == 0 or index == len(banner_futures):
                print(f"[base-banners] {index}/{len(banner_futures)}")

    sql_path = CHUNK_DIR / "seed-part-01-base-images-web.sql"
    write_base_sql(BaseImageRows(user_rows, category_rows, banner_rows), sql_path)
    return sql_path


def build_all_product_chunks() -> list[Path]:
    return sorted(
        path for path in CHUNK_DIR.glob("seed-part-products-*.sql")
        if re.fullmatch(r"seed-part-products-\d{4}-\d{4}\.sql", path.name)
    )


def parse_args():
    parser = argparse.ArgumentParser(description="从网络替换初始化 SQL 中的图片，并上传 OSS")
    parser.add_argument("--workers", type=int, default=18, help="并发任务数")
    parser.add_argument("--skip-delete", action="store_true", help="不删除旧 OSS 前缀")
    parser.add_argument("--skip-base", action="store_true", help="跳过基础资源（头像/分类/Banner）")
    parser.add_argument("--skip-products", action="store_true", help="跳过商品图片")
    parser.add_argument("--chunk", type=str, help="只处理单个商品分片文件名，如 seed-part-products-0001-1000.sql")
    parser.add_argument("--limit", type=int, help="仅处理当前商品分片中的前 N 条，用于小批量验证")
    return parser.parse_args()


def main():
    args = parse_args()
    if not args.skip_delete:
        delete_prefixes(DELETE_PREFIXES)
    dedupe = DedupeStore(SIMILARITY_THRESHOLD)
    generated_sql: list[str] = []

    if not args.skip_base:
        generated_sql.append(str(process_base_assets(dedupe, max(8, min(args.workers, 24)))))

    if not args.skip_products:
        product_chunks = [CHUNK_DIR / args.chunk] if args.chunk else build_all_product_chunks()
        for chunk_path in product_chunks:
            generated_sql.append(str(process_product_chunk(chunk_path, dedupe, max(6, args.workers), args.limit)))

    print(json.dumps({"generatedSql": generated_sql}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    random.seed(20260419)
    main()
