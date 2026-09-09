#!/usr/bin/env bash
set -euo pipefail

PORT=4500
TOKEN_FILE="/home/codexapp/.codex/app-server-token"

echo "=== 1. Kiem tra trang thai Systemd Service ==="
systemctl status codex-app-server.service --no-pager | head -n 15

echo -e "\n=== 2. Kiem tra Endpoint noi bo ==="
READY_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:${PORT}/readyz || echo "failed")
HEALTH_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:${PORT}/healthz || echo "failed")
echo "HTTP /readyz  : $READY_CODE"
echo "HTTP /healthz : $HEALTH_CODE"

echo -e "\n=== 3. Kiem tra trang thai Tailscale Serve ==="
tailscale serve status 2>&1 || echo "Tailscale serve chua duoc bat"

echo -e "\n=== 4. Kiem tra tep Token ==="
if [ -f "$TOKEN_FILE" ]; then
    echo "Token file: $TOKEN_FILE (Quyen: $(stat -c '%a %U:%G' "$TOKEN_FILE"))"
else
    echo "CANH BAO: Khong tim thay token tai $TOKEN_FILE"
fi

echo -e "\n=== 5. Kiem tra trang thai dang nhap tai khoan OpenAI ==="
sudo -u codexapp env HOME=/home/codexapp timeout 5 codex login status 2>&1 || echo "Kiem tra hoan tat"
