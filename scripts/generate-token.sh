#!/usr/bin/env bash
set -euo pipefail

TOKEN_FILE="/home/codexapp/.codex/app-server-token"

echo ">>> Dang tao ma Token bao mat moi..."
mkdir -p "$(dirname "$TOKEN_FILE")"
NEW_TOKEN=$(openssl rand -base64 48 | tr -d '\n')

echo "$NEW_TOKEN" > "${TOKEN_FILE}.new"
chown codexapp:codexapp "${TOKEN_FILE}.new"
chmod 600 "${TOKEN_FILE}.new"
mv "${TOKEN_FILE}.new" "$TOKEN_FILE"

if systemctl is-active --quiet codex-app-server.service; then
    echo ">>> Khoi dong lai codex-app-server.service..."
    systemctl restart codex-app-server.service
fi

echo "=================================================="
echo "Ma Token moi cho Codex Mobile App:"
echo "$NEW_TOKEN"
echo "=================================================="
