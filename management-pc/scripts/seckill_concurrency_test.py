#!/usr/bin/env python3
"""
Concurrency test for the seckill order endpoint.

The script intentionally uses only the Python standard library so it can run on
Windows machines without installing extra packages.

Token preparation:
  - Put one user JWT per line in --token-file, or pass --token multiple times.
  - Use different user tokens when you want to test real high concurrency. A
    single token will mostly test per-user rate limiting and idempotency.
"""

import argparse
import json
import socket
import statistics
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Tuple

DEFAULT_TEST_ACCOUNTS = [
    "13800000003",
    "13800000004",
    "13800000005",
    "13800000006",
    "13800000007",
    "13910000001",
    "13910000002",
    "13910000003",
    "13910000004",
    "13910000005",
    "13910000006",
    "13910000007",
    "13910000008",
    "13910000009",
    "13910000010",
    "13910000011",
    "13910000012",
    "13910000013",
    "13910000014",
    "13910000015",
]


class RequestResult:
    def __init__(
        self,
        index: int,
        ok: bool,
        http_status: int,
        business_code: Optional[int],
        message: str,
        latency_ms: float,
        order_id: Optional[int] = None,
        error: Optional[str] = None,
    ) -> None:
        self.index = index
        self.ok = ok
        self.http_status = http_status
        self.business_code = business_code
        self.message = message
        self.latency_ms = latency_ms
        self.order_id = order_id
        self.error = error

    def to_dict(self) -> Dict[str, Any]:
        return {
            "index": self.index,
            "ok": self.ok,
            "http_status": self.http_status,
            "business_code": self.business_code,
            "message": self.message,
            "latency_ms": self.latency_ms,
            "order_id": self.order_id,
            "error": self.error,
        }


def join_url(base_url: str, path: str) -> str:
    return base_url.rstrip("/") + "/" + path.lstrip("/")


def parse_json_response(raw: bytes) -> Dict[str, Any]:
    if not raw:
        return {}
    try:
        payload = json.loads(raw.decode("utf-8"))
    except json.JSONDecodeError:
        return {"message": raw.decode("utf-8", errors="replace")}
    return payload if isinstance(payload, dict) else {"data": payload}


def http_json(
    method: str,
    url: str,
    token: Optional[str] = None,
    payload: Optional[Dict[str, Any]] = None,
    timeout: float = 10.0,
    headers: Optional[Dict[str, str]] = None,
) -> Tuple[int, Dict[str, Any]]:
    body = None
    request_headers = {
        "Accept": "application/json",
        "Content-Type": "application/json",
        **(headers or {}),
    }
    if token:
        request_headers["Authorization"] = f"Bearer {token}"
    if payload is not None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")

    request = urllib.request.Request(url, data=body, headers=request_headers, method=method.upper())
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, parse_json_response(response.read())
    except urllib.error.HTTPError as exc:
        return exc.code, parse_json_response(exc.read())


def read_redis_line(stream: Any) -> bytes:
    line = stream.readline()
    if not line:
        raise RuntimeError("empty Redis response")
    return line.rstrip(b"\r\n")


def redis_get(host: str, port: int, key: str, timeout: float) -> Optional[str]:
    command = f"*2\r\n$3\r\nGET\r\n${len(key)}\r\n{key}\r\n".encode("utf-8")
    with socket.create_connection((host, port), timeout=timeout) as sock:
        sock.sendall(command)
        stream = sock.makefile("rb")
        header = read_redis_line(stream)
        if header.startswith(b"-"):
            raise RuntimeError(header[1:].decode("utf-8", errors="replace"))
        if header == b"$-1":
            return None
        if not header.startswith(b"$"):
            raise RuntimeError(f"unexpected Redis response: {header!r}")
        length = int(header[1:])
        value = stream.read(length)
        stream.read(2)
    return value.decode("utf-8", errors="replace")


def decode_redis_string(value: Optional[str]) -> Optional[str]:
    if value is None:
        return None
    text = value.strip()
    if not text:
        return text
    try:
        parsed = json.loads(text)
        if isinstance(parsed, str):
            return parsed
    except json.JSONDecodeError:
        pass
    return text


