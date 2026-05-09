#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成基础资源图片（头像、分类 icon、banner），上传到阿里云 OSS，并产出 SQL 更新文件。
"""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

from PIL import Image
from PIL import ImageDraw
from PIL import ImageFilter

from backend.python_analytics.spark_jobs.oss_archive import build_oss_config_from_env
from scripts.generate_product_images_to_oss import CATEGORY_PALETTES, DEFAULT_BUCKET_NAME, DEFAULT_OSS_ENDPOINT, DEFAULT_PUBLIC_URL, FONT_CANDIDATES


ROOT = Path(__file__).resolve().parents[1]
BASE_SQL = ROOT / "backend" / "src" / "main" / "resources" / "sql" / "chunks" / "seed-part-01-base.sql"
OUTPUT_DIR = ROOT / "output" / "generated-base-images"
SQL_DIR = ROOT / "backend" / "src" / "main" / "resources" / "sql" / "chunks"
NOW = "2026-04-18 21:20:00"

USER_ROW_RE = re.compile(
    r"^\((\d+), '([^']*(?:''[^']*)*)', '([^']*(?:''[^']*)*)', '([^']*(?:''[^']*)*)', '([^']*(?:''[^']*)*)', "
    r"'([^']*(?:''[^']*)*)', '([^']*(?:''[^']*)*)', '([^']*(?:''[^']*)*)',",
    re.MULTILINE,
)
CATEGORY_ROW_RE = re.compile(
    r"^\((\d+), '([^']*(?:''[^']*)*)', (\d+), '([^']*(?:''[^']*)*)',",
    re.MULTILINE,
)
BANNER_ROW_RE = re.compile(
    r"^\((\d+), '([^']*(?:''[^']*)*)', '([^']*(?:''[^']*)*)',",
    re.MULTILINE,
)


@dataclass
class UserAsset:
    user_id: int
    avatar: str
    nickname: str
    role: str


@dataclass
class CategoryAsset:
    category_id: int
    name: str
    parent_id: int
    icon: str


@dataclass
class BannerAsset:
    banner_id: int
    title: str
    image: str


def load_font(size: int):
    from PIL import ImageFont

    for candidate in FONT_CANDIDATES:
        if candidate.exists():
            try:
                return ImageFont.truetype(str(candidate), size=size)
            except OSError:
                continue
    return ImageFont.load_default()


def interpolate_color(start: tuple[int, int, int], end: tuple[int, int, int], factor: float) -> tuple[int, int, int]:
    return tuple(int(start[index] + (end[index] - start[index]) * factor) for index in range(3))


def short_text(text: str, limit: int) -> str:
    return text[:limit].strip() or text


def wrap_text(draw: ImageDraw.ImageDraw, text: str, font, max_width: int) -> list[str]:
    lines: list[str] = []
    current = ""
    for char in text:
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


def palette_for_text(text: str) -> tuple[tuple[int, int, int], tuple[int, int, int], tuple[int, int, int], tuple[int, int, int]]:
    for name, palette in CATEGORY_PALETTES.items():
        if name in text:
            return palette
    return CATEGORY_PALETTES["通用商品"]


def parse_base_assets() -> tuple[list[UserAsset], list[CategoryAsset], list[BannerAsset]]:
    text = BASE_SQL.read_text(encoding="utf-8")
    users: list[UserAsset] = []
    categories: list[CategoryAsset] = []
    banners: list[BannerAsset] = []

    for groups in USER_ROW_RE.findall(text):
        users.append(
            UserAsset(
                user_id=int(groups[0]),
                avatar=groups[5].replace("''", "'"),
                nickname=groups[6].replace("''", "'"),
                role=groups[7].replace("''", "'"),
            )
        )

    for groups in CATEGORY_ROW_RE.findall(text):
        categories.append(
            CategoryAsset(
                category_id=int(groups[0]),
                name=groups[1].replace("''", "'"),
                parent_id=int(groups[2]),
                icon=groups[3].replace("''", "'"),
            )
        )

    for groups in BANNER_ROW_RE.findall(text):
        banners.append(
            BannerAsset(
                banner_id=int(groups[0]),
                title=groups[1].replace("''", "'"),
                image=groups[2].replace("''", "'"),
            )
        )

    return users, categories, banners


def render_avatar(asset: UserAsset, output_path: Path):
    width = 320
    height = 320
    role_palette = {
        "admin": ((17, 24, 39), (75, 85, 99), (243, 244, 246), (255, 255, 255)),
        "merchant": ((29, 78, 216), (56, 189, 248), (239, 246, 255), (255, 255, 255)),
        "user": ((37, 99, 235), (96, 165, 250), (239, 246, 255), (255, 255, 255)),
    }
    primary, secondary, soft, white = role_palette.get(asset.role, role_palette["user"])
    image = Image.new("RGB", (width, height), primary)
    draw = ImageDraw.Draw(image)
    for y in range(height):
        draw.line((0, y, width, y), fill=interpolate_color(primary, secondary, y / max(1, height - 1)))
    overlay = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    overlay_draw = ImageDraw.Draw(overlay)
    overlay_draw.ellipse((-60, -60, 160, 160), fill=(*soft, 90))
    overlay_draw.ellipse((170, 90, 340, 260), fill=(255, 255, 255, 56))
    overlay_draw.rounded_rectangle((46, 190, 274, 284), radius=28, fill=(255, 255, 255, 214))
    overlay = overlay.filter(ImageFilter.GaussianBlur(radius=2))
    image = Image.alpha_composite(image.convert("RGBA"), overlay)
    draw = ImageDraw.Draw(image)
    draw.ellipse((78, 44, 242, 208), fill=white)
    draw.ellipse((114, 72, 206, 164), fill=soft)
    draw.rounded_rectangle((90, 150, 230, 222), radius=28, fill=white)
    draw.rounded_rectangle((118, 166, 202, 214), radius=20, fill=soft)
    title_font = load_font(34)
    role_font = load_font(26)
    nickname = short_text(asset.nickname, 4)
    role_text = {"admin": "ADMIN", "merchant": "SHOP", "user": "USER"}.get(asset.role, asset.role.upper())
    bbox = draw.textbbox((0, 0), nickname, font=title_font)
    draw.text(((width - (bbox[2] - bbox[0])) / 2, 214), nickname, fill=primary, font=title_font)
    role_bbox = draw.textbbox((0, 0), role_text, font=role_font)
    draw.text(((width - (role_bbox[2] - role_bbox[0])) / 2, 252), role_text, fill=(71, 85, 105), font=role_font)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    image.convert("RGB").save(output_path, format="PNG", optimize=True)


def render_category_icon(asset: CategoryAsset, output_path: Path):
    width = 320
    height = 320
    primary, secondary, soft, dark = palette_for_text(asset.name)
    image = Image.new("RGB", (width, height), primary)
    draw = ImageDraw.Draw(image)
    for y in range(height):
        draw.line((0, y, width, y), fill=interpolate_color(primary, secondary, y / max(1, height - 1)))
    overlay = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    overlay_draw = ImageDraw.Draw(overlay)
    overlay_draw.ellipse((-50, -40, 170, 170), fill=(*soft, 90))
    overlay_draw.rounded_rectangle((42, 188, 278, 282), radius=28, fill=(255, 255, 255, 220))
    overlay = overlay.filter(ImageFilter.GaussianBlur(radius=1))
    image = Image.alpha_composite(image.convert("RGBA"), overlay)
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((78, 54, 242, 190), radius=44, fill=(255, 255, 255))
    draw.rounded_rectangle((104, 80, 216, 164), radius=28, fill=soft)
    title_font = load_font(34)
    sub_font = load_font(24)
    lines = wrap_text(draw, short_text(asset.name, 6), title_font, 220)
    y = 206
    for line in lines[:2]:
        bbox = draw.textbbox((0, 0), line, font=title_font)
        draw.text(((width - (bbox[2] - bbox[0])) / 2, y), line, fill=dark, font=title_font)
        y += 36
    sub_text = "主分类" if asset.parent_id == 0 else "子分类"
    sub_bbox = draw.textbbox((0, 0), sub_text, font=sub_font)
    draw.text(((width - (sub_bbox[2] - sub_bbox[0])) / 2, 266), sub_text, fill=(71, 85, 105), font=sub_font)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    image.convert("RGB").save(output_path, format="PNG", optimize=True)


def banner_category_title(title: str) -> str:
    return title.split("热销会场", 1)[0].strip() or title


def render_banner(asset: BannerAsset, output_path: Path):
    width = 1200
    height = 420
    category_name = banner_category_title(asset.title)
    primary, secondary, soft, dark = palette_for_text(category_name)
    image = Image.new("RGB", (width, height), primary)
    draw = ImageDraw.Draw(image)
    for x in range(width):
        draw.line((x, 0, x, height), fill=interpolate_color(primary, secondary, x / max(1, width - 1)))
    overlay = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    overlay_draw = ImageDraw.Draw(overlay)
    overlay_draw.ellipse((-120, -80, 380, 340), fill=(*soft, 90))
    overlay_draw.ellipse((810, 40, 1280, 380), fill=(255, 255, 255, 60))
    overlay_draw.rounded_rectangle((64, 68, 652, 354), radius=36, fill=(255, 255, 255, 230))
    overlay_draw.rounded_rectangle((782, 78, 1098, 342), radius=50, fill=(255, 255, 255, 88))
    overlay = overlay.filter(ImageFilter.GaussianBlur(radius=2))
    image = Image.alpha_composite(image.convert("RGBA"), overlay)
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((848, 116, 1040, 308), radius=46, fill=(255, 255, 255))
    draw.rounded_rectangle((886, 152, 1002, 274), radius=32, fill=soft)
    title_font = load_font(64)
    category_font = load_font(34)
    meta_font = load_font(26)
    title_lines = wrap_text(draw, asset.title, title_font, 520)
    y = 126
    for line in title_lines[:2]:
        bbox = draw.textbbox((0, 0), line, font=title_font)
        draw.text((100, y), line, fill=dark, font=title_font)
        y += 78
    draw.text((104, 92), category_name, fill=primary, font=category_font)
    draw.text((104, 302), "品质好物精选会场  |  电商首页 Banner", fill=(71, 85, 105), font=meta_font)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    image.convert("RGB").save(output_path, format="PNG", optimize=True)


@lru_cache(maxsize=1)
def get_oss_bucket():
    config = build_oss_config_from_env("assets/generated")
    if config is None:
        import os
        from types import SimpleNamespace

        access_key_id = os.getenv("ALIYUN_OSS_ACCESS_KEY_ID", "").strip()
        access_key_secret = os.getenv("ALIYUN_OSS_ACCESS_KEY_SECRET", "").strip()
        if not access_key_id or not access_key_secret:
            raise RuntimeError("未读取到 OSS 凭证，无法上传基础图片")
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
    endpoint_host = config.endpoint.split('://', 1)[-1]
    return f"https://{config.bucket_name}.{endpoint_host}/{object_key}"


def build_object_key(kind: str, asset_id: int) -> str:
    return f"assets/generated/2026/04/18/{kind}-{asset_id:04d}.png"


def write_update_sql(user_rows: list[tuple[int, str]], category_rows: list[tuple[int, str]], banner_rows: list[tuple[int, str]], output_name: str):
    output_sql = SQL_DIR / output_name
    with output_sql.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("-- 基础图片 OSS 回填 SQL\n")
        handle.write(f"-- 生成时间: {NOW}\n\n")
        for user_id, url in user_rows:
            handle.write(f"UPDATE `user` SET `avatar` = '{url}', `update_time` = '{NOW}' WHERE `id` = {user_id};\n")
        handle.write("\n")
        for category_id, url in category_rows:
            handle.write(f"UPDATE `category` SET `icon` = '{url}' WHERE `id` = {category_id};\n")
        handle.write("\n")
        for banner_id, url in banner_rows:
            handle.write(f"UPDATE `banner` SET `image` = '{url}' WHERE `id` = {banner_id};\n")


def filter_assets_by_range(items: list, start_id: int | None, end_id: int | None, attr_name: str):
    if start_id is None and end_id is None:
        return items
    filtered = []
    for item in items:
        value = getattr(item, attr_name)
        if start_id is not None and value < start_id:
            continue
        if end_id is not None and value > end_id:
            continue
        filtered.append(item)
    return filtered


def chunk_name(asset_kind: str, start_id: int | None, end_id: int | None) -> str:
    suffix = asset_kind
    if start_id is not None or end_id is not None:
        suffix = f"{suffix}-{(start_id or 0):04d}-{(end_id or 9999):04d}"
    return f"seed-part-01-base-images-{suffix}.sql"


def run(skip_oss: bool, asset_kind: str, start_id: int | None, end_id: int | None):
    users, categories, banners = parse_base_assets()
    if asset_kind in {"all", "users"}:
        users = filter_assets_by_range(users, start_id, end_id, "user_id")
    else:
        users = []
    if asset_kind in {"all", "categories"}:
        categories = filter_assets_by_range(categories, start_id, end_id, "category_id")
    else:
        categories = []
    if asset_kind in {"all", "banners"}:
        banners = filter_assets_by_range(banners, start_id, end_id, "banner_id")
    else:
        banners = []
    base_dir = OUTPUT_DIR / "2026-04-18"
    user_updates: list[tuple[int, str]] = []
    category_updates: list[tuple[int, str]] = []
    banner_updates: list[tuple[int, str]] = []

    for index, asset in enumerate(users, start=1):
        local_path = base_dir / "avatars" / f"user-{asset.user_id:04d}.png"
        render_avatar(asset, local_path)
        if not skip_oss:
            url = upload_file_to_oss(local_path, build_object_key("avatar", asset.user_id))
            user_updates.append((asset.user_id, url))
        if index % 100 == 0:
            print(f"[progress] users {index}/{len(users)}")

    for index, asset in enumerate(categories, start=1):
        local_path = base_dir / "categories" / f"category-{asset.category_id:04d}.png"
        render_category_icon(asset, local_path)
        if not skip_oss:
            url = upload_file_to_oss(local_path, build_object_key("category", asset.category_id))
            category_updates.append((asset.category_id, url))
        if index % 20 == 0:
            print(f"[progress] categories {index}/{len(categories)}")

    for index, asset in enumerate(banners, start=1):
        local_path = base_dir / "banners" / f"banner-{asset.banner_id:04d}.png"
        render_banner(asset, local_path)
        if not skip_oss:
            url = upload_file_to_oss(local_path, build_object_key("banner", asset.banner_id))
            banner_updates.append((asset.banner_id, url))
        if index % 10 == 0:
            print(f"[progress] banners {index}/{len(banners)}")

    if skip_oss:
        print(f"[ok] 本地基础图片已生成: {base_dir}")
        return

    output_name = chunk_name(asset_kind, start_id, end_id)
    write_update_sql(user_updates, category_updates, banner_updates, output_name)
    summary = {
        "users": len(user_updates),
        "categories": len(category_updates),
        "banners": len(banner_updates),
        "outputSql": str(SQL_DIR / output_name),
    }
    print(json.dumps(summary, ensure_ascii=False))


def parse_args():
    parser = argparse.ArgumentParser(description="生成基础资源图片并上传 OSS")
    parser.add_argument("--asset-kind", choices=["all", "users", "categories", "banners"], default="all")
    parser.add_argument("--start-id", type=int)
    parser.add_argument("--end-id", type=int)
    parser.add_argument("--skip-oss", action="store_true")
    return parser.parse_args()


def main():
    args = parse_args()
    run(args.skip_oss, args.asset_kind, args.start_id, args.end_id)


if __name__ == "__main__":
    main()
