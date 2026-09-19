#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./scripts/with-env.sh ./mvnw --batch-mode --no-transfer-progress clean verify
./scripts/verify-admin.sh
