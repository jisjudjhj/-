#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Upload locally generated gpt-image-2 product images to OSS and update products.

Expected image names:
  output/image2-product-images/product-0001.jpeg
  output/image2-product-images/product-0002.jpeg

The script writes:
  output/image2-product-images/oss-manifest.json
  output/update_image2_product_images.sql

It can also apply the SQL directly with --apply-db when MySQL is reachable.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import time
from datetime import datetime
from pathlib import Path
from typing import Any

import oss2  # type: ignore
import pymysql  # type: ignore
import yaml


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_IMAGES_DIR = ROOT / "output" / "image2-product-images"
DEFAULT_PRODUCTS_JSON = ROOT / "output" / "all_products_for_imagegen.json"
DEFAULT_SQL_PATH = ROOT / "output" / "update_image2_product_images.sql"
DEFAULT_MANIFEST_PATH = DEFAULT_IMAGES_DIR / "oss-manifest.json"
APP_YML = ROOT / "backend" / "src" / "main" / "resources" / "application.yml"
SERVER_ENV = ROOT / "output" / "server.env"


def resolve_placeholder(value: Any) -> str:
    if value is None:
        return ""
    text = str(value)
    match = re.fullmatch(r"\$\{([^:}]+):([^}]*)}", text)
    if not match:
        return text
    env_name, fallback = match.groups()
    return os.getenv(env_name, fallback)


def read_server_env() -> dict[str, str]:
    values: dict[str, str] = {}
    if not SERVER_ENV.exists():
        return values
    for line in SERVER_ENV.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def read_oss_config() -> dict[str, str]:
    app_config: dict[str, Any] = {}
    if APP_YML.exists():
        raw = yaml.safe_load(APP_YML.read_text(encoding="utf-8")) or {}
        app_config = (((raw.get("aliyun") or {}).get("oss")) or {})

    endpoint = os.getenv("ALIYUN_OSS_ENDPOINT") or resolve_placeholder(app_config.get("endpoint"))
    access_key_id = os.getenv("ALIYUN_OSS_ACCESS_KEY_ID") or resolve_placeholder(app_config.get("access-key-id"))
    access_key_secret = os.getenv("ALIYUN_OSS_ACCESS_KEY_SECRET") or resolve_placeholder(app_config.get("access-key-secret"))
    bucket_name = os.getenv("ALIYUN_OSS_BUCKET_NAME") or resolve_placeholder(app_config.get("bucket-name"))
    url_prefix = os.getenv("ALIYUN_OSS_URL_PREFIX") or resolve_placeholder(app_config.get("url-prefix"))

    missing = [
        name
        for name, value in {
            "ALIYUN_OSS_ENDPOINT": endpoint,
            "ALIYUN_OSS_ACCESS_KEY_ID": access_key_id,
            "ALIYUN_OSS_ACCESS_KEY_SECRET": access_key_secret,
            "ALIYUN_OSS_BUCKET_NAME": bucket_name,
        }.items()
        if not value
    ]
    if missing:
        raise RuntimeError("OSS 配置不完整: " + ", ".join(missing))
    if not endpoint.startswith(("http://", "https://")):
        endpoint = "https://" + endpoint
    return {
        "endpoint": endpoint,
        "access_key_id": access_key_id,
        "access_key_secret": access_key_secret,
        "bucket_name": bucket_name,
        "url_prefix": url_prefix,
    }


def read_db_config(args: argparse.Namespace) -> dict[str, Any]:
    server_env = read_server_env()
    return {
        "host": args.db_host or os.getenv("DB_HOST") or server_env.get("SERVER_IP") or "127.0.0.1",
        "port": int(args.db_port or os.getenv("DB_PORT") or 3306),
        "user": args.db_user or os.getenv("DB_USERNAME") or server_env.get("DB_USERNAME") or "",
        "password": args.db_password or os.getenv("DB_PASSWORD") or server_env.get("DB_PASSWORD") or "",
        "database": args.db_name or os.getenv("DB_NAME") or server_env.get("DB_NAME") or "",
        "charset": "utf8mb4",
    }


def content_type_for(path: Path) -> str:
    suffix = path.suffix.lower()
    if suffix in {".jpg", ".jpeg"}:
        return "image/jpeg"
    if suffix == ".png":
        return "image/png"
    if suffix == ".webp":
        return "image/webp"
    return "application/octet-stream"


def public_url(config: dict[str, str], object_key: str) -> str:
    url_prefix = config.get("url_prefix", "").rstrip("/")
    if url_prefix:
        return f"{url_prefix}/{object_key}"
    endpoint_host = config["endpoint"].split("://", 1)[-1]
    return f"https://{config['bucket_name']}.{endpoint_host}/{object_key}"


