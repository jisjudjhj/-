from __future__ import annotations

import argparse
import posixpath
import stat
import sys
from pathlib import Path

import paramiko


PROJECT_ROOT = Path(__file__).resolve().parents[1]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Deploy ecommerce project to remote server via SSH.")
    parser.add_argument("--host", required=True)
    parser.add_argument("--port", type=int, default=22)
    parser.add_argument("--user", required=True)
    parser.add_argument("--password", required=True)
    parser.add_argument(
        "--skip-db",
        action="store_true",
        help="Skip importing schema and seed data.",
    )
    return parser.parse_args()


def ensure_local_file(path: Path) -> None:
    if not path.exists():
        raise FileNotFoundError(f"Required local path not found: {path}")


def sftp_mkdir_p(sftp: paramiko.SFTPClient, remote_dir: str) -> None:
    parts = [part for part in remote_dir.split("/") if part]
    current = "/"
    for part in parts:
        current = posixpath.join(current, part)
        try:
            sftp.stat(current)
        except FileNotFoundError:
            sftp.mkdir(current)


def upload_file(sftp: paramiko.SFTPClient, local_path: Path, remote_path: str) -> None:
    sftp_mkdir_p(sftp, posixpath.dirname(remote_path))
    print(f"[upload] {local_path} -> {remote_path}")
    sftp.put(str(local_path), remote_path)


def upload_tree(sftp: paramiko.SFTPClient, local_dir: Path, remote_dir: str) -> None:
    ensure_local_file(local_dir)
    for item in sorted(local_dir.rglob("*")):
        remote_path = posixpath.join(remote_dir, item.relative_to(local_dir).as_posix())
        if item.is_dir():
            sftp_mkdir_p(sftp, remote_path)
            continue
        upload_file(sftp, item, remote_path)


def run(ssh: paramiko.SSHClient, command: str, check: bool = True) -> tuple[int, str, str]:
    print(f"[remote] {command}")
    stdin, stdout, stderr = ssh.exec_command(command)
    exit_code = stdout.channel.recv_exit_status()
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    if out.strip():
        print(out.strip())
    if err.strip():
        print(err.strip(), file=sys.stderr)
    if check and exit_code != 0:
        raise RuntimeError(f"Remote command failed ({exit_code}): {command}")
    return exit_code, out, err


def remote_exists(sftp: paramiko.SFTPClient, remote_path: str) -> bool:
    try:
        sftp.stat(remote_path)
        return True
    except FileNotFoundError:
        return False


def main() -> int:
    args = parse_args()

    artifact_map = {
        PROJECT_ROOT / "backend/target/ecommerce-recommendation-1.0.0.jar": "/opt/ecommerce/backend/app.jar",
        PROJECT_ROOT / "management-pc/nginx.default.conf": "/opt/ecommerce/nginx.default.conf",
        PROJECT_ROOT / "deploy/docker-compose.runtime.yml": "/opt/ecommerce/docker-compose.runtime.yml",
        PROJECT_ROOT / "deploy/nginx-ecommerce.conf": "/opt/ecommerce/nginx-ecommerce.conf",
        PROJECT_ROOT / "output/server.env": "/opt/ecommerce/server.env",
        PROJECT_ROOT / "output/schema.runtime.111111.sql": "/opt/ecommerce/schema.sql",
        PROJECT_ROOT / "output/seed.111111.sql": "/opt/ecommerce/seed.sql",
    }

    for local_path in artifact_map:
        ensure_local_file(local_path)
    ensure_local_file(PROJECT_ROOT / "management-pc/dist")

    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    print(f"[connect] {args.user}@{args.host}:{args.port}")
    ssh.connect(
        hostname=args.host,
        port=args.port,
        username=args.user,
        password=args.password,
        timeout=20,
        banner_timeout=20,
        auth_timeout=20,
    )

    try:
        sftp = ssh.open_sftp()
        try:
            run(ssh, "mkdir -p /opt/deploy-backups /opt/ecommerce/backend /opt/ecommerce/admin-dist /opt/ecommerce/uploads")

            upload_tree(sftp, PROJECT_ROOT / "management-pc/dist", "/opt/ecommerce/admin-dist")
            for local_path, remote_path in artifact_map.items():
                upload_file(sftp, local_path, remote_path)

            deploy_script = r"""bash -lc '
set -euo pipefail
ts=$(date +%Y%m%d-%H%M%S)
backup_dir="/opt/deploy-backups/ecommerce-${ts}"
mkdir -p "$backup_dir"

if [ -f /etc/nginx/conf.d/bakery.conf ]; then
  cp -a /etc/nginx/conf.d/bakery.conf "$backup_dir/bakery.conf"
fi
if [ -d /var/www/bakery ]; then
  cp -a /var/www/bakery "$backup_dir/"
fi
if [ -d /opt/bakery ]; then
  cp -a /opt/bakery "$backup_dir/"
fi

MYSQL_BIN=$(command -v mysql || true)
MYSQLDUMP_BIN=$(command -v mysqldump || true)
if [ -x /www/server/mysql/bin/mysql ]; then
  MYSQL_BIN=/www/server/mysql/bin/mysql
fi
if [ -x /www/server/mysql/bin/mysqldump ]; then
  MYSQLDUMP_BIN=/www/server/mysql/bin/mysqldump
fi

if [ -n "$MYSQLDUMP_BIN" ]; then
  "$MYSQLDUMP_BIN" -u111111 -proot 111111 > "$backup_dir/111111-before-ecommerce.sql" || true
fi

pkill -f "bakery-server-1.0.0.jar" || true

if [ -f /etc/nginx/conf.d/bakery.conf ]; then
  mv /etc/nginx/conf.d/bakery.conf "$backup_dir/bakery.conf.disabled"
fi

cp -f /opt/ecommerce/nginx-ecommerce.conf /etc/nginx/conf.d/ecommerce.conf

%DB_IMPORT_BLOCK%

docker compose --env-file /opt/ecommerce/server.env -f /opt/ecommerce/docker-compose.runtime.yml up -d redis rabbitmq backend admin-frontend

nginx -t
systemctl reload nginx || nginx -s reload

echo
echo "[docker ps]"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
echo
echo "[local checks]"
curl -I --max-time 10 http://127.0.0.1:8088 || true
curl -I --max-time 10 http://127.0.0.1/api/products || true
'"""

            db_import_block = ""
            if not args.skip_db:
                db_import_block = """
if [ -z "$MYSQL_BIN" ]; then
  echo "mysql client not found" >&2
  exit 1
fi
"$MYSQL_BIN" -u111111 -proot 111111 < /opt/ecommerce/schema.sql
"$MYSQL_BIN" -u111111 -proot 111111 < /opt/ecommerce/seed.sql
"""
            deploy_script = deploy_script.replace("%DB_IMPORT_BLOCK%", db_import_block.strip())
            run(ssh, deploy_script)
        finally:
            sftp.close()
    finally:
        ssh.close()

    print("[done] deploy completed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
