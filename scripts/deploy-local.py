#!/usr/bin/env python3
"""部署仅供本机使用的完整Podman实例，并将持久化数据、凭证和备份保存在固定目录。"""

import argparse
import fcntl
import hashlib
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import secrets
import shlex
import shutil
import subprocess
import time
from types import SimpleNamespace
from urllib.error import HTTPError, URLError

from middleware import LocalConfiguration, S3Client

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_DATA = Path.home() / ".local/share/han-menu"
NAMES = ["postgres", "redis", "rustfs", "backend", "frontend"]
ORIGIN = "http://127.0.0.1:18080"


class Deployment:
    """只管理独立的han-menu-app实例，持久化路径一经创建不得隐式切换。"""

    def __init__(self, data):
        self.data = Path(data).expanduser().resolve()
        self.env_path = self.data / "config/runtime.env"
        self.values = {}
        if self.env_path.exists():
            for line in self.env_path.read_text().splitlines():
                if line and not line.startswith("#"):
                    key, value = line.split("=", 1)
                    self.values[key] = " ".join(shlex.split(value))
        if self.values and self.values.get("HAN_MENU_DATA_ROOT") != str(self.data):
            raise ValueError("持久化目录与已登记配置不一致，请先确认数据迁移方式")
        self.compose = ["podman-compose", "--env-file", str(self.env_path), "-f",
                        str(self.data / "config/compose.yml"), "-p", "han-menu-app"]

    def run(self, command, *, input_text=None, extra_env=None, timeout=180):
        """子进程输出只用于诊断并脱敏，凭证不通过命令参数或日志打印。"""
        result = subprocess.run(command, cwd=ROOT, env=os.environ | self.values | (extra_env or {}),
                                input=input_text, capture_output=True, text=True, timeout=timeout)
        if result.returncode:
            output = result.stdout + result.stderr
            for key, value in self.values.items():
                if value and re.search(r"PASSWORD|SECRET|KEY|ALIPAY", key):
                    output = output.replace(value, "[已隐藏]")
            raise RuntimeError(output[-5000:].strip())
        return result.stdout.strip()

    def write_env(self, path, values):
        """固定字符集的配置写入私有文件，不覆盖已有管理员凭证。"""
        if any("\n" in value or "\r" in value for value in values.values()):
            raise ValueError("环境配置必须为单行值")
        path.write_text("".join(f"{key}={shlex.quote(value)}\n" for key, value in values.items()))
        path.chmod(0o600)

    def prepare(self):
        """创建新的独立实例；重复部署保留数据和随机凭证。"""
        if not self.values and self.data.exists() and any(self.data.iterdir()):
            raise ValueError("目标目录非空且不是本工具登记的实例，拒绝覆盖")
        self.check_instance()
        self.data.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.data.chmod(0o700)
        for name in ["config", "postgres", "redis", "rustfs", "logs", "backups", "releases"]:
            (self.data / name).mkdir(exist_ok=True, mode=0o700)
        # PG18入口只调整PGDATA子目录，挂载父目录需要允许postgres用户穿过。
        # 宿主机实例根目录仍为0700，实际PGDATA保持0700。
        (self.data / "postgres").chmod(0o755)
        if not self.values:
            self.values = {
                "HAN_MENU_DATA_ROOT": str(self.data),
                "POSTGRES_PASSWORD": secrets.token_urlsafe(32),
                "DB_PASSWORD": secrets.token_urlsafe(32),
                "REDIS_PASSWORD": secrets.token_urlsafe(32),
                "RUSTFS_ACCESS_KEY": "hanmenu" + secrets.token_hex(8),
                "RUSTFS_SECRET_KEY": secrets.token_urlsafe(32),
                "IDENTITY_BOOTSTRAP_USERNAME": "admin",
                "IDENTITY_BOOTSTRAP_PASSWORD": secrets.token_urlsafe(24),
                "BACKEND_IMAGE": "localhost/han-menu-app-backend:unbuilt",
                "FRONTEND_IMAGE": "localhost/han-menu-app-frontend:unbuilt",
            }
            # 仅继承已有支付宝沙箱配置，不把开发库账号或开发管理员带入新实例。
            source = LocalConfiguration().values
            self.values.update({key: value for key, value in source.items() if key.startswith("ALIPAY_")})
            self.write_env(self.env_path, self.values)
            access = self.data / "ACCESS.md"
            access.write_text(f"# HAN MENU 本机测试\n\n地址：{ORIGIN}\n\n用户名：admin\n\n初始密码：{self.values['IDENTITY_BOOTSTRAP_PASSWORD']}\n\n首次登录后可在「我的账号」改密；改密后本文件不会自动同步。\n")
            access.chmod(0o600)
        config = self.data / "config"
        shutil.copyfile(ROOT / "infra/local-app/compose.yml", config / "compose.yml")
        redis = config / "redis.conf"
        redis.write_text("bind 0.0.0.0\nprotected-mode yes\nport 6379\ndir /data\n"
                         "appendonly yes\nappendfsync everysec\nmaxmemory 256mb\n"
                         f"maxmemory-policy noeviction\nrequirepass \"{self.values['REDIS_PASSWORD']}\"\n")
        # 外层目录私有；容器内Redis用户需要读取单独挂载的配置文件。
        redis.chmod(0o644)
        backend = {key: self.values[key] for key in ["DB_PASSWORD", "REDIS_PASSWORD", "RUSTFS_ACCESS_KEY",
                    "RUSTFS_SECRET_KEY", "IDENTITY_BOOTSTRAP_USERNAME", "IDENTITY_BOOTSTRAP_PASSWORD"]}
        backend.update({"SERVER_ADDRESS": "0.0.0.0", "SERVER_PORT": "8080",
            "DB_URL": "jdbc:postgresql://database:5432/han_menu", "DB_USERNAME": "han_menu",
            "REDIS_HOST": "cache", "REDIS_PORT": "6379", "RUSTFS_ENDPOINT": "http://storage:9000",
            "RUSTFS_PUBLIC_ENDPOINT": ORIGIN, "RUSTFS_BUCKET": "han-menu", "RUSTFS_REGION": "us-east-1",
            "NOTIFICATION_ALLOWED_ORIGINS": ORIGIN, "IDENTITY_BOOTSTRAP_ENABLED": "true",
            "SPRINGDOC_API_DOCS_ENABLED": "false", "SPRINGDOC_SWAGGER_UI_ENABLED": "false"})
        backend.update({key: value for key, value in self.values.items() if key.startswith("ALIPAY_")})
        self.write_env(config / "backend.env", backend)
        # 前端沿用PC-6已验证的模板。私有图片以原始桶路径和Host代理，避免破坏S3签名。
        template = (ROOT / "admin/deploy/nginx.conf.template").read_text()
        marker = "    location ^~ /api/ {"
        if template.count(marker) != 1:
            raise ValueError("Nginx模板结构已变更，需重新核对图片代理")
        storage = """    location ^~ /han-menu/ {
      limit_except GET HEAD { deny all; }
      proxy_pass http://127.0.0.1:18090;
      proxy_set_header Host $http_host;
      proxy_intercept_errors off;
      add_header Cache-Control private,no-store always;
    }
"""
        template = template.replace(marker, storage + marker)
        replacements = {"LISTEN": "127.0.0.1:18080", "SERVER_NAME": "127.0.0.1",
                        "ROOT": '"/usr/share/nginx/html"', "BACKEND": "127.0.0.1:18082",
                        "TLS": "# 仅绑定本机回环；localhost属于浏览器安全上下文。"}
        for key, value in replacements.items():
            template = template.replace("{{" + key + "}}", value)
        (config / "nginx.conf").write_text(template)
        (config / "nginx.conf").chmod(0o644)

    def deploy(self):
        """复制已构建的发布物到固定目录，再构建两个不包含凭证的运行镜像。"""
        self.prepare()
        jar = ROOT / "target/han-menu-0.0.1-SNAPSHOT.jar"
        dist = ROOT / "admin/dist"
        if not jar.is_file() or not (dist / "index.html").is_file():
            raise ValueError("请先执行 ./scripts/verify.sh，生成验证过的JAR和dist")
        revision = self.run(["git", "rev-parse", "--short=12", "HEAD"])
        release_id = revision + "-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        release = self.data / "releases" / release_id
        release.mkdir(mode=0o700)
        shutil.copyfile(jar, release / "application.jar")
        (release / "application.jar").chmod(0o644)
        shutil.copytree(dist, release / "dist")
        for name in ["Backend.Containerfile", "Frontend.Containerfile", "healthcheck.sh"]:
            shutil.copyfile(ROOT / "infra/local-app" / name, release / name)
        images = json.loads((ROOT / "infra/local-app/images.json").read_text())
        for kind, base in [("backend", "JAVA_IMAGE"), ("frontend", "NGINX_IMAGE")]:
            image = f"localhost/han-menu-app-{kind}:{release_id.lower()}"
            self.run(["podman", "build", "--build-arg", f"{base}={images[base]}", "-f",
                      str(release / f"{kind.capitalize()}.Containerfile"), "-t", image, str(release)], timeout=600)
            self.values[f"{kind.upper()}_IMAGE"] = image
        (release / "release.json").write_text(json.dumps({
            "revision": revision,
            "dirty": bool(self.run(["git", "status", "--porcelain"])),
            "releaseId": release_id,
            "jarSha256": hashlib.sha256((release / "application.jar").read_bytes()).hexdigest(),
            "backendImage": self.values["BACKEND_IMAGE"],
            "frontendImage": self.values["FRONTEND_IMAGE"],
            "baseImages": images,
        }, ensure_ascii=False, indent=2) + "\n")
        self.values["RELEASE_ID"] = release_id
        self.write_env(self.env_path, self.values)
        print(f"运行镜像已构建，发布物保存在 {release}", flush=True)
        self.up()

    def check_instance(self):
        """固定名称只允许指向登记的数据目录，避免切换目录时替换另一实例。"""
        for name in NAMES:
            existing = subprocess.run(["podman", "inspect", "--format", "{{.HostConfig.LogConfig.Path}}",
                                       f"han-menu-app-{name}"], capture_output=True, text=True)
            if existing.returncode == 0 and existing.stdout.strip() != str(self.data / f"logs/{name}.log"):
                raise ValueError("已有同名部署使用其他数据目录，拒绝隐式切换")

    def require_config(self):
        self.check_instance()
        if not self.values:
            raise ValueError("实例尚未部署，请先运行deploy")

    def healthy(self, name):
        return self.run(["podman", "inspect", "--format", "{{.State.Health.Status}}",
                         f"han-menu-app-{name}"]) == "healthy"

    def wait(self, names, timeout=180):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if all(self.healthy(name) for name in names):
                return
            time.sleep(2)
        raise RuntimeError("容器健康检查超时；请使用status和固定目录中的日志定位")

    def sql(self, statement):
        return self.run(["podman", "exec", "-i", "han-menu-app-postgres", "psql", "-X", "-At",
                         "-U", "postgres", "-d", "postgres", "-v", "ON_ERROR_STOP=1"], input_text=statement)

    def initialize(self):
        """只创建缺失的应用角色、数据库和私有桶，不清理或重置任何已有业务数据。"""
        if self.sql("SELECT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='han_menu');") != "t":
            password = self.values["DB_PASSWORD"]
            if not re.fullmatch(r"[A-Za-z0-9_-]+", password):
                raise ValueError("数据库密码字符集不合法")
            self.sql(f"CREATE ROLE han_menu LOGIN PASSWORD '{password}';")
        if self.sql("SELECT EXISTS(SELECT 1 FROM pg_database WHERE datname='han_menu');") != "t":
            self.sql("CREATE DATABASE han_menu OWNER han_menu;")
        s3 = S3Client(SimpleNamespace(values={"RUSTFS_ENDPOINT": "http://127.0.0.1:18090",
                         "RUSTFS_REGION": "us-east-1", "RUSTFS_ACCESS_KEY": self.values["RUSTFS_ACCESS_KEY"],
                         "RUSTFS_SECRET_KEY": self.values["RUSTFS_SECRET_KEY"]}))
        for attempt in range(30):
            try:
                try:
                    s3.request("HEAD", "/han-menu")
                except HTTPError as error:
                    if error.code != 404:
                        raise
                    s3.request("PUT", "/han-menu")
                return
            except HTTPError as error:
                if error.code != 503 or attempt == 29:
                    raise
                time.sleep(2)

    def up(self):
        self.require_config()
        self.run(self.compose + ["up", "-d", "database", "cache", "storage"])
        self.wait(["postgres", "redis", "rustfs"])
        self.initialize()
        self.run(self.compose + ["up", "-d", "backend", "frontend"])
        self.wait(["backend", "frontend"])
        print(f"本机管理端已就绪：{ORIGIN}\n数据目录：{self.data}\n登录信息：{self.data / 'ACCESS.md'}", flush=True)

    def stop(self):
        self.require_config()
        self.run(self.compose + ["stop"], timeout=120)
        print("本机实例已停止，固定目录中的数据保留")

    def status(self):
        for name in NAMES:
            print(self.run(["podman", "ps", "-a", "--filter", f"name=^han-menu-app-{name}$",
                "--format", "{{.Names}} {{.Status}} {{.Ports}} "]))

    def backup(self):
        """短暂停机生成包含数据库、对象、凭证和发布物的一致冷备份，结束后恢复运行。"""
        self.require_config()
        running = any(self.run(["podman", "inspect", "--format", "{{.State.Running}}",
                                f"han-menu-app-{name}"]) == "true" for name in NAMES)
        target = self.data / "backups" / ("han-menu-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + ".tar.gz")
        self.stop()
        try:
            self.run(["podman", "unshare", "tar", "-czf", str(target), "--exclude=./backups",
                      "-C", str(self.data), "."], timeout=600)
            target.chmod(0o600)
            print(f"一致冷备份已保存：{target}")
        finally:
            if running:
                self.up()

    def install_service(self):
        self.require_config()
        directory = Path.home() / ".config/systemd/user"
        directory.mkdir(parents=True, exist_ok=True)
        unit = directory / "han-menu-app.service"
        unit.write_text(f'''[Unit]
Description=Han Menu local frontend, backend and persistent data
After=network-online.target
Wants=network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory={ROOT}
Environment=PATH={Path.home()}/.local/bin:/usr/local/bin:/usr/bin:/bin
ExecStart=/usr/bin/python3 "{ROOT}/scripts/deploy-local.py" up --data-dir "{self.data}"
ExecStop=/usr/bin/python3 "{ROOT}/scripts/deploy-local.py" stop --data-dir "{self.data}"
TimeoutStartSec=300
TimeoutStopSec=120

[Install]
WantedBy=default.target
''')
        self.run(["systemctl", "--user", "daemon-reload"])
        self.run(["systemctl", "--user", "enable", "--now", "han-menu-app.service"], timeout=360)
        print("已启用用户服务 han-menu-app.service")


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["deploy", "up", "stop", "status", "backup", "install-service"])
    parser.add_argument("--data-dir", default=str(DEFAULT_DATA))
    args = parser.parse_args()
    deployment = Deployment(args.data_dir)
    operation = getattr(deployment, args.action.replace("-", "_"))
    if args.action in ("status", "install-service"):
        operation()
        return
    lock_path = Path.home() / ".cache/han-menu-deploy.lock"
    lock_path.parent.mkdir(parents=True, exist_ok=True)
    with lock_path.open("w") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RuntimeError("已有本机部署操作正在执行，请等待完成") from None
        operation()


if __name__ == "__main__":
    try:
        main()
    except HTTPError as error:
        raise SystemExit(f"对象存储初始化失败：HTTP {error.code}") from None
    except (OSError, ValueError, RuntimeError, URLError, subprocess.TimeoutExpired) as error:
        raise SystemExit(str(error)) from None
