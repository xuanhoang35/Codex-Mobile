# Hướng dẫn kết nối từ ứng dụng điện thoại (Codex Mobile App)

## 1. Yêu cầu chuẩn bị
- Điện thoại (Android hoặc iOS) kết nối Internet (qua 4G/5G hoặc WiFi thông thường).
- Không bắt buộc dùng VPN/Tailscale do hệ thống đã được định tuyến an toàn qua Cloudflare Tunnel (`codex.webdemo.name.vn`).
- Ứng dụng **Codex Mobile** (hoặc client hỗ trợ Codex App Server).

## 2. Thông tin cấu hình trên App
Trong giao diện ứng dụng trên điện thoại, vào mục Cài đặt máy chủ (Server Settings) và điền:

- **Server URL / WebSocket Endpoint**:
  ```text
  wss://codex.webdemo.name.vn/
  ```

- **Capability Token (Mã xác thực Bearer)**:
  Lấy mã token trên máy chủ:
  ```bash
  cat /home/codexapp/.codex/app-server-token
  ```
  Dán chuỗi token này vào mục **Codex Secret Token**.

- **Header xác thực**:
  ```http
  Authorization: Bearer <TOKEN_CỦA_BẠN>
  ```

## 3. Bắt đầu sử dụng
- Nhấn nút **Lưu cài đặt** (Save).
- Ứng dụng sẽ tự động kết nối qua WebSocket bảo mật và đồng bộ workspace tại `/srv/codex-workspaces` để bạn bắt đầu ra lệnh, lập trình và quản lý máy chủ trực tiếp trên điện thoại!
