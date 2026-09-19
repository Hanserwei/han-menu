#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "${ALIPAY_SANDBOX_ACCEPTANCE:-}" != true ]]; then
  echo '请显式设置 ALIPAY_SANDBOX_ACCEPTANCE=true，仅对沙箱模拟资金执行手动验收。' >&2
  exit 1
fi
./scripts/with-env.sh ./mvnw --batch-mode --no-transfer-progress test-compile dependency:build-classpath \
  -Dmdep.outputFile=target/sandbox-classpath
exec ./scripts/with-env.sh python3 - <<'PY'
import os
from pathlib import Path
classpath = "target/test-classes:target/classes:" + Path("target/sandbox-classpath").read_text().strip()
os.execvp("java", ["java", "-cp", classpath, "com.hanserwei.hanmenu.support.SandboxAcceptance"])
PY
