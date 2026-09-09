#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# Codex Mobile App Server - Cai dat tu dong
# Ho tro: Ubuntu 22.04+, Debian 12+
# ==============================================================================

if [ "$(id -u)" -ne 0 ]; then
    echo "LOI: Script can quyen root. Hay chay: sudo ./install.sh" >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

echo ">>> 1. Cai dat cac goi phu thuoc he thong..."
apt-get update -qq
apt-get install -y -qq curl openssl jq bubblewrap apparmor-profiles apparmor-utils

echo ">>> 2. Cai dat Node.js & OpenAI Codex CLI..."
if ! command -v node >/dev/null 2>&1; then
    echo "Dang cai dat Node.js LTS..."
    curl -fsSL https://deb.nodesource.com/setup_lts.x | bash -
    apt-get install -y -qq nodejs
fi

echo "Cai dat @openai/codex toan cuc qua npm..."
npm install -g @openai/codex

echo "Phien ban Codex CLI: $(codex --version)"

echo ">>> 3. Tao user he thong 'codexapp' va thu muc lam viec..."
id codexapp >/dev/null 2>&1 || useradd --system --create-home --shell /bin/bash codexapp
install -d -o codexapp -g codexapp -m 700 /home/codexapp/.codex /srv/codex-workspaces

echo ">>> 4. Cau hinh bao mat AppArmor cho Bubblewrap..."
if [ -f "$ROOT_DIR/apparmor/bwrap-userns-restrict" ]; then
    install -m 0644 "$ROOT_DIR/apparmor/bwrap-userns-restrict" /etc/apparmor.d/bwrap-userns-restrict
    apparmor_parser -r /etc/apparmor.d/bwrap-userns-restrict 2>/dev/null || true
fi

echo ">>> 5. Tao Token xac thuc..."
TOKEN_FILE="/home/codexapp/.codex/app-server-token"
if [ ! -f "$TOKEN_FILE" ]; then
    openssl rand -base64 48 | tr -d '\n' > "$TOKEN_FILE"
    chown codexapp:codexapp "$TOKEN_FILE"
    chmod 600 "$TOKEN_FILE"
fi

echo ">>> 6. Thiet lap file cau hinh config.toml..."
CONFIG_FILE="/home/codexapp/.codex/config.toml"
cp "$ROOT_DIR/config/config.toml" "$CONFIG_FILE"
chown codexapp:codexapp "$CONFIG_FILE"
chmod 600 "$CONFIG_FILE"

echo ">>> 7. Cai dat va kich hoat Systemd Service..."
SERVICE_FILE="/etc/systemd/system/codex-app-server.service"
cp "$ROOT_DIR/systemd/codex-app-server.service" "$SERVICE_FILE"

systemctl daemon-reload
systemctl enable --now codex-app-server.service
sleep 2

echo ">>> 8. Cau hinh Tailscale Serve..."
if command -v tailscale >/dev/null 2>&1; then
    tailscale serve --bg --https=8443 http://127.0.0.1:4500
    tailscale serve status
else
    echo "Luu y: Tailscale chua duoc cai dat tren may nay. Hay cai dat Tailscale de public ket noi an toan."
fi

echo "================================================================="
echo "  HOAN TAT CAI DAT CODEX MOBILE APP SERVER!"
echo "================================================================="
echo "Kiem tra trang thai: $SCRIPT_DIR/verify.sh"
echo "Capability Token   : $(cat $TOKEN_FILE)"
echo ""
echo "Buoc tiep theo: Dang nhap tai khoan OpenAI bang cach chay:"
echo "  sudo -u codexapp env HOME=/home/codexapp codex login --device-auth"
echo "================================================================="
