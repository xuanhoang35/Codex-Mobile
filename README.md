# 📱 Codex Mobile - Unified Multi-Engine AI Coding Hub

Dự án hợp nhất toàn diện hệ sinh thái **Codex Mobile**: Bao gồm **Máy chủ điều phối (Server Backend)** trên Linux và **Ứng dụng di động (Android Client)** được xây dựng bằng **Jetpack Compose + Kotlin**, tích hợp bộ ba quyền lực: **OpenAI Codex + Google Antigravity + Cline**.

---

## 🏗 Kiến trúc tổng quan (Architecture Overview)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                 📱 Codex Mobile App (Android Jetpack Compose)               │
│                                                                             │
│  ┌───────────────────────┬─────────────────────────┬──────────────────────┐ │
│  │    ⚡ Codex Engine    │  🪐 Antigravity Engine  │    🤖 Cline Engine   │ │
│  │ (OpenAI o3-mini/o1/4o)│ (Gemini 2.5 Pro/Flash)  │(Claude 3.7 / DeepSeek│ │
│  └───────────┬───────────┴────────────┬────────────┴──────────┬───────────┘ │
│              │                        │                       │             │
│              ▼                        ▼                       ▼             │
│    [WebSocket Protocol]       [Google Gemini API]     [OpenRouter / Claude] │
│    [Capability Token]        [Google Login - Gmail]   [Universal Agent SSE] │
│              │                        │                       │             │
│              └────────────────────────┼───────────────────────┘             │
│                                       ▼                                     │
│                  📂 Universal Conversation Store & Snippets                 │
│         (Lịch sử trò chuyện xuyên suốt không đứt mạch khi đổi Engine)       │
└───────────────────────────────────────┬─────────────────────────────────────┘
                                        │ 🔐 WireGuard (Tailscale VPN)
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    🌐 Máy chủ Remote Server (Tailscale)                     │
│                intsoft-codebank-vn.tailb240ce.ts.net:8443                   │
│                                                                             │
│  🔒 Tailscale Serve (Reverse Proxy HTTPS / WSS :8443)                       │
│       │                                                                     │
│       ▼ (Loopback ws://127.0.0.1:4500)                                      │
│  ⚙️ Codex App Server (@openai/codex CLI)                                     │
│       - Quản lý qua Systemd (codex-app-server.service)                      │
│       - Xác thực Capability Token 48-byte bảo mật                           │
│       - Chạy độc lập dưới tài khoản an toàn 'codexapp'                      │
│       - Sandbox bảo vệ: Bubblewrap (bwrap) & AppArmor                       │
│       - Thư mục Workspace: /srv/codex-workspaces                            │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 🌟 Điểm nổi bật (Key Features)

### 1. Bộ 3 Tam Mã (The Holy Trinity of AI Coding)
- ⚡ **Codex (OpenAI)**: Kết nối trực tiếp tới máy chủ qua WebSocket bảo mật, điều khiển Workspace, thực thi terminal và duyệt file từ xa.
- 🪐 **Antigravity (Google DeepMind / Gemini)**: Tận dụng cửa sổ ngữ cảnh khổng lồ (1M - 2M tokens), nuốt trọn codebase lớn không lo tràn bộ nhớ, phân tích ảnh chụp màn hình UI thiết kế, đăng nhập một chạm bằng Google (Gmail).
- 🤖 **Cline (Universal Agent)**: Tích hợp vạn năng qua OpenRouter (Claude 3.7 Sonnet, DeepSeek R1/V3, Qwen), cơ chế diff code từng dòng chi tiết.
- 🔄 **Kế thừa ngữ cảnh (Context Handoff)**: Chuyển đổi qua lại giữa Codex, Antigravity và Cline ngay trong cùng một cuộc trò chuyện mà không bị mất dấu bối cảnh.

### 2. An toàn tuyệt đối (Zero Attack Surface)
- Không mở bất kỳ cổng public nào ra Internet; mọi kết nối đi qua mạng mã hóa riêng ảo Tailscale.
- Sandbox cô lập trên máy chủ với Bubblewrap (`bwrap`) và AppArmor ngăn chặn truy cập trái phép vào hệ điều hành gốc.

---

## 📁 Cấu trúc thư mục (Repository Layout)

```
Codex-Mobile/
├── android/                   # 📱 Toàn bộ mã nguồn ứng dụng Android
│   ├── app/                   # Module ứng dụng chính
│   │   ├── src/main/kotlin/   # Mã nguồn Kotlin Jetpack Compose
│   │   │   └── vn/intsoft/codexmobile/
│   │   │       ├── data/      # Models, MultiEngineClient, TokenStore, Queue
│   │   │       ├── ui/        # CodexApp, SnippetScreen, CodexViewModel
│   │   │       └── MainActivity.kt
│   │   ├── src/main/res/      # Giao diện, biểu tượng, chuỗi ngôn ngữ
│   │   ├── AndroidManifest.xml
│   │   └── build.gradle
│   ├── gradle/wrapper/        # Gradle Wrapper 8.10.2
│   ├── build.gradle           # Cấu hình Gradle Root (Kotlin 2.0, Compose)
│   └── settings.gradle
├── scripts/                   # ⚙️ Kịch bản tự động hóa máy chủ
│   ├── install.sh             # Cài đặt tự động 1-click máy chủ
│   ├── verify.sh              # Kiểm tra sức khỏe toàn diện hệ thống
│   └── generate-token.sh      # Tạo mới hoặc xoay vòng Token bảo mật
├── config/                    # 📄 Cấu hình server
│   └── config.toml            # File cấu hình mẫu cho codex app-server
├── systemd/                   # 🛠 Dịch vụ nền Linux
│   └── codex-app-server.service
├── apparmor/                  # 🛡 Hồ sơ bảo mật sandbox
│   └── bwrap-userns-restrict
└── client/                    # 🧪 Công cụ kiểm thử kết nối
    ├── connect-mobile.md
    └── probe.js
```

---

## 🚀 Hướng dẫn triển khai

### A. Triển khai Máy chủ (Server Deployment)
1. Clone repository về máy chủ Ubuntu / Debian:
   ```bash
   git clone https://github.com/xuanhoang35/Codex-Mobile.git
   cd Codex-Mobile
   ```
2. Chạy cài đặt tự động:
   ```bash
   chmod +x scripts/*.sh
   sudo ./scripts/install.sh
   ```
3. Đăng nhập tài khoản OpenAI:
   ```bash
   sudo -u codexapp env HOME=/home/codexapp codex login --device-auth
   ```
4. Kiểm tra sức khỏe:
   ```bash
   sudo ./scripts/verify.sh
   ```

### B. Mở và Biên dịch Ứng dụng Di động (Mobile App)
1. Mở thư mục `android/` bằng **Android Studio** (Koala / Ladybug / mới hơn).
2. Android Studio sẽ tự động đồng bộ Gradle (Gradle 8.10.2, JDK 17, Compose 2024.10).
3. Chọn **Build > Build Bundle(s) / APK(s) > Build APK(s)** hoặc kết nối điện thoại và nhấn **Run (Shift + F10)**.
4. Mở app trên điện thoại:
   - Vào **Cài đặt**: Đăng nhập Google (Gmail) để dùng Antigravity & Cline.
   - Nhập Endpoint WebSocket của máy chủ (VD: `wss://intsoft-codebank-vn.tailb240ce.ts.net:8443/`).
   - Chuyển đổi linh hoạt giữa 3 Engine ngay trên thanh công cụ!