def resolve_captcha_code(args: argparse.Namespace) -> Tuple[str, str]:
    status, payload = http_json("GET", join_url(args.base_url, "/captcha"), timeout=args.timeout)
    if status != 200 or payload.get("code") != 200:
        raise RuntimeError(f"failed to load captcha: http={status}, payload={payload}")
    data = payload.get("data") or {}
    captcha_key = data.get("captchaKey")
    if not captcha_key:
        raise RuntimeError(f"captcha key missing: {payload}")

    redis_key = f"captcha:code:{captcha_key}"
    captcha_code = decode_redis_string(redis_get(args.redis_host, args.redis_port, redis_key, args.timeout))
    if not captcha_code:
        raise RuntimeError(f"captcha code missing in Redis key: {redis_key}")
    return captcha_key, captcha_code


def login_by_password(args: argparse.Namespace, account: str, password: str) -> Dict[str, Any]:
    captcha_key, captcha_code = resolve_captcha_code(args)
    status, payload = http_json(
        "POST",
        join_url(args.base_url, "/auth/login"),
        payload={
            "loginType": "password",
            "username": account,
            "password": password,
            "captchaKey": captcha_key,
            "captchaCode": captcha_code,
        },
        timeout=args.timeout,
    )
    if status != 200 or payload.get("code") != 200:
        raise RuntimeError(f"login failed for {account}: http={status}, payload={payload}")
    data = payload.get("data") or {}
    token = data.get("token")
    user = data.get("user") or {}
    user_id = user.get("id")
    if not token or not user_id:
        raise RuntimeError(f"login payload missing token or user id for {account}: {payload}")
    return {"account": account, "token": token, "user": user}


def adjust_balance(args: argparse.Namespace, admin_token: str, user_id: int, amount: float, reason: str) -> Dict[str, Any]:
    status, payload = http_json(
        "POST",
        join_url(args.base_url, "/admin/wallet/adjust"),
        token=admin_token,
        payload={
            "userId": user_id,
            "amount": amount,
            "reason": reason,
        },
        timeout=args.timeout,
    )
    if status != 200 or payload.get("code") != 200:
        raise RuntimeError(f"wallet recharge failed for user {user_id}: http={status}, payload={payload}")
    return payload


def prepare_auto_login(args: argparse.Namespace) -> Tuple[List[str], Dict[str, Any]]:
    accounts = list(args.login_account or [])
    if not accounts:
        accounts = DEFAULT_TEST_ACCOUNTS[: max(1, min(args.auto_login_users, len(DEFAULT_TEST_ACCOUNTS)))]

    admin_token = args.admin_token
    if args.recharge_amount > 0:
        admin_token = admin_token or login_by_password(args, args.admin_account, args.admin_password)["token"]

    tokens: List[str] = []
    users: List[Dict[str, Any]] = []
    recharges: List[Dict[str, Any]] = []
    for account in accounts:
        login = login_by_password(args, account, args.login_password)
        user = login["user"]
        tokens.append(login["token"])
        users.append({
            "account": account,
            "userId": user.get("id"),
            "nickname": user.get("nickname"),
            "role": user.get("role"),
        })
        if admin_token and args.recharge_amount > 0:
            recharge = adjust_balance(
                args,
                admin_token,
                int(user["id"]),
                args.recharge_amount,
                "秒杀一键压测自动充值",
            )
            recharges.append({
                "account": account,
                "userId": user.get("id"),
                "amount": args.recharge_amount,
                "message": recharge.get("message"),
            })

    return tokens, {
        "autoLogin": True,
        "accounts": users,
        "rechargeAmount": args.recharge_amount,
        "recharges": recharges,
    }


def load_tokens(token_values: Iterable[str], token_file: Optional[str]) -> List[str]:
    tokens = [item.strip() for item in token_values if item and item.strip()]
    if token_file:
        path = Path(token_file)
        if not path.exists():
            raise FileNotFoundError(f"token file not found: {path}")
        tokens.extend(
            line.strip()
            for line in path.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.strip().startswith("#")
        )
    deduped = []
    seen = set()
    for token in tokens:
        if token not in seen:
            seen.add(token)
            deduped.append(token)
    return deduped


