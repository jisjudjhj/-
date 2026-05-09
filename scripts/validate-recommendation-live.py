from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from typing import Any


def fail(message: str) -> None:
    print(f"[FAIL] {message}")
    raise SystemExit(1)


def warn(message: str) -> None:
    print(f"[WARN] {message}")


def ok(message: str) -> None:
    print(f"[PASS] {message}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate live recommendation output through admin APIs."
    )
    parser.add_argument("--base-url", default=os.getenv("ECOMMERCE_BASE_URL", "http://localhost:8080"))
    parser.add_argument("--token", default=os.getenv("ECOMMERCE_ADMIN_TOKEN", ""))
    parser.add_argument("--user-id", type=int, action="append", dest="user_ids", default=[])
    parser.add_argument("--sample-size", type=int, default=10)
    parser.add_argument("--user-page-size", type=int, default=80)
    parser.add_argument("--limit", type=int, default=10)
    parser.add_argument("--min-hit-rate", type=float, default=60.0)
    parser.add_argument("--min-products", type=int, default=5)
    parser.add_argument("--low-stock-threshold", type=int, default=20)
    parser.add_argument("--timeout", type=float, default=15.0)
    return parser.parse_args()


def build_url(base_url: str, path: str, query: dict[str, Any] | None = None) -> str:
    base = base_url.rstrip("/")
    url = f"{base}{path}"
    if query:
        url = f"{url}?{urllib.parse.urlencode(query)}"
    return url


def request_json(url: str, token: str, timeout: float) -> dict[str, Any]:
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = token if token.lower().startswith("bearer ") else f"Bearer {token}"
    request = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
            return json.loads(raw)
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        if exc.code in (401, 403):
            fail(
                f"admin API rejected the request ({exc.code}). "
                "Pass --token or set ECOMMERCE_ADMIN_TOKEN with an admin JWT."
            )
        fail(f"HTTP {exc.code} from {url}: {body[:500]}")
    except urllib.error.URLError as exc:
        fail(f"cannot reach backend at {url}: {exc.reason}")
    except json.JSONDecodeError as exc:
        fail(f"backend returned non-JSON response from {url}: {exc}")
    raise AssertionError("unreachable")


def unwrap_result(payload: dict[str, Any], url: str) -> dict[str, Any]:
    if payload.get("code") != 200:
        fail(f"API failed at {url}: code={payload.get('code')} message={payload.get('message')}")
    data = payload.get("data")
    if not isinstance(data, dict):
        fail(f"API response at {url} does not contain an object data payload")
    return data


def unwrap_payload(payload: dict[str, Any], url: str) -> Any:
    if payload.get("code") != 200:
        fail(f"API failed at {url}: code={payload.get('code')} message={payload.get('message')}")
    return payload.get("data")


def as_float(value: Any) -> float:
    if value is None:
        return 0.0
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0


def product_id(product: Any) -> Any:
    return product.get("id") if isinstance(product, dict) else None


def product_reason(product: Any) -> str:
    if not isinstance(product, dict):
        return ""
    return str(product.get("recommendReason") or product.get("reasonText") or "").strip()


def explanation_reason(explanation: Any) -> str:
    if not isinstance(explanation, dict):
        return ""
    reason_text = str(explanation.get("reasonText") or "").strip()
    if reason_text:
        return reason_text
    reasons = explanation.get("reasons")
    if isinstance(reasons, list):
        return " ".join(str(item) for item in reasons if item).strip()
    return ""


def ordered_ids(products: Any) -> list[Any]:
    if not isinstance(products, list):
        return []
    return [item for item in (product_id(product) for product in products) if item is not None]


def as_int(value: Any) -> int:
    if value is None:
        return 0
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


def norm(value: Any) -> str:
    return str(value or "").strip().lower()


def page_records(data: Any) -> list[dict[str, Any]]:
    if isinstance(data, list):
        return [item for item in data if isinstance(item, dict)]
    if not isinstance(data, dict):
        return []
    for key in ("records", "list", "items", "rows"):
        value = data.get(key)
        if isinstance(value, list):
            return [item for item in value if isinstance(item, dict)]
    return []


def extract_total(data: Any, records: list[dict[str, Any]]) -> int:
    if isinstance(data, dict):
        for key in ("total", "count", "totalCount"):
            value = data.get(key)
            if value is not None:
                return as_int(value)
    return len(records)


