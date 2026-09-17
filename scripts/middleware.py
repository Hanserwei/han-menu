#!/usr/bin/env python3
"""管理本机项目专用的 Redis 与 RustFS，初始化持久化空间并验证真实读写。"""

import argparse
from datetime import datetime, timezone
import hashlib
import hmac
import os
from pathlib import Path
import re
import secrets
import shlex
import socket
import subprocess
import time
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlsplit
from urllib.request import ProxyHandler, Request, build_opener
import uuid

ROOT = Path(__file__).resolve().parent.parent


class LocalConfiguration:
    """只管理本地配置，保留数据库凭证和已有随机密码，不从其他项目导入配置。"""

    def __init__(self):
        self.path = ROOT / ".env"
        self.values = {}
        self.original = self.path.read_text() if self.path.exists() else ""
        for line in self.original.splitlines():
            match = re.match(r"(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)", line)
            if match:
                self.values[match[1]] = " ".join(shlex.split(match[2], comments=True))

    def prepare(self):
        """首次生成本地凭证；后续运行保留原值，避免容器重建造成认证变化。"""
        defaults = {
            "REDIS_HOST": "127.0.0.1",
            "REDIS_PORT": "6379",
            "REDIS_USERNAME": "default",
            "REDIS_DATABASE": "0",
            "REDIS_KEY_PREFIX": "han-menu:",
            "REDIS_PASSWORD": secrets.token_urlsafe(32),
            "RUSTFS_ENDPOINT": "http://127.0.0.1:9000",
            "RUSTFS_CONSOLE_URL": "http://127.0.0.1:9001/rustfs/console/",
            "RUSTFS_REGION": "us-east-1",
            "RUSTFS_ACCESS_KEY": "hanmenu" + secrets.token_hex(8),
            "RUSTFS_SECRET_KEY": secrets.token_urlsafe(32),
            "RUSTFS_BUCKET": "han-menu",
            "RUSTFS_TEST_BUCKET": "han-menu-test",
        }
        additions = {key: value for key, value in defaults.items() if key not in self.values}
        self.values.update(additions)
        self.validate()
        if additions:
            content = self.original.rstrip() + "\n\n# 本机 Redis 与 RustFS，由部署脚本初始化。\n"
            content += "".join(f"{key}={shlex.quote(value)}\n" for key, value in additions.items())
            descriptor = os.open(self.path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
            with os.fdopen(descriptor, "w") as stream:
                stream.write(content.lstrip())
        self.path.chmod(0o600)
        directory = ROOT / ".local"
        directory.mkdir(mode=0o700, exist_ok=True)
        directory.chmod(0o700)
        password = self.values["REDIS_PASSWORD"]
        # 外层目录仅当前用户可访问；单文件挂载后需允许容器内 Redis 用户读取配置。
        config = directory / "redis.conf"
        config.write_text(
            "bind 0.0.0.0\nprotected-mode yes\nport 6379\n"
            "dir /data\nappendonly yes\nappendfsync everysec\n"
            "maxmemory 256mb\nmaxmemory-policy noeviction\n"
            f'requirepass "{password}"\n'
        )
        config.chmod(0o644)

    def validate(self):
        """限制脚本操作范围，防止误把本地初始化与写入验证指向 PVE 服务。"""
        expected = {
            "REDIS_HOST": "127.0.0.1", "REDIS_PORT": "6379",
            "REDIS_USERNAME": "default", "REDIS_DATABASE": "0",
            "REDIS_KEY_PREFIX": "han-menu:",
            "RUSTFS_ENDPOINT": "http://127.0.0.1:9000", "RUSTFS_REGION": "us-east-1",
            "RUSTFS_BUCKET": "han-menu", "RUSTFS_TEST_BUCKET": "han-menu-test",
        }
        for key, value in expected.items():
            if self.values.get(key) != value:
                raise ValueError(f"本机部署脚本要求 {key}={value}，请检查 .env")
        for key in ("REDIS_PASSWORD", "RUSTFS_ACCESS_KEY", "RUSTFS_SECRET_KEY"):
            value = self.values.get(key, "")
            if value.startswith("replace") or not re.fullmatch(r"[A-Za-z0-9_-]{16,}", value):
                raise ValueError(f"{key} 需要至少 16 位字母、数字、下划线或连字符")

    def run(self, command, *, extra_env=None, timeout=120):
        """捕获工具输出并移除密钥，避免 Compose 输出把凭证写入终端或日志。"""
        environment = os.environ | self.values | (extra_env or {})
        result = subprocess.run(
            command, cwd=ROOT, env=environment, capture_output=True, text=True, timeout=timeout,
        )
        output = result.stdout + result.stderr
        for key, value in self.values.items():
            if value and re.search(r"PASSWORD|SECRET|ACCESS_KEY", key):
                output = output.replace(value, "[已隐藏]")
        if result.returncode:
            raise RuntimeError(output.strip())
        return output.strip()


class RedisClient:
    """通过应用将使用的本机映射端口验证 Redis，避免仅验证容器内部网络。"""

    def __init__(self, config):
        self.config = config.values

    def execute(self, *arguments):
        """仅实现初始化检查所需的 RESP 标量响应，每次连接先完成认证。"""
        address = (self.config["REDIS_HOST"], int(self.config["REDIS_PORT"]))
        with socket.create_connection(address, timeout=5) as connection:
            with connection.makefile("rb") as stream:
                def send(*parts):
                    values = [str(part).encode() for part in parts]
                    payload = b"*" + str(len(values)).encode() + b"\r\n"
                    payload += b"".join(
                        b"$" + str(len(value)).encode() + b"\r\n" + value + b"\r\n"
                        for value in values
                    )
                    connection.sendall(payload)
                    marker = stream.read(1)
                    result = stream.readline().rstrip(b"\r\n")
                    if marker == b"$":
                        length = int(result)
                        if length < 0:
                            return None
                        result = stream.read(length)
                        stream.read(2)
                    elif marker not in (b"+", b":"):
                        raise RuntimeError("Redis 返回错误或不支持的响应")
                    return result.decode()

                if send("AUTH", self.config["REDIS_PASSWORD"]) != "OK":
                    raise RuntimeError("Redis 认证失败")
                return send(*arguments)


class S3Client:
    """仅供本地资源初始化的轻量签名客户端，业务阶段使用正式 S3 SDK 适配器。"""

    def __init__(self, config):
        self.config = config.values
        self.endpoint = self.config["RUSTFS_ENDPOINT"]
        # 本地请求绕过代理，凭证仅发送至经过 LocalConfiguration 校验的本机端点。
        self.http = build_opener(ProxyHandler({}))

    def request(self, method, path, body=b""):
        """使用 AWS Signature V4 签名，支持本次初始化所需的桶和对象操作。"""
        now = datetime.now(timezone.utc)
        stamp, day = now.strftime("%Y%m%dT%H%M%SZ"), now.strftime("%Y%m%d")
        host = urlsplit(self.endpoint).netloc
        digest = hashlib.sha256(body).hexdigest()
        headers = f"host:{host}\nx-amz-content-sha256:{digest}\nx-amz-date:{stamp}\n"
        signed_headers = "host;x-amz-content-sha256;x-amz-date"
        canonical = "\n".join([method, path, "", headers, signed_headers, digest])
        region = self.config["RUSTFS_REGION"]
        scope = f"{day}/{region}/s3/aws4_request"
        text = "\n".join([
            "AWS4-HMAC-SHA256", stamp, scope, hashlib.sha256(canonical.encode()).hexdigest(),
        ])
        key = ("AWS4" + self.config["RUSTFS_SECRET_KEY"]).encode()
        for part in (day, region, "s3", "aws4_request"):
            key = hmac.new(key, part.encode(), hashlib.sha256).digest()
        signature = hmac.new(key, text.encode(), hashlib.sha256).hexdigest()
        authorization = (
            f'AWS4-HMAC-SHA256 Credential={self.config["RUSTFS_ACCESS_KEY"]}/{scope}, '
            f"SignedHeaders={signed_headers}, Signature={signature}"
        )
        request = Request(
            self.endpoint + path, method=method,
            data=body if method == "PUT" else None,
            headers={"x-amz-date": stamp, "x-amz-content-sha256": digest,
                     "Authorization": authorization},
        )
        with self.http.open(request, timeout=10) as response:
            return response.status, response.read()

    def initialize_buckets(self):
        """幂等创建开发和测试专用桶；默认保持私有，不启用匿名上传或下载。"""
        for name in (self.config["RUSTFS_BUCKET"], self.config["RUSTFS_TEST_BUCKET"]):
            path = "/" + quote(name, safe="")
            try:
                self.request("HEAD", path)
            except HTTPError as error:
                if error.code != 404:
                    raise
                self.request("PUT", path)
            print(f"已准备私有 Bucket：{name}")

    def verify(self):
        """在两个桶内分别验证上传、读取与删除，只清理本次生成的唯一对象。"""
        for name in (self.config["RUSTFS_BUCKET"], self.config["RUSTFS_TEST_BUCKET"]):
            path = f"/{name}/.verification/{uuid.uuid4().hex}.txt"
            body = b"han-menu local object storage verification\n"
            try:
                self.request("PUT", path, body)
                _, result = self.request("GET", path)
                if result != body:
                    raise RuntimeError("对象存储读写内容不一致")
            finally:
                self.request("DELETE", path)
            try:
                self.request("HEAD", path)
            except HTTPError as error:
                if error.code != 404:
                    raise
            else:
                raise RuntimeError("验证对象删除后仍然存在")
            print(f"RustFS {name}：上传、读取、删除验证通过")


class LocalMiddleware:
    """编排容器生命周期与资源验证，不修改现有 PostgreSQL 或任何远端部署。"""

    def __init__(self, config):
        self.config = config
        self.compose = [
            "podman-compose", "--env-file", str(config.path),
            "-f", str(ROOT / "infra/compose.yml"), "-p", "han-menu",
        ]

    def up(self):
        """启动固定版本容器，等待健康检查成功，再初始化对象存储空间。"""
        self.config.prepare()
        print(self.config.run(self.compose + ["up", "-d"]))
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            healthy = [
                self.config.run([
                    "podman", "inspect", "--format", "{{.State.Health.Status}}", name,
                ]) == "healthy"
                for name in ("han-menu-redis", "han-menu-rustfs")
            ]
            if all(healthy):
                try:
                    # /health 可早于 S3 存储层就绪，只有桶操作也成功才完成启动。
                    S3Client(self.config).initialize_buckets()
                except HTTPError as error:
                    if error.code != 503:
                        raise
                else:
                    print("Redis 与 RustFS 均已健康启动，凭证保存在被 Git 忽略的 .env。")
                    return
            time.sleep(2)
        raise RuntimeError("容器未在 90 秒内健康启动，请检查容器日志")

    def verify(self):
        """验证 Redis 认证和限时键读写，以及 RustFS 的实际对象操作。"""
        self.config.validate()
        redis = RedisClient(self.config).execute
        if redis("PING") != "PONG":
            raise RuntimeError("Redis 认证检查失败")
        key = "han-menu:verification:" + uuid.uuid4().hex
        try:
            if redis("SET", key, "verified", "EX", "60", "NX") != "OK":
                raise RuntimeError("Redis 写入检查失败")
            if redis("GET", key) != "verified":
                raise RuntimeError("Redis 读取检查失败")
        finally:
            redis("DEL", key)
        if redis("EXISTS", key) != "0":
            raise RuntimeError("Redis 验证键未清理")
        print("Redis：认证、写入、读取、删除验证通过")
        S3Client(self.config).verify()
        # 显式使用控制台路径，避免根路径被当成未签名的 S3 请求而返回 403。
        http = build_opener(ProxyHandler({}))
        with http.open("http://127.0.0.1:9001/rustfs/console/", timeout=5) as response:
            if response.status != 200:
                raise RuntimeError("RustFS 控制台不可用")
        print("RustFS 控制台：HTTP 200")

    def install_service(self):
        """安装当前用户的 systemd 单元，复用同一启动流程并保留原有数据卷。"""
        directory = Path.home() / ".config/systemd/user"
        directory.mkdir(parents=True, exist_ok=True)
        template = (ROOT / "infra/han-menu-middleware.service").read_text()
        (directory / "han-menu-middleware.service").write_text(
            template.replace("@PROJECT_DIR@", str(ROOT))
        )
        self.config.run(["systemctl", "--user", "daemon-reload"])
        print(self.config.run([
            "systemctl", "--user", "enable", "--now", "han-menu-middleware.service",
        ]))
        print("已启用当前用户的 han-menu-middleware.service。")


def main():
    """提供初始化、停止、状态查看和读写验证入口，停止操作保留容器数据卷。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("up", "stop", "status", "verify", "install-service"))
    action = parser.parse_args().action
    configuration = LocalConfiguration()
    middleware = LocalMiddleware(configuration)
    if action == "up":
        middleware.up()
    elif action == "verify":
        middleware.verify()
    elif action == "install-service":
        middleware.install_service()
    elif action == "stop":
        print(configuration.run(middleware.compose + ["stop"]))
    else:
        print(configuration.run([
            "podman", "ps", "-a", "--filter", "name=han-menu-",
            "--format", "{{.Names}} {{.Image}} {{.Status}} {{.Ports}}",
        ]))


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, ValueError, OSError, URLError, subprocess.TimeoutExpired) as error:
        # 不打印请求对象或 traceback，避免认证头和进程环境出现在诊断输出中。
        if isinstance(error, HTTPError):
            raise SystemExit(f"对象存储请求失败：HTTP {error.code}") from None
        raise SystemExit(str(error)) from None
