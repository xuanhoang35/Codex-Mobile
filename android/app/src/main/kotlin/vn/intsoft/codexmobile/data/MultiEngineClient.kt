package vn.intsoft.codexmobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import vn.intsoft.codexmobile.ui.ChatMessage
import vn.intsoft.codexmobile.ui.MessageRole
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class MultiEngineClient(
    private val tokenStore: SecureTokenStore
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // -------------------------------------------------------------------------
    // Dynamic Model Registry
    // -------------------------------------------------------------------------
    fun getDefaultModelsForEngine(engine: AiEngine): List<CodexModel> {
        return when (engine) {
            AiEngine.CODEX -> listOf(
                CodexModel("gpt-4o", "GPT-4o (Đa năng)", "Model hàng đầu của OpenAI", engine),
                CodexModel("o3-mini", "o3-mini (Suy luận cao)", "Tối ưu hóa lập trình và tư duy logic", engine),
                CodexModel("o1", "o1 (Full Reasoning)", "Mô hình suy luận sâu toàn diện", engine),
                CodexModel("gpt-4o-mini", "GPT-4o Mini (Siêu nhanh)", "Nhẹ và tốc độ cao", engine)
            )
            AiEngine.ANTIGRAVITY -> listOf(
                CodexModel("gemini-2.5-pro", "Gemini 2.5 Pro (DeepMind)", "Model mạnh nhất của Google, ngữ cảnh 2M tokens", engine),
                CodexModel("gemini-2.5-flash", "Gemini 2.5 Flash", "Cân bằng tốc độ và trí tuệ nhân tạo thế hệ mới", engine),
                CodexModel("gemini-2.0-flash-thinking", "Gemini 2.0 Thinking", "Suy luận từng bước sâu sắc và chi tiết", engine),
                CodexModel("gemini-2.0-flash", "Gemini 2.0 Flash (Fast)", "Phản hồi tức thì, tối ưu thao tác mã nguồn", engine)
            )
            AiEngine.CLINE -> listOf(
                CodexModel("claude-3-7-sonnet", "Claude 3.7 Sonnet (Hybrid)", "Đỉnh cao code & agentic reasoning từ Anthropic", engine),
                CodexModel("deepseek-r1", "DeepSeek R1 (Reasoning)", "Mô hình mã nguồn mở tư duy mạnh mẽ", engine),
                CodexModel("deepseek-v3", "DeepSeek V3", "Model tổng quát tốc độ cao chi phí tối ưu", engine),
                CodexModel("qwen-2.5-coder-32b", "Qwen 2.5 Coder 32B", "Chuyên biệt lập trình đa ngôn ngữ", engine)
            )
        }
    }

    // -------------------------------------------------------------------------
    // Antigravity (Google DeepMind / Gemini) Stream Execution
    // -------------------------------------------------------------------------
    suspend fun streamAntigravity(
        modelId: String,
        messages: List<ChatMessage>,
        newPrompt: String,
        onChunk: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val apiKey = tokenStore.getAntigravityApiKey()
            val googleUser = tokenStore.getGoogleUser()

            val contents = JSONArray()

            // Context Handoff: convert past conversation into Gemini Contents format
            for (msg in messages) {
                val roleStr = if (msg.role == MessageRole.USER) "user" else "model"
                val partObj = JSONObject().put("text", msg.content)
                val contentObj = JSONObject()
                    .put("role", roleStr)
                    .put("parts", JSONArray().put(partObj))
                contents.put(contentObj)
            }

            // Current prompt
            contents.put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", newPrompt)))
            )

            val payload = JSONObject().apply {
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("maxOutputTokens", 8192)
                })
            }

            val url = if (apiKey.isNotBlank()) {
                "https://generativelanguage.googleapis.com/v1beta/models/$modelId:streamGenerateContent?key=$apiKey&alt=sse"
            } else {
                "https://generativelanguage.googleapis.com/v1beta/models/$modelId:streamGenerateContent?alt=sse"
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))

            // If Google OAuth user token is available, attach Bearer Auth
            googleUser?.accessToken?.takeIf { it.isNotBlank() }?.let { token ->
                requestBuilder.header("Authorization", "Bearer $token")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Lỗi kết nối Gemini API (${response.code})"
                onError("Antigravity: $errorBody")
                return@withContext
            }

            val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
            val fullText = StringBuilder()
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.startsWith("data:")) {
                    val jsonStr = currentLine.removePrefix("data:").trim()
                    if (jsonStr == "[DONE]") break
                    try {
                        val json = JSONObject(jsonStr)
                        val candidates = json.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val candidate = candidates.getJSONObject(0)
                            val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                val chunk = parts.getJSONObject(0).optString("text")
                                if (chunk.isNotEmpty()) {
                                    fullText.append(chunk)
                                    withContext(Dispatchers.Main) { onChunk(chunk) }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // ignore keepalive/partial frames
                    }
                }
            }
            withContext(Dispatchers.Main) { onComplete(fullText.toString()) }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onError(e.message ?: "Lỗi thực thi Antigravity") }
        }
    }

    // -------------------------------------------------------------------------
    // Cline (Universal Agent / OpenRouter / Claude / DeepSeek) Stream Execution
    // -------------------------------------------------------------------------
    suspend fun streamCline(
        modelId: String,
        messages: List<ChatMessage>,
        newPrompt: String,
        onChunk: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val apiKey = tokenStore.getClineApiKey()
            val endpoint = tokenStore.getClineEndpoint().trimEnd('/') + "/chat/completions"

            val msgArray = JSONArray()

            // System prompt for Cline Agentic behavior
            msgArray.put(
                JSONObject()
                    .put("role", "system")
                    .put("content", "You are Cline Mobile Assistant, an expert autonomous software engineer. Maintain context across conversation and write clean code.")
            )

            // Past conversation context handoff
            for (msg in messages) {
                val roleStr = when (msg.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                    MessageRole.SYSTEM -> "system"
                }
                msgArray.put(
                    JSONObject()
                        .put("role", roleStr)
                        .put("content", msg.content)
                )
            }

            // New prompt
            msgArray.put(
                JSONObject()
                    .put("role", "user")
                    .put("content", newPrompt)
            )

            val payload = JSONObject().apply {
                put("model", modelId)
                put("messages", msgArray)
                put("stream", true)
            }

            val requestBuilder = Request.Builder()
                .url(endpoint)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .header("HTTP-Referer", "https://codexmobile.intsoft.vn")
                .header("X-Title", "Codex Mobile Unified Hub")

            if (apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Lỗi kết nối Cline endpoint (${response.code})"
                onError("Cline: $errorBody")
                return@withContext
            }

            val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
            val fullText = StringBuilder()
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.startsWith("data:")) {
                    val jsonStr = currentLine.removePrefix("data:").trim()
                    if (jsonStr == "[DONE]") break
                    try {
                        val json = JSONObject(jsonStr)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val chunk = delta?.optString("content") ?: ""
                            if (chunk.isNotEmpty()) {
                                fullText.append(chunk)
                                withContext(Dispatchers.Main) { onChunk(chunk) }
                            }
                        }
                    } catch (e: Exception) {
                        // ignore malformed chunks
                    }
                }
            }
            withContext(Dispatchers.Main) { onComplete(fullText.toString()) }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onError(e.message ?: "Lỗi thực thi Cline") }
        }
    }
}

