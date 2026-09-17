#!/usr/bin/env python3
"""复用已有 Podman PostgreSQL 容器，准备项目独立的开发库、测试库和本地凭证。"""

import os
from pathlib import Path
import secrets
import subprocess
import time

ROOT = Path(__file__).resolve().parent.parent
ENV_FILE = ROOT / ".env"
CONTAINER = os.environ.get("PG_CONTAINER", "pg18")


def sql(statement):
    """使用容器内已有管理员执行 SQL，避免将密码拼接到命令行或输出到日志。"""
    return subprocess.run(
        ["podman", "exec", "-i", CONTAINER, "sh", "-c",
         'exec psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-postgres}" -d postgres -At'],
        input=statement, text=True, check=True, capture_output=True,
    ).stdout.strip()


subprocess.run(["podman", "start", CONTAINER], check=True, stdout=subprocess.DEVNULL)
for attempt in range(60):
    ready = subprocess.run(
        ["podman", "exec", CONTAINER, "sh", "-c",
         'pg_isready -U "${POSTGRES_USER:-postgres}" -d postgres'],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    if ready.returncode == 0:
        break
    time.sleep(0.5)
else:
    raise SystemExit("PostgreSQL did not become ready within 30 seconds.")

role_exists = sql("SELECT 1 FROM pg_roles WHERE rolname = 'han_menu';") == "1"
if not ENV_FILE.exists():
    if role_exists:
        raise SystemExit("han_menu already exists; restore .env manually to preserve its password.")
    password = secrets.token_urlsafe(32)
    sql(f"CREATE ROLE han_menu LOGIN PASSWORD '{password}';")
    descriptor = os.open(ENV_FILE, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w") as stream:
        stream.write(
            "DB_URL=jdbc:postgresql://localhost:5432/han_menu\n"
            "DB_USERNAME=han_menu\n"
            f"DB_PASSWORD={password}\n"
            "TEST_DB_URL=jdbc:postgresql://localhost:5432/han_menu_test\n"
            "TEST_DB_USERNAME=han_menu\n"
            f"TEST_DB_PASSWORD={password}\n"
        )
elif not role_exists:
    raise SystemExit(".env exists but han_menu role is missing; check container selection.")

for database in ("han_menu", "han_menu_test"):
    if sql(f"SELECT 1 FROM pg_database WHERE datname = '{database}';") != "1":
        sql(f'CREATE DATABASE "{database}" OWNER han_menu;')

print("Project databases are ready in " + CONTAINER + "; credentials are in ignored .env.")