def fetch_typical_user_ids(args: argparse.Namespace) -> list[int]:
    if args.user_ids:
        return args.user_ids[: max(1, args.sample_size)]

    page_size = max(args.sample_size * 4, args.user_page_size, 20)
    url = build_url(args.base_url, "/api/admin/users", {"page": 1, "size": page_size})
    data = unwrap_result(request_json(url, args.token, args.timeout), url)
    records = page_records(data)
    if not records:
        fail("cannot select typical users: /api/admin/users returned no records")

    def user_score(user: dict[str, Any]) -> tuple[int, int]:
        role = norm(user.get("role"))
        status = as_int(user.get("status"))
        user_id = as_int(user.get("id"))
        role_rank = 0 if role in ("user", "customer", "member", "") else 1
        status_rank = 0 if status == 1 else 1
        return (role_rank, status_rank, user_id)

    candidates = []
    for user in sorted(records, key=user_score):
        role = norm(user.get("role"))
        if role in ("admin", "merchant", "super_admin", "administrator"):
            continue
        user_id = as_int(user.get("id"))
        if user_id > 0 and user_id not in candidates:
            candidates.append(user_id)
        if len(candidates) >= args.sample_size:
            break
    if not candidates:
        fail("cannot select typical users: no non-admin user ids found")
    return candidates


def flatten_categories(nodes: Any) -> dict[str, Any]:
    mapping: dict[str, Any] = {}
    if not isinstance(nodes, list):
        return mapping
    stack = [item for item in nodes if isinstance(item, dict)]
    while stack:
        item = stack.pop(0)
        name = item.get("name") or item.get("categoryName") or item.get("label")
        category_id = item.get("id") or item.get("categoryId")
        if name and category_id is not None:
            mapping[norm(name)] = category_id
        children = item.get("children")
        if isinstance(children, list):
            stack.extend(child for child in children if isinstance(child, dict))
    return mapping


def load_category_name_id_map(args: argparse.Namespace) -> dict[str, Any]:
    url = build_url(args.base_url, "/api/products/categories")
    try:
        data = unwrap_payload(request_json(url, args.token, args.timeout), url)
        return flatten_categories(data)
    except SystemExit:
        raise
    except Exception as exc:
        warn(f"cannot load category tree for diagnostics: {exc}")
        return {}


def load_product_stats(args: argparse.Namespace, category_names: list[Any], category_map: dict[str, Any]) -> dict[str, dict[str, Any]]:
    stats: dict[str, dict[str, Any]] = {}
    for category_name in category_names:
        label = str(category_name)
        category_id = category_map.get(norm(label))
        if category_id is None:
            stats[label] = {"matchedCategory": False, "total": 0, "stock": 0, "lowStockItems": 0}
            continue
        url = build_url(
            args.base_url,
            "/api/admin/products",
            {"page": 1, "size": 200, "status": 1, "categoryId": category_id},
        )
        try:
            data = unwrap_result(request_json(url, args.token, args.timeout), url)
        except SystemExit:
            raise
        except Exception as exc:
            warn(f"cannot load products for category {label}: {exc}")
            stats[label] = {"matchedCategory": True, "total": 0, "stock": 0, "lowStockItems": 0}
            continue
        records = page_records(data)
        stats[label] = {
            "matchedCategory": True,
            "total": extract_total(data, records),
            "stock": sum(as_int(item.get("stock")) for item in records),
            "lowStockItems": sum(1 for item in records if as_int(item.get("stock")) <= args.low_stock_threshold),
        }
    return stats