def find_candidate_apply(base_url: str, timeout: float) -> Dict[str, Any]:
    query = urllib.parse.urlencode({
        "limit": 100,
        "includeHistory": "false",
        "groupByActivity": "false",
    })
    status, payload = http_json("GET", f"{join_url(base_url, '/seckill/products')}?{query}", timeout=timeout)
    if status != 200 or payload.get("code") != 200:
        raise RuntimeError(f"failed to load seckill products: http={status}, payload={payload}")

    products = payload.get("data") or []
    if not isinstance(products, list):
        raise RuntimeError(f"unexpected seckill products payload: {payload}")

    for item in products:
        if not isinstance(item, dict):
            continue
        apply_id = item.get("seckillApplyId") or item.get("applyId")
        remaining_stock = int(item.get("remainingStock") or 0)
        status_value = int(item.get("runtimeStatus") or item.get("seckillStatus") or -1)
        if apply_id and remaining_stock > 0 and status_value == 1:
            return item
    raise RuntimeError("no active seckill product with remaining stock was found")


def create_order_payload(args: argparse.Namespace, apply_id: Optional[int], product_id: Optional[int]) -> Dict[str, Any]:
    payload: Dict[str, Any] = {
        "quantity": args.quantity,
        "address": args.address,
        "receiverName": args.receiver_name,
        "receiverPhone": args.receiver_phone,
        "remark": "seckill concurrency test",
    }
    if apply_id:
        payload["seckillApplyId"] = apply_id
    if product_id:
        payload["productId"] = product_id
    return payload


def run_one(
    index: int,
    args: argparse.Namespace,
    token: str,
    apply_id: Optional[int],
    product_id: Optional[int],
    start_event: threading.Event,
) -> RequestResult:
    start_event.wait()
    url = join_url(args.base_url, "/seckill/orders")
    payload = create_order_payload(args, apply_id, product_id)
    payload["idempotencyKey"] = f"stress-{int(time.time())}-{index}-{uuid.uuid4().hex}"
    headers = {"Idempotency-Key": payload["idempotencyKey"]}

    started = time.perf_counter()
    try:
        http_status, response = http_json(
            "POST",
            url,
            token=token,
            payload=payload,
            timeout=args.timeout,
            headers=headers,
        )
        elapsed = (time.perf_counter() - started) * 1000
        business_code = response.get("code")
        data = response.get("data")
        order_id = data.get("id") if isinstance(data, dict) else None
        return RequestResult(
            index=index,
            ok=http_status == 200 and business_code == 200,
            http_status=http_status,
            business_code=business_code,
            message=str(response.get("message") or ""),
            latency_ms=elapsed,
            order_id=order_id,
        )
    except Exception as exc:  # noqa: BLE001 - CLI diagnostics should capture all failures.
        elapsed = (time.perf_counter() - started) * 1000
        return RequestResult(
            index=index,
            ok=False,
            http_status=0,
            business_code=None,
            message="request failed",
            latency_ms=elapsed,
            error=str(exc),
        )


def percentile(values: List[float], rank: float) -> float:
    if not values:
        return 0.0
    if len(values) == 1:
        return values[0]
    ordered = sorted(values)
    position = (len(ordered) - 1) * rank
    lower = int(position)
    upper = min(lower + 1, len(ordered) - 1)
    weight = position - lower
    return ordered[lower] * (1 - weight) + ordered[upper] * weight


