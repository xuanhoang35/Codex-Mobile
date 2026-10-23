package vn.intsoft.codexmobile.data

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class OAuthCallbackServer(
    private val onCallbackReceived: (code: String, state: String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    @Volatile
    private var isRunning = false

    fun start() {
        if (isRunning) return
        Thread {
            try {
                serverSocket = ServerSocket(1455, 5, InetAddress.getByName("127.0.0.1"))
                isRunning = true
                Log.d("OAuthServer", "OAuth callback server listening on 127.0.0.1:1455")
                while (isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        handleClient(client)
                    } catch (e: Exception) {
                        if (!isRunning) break
                        Log.e("OAuthServer", "Accept error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("OAuthServer", "Could not start server on port 1455: ${e.message}")
            }
        }.start()
    }

    private fun handleClient(socket: Socket) {
        Thread {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val firstLine = reader.readLine() ?: return@Thread
                val uri = firstLine.split(" ").getOrNull(1) ?: ""
                val query = if (uri.contains("?")) uri.substringAfter("?") else ""
                val params = query.split("&").associate {
                    val parts = it.split("=")
                    (parts.getOrNull(0) ?: "") to (parts.getOrNull(1) ?: "")
                }
                val code = params["code"] ?: ""
                val state = params["state"] ?: ""

                val responseHtml = """
                    <!DOCTYPE html>
                    <html lang="vi">
                    <head>
                        <meta charset="utf-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <title>Codex Mobile - Đăng nhập</title>
                        <style>
                            body { background: #0f141c; color: #e6edf3; font-family: -apple-system, BlinkMacSystemFont, sans-serif; text-align: center; padding: 40px 20px; }
                            .card { background: #161b22; border: 1px solid #10a37f; border-radius: 12px; padding: 24px; max-width: 420px; margin: 0 auto; box-shadow: 0 4px 20px rgba(0,0,0,0.5); }
                            h2 { color: #10a37f; margin-bottom: 12px; font-size: 20px; }
                            p { color: #8b949e; font-size: 14px; line-height: 1.5; margin: 8px 0; }
                            .btn { display: inline-block; margin-top: 16px; padding: 10px 20px; background: #10a37f; color: #000; font-weight: bold; border-radius: 8px; text-decoration: none; }
                        </style>
                    </head>
                    <body>
                        <div class="card">
                            <h2>✅ Đăng nhập ChatGPT thành công!</h2>
                            <p>Xác thực tài khoản OpenAI đã hoàn tất.</p>
                            <p>Bạn có thể đóng tab trình duyệt này và quay lại ứng dụng <b>Codex Mobile</b>.</p>
                        </div>
                        <script>
                            setTimeout(function() { window.close(); }, 2000);
                        </script>
                    </body>
                    </html>
                """.trimIndent()

                val header = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=utf-8\r\n" +
                        "Content-Length: ${responseHtml.toByteArray(Charsets.UTF_8).size}\r\n" +
                        "Connection: close\r\n\r\n"

                socket.getOutputStream().write(header.toByteArray(Charsets.UTF_8))
                socket.getOutputStream().write(responseHtml.toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
                socket.close()

                if (code.isNotBlank()) {
                    onCallbackReceived(code, state)
                }
            } catch (e: Exception) {
                Log.e("OAuthServer", "Error handling client: ${e.message}")
            }
        }.start()
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }
}