def diagnose(args: argparse.Namespace,
             compare: dict[str, Any],
             preview: dict[str, Any],
             online: list[Any],
             quality: dict[str, Any],
             product_stats: dict[str, dict[str, Any]],
             missing_reason: list[Any]) -> list[str]:
    diagnostics: list[str] = []
    top_categories = quality.get("topPreferenceCategories")
    hit_rate = as_float(quality.get("topCategoryHitRate"))
    inspect_size = as_int(quality.get("inspectSize"))
    distribution = quality.get("categoryDistribution")

    if not isinstance(top_categories, list) or not top_categories:
        diagnostics.append("冷启动数据少：没有可用的 Top 偏好类目，需增加浏览、加购、收藏、购买或搜索行为。")
    if inspect_size < max(1, min(args.min_products, args.limit)):
        diagnostics.append(f"候选池不足：最终推荐只返回 {len(online)} 个商品，低于验收下限。")

    for category, stat in product_stats.items():
        if not stat.get("matchedCategory"):
            diagnostics.append(f"类目映射缺失：偏好类目「{category}」没有匹配到分类表，需检查分类命名。")
            continue
        total = as_int(stat.get("total"))
        stock = as_int(stat.get("stock"))
        low_stock_items = as_int(stat.get("lowStockItems"))
        if total <= 0:
            diagnostics.append(f"类目商品太少：偏好类目「{category}」没有在售商品，推荐无法命中。")
        elif total < args.limit:
            diagnostics.append(f"类目商品偏少：偏好类目「{category}」在售商品 {total} 个，低于本次推荐位 {args.limit}。")
        elif stock <= args.low_stock_threshold:
            diagnostics.append(f"库存不足：偏好类目「{category}」总库存 {stock}，容易被库存过滤或排序降权。")
        elif low_stock_items >= max(1, total // 2):
            diagnostics.append(f"库存结构偏弱：偏好类目「{category}」低库存商品较多，需补货或下架异常库存。")

    hot_ids = ordered_ids(compare.get("hot"))
    online_ids = ordered_ids(online)
    if hot_ids and online_ids[: len(hot_ids)] == hot_ids[: len(online_ids)]:
        diagnostics.append("个性化失效：最终排序与热门榜完全一致，疑似候选不足或退化到热门兜底。")

    if missing_reason:
        diagnostics.append(f"解释缺失：Top 商品 {missing_reason} 没有推荐理由，答辩时难以说明算法依据。")

    if hit_rate < args.min_hit_rate and not diagnostics:
        diagnostics.append(
            f"排序偏离画像：Top 类目命中率 {hit_rate:.2f}%，分布为 {distribution}，应检查混合权重、重排或召回候选。"
        )
    if not preview.get("products"):
        diagnostics.append("推荐结果为空：实时预览没有商品，需检查用户画像初始化、候选召回和商品上架状态。")
    return diagnostics


def validate_user(args: argparse.Namespace, user_id: int, category_map: dict[str, Any]) -> dict[str, Any]:
    query = {"limit": max(1, min(args.limit, 50))}
    compare_url = build_url(args.base_url, f"/api/admin/recommend/compare/{user_id}", query)
    preview_url = build_url(args.base_url, f"/api/admin/recommend/preview/{user_id}", query)

    compare = unwrap_result(request_json(compare_url, args.token, args.timeout), compare_url)
    preview = unwrap_result(request_json(preview_url, args.token, args.timeout), preview_url)

    online = compare.get("online")
    if not isinstance(online, list):
        online = []

    quality = compare.get("quality")
    if not isinstance(quality, dict):
        quality = {}
    top_categories = quality.get("topPreferenceCategories")

    hit_rate = as_float(quality.get("topCategoryHitRate"))
    status = str(quality.get("status") or "")

    preview_products = preview.get("products")
    preview_explanations = preview.get("explanations")
    if not isinstance(preview_explanations, list):
        preview_explanations = []
    if not isinstance(preview_products, list):
        preview_products = []

    head_size = min(3, len(preview_products))
    missing_reason = []
    for index in range(head_size):
        product = preview_products[index]
        explanation = preview_explanations[index] if index < len(preview_explanations) else None
        if not product_reason(product) and not explanation_reason(explanation):
            missing_reason.append(product_id(product) or f"rank-{index + 1}")

    product_stats = load_product_stats(
        args,
        top_categories if isinstance(top_categories, list) else [],
        category_map,
    )
    diagnostics = diagnose(args, compare, preview, online, quality, product_stats, missing_reason)
    passed = (
        isinstance(top_categories, list)
        and bool(top_categories)
        and len(online) >= max(1, min(args.min_products, args.limit))
        and hit_rate >= args.min_hit_rate
        and status != "LOW_MATCH"
        and not missing_reason
        and not any("个性化失效" in item for item in diagnostics)
    )
    return {
        "userId": user_id,
        "passed": passed,
        "onlineCount": len(online),
        "hitRate": hit_rate,
        "status": status or "UNKNOWN",
        "topCategories": top_categories if isinstance(top_categories, list) else [],
        "distribution": quality.get("categoryDistribution"),
        "productStats": product_stats,
        "diagnostics": diagnostics,
    }


def print_report(report: dict[str, Any]) -> None:
    marker = "PASS" if report["passed"] else "LOW"
    top = ",".join(str(item) for item in report.get("topCategories") or []) or "-"
    print(
        f"[{marker}] user {report['userId']}: online={report['onlineCount']}, "
        f"hitRate={report['hitRate']:.2f}%, top={top}, status={report['status']}"
    )
    if not report["passed"]:
        for item in report.get("diagnostics") or ["未识别到明确原因，需人工查看接口返回。"]:
            print(f"       - {item}")


def main() -> None:
    args = parse_args()
    user_ids = fetch_typical_user_ids(args)
    print(f"[audit] selected users: {', '.join(str(item) for item in user_ids)}")
    category_map = load_category_name_id_map(args)
    reports = []
    for user_id in user_ids:
        report = validate_user(args, user_id, category_map)
        reports.append(report)
        print_report(report)

    low_reports = [report for report in reports if not report["passed"]]
    if low_reports:
        print("[audit] users below threshold:")
        for report in low_reports:
            print(
                f"       user {report['userId']}: hitRate={report['hitRate']:.2f}%, "
                f"top={','.join(str(item) for item in report.get('topCategories') or []) or '-'}"
            )
        fail(f"batch validation found {len(low_reports)} user(s) below {args.min_hit_rate:.2f}%")
    ok("batch live recommendation validation passed")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        warn("interrupted")
        sys.exit(130)
