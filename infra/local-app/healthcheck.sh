#!/usr/bin/env bash
# 运行镜像只有JRE，使用本地HTTP探测避免为健康检查额外安装网络工具。
set -euo pipefail
exec 3<>/dev/tcp/127.0.0.1/8080
printf 'GET /actuator/health HTTP/1.0\r\nHost: localhost\r\n\r\n' >&3
IFS= read -r response <&3
[[ "$response" == HTTP/1.?\ 200* ]]
