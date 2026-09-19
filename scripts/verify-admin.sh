#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# 前端有独立包管理和构建，保持与后端相同的全量提交门禁。
if command -v fnm >/dev/null 2>&1 && command -v pnpm >/dev/null 2>&1; then
  admin_node_version=$(cat admin/.node-version)
  fnm exec --using "$admin_node_version" pnpm --dir admin verify
  ./scripts/with-env.sh fnm exec --using "$admin_node_version" pnpm --dir admin test:e2e
elif command -v pnpm >/dev/null 2>&1; then
  pnpm --dir admin verify
  ./scripts/with-env.sh pnpm --dir admin test:e2e
elif command -v fish >/dev/null 2>&1; then
  fish -lc 'pnpm --dir admin verify'
  ./scripts/with-env.sh fish -lc 'pnpm --dir admin test:e2e'
else
  echo "需要 Node 24 LTS 与 pnpm；也可在 fish 环境配置 pnpm。" >&2
  exit 1
fi
