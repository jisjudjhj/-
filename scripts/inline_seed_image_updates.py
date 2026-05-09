#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把图片更新分片回写到原始 seed 分块中，彻底移除 placehold.co 字面量。
"""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

CHUNK_DIR = ROOT / "backend" / "src" / "main" / "resources" / "sql" / "chunks"

PRODUCT_UPDATE_RE = re.compile(
    r"UPDATE `product` SET `image` = '((?:[^']|'')*)', `images` = '((?:[^']|'')*)', `update_time` = '((?:[^']|'')*)' WHERE `id` = (\d+);"
)
USER_UPDATE_RE = re.compile(r"UPDATE `user` SET `avatar` = '((?:[^']|'')*)', `update_time` = '((?:[^']|'')*)' WHERE `id` = (\d+);")
CATEGORY_UPDATE_RE = re.compile(r"UPDATE `category` SET `icon` = '((?:[^']|'')*)' WHERE `id` = (\d+);")
BANNER_UPDATE_RE = re.compile(r"UPDATE `banner` SET `image` = '((?:[^']|'')*)' WHERE `id` = (\d+);")


def parse_sql_tuple(line: str) -> tuple[list[str], str] | None:
    stripped = line.strip()
    if not stripped.startswith("("):
        return None
    suffix = ""
    if stripped.endswith("),"):
        suffix = "),"
        body = stripped[1:-2]
    elif stripped.endswith(");"):
        suffix = ");"
        body = stripped[1:-2]
    elif stripped.endswith(")"):
        suffix = ")"
        body = stripped[1:-1]
    else:
        return None

    fields: list[str] = []
    current: list[str] = []
    in_quote = False
    i = 0
    while i < len(body):
        char = body[i]
        if char == "'":
            current.append(char)
            if in_quote and i + 1 < len(body) and body[i + 1] == "'":
                current.append("'")
                i += 2
                continue
            in_quote = not in_quote
            i += 1
            continue
        if char == "," and not in_quote:
            fields.append("".join(current).strip())
            current = []
            i += 1
            continue
        current.append(char)
        i += 1
    fields.append("".join(current).strip())
    return fields, suffix


def build_sql_tuple(fields: list[str], suffix: str) -> str:
    return "(" + ", ".join(fields) + suffix


def parse_product_updates() -> dict[int, tuple[str, str, str]]:
    mapping: dict[int, tuple[str, str, str]] = {}
    for path in sorted(CHUNK_DIR.glob("seed-part-products-images-*.sql")):
        text = path.read_text(encoding="utf-8")
        for image, images, update_time, product_id in PRODUCT_UPDATE_RE.findall(text):
            mapping[int(product_id)] = (image, images, update_time)
    return mapping


def parse_product_spec_name_map() -> dict[int, int]:
    mapping: dict[int, int] = {}
    for path in sorted(CHUNK_DIR.glob("seed-part-products-[0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9].sql")):
        section = ""
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.startswith("INSERT INTO `product_spec_name`"):
                section = "product_spec_name"
                continue
            if line.startswith("INSERT INTO `"):
                section = ""
                continue

            if section != "product_spec_name":
                continue

            parsed = parse_sql_tuple(line)
            if parsed is None:
                continue
            fields, _ = parsed
            if len(fields) < 2:
                continue
            try:
                spec_name_id = int(fields[0])
                product_id = int(fields[1])
            except ValueError:
                continue
            mapping[spec_name_id] = product_id
    return mapping


def parse_base_updates() -> tuple[dict[int, tuple[str, str]], dict[int, str], dict[int, str]]:
    path = CHUNK_DIR / "seed-part-01-base-images-web.sql"
    text = path.read_text(encoding="utf-8")
    user_updates = {int(user_id): (avatar, update_time) for avatar, update_time, user_id in USER_UPDATE_RE.findall(text)}
    category_updates = {int(category_id): icon for icon, category_id in CATEGORY_UPDATE_RE.findall(text)}
    banner_updates = {int(banner_id): image for image, banner_id in BANNER_UPDATE_RE.findall(text)}
    return user_updates, category_updates, banner_updates


def rewrite_product_chunk(path: Path, product_updates: dict[int, tuple[str, str, str]]) -> int:
    lines = path.read_text(encoding="utf-8").splitlines()
    updated = 0
    for index, line in enumerate(lines):
        parsed = parse_sql_tuple(line)
        if parsed is None:
            continue
        fields, suffix = parsed
        if len(fields) < 16:
            continue
        try:
            product_id = int(fields[0])
        except ValueError:
            continue
        payload = product_updates.get(product_id)
        if payload is None:
            continue
        image, images, update_time = payload
        fields[7] = f"'{image}'"
        fields[8] = f"'{images}'"
        fields[15] = f"'{update_time}'"
        lines[index] = build_sql_tuple(fields, suffix)
        updated += 1
    path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    return updated


def rewrite_product_related_chunk(
    path: Path,
    product_updates: dict[int, tuple[str, str, str]],
    spec_name_to_product: dict[int, int],
) -> tuple[int, int]:
    lines = path.read_text(encoding="utf-8").splitlines()
    section = ""
    sku_count = 0
    spec_value_count = 0
    product_image_by_id = {product_id: payload[0] for product_id, payload in product_updates.items()}
    fallback_images = list(product_image_by_id.values())
    fallback_index = 0

    def next_fallback_image() -> str | None:
        nonlocal fallback_index
        if not fallback_images:
            return None
        image = fallback_images[fallback_index % len(fallback_images)]
        fallback_index += 1
        return image

    for index, line in enumerate(lines):
        if line.startswith("INSERT INTO `product_sku`"):
            section = "product_sku"
            continue
        if line.startswith("INSERT INTO `product_spec_value`"):
            section = "product_spec_value"
            continue
        if line.startswith("INSERT INTO `"):
            section = ""
            continue

        parsed = parse_sql_tuple(line)
        if parsed is None:
            continue
        fields, suffix = parsed

        if section == "product_sku" and len(fields) >= 9:
            try:
                product_id = int(fields[1])
            except ValueError:
                continue
            image = product_image_by_id.get(product_id)
            if not image:
                continue
            fields[8] = f"'{image}'"
            lines[index] = build_sql_tuple(fields, suffix)
            sku_count += 1
        elif section == "product_spec_value" and len(fields) >= 4:
            try:
                spec_name_id = int(fields[1])
            except ValueError:
                continue
            product_id = spec_name_to_product.get(spec_name_id)
            image = product_image_by_id.get(product_id) if product_id is not None else None
            if not image:
                image = next_fallback_image()
            if not image:
                continue
            fields[3] = f"'{image}'"
            lines[index] = build_sql_tuple(fields, suffix)
            spec_value_count += 1

    path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    return sku_count, spec_value_count


def rewrite_base_chunk(path: Path, user_updates: dict[int, tuple[str, str]], category_updates: dict[int, str], banner_updates: dict[int, str]) -> tuple[int, int, int]:
    lines = path.read_text(encoding="utf-8").splitlines()
    section = ""
    user_count = 0
    category_count = 0
    banner_count = 0

    for index, line in enumerate(lines):
        if line.startswith("INSERT INTO `user`"):
            section = "user"
            continue
        if line.startswith("INSERT INTO `category`"):
            section = "category"
            continue
        if line.startswith("INSERT INTO `banner`"):
            section = "banner"
            continue

        parsed = parse_sql_tuple(line)
        if parsed is None:
            continue
        fields, suffix = parsed
        if not fields:
            continue
        try:
            row_id = int(fields[0])
        except ValueError:
            continue

        if section == "user" and len(fields) >= 15 and row_id in user_updates:
            avatar, update_time = user_updates[row_id]
            fields[5] = f"'{avatar}'"
            fields[14] = f"'{update_time}'"
            lines[index] = build_sql_tuple(fields, suffix)
            user_count += 1
        elif section == "category" and len(fields) >= 4 and row_id in category_updates:
            fields[3] = f"'{category_updates[row_id]}'"
            lines[index] = build_sql_tuple(fields, suffix)
            category_count += 1
        elif section == "banner" and len(fields) >= 3 and row_id in banner_updates:
            fields[2] = f"'{banner_updates[row_id]}'"
            lines[index] = build_sql_tuple(fields, suffix)
            banner_count += 1

    path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    return user_count, category_count, banner_count


def rewrite_commerce_chunk(path: Path, product_updates: dict[int, tuple[str, str, str]]) -> int:
    lines = path.read_text(encoding="utf-8").splitlines()
    section = ""
    updated = 0
    product_image_by_id = {product_id: payload[0] for product_id, payload in product_updates.items()}

    for index, line in enumerate(lines):
        if line.startswith("INSERT INTO `order_item`"):
            section = "order_item"
            continue
        if line.startswith("INSERT INTO `"):
            section = ""
            continue

        if section != "order_item":
            continue

        parsed = parse_sql_tuple(line)
        if parsed is None:
            continue
        fields, suffix = parsed
        if len(fields) < 7:
            continue
        try:
            product_id = int(fields[2])
        except ValueError:
            continue
        image = product_image_by_id.get(product_id)
        if not image:
            continue
        fields[6] = f"'{image}'"
        lines[index] = build_sql_tuple(fields, suffix)
        updated += 1

    path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    return updated


def rewrite_extended_chunk(
    path: Path,
    user_updates: dict[int, tuple[str, str]],
    banner_updates: dict[int, str],
) -> tuple[int, int, int]:
    lines = path.read_text(encoding="utf-8").splitlines()
    section = ""
    seckill_count = 0
    profile_count = 0
    agent_count = 0
    banner_urls = [banner_updates[banner_id] for banner_id in sorted(banner_updates)]
    banner_index = 0

    def next_banner() -> str | None:
        nonlocal banner_index
        if not banner_urls:
            return None
        image = banner_urls[banner_index % len(banner_urls)]
        banner_index += 1
        return image

    for index, line in enumerate(lines):
        if line.startswith("INSERT INTO `seckill_activity`"):
            section = "seckill_activity"
            continue
        if line.startswith("INSERT INTO `profile_change_request`"):
            section = "profile_change_request"
            continue
        if line.startswith("INSERT INTO `im_support_agent`"):
            section = "im_support_agent"
            continue
        if line.startswith("INSERT INTO `"):
            section = ""
            continue

        parsed = parse_sql_tuple(line)
        if parsed is None:
            continue
        fields, suffix = parsed

        if section == "seckill_activity" and len(fields) >= 3:
            image = next_banner()
            if not image:
                continue
            fields[2] = f"'{image}'"
            lines[index] = build_sql_tuple(fields, suffix)
            seckill_count += 1
        elif section == "profile_change_request" and len(fields) >= 6:
            try:
                user_id = int(fields[1])
            except ValueError:
                continue
            user_payload = user_updates.get(user_id)
            if user_payload is None:
                continue
            avatar, _ = user_payload
            fields[3] = f"'{avatar}'"
            fields[5] = f"'{avatar}'"
            lines[index] = build_sql_tuple(fields, suffix)
            profile_count += 1
        elif section == "im_support_agent" and len(fields) >= 4:
            try:
                user_id = int(fields[1])
            except ValueError:
                continue
            user_payload = user_updates.get(user_id)
            if user_payload is None:
                continue
            avatar, _ = user_payload
            fields[3] = f"'{avatar}'"
            lines[index] = build_sql_tuple(fields, suffix)
            agent_count += 1

    path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    return seckill_count, profile_count, agent_count


def main():
    product_updates = parse_product_updates()
    spec_name_to_product = parse_product_spec_name_map()
    user_updates, category_updates, banner_updates = parse_base_updates()

    product_total = 0
    sku_total = 0
    spec_value_total = 0
    for path in sorted(CHUNK_DIR.glob("seed-part-products-[0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9].sql")):
        product_total += rewrite_product_chunk(path, product_updates)
        sku_count, spec_value_count = rewrite_product_related_chunk(path, product_updates, spec_name_to_product)
        sku_total += sku_count
        spec_value_total += spec_value_count

    user_count, category_count, banner_count = rewrite_base_chunk(
        CHUNK_DIR / "seed-part-01-base.sql",
        user_updates,
        category_updates,
        banner_updates,
    )
    order_item_total = 0
    for path in sorted(CHUNK_DIR.glob("seed-part-commerce-[0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9].sql")):
        order_item_total += rewrite_commerce_chunk(path, product_updates)

    seckill_count, profile_count, agent_count = rewrite_extended_chunk(
        CHUNK_DIR / "seed-part-extended.sql",
        user_updates,
        banner_updates,
    )

    print(
        {
            "productsUpdated": product_total,
            "productSkusUpdated": sku_total,
            "productSpecValuesUpdated": spec_value_total,
            "usersUpdated": user_count,
            "categoriesUpdated": category_count,
            "bannersUpdated": banner_count,
            "orderItemsUpdated": order_item_total,
            "seckillActivitiesUpdated": seckill_count,
            "profileChangeRequestsUpdated": profile_count,
            "imSupportAgentsUpdated": agent_count,
        }
    )


if __name__ == "__main__":
    main()