def summarize(results: List[RequestResult], started_at: float, finished_at: float) -> Dict[str, Any]:
    latencies = [item.latency_ms for item in results]
    ok_count = sum(1 for item in results if item.ok)
    message_counts: Dict[str, int] = {}
    for item in results:
        key = item.message or item.error or f"http={item.http_status},code={item.business_code}"
        message_counts[key] = message_counts.get(key, 0) + 1

    duration = max(finished_at - started_at, 0.001)
    return {
        "requests": len(results),
        "success": ok_count,
        "failed": len(results) - ok_count,
        "duration_seconds": round(duration, 3),
        "throughput_per_second": round(len(results) / duration, 2),
        "latency_ms": {
            "min": round(min(latencies), 2) if latencies else 0,
            "avg": round(statistics.mean(latencies), 2) if latencies else 0,
            "p50": round(percentile(latencies, 0.50), 2),
            "p95": round(percentile(latencies, 0.95), 2),
            "max": round(max(latencies), 2) if latencies else 0,
        },
        "messages": dict(sorted(message_counts.items(), key=lambda item: item[1], reverse=True)),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run a high-concurrency seckill order test.")
    parser.add_argument("--base-url", default="http://localhost:8080/api", help="API base URL, including /api.")
    parser.add_argument("--apply-id", type=int, help="Seckill apply id. Auto-detected when omitted.")
    parser.add_argument("--product-id", type=int, help="Product id. Optional fallback when apply id is omitted.")
    parser.add_argument("--quantity", type=int, default=1)
    parser.add_argument("--concurrency", type=int, default=50)
    parser.add_argument("--requests", type=int, default=200)
    parser.add_argument("--timeout", type=float, default=10.0)
    parser.add_argument("--token", action="append", default=[], help="User JWT. Can be passed multiple times.")
    parser.add_argument("--token-file", help="File containing one user JWT per line.")
    parser.add_argument("--auto-login", action="store_true", help="Log in local test users automatically before running.")
    parser.add_argument("--auto-login-users", type=int, default=1, help="Number of default test accounts to use when --login-account is omitted.")
    parser.add_argument("--login-account", action="append", default=[], help="User account to log in. Can be passed multiple times.")
    parser.add_argument("--login-password", default="123456")
    parser.add_argument("--admin-account", default="admin")
    parser.add_argument("--admin-password", default="123456")
    parser.add_argument("--admin-token", help="Admin JWT used for recharge. Avoids an extra admin login during one-click tests.")
    parser.add_argument("--recharge-amount", type=float, default=0.0, help="Recharge each auto-login user before testing.")
    parser.add_argument("--redis-host", default="127.0.0.1", help="Redis host used to read local captcha answers.")
    parser.add_argument("--redis-port", type=int, default=6379, help="Redis port used to read local captcha answers.")
    parser.add_argument("--address", default="北京市朝阳区秒杀压测路 1 号")
    parser.add_argument("--receiver-name", default="压测用户")
    parser.add_argument("--receiver-phone", default="13800009999")
    parser.add_argument("--dry-run", action="store_true", help="Only resolve the seckill target and print it.")
    parser.add_argument("--output", help="Write full JSON result to this file.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.quantity <= 0:
        print("--quantity must be greater than 0", file=sys.stderr)
        return 2
    if args.concurrency <= 0 or args.requests <= 0:
        print("--concurrency and --requests must be greater than 0", file=sys.stderr)
        return 2

    candidate = None
    apply_id = args.apply_id
    product_id = args.product_id
    if not apply_id:
        candidate = find_candidate_apply(args.base_url, args.timeout)
        apply_id = int(candidate.get("seckillApplyId") or candidate.get("applyId"))
        product_id = int(candidate.get("productId") or candidate.get("id") or product_id or 0) or None

    print(f"target apply_id={apply_id}, product_id={product_id or '-'}")
    if candidate:
        print(f"target product={candidate.get('productName')}, remaining={candidate.get('remainingStock')}")

    if args.dry_run:
        return 0

    preparation: Dict[str, Any] = {"autoLogin": False, "accounts": [], "rechargeAmount": 0, "recharges": []}
    tokens = load_tokens(args.token, args.token_file)
    if args.auto_login:
        print("auto login enabled; resolving captcha through local Redis and preparing test accounts")
        auto_tokens, preparation = prepare_auto_login(args)
        tokens.extend(auto_tokens)
    if not tokens:
        print("no user token provided; use --token or --token-file", file=sys.stderr)
        return 2
    if len(tokens) == 1:
        print("warning: only one token provided; per-user rate limiting may dominate the result", file=sys.stderr)

    start_event = threading.Event()
    started_at = time.perf_counter()
    results: List[RequestResult] = []
    with ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        futures = [
            executor.submit(
                run_one,
                i,
                args,
                tokens[i % len(tokens)],
                apply_id,
                product_id,
                start_event,
            )
            for i in range(args.requests)
        ]
        start_event.set()
        for future in as_completed(futures):
            results.append(future.result())
    finished_at = time.perf_counter()

    results.sort(key=lambda item: item.index)
    summary = summarize(results, started_at, finished_at)
    print(json.dumps(summary, ensure_ascii=False, indent=2))

    if args.output:
        output = {
            "target": {
                "applyId": apply_id,
                "productId": product_id,
                "candidate": candidate,
            },
            "preparation": preparation,
            "summary": summary,
            "results": [item.to_dict() for item in results],
        }
        Path(args.output).write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"wrote detail report: {args.output}")

    return 0 if summary["success"] > 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
