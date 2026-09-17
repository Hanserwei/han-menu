#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
git config --local core.hooksPath .githooks
echo "Installed pre-commit hook: every commit must pass clean verify."
