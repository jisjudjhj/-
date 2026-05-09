#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
将分块 seed SQL 收口为标准初始化文件：
1. schema.sql 保持为建表文件
2. seed.sql   作为初始化数据文件
"""

from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SQL_DIR = ROOT / "backend" / "src" / "main" / "resources" / "sql"
CHUNK_DIR = SQL_DIR / "chunks"
SCHEMA_SOURCE = SQL_DIR / "schema.sql"
INIT_TARGET = SQL_DIR / "seed.sql"


def ordered_chunk_files() -> list[Path]:
    ordered = [
        CHUNK_DIR / "seed-part-01-base.sql",
    ]
    ordered.extend(
        sorted(
            CHUNK_DIR.glob("seed-part-products-[0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9].sql"),
            key=chunk_sort_key,
        )
    )
    ordered.extend(
        sorted(
            CHUNK_DIR.glob("seed-part-commerce-[0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9].sql"),
            key=chunk_sort_key,
        )
    )
    ordered.append(CHUNK_DIR / "seed-part-extended.sql")
    ordered.extend(sorted(CHUNK_DIR.glob("seed-part-01-base-images*.sql")))
    ordered.extend(sorted(CHUNK_DIR.glob("seed-part-products-images-*.sql")))
    return [path for path in ordered if path.exists()]


def chunk_sort_key(path: Path) -> tuple[int, int, str]:
    match = re.search(r"(\d+)-(\d+)$", path.stem)
    if not match:
        return (10**9, 10**9, path.name)
    return (int(match.group(1)), int(match.group(2)), path.name)


def build_init_file():
    parts: list[str] = [
        "-- Ecommerce Seed Bundle",
        "-- 自动生成，请勿手工维护",
        "SET NAMES utf8mb4;",
        "USE ecommerce_recommend;",
        "",
    ]
    for chunk_file in ordered_chunk_files():
        parts.append(f"-- >>> BEGIN {chunk_file.name}")
        parts.append(chunk_file.read_text(encoding="utf-8").rstrip())
        parts.append(f"-- <<< END {chunk_file.name}")
        parts.append("")
    INIT_TARGET.write_text("\n".join(parts).rstrip() + "\n", encoding="utf-8", newline="\n")


def main():
    build_init_file()
    print(f"[ok] schema => {SCHEMA_SOURCE}")
    print(f"[ok] init   => {INIT_TARGET}")


if __name__ == "__main__":
    main()
