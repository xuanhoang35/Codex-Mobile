# 📱 Codex Mobile - Remote App Server Setup & Architecture

Kho lưu trữ mã nguồn cấu hình, kịch bản tự động hóa và tài liệu hướng dẫn triển khai **Codex Mobile App Server** (OpenAI Codex) trên máy chủ Ubuntu/Debian, cho phép kết nối an toàn từ thiết bị di động (iOS / Android) qua giao thức WebSocket và mạng riêng ảo Tailscale.

---

## 🏗 Kiến trúc tổng quan (Architecture Overview)

```
┌─────────────────────────────────────────────────────────────┐
│                    📱 Thiết bị di động                      │
│             (Codex Mobile App trên Android / iOS)            │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               │ 🔐 Mã hóa WireGuard (Tailscale VPN)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                  🌐 Mạng riêng ảo Tailscale                 │
│              (intsoft-codebank-vn.tailb240ce.ts.net)        │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               │ 🔒 HTTPS / WSS (:8443) với Auto TLS/SSL
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                   🛡 Tailscale Serve Ingress                │
│                 (Reverse Proxy nội bộ an toàn)              │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               │ 🔄 Loopback ws://127.0.0.1:4500
                               ▼
┌─────────────────────────────────────────────────────────────┐
│            ⚙️ Codex App Server (@openai/codex)              │
│       - User: codexapp (Chạy cô lập không có sudo)          │
│       - Xác thực: Capability Token 48-byte bảo mật          │
│       - Quản lý: Systemd (codex-app-server.service)          │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│            📦 Môi trường thực thi Workspaces & Sandbox      │
│       - Thư mục: /srv/codex-workspaces                      │
│       - Cô lập Sandbox: Bubblewrap (bwrap) & AppArmor       │
└─────────────────────────────────────────────────────────────┘
```

---

## 🌟 Điểm nổi bật (Key Features)

- 🔒 **Zero Public Attack Surface**: Máy chủ không cần mở port ra ngoài Internet công cộng, loại trừ hoàn toàn nguy cơ bị quét cổng hay tấn công brute-force.
- 🛡 **Bảo mật 2 tầng**:
  1. **Lớp mạng**: Chỉ thiết bị trong mạng Tailnet của bạn mới có thể định tuyến tới máy chủ.
  2. **Lớp ứng dụng**: Xác thực bằng mã Capability Token 48-byte cryptographically secure (`Authorization: Bearer <TOKEN>`).
- 📦 **Sandbox an toàn**: Hoạt động dưới user riêng `codexapp`, kết hợp cơ chế sandbox Bubblewrap (`bwrap`) và profile AppArmor, bảo vệ an toàn cho hệ thống gốc.
- 🔄 **Auto SSL & WebSocket**: Tailscale Serve tự động cấp chứng chỉ HTTPS/TLS, hỗ trợ đầy đủ WebSocket (`wss://`) để app mobile kết nối mượt mà không bị lỗi cert.
- 🚀 **Tự động hóa hoàn toàn**: Kèm theo script cài đặt 1-click, script kiểm tra sức khỏe và xoay vòng token.

---

## 📋 Yêu cầu hệ thống (Prerequisites)

- **Hệ điều hành**: Ubuntu 22.04 LTS, Ubuntu 24.04 LTS hoặc Debian 12+.
- **Node.js**: Phiên bản 18+ (LTS).
- **Tailscale**: Đã cài đặt và đăng nhập cùng mạng với điện thoại.
- **Quyền quản trị**: `sudo` hoặc `root`.

---

## 🚀 Cài đặt tự động (Quick Start)

1. Clone repository về máy chủ:
   ```bash
   git clone https://github.com/xuanhoang35/Codex-Mobile.git
   cd Codex-Mobile
   ```

2. Cấp quyền và chạy kịch bản cài đặt tự động:
   ```bash
   chmod +x scripts/*.sh
   sudo ./scripts/install.sh
   ```

3. Đăng nhập tài khoản OpenAI / Codex:
   ```bash
   sudo -u codexapp env HOME=/home/codexapp codex login --device-auth
   ```
   *Mở liên kết hiển thị trên màn hình và xác nhận mã đăng nhập trên trình duyệt.*

4. Kiểm tra sức khỏe toàn hệ thống:
   ```bash
   sudo ./scripts/verify.sh
   ```

---

## 📱 Hướng dẫn kết nối từ ứng dụng điện thoại

1. **Bật Tailscale trên điện thoại**: Đảm bảo điện thoại của bạn đã bật Tailscale và đang cùng online trong Tailnet.
2. **Lấy thông tin kết nối trên máy chủ**:
   - **URL WebSocket**: 
     ```text
     wss://<ten-may-chu-tailscale>:8443/
     ```
     *(Ví dụ: `wss://intsoft-codebank-vn.tailb240ce.ts.net:8443/`)*
   - **Token xác thực**:
     ```bash
     cat /home/codexapp/.codex/app-server-token
     ```
3. **Thiết lập trên App**:
   - Nhập **Server URL** và **Token** vào ứng dụng.
   - Nhấn **Connect**. Khi kết nối thành công, bạn có thể tạo thread, ra lệnh cho Codex đọc/ghi file và thực thi lệnh trong `/srv/codex-workspaces` trực tiếp trên điện thoại.

Chi tiết xem tại: [`client/connect-mobile.md`](client/connect-mobile.md).

---

## 🛠 Quản lý & Vận hành (Operations)

### 1. Kiểm tra trạng thái dịch vụ
```bash
systemctl status codex-app-server.service
```

### 2. Xem log hoạt động theo thời gian thực
```bash
journalctl -u codex-app-server.service -f
```

### 3. Xoay vòng Token bảo mật (Rotate Token)
Khi cần đổi token mới để đảm bảo an toàn hoặc cấp lại cho điện thoại:
```bash
sudo ./scripts/generate-token.sh
```

### 4. Khởi động lại dịch vụ
```bash
sudo systemctl restart codex-app-server.service
```

---

## 📂 Cấu trúc thư mục (Repository Structure)

```text
Codex-Mobile/
├── README.md                      # Tài liệu hướng dẫn tổng quan & kiến trúc
├── .gitignore                     # Bỏ qua token và dữ liệu nhạy cảm
├── systemd/
│   └── codex-app-server.service   # File cấu hình dịch vụ Systemd
├── config/
│   └── config.toml                # Cấu hình Codex Server & quyền Workspace
├── apparmor/
│   └── bwrap-userns-restrict      # Profile AppArmor cho Bubblewrap Sandbox
├── scripts/
│   ├── install.sh                 # Kịch bản cài đặt tự động toàn bộ
│   ├── generate-token.sh          # Tạo hoặc xoay vòng Token xác thực
│   └── verify.sh                  # Kịch bản kiểm tra sức khỏe và kết nối
└── client/
    ├── probe.js                   # Công cụ test kết nối WebSocket độc lập
    └── connect-mobile.md          # Hướng dẫn kết nối chi tiết cho mobile
```

---

## 📄 Bản quyền (License)

Dự án được phát hành dưới giấy phép [MIT License](LICENSE).
