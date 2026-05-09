#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Download real product photos from web image results into a local folder.

This script intentionally avoids text-to-image generation. It reads products from
output/all_products_for_imagegen.json, searches web image result pages with
product-specific queries, validates downloaded images, center-crops them to a
square product-card friendly JPEG, and writes a JSON manifest.
"""

from __future__ import annotations

import argparse
import html
import io
import json
import re
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import requests
from PIL import Image, ImageOps


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_PRODUCTS_JSON = ROOT / "output" / "all_products_for_imagegen.json"
DEFAULT_OUTPUT_DIR = ROOT / "output" / "web-matched-product-images"
IMAGE_SIZE = 960
JPEG_QUALITY = 88
REQUEST_TIMEOUT = 25
THREAD_LOCAL = threading.local()
SEARCH_CACHE: dict[str, list[str]] = {}
SEARCH_CACHE_LOCK = threading.Lock()
DEDUPE_LOCK = threading.Lock()


@dataclass(frozen=True)
class Product:
    product_id: int
    name: str
    description: str
    category_name: str
    tags: list[str]


class DedupeStore:
    def __init__(self, threshold: int):
        self.threshold = threshold
        self.hashes: list[int] = []

    def accept(self, image: Image.Image) -> bool:
        value = dhash(image)
        with DEDUPE_LOCK:
            for existing in self.hashes:
                if hamming_distance(value, existing) <= self.threshold:
                    return False
            self.hashes.append(value)
            return True


def session() -> requests.Session:
    existing = getattr(THREAD_LOCAL, "session", None)
    if existing is not None:
        return existing

    s = requests.Session()
    s.headers.update(
        {
            "User-Agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                "(KHTML, like Gecko) Chrome/135.0 Safari/537.36"
            ),
            "Accept": "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
            "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
        }
    )
    THREAD_LOCAL.session = s
    return s


def load_products(path: Path, limit: int, offset: int) -> list[Product]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    products: list[Product] = []
    for row in raw[offset : offset + limit]:
        tags = row.get("tags") or []
        products.append(
            Product(
                product_id=int(row["id"]),
                name=str(row.get("name") or ""),
                description=str(row.get("description") or ""),
                category_name=str(row.get("categoryName") or ""),
                tags=[str(tag) for tag in tags],
            )
        )
    return products


def clean_query_text(text: str) -> str:
    text = re.sub(r"场景:[^\s,，]+|价格:[^\s,，]+|热度:[^\s,，]+|类目:[^\s,，]+", "", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text


def query_terms(product: Product) -> list[str]:
    tag_text = " ".join(tag for tag in product.tags[:4] if ":" not in tag)
    base = clean_query_text(f"{product.name} {tag_text}")
    category = product.category_name or "商品"
    return [
        f"{base} 实物 商品图",
        f"{product.name} 实拍 商品",
        f"{base} 电商主图",
        f"{category} {product.name} 实物图",
        f"{product.name} product photo real item",
    ]


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


def fetch_image(url: str) -> bytes:
    response = session().get(url, timeout=REQUEST_TIMEOUT, allow_redirects=True)
    response.raise_for_status()
    content_type = response.headers.get("Content-Type", "")
    if "image" not in content_type.lower() and len(response.content) < 10_000:
        raise ValueError(f"not an image response: {content_type}")
    return response.content


def normalize_image(content: bytes) -> Image.Image:
    image = Image.open(io.BytesIO(content)).convert("RGB")
    width, height = image.size
    if width < 360 or height < 360:
        raise ValueError(f"image too small: {width}x{height}")
    return ImageOps.fit(image, (IMAGE_SIZE, IMAGE_SIZE), method=Image.Resampling.LANCZOS)


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
            if pixels[offset + col] > pixels[offset + col + 1]:
                value |= 1 << bit
            bit += 1
    return value


def hamming_distance(a: int, b: int) -> int:
    return (a ^ b).bit_count()


def download_one(product: Product, output_dir: Path, dedupe: DedupeStore) -> dict[str, Any]:
    destination = output_dir / f"product-{product.product_id:04d}.jpg"
    errors: list[str] = []
    seen: set[str] = set()

    for query in query_terms(product):
        try:
            candidates = search_bing_image_urls(query)[:12]
        except Exception as exc:  # noqa: BLE001
            errors.append(f"{query}: search failed: {exc}")
            continue

        for url in candidates:
            if url in seen:
                continue
            seen.add(url)
            try:
                image = normalize_image(fetch_image(url))
                if not dedupe.accept(image):
                    errors.append(f"{url}: too similar to an accepted image")
                    continue
                save_jpeg(image, destination)
                return {
                    "id": product.product_id,
                    "name": product.name,
                    "query": query,
                    "sourceUrl": url,
                    "localPath": str(destination),
                    "status": "ok",
                }
            except Exception as exc:  # noqa: BLE001
                errors.append(f"{url}: {exc}")

        time.sleep(0.2)

    return {
        "id": product.product_id,
        "name": product.name,
        "localPath": str(destination),
        "status": "failed",
        "errors": errors[-8:],
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="多线程下载真实商品图片到本地")
    parser.add_argument("--products-json", type=Path, default=DEFAULT_PRODUCTS_JSON)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--limit", type=int, default=100000)
    parser.add_argument("--offset", type=int, default=0)
    parser.add_argument("--workers", type=int, default=12)
    parser.add_argument("--similarity-threshold", type=int, default=5, help="dHash 汉明距离小于等于该值时认为太相似")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    products = load_products(args.products_json, args.limit, args.offset)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    dedupe = DedupeStore(args.similarity_threshold)

    rows: list[dict[str, Any]] = []
    with ThreadPoolExecutor(max_workers=max(1, args.workers)) as executor:
        future_map = {executor.submit(download_one, product, args.output_dir, dedupe): product for product in products}
        for index, future in enumerate(as_completed(future_map), start=1):
            row = future.result()
            rows.append(row)
            print(f"[{index}/{len(products)}] {row['status']} product={row['id']} {row['name']}")

    manifest = args.output_dir / "manifest.json"
    manifest.write_text(
        json.dumps(sorted(rows, key=lambda item: item["id"]), ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    ok_count = sum(1 for row in rows if row["status"] == "ok")
    print(json.dumps({"ok": ok_count, "failed": len(rows) - ok_count, "manifest": str(manifest)}, ensure_ascii=False))
    return 0 if ok_count == len(rows) else 1


if __name__ == "__main__":
    raise SystemExit(main())
