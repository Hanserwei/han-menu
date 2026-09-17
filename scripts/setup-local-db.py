#!/usr/bin/env python3
"""保留旧命令入口，统一交给中间件脚本准备 PostgreSQL、Redis 和 RustFS。"""

from pathlib import Path
import subprocess
import sys

if __name__ == "__main__":
    command = Path(__file__).resolve().parent / "middleware.py"
    raise SystemExit(subprocess.call([sys.executable, str(command), "up"]))