def load_products(path: Path, limit: int, offset: int) -> list[dict[str, Any]]:
    products = json.loads(path.read_text(encoding="utf-8"))
    return products[offset : offset + limit]


def upload_images(args: argparse.Namespace) -> list[dict[str, Any]]:
    products = load_products(args.products_json, args.limit, args.offset)
    config = read_oss_config()
    auth = oss2.Auth(config["access_key_id"], config["access_key_secret"])
    bucket = oss2.Bucket(auth, config["endpoint"], config["bucket_name"])
    prefix = args.object_prefix.strip("/")

    rows: list[dict[str, Any]] = []
    for product in products:
        product_id = int(product["id"])
        local_path = None
        for suffix in (".jpeg", ".jpg", ".png", ".webp"):
            candidate = args.images_dir / f"product-{product_id:04d}{suffix}"
            if candidate.exists():
                local_path = candidate
                break
        if local_path is None:
            raise FileNotFoundError(f"未找到商品 {product_id} 的本地 image2 图片")

        object_key = f"{prefix}/product-{product_id:04d}{local_path.suffix.lower()}"
        headers = {
            "Content-Type": content_type_for(local_path),
            "Content-Disposition": "inline",
            "Cache-Control": "public, max-age=31536000",
        }
        last_error: Exception | None = None
        for attempt in range(1, 4):
            try:
                bucket.put_object_from_file(object_key, str(local_path), headers=headers)
                last_error = None
                break
            except Exception as exc:  # noqa: BLE001
                last_error = exc
                if attempt < 3:
                    time.sleep(1.5 * attempt)
        if last_error is not None:
            raise last_error
        url = public_url(config, object_key)
        rows.append(
            {
                "id": product_id,
                "name": product.get("name", ""),
                "localPath": str(local_path),
                "objectKey": object_key,
                "url": url,
            }
        )
        print(f"[oss] product={product_id} -> {url}")
    return rows


def write_sql(rows: list[dict[str, Any]], sql_path: Path):
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    sql_path.parent.mkdir(parents=True, exist_ok=True)
    with sql_path.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("-- image2 商品图片 OSS 回填 SQL\n")
        handle.write(f"-- 生成时间: {now}\n\n")
        for row in rows:
            url = row["url"].replace("'", "''")
            images = json.dumps([row["url"]], ensure_ascii=False).replace("'", "''")
            handle.write(
                "UPDATE `product` SET `image` = '{url}', `images` = '{images}', "
                "`update_time` = '{now}' WHERE `id` = {product_id};\n".format(
                    url=url,
                    images=images,
                    now=now,
                    product_id=int(row["id"]),
                )
            )


def apply_db(rows: list[dict[str, Any]], db_config: dict[str, Any]):
    if not db_config["database"]:
        raise RuntimeError("DB_NAME 未配置")
    connection = pymysql.connect(**db_config)
    try:
        with connection.cursor() as cursor:
            for row in rows:
                url = row["url"]
                cursor.execute(
                    "UPDATE `product` SET `image`=%s, `images`=%s, `update_time`=NOW() WHERE `id`=%s",
                    (url, json.dumps([url], ensure_ascii=False), int(row["id"])),
                )
        connection.commit()
        print(f"[db] updated {len(rows)} products")
    finally:
        connection.close()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="上传 image2 商品图到 OSS，并可回写 MySQL")
    parser.add_argument("--images-dir", type=Path, default=DEFAULT_IMAGES_DIR)
    parser.add_argument("--products-json", type=Path, default=DEFAULT_PRODUCTS_JSON)
    parser.add_argument("--limit", type=int, default=50)
    parser.add_argument("--offset", type=int, default=0)
    parser.add_argument("--object-prefix", default="products/image2/2026/04/30")
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST_PATH)
    parser.add_argument("--sql", type=Path, default=DEFAULT_SQL_PATH)
    parser.add_argument("--merge-manifest", action="store_true")
    parser.add_argument("--apply-db", action="store_true")
    parser.add_argument("--db-host")
    parser.add_argument("--db-port", type=int)
    parser.add_argument("--db-user")
    parser.add_argument("--db-password")
    parser.add_argument("--db-name")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    rows = upload_images(args)
    if args.merge_manifest and args.manifest.exists():
        existing = json.loads(args.manifest.read_text(encoding="utf-8"))
        by_id = {int(row["id"]): row for row in existing}
        by_id.update({int(row["id"]): row for row in rows})
        rows = [by_id[key] for key in sorted(by_id)]
    args.manifest.parent.mkdir(parents=True, exist_ok=True)
    args.manifest.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
    write_sql(rows, args.sql)
    print(f"[manifest] {args.manifest}")
    print(f"[sql] {args.sql}")
    if args.apply_db:
        apply_db(rows, read_db_config(args))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
