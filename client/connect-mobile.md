# Huong dan ket noi tu ung dung dien thoai (Codex Mobile App)

## 1. Yeu cau chuan bi
- Dien thoai (Android hoac iOS) da cai dat ung dung **Tailscale**.
- Dien thoai da dang nhap vao cung mang Tailnet voi may chu.
- Ung dung **Codex Mobile** (hoac client ho tro Codex App Server).

## 2. Thong tin cau hinh tren App
Trong giao dien ung dung tren dien thoai, vao muc Cai dat may chu (Server Settings) va dien:

- **Server URL / WebSocket Endpoint**:
  ```text
  wss://intsoft-codebank-vn.tailb240ce.ts.net:8443/
  ```
  *(Hoac su dung IP mang Tailscale: `wss://100.81.123.50:8443/`)*

- **Capability Token (Ma xac thuc Bearer)**:
  Lay ma token tren may chu:
  ```bash
  cat /home/codexapp/.codex/app-server-token
  ```
  Dan chuoi token nay vao muc **Token / API Key**.

- **Header xac thuc**:
  ```http
  Authorization: Bearer <TOKEN_CUA_BAN>
  ```

## 3. Bat dau su dung
- Nhan nut **Connect** hoac **Test Connection**.
- Khi bao trang thai Connected, he thong se dong bo danh sach workspace tai `/srv/codex-workspaces` de ban bat dau ra lenh, lap trinh va quan ly may chu truc tiep tren dien thoai!
