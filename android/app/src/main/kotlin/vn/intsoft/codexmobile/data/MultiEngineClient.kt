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
                CodexModel("gpt-4o", "GPT-4o (Đa năng)", "Model hàng đầu của OpenAI", engine, badge = "Frontier", isDefault = true),
                CodexModel("o3-mini", "o3-mini (Suy luận cao)", "Tối ưu hóa lập trình và tư duy logic", engine, badge = "Reasoning"),
                CodexModel("o1", "o1 (Full Reasoning)", "Mô hình suy luận sâu toàn diện", engine, badge = "Reasoning"),
                CodexModel("gpt-4o-mini", "GPT-4o Mini (Siêu nhanh)", "Nhẹ và tốc độ cao", engine, badge = "Fast")
            )
            AiEngine.ANTIGRAVITY -> listOf(
                CodexModel("gemini-3.8-flash-high", "Gemini 3.8 Flash High", "Mô hình siêu tốc độ & tư duy cao cấp của DeepMind", engine, badge = "High", isDefault = true),
                CodexModel("claude-sonnet-4-6-thinking", "Claude Sonnet 4.6 (Thinking)", "Tư duy suy luận logic sâu sắc thế hệ mới", engine, badge = "Notice"),
                CodexModel("claude-opus-4-6-thinking", "Claude Opus 4.6 (Thinking)", "Đỉnh cao trí tuệ lập trình & kiến trúc mã nguồn phức tạp", engine, badge = "Notice"),
                CodexModel("gpt-oss-120b-medium", "GPT-OSS 120B (Medium)", "Mô hình mã nguồn mở 120B siêu năng lực tính toán", engine, badge = "Notice"),
                CodexModel("gemini-4-argon", "Gemini 4 Argon", "Mô hình tiền phong DeepMind Frontier thế hệ 4", engine, badge = "Unavailable", isAvailable = false),
                CodexModel("gemini-2.5-pro", "Gemini 2.5 Pro (DeepMind)", "Model mạnh nhất của Google, ngữ cảnh 2M tokens", engine, badge = "Reasoning"),
                CodexModel("gemini-2.5-flash", "Gemini 2.5 Flash", "Cân bằng tốc độ và trí tuệ nhân tạo thế hệ mới", engine, badge = "Fast"),
                CodexModel("gemini-2.0-flash-thinking", "Gemini 2.0 Thinking", "Suy luận từng bước sâu sắc và chi tiết", engine, badge = "Thinking"),
                CodexModel("gemini-2.0-flash", "Gemini 2.0 Flash (Fast)", "Phản hồi tức thì, tối ưu thao tác mã nguồn", engine, badge = "Fast")
            )
            AiEngine.CLINE -> listOf(
                CodexModel("deepseek/deepseek-r1:free", "[FREE] DeepSeek R1 (Thinking)", "Mô hình suy luận mã nguồn mở miễn phí hàng đầu", engine, isFree = true, badge = "FREE", isDefault = true),
                CodexModel("deepseek/deepseek-chat:free", "[FREE] DeepSeek V3 (Fast)", "Mô hình tổng quát mã nguồn mở tốc độ cao miễn phí", engine, isFree = true, badge = "FREE"),
                CodexModel("meta-llama/llama-3.3-70b-instruct:free", "[FREE] Llama 3.3 70B (Meta)", "Mô hình 70B mã nguồn mở cao cấp của Meta (Free)", engine, isFree = true, badge = "FREE"),
                CodexModel("google/gemini-2.0-flash-exp:free", "[FREE] Gemini 2.0 Flash (Google)", "Google AI Studio miễn phí tốc độ cao thế hệ 2.0", engine, isFree = true, badge = "FREE"),
                CodexModel("google/gemini-2.0-flash-thinking-exp:free", "[FREE] Gemini 2.0 Thinking (Google)", "Google AI suy luận logic từng bước miễn phí", engine, isFree = true, badge = "FREE"),
                CodexModel("qwen/qwen-2.5-coder-32b-instruct:free", "[FREE] Qwen 2.5 Coder 32B", "Chuyên biệt lập trình Alibaba Cloud đa ngôn ngữ (Free)", engine, isFree = true, badge = "FREE"),
                CodexModel("mistralai/mistral-small-24b-instruct-2501:free", "[FREE] Mistral Small 24B", "Mô hình Châu Âu gọn nhẹ chính xác miễn phí", engine, isFree = true, badge = "FREE"),
                CodexModel("cognitivecomputations/dolphin3.0-r1-mistral-24b:free", "[FREE] Dolphin 3.0 R1 24B", "Chuyên sâu lý luận tự do nguồn mở (Free)", engine, isFree = true, badge = "FREE"),
                CodexModel("anthropic/claude-3.7-sonnet", "Claude 3.7 Sonnet (Hybrid)", "Đỉnh cao code & agentic reasoning từ Anthropic", engine, isFree = false, badge = "PRO"),
                CodexModel("anthropic/claude-3.5-sonnet", "Claude 3.5 Sonnet (Agentic Standard)", "Tiêu chuẩn vàng lập trình tự hành", engine, isFree = false, badge = "PRO"),
                CodexModel("openai/gpt-4o", "GPT-4o (Omni Frontier)", "Mô hình đa phương thức tiêu chuẩn quốc tế", engine, isFree = false, badge = "PRO"),
                CodexModel("openai/o3-mini", "o3-mini (High Reasoning)", "Mô hình suy luận toán học và logic code cao cấp", engine, isFree = false, badge = "PRO"),
                CodexModel("openai/gpt-4o-mini", "GPT-4o Mini (Ultra Fast)", "Tốc độ phản hồi cực nhanh, tối ưu tài nguyên", engine, isFree = false, badge = "PRO")
            )
        }
    }

    // -------------------------------------------------------------------------
    // Antigravity (Google DeepMind / Gemini) Stream Execution with Permission Mode
    // -------------------------------------------------------------------------
    suspend fun streamAntigravity(
        modelId: String,
        messages: List<ChatMessage>,
        newPrompt: String,
        permissionMode: PermissionMode = PermissionMode.FULL,
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

            // Autonomous Permission Instruction
            val permissionInstruction = when (permissionMode) {
                PermissionMode.FULL -> "CHẾ ĐỘ TỰ HÀNH TOÀN QUYỀN (FULL/YOLO): Bạn có toàn quyền thực thi mạnh mẽ nhất của Antigravity Agent. Tự động đề xuất sửa đổi mã nguồn, sinh file code hoàn chỉnh, lập kế hoạch chi tiết và giải quyết vấn đề triệt để mà không cần hỏi phê duyệt các bước trung gian."
                PermissionMode.AUTO -> "CHẾ ĐỘ TỰ ĐỘNG CÂN BẰNG (AUTO): Tự động phân tích, đọc hiểu và xuất giải pháp hoàn chỉnh. Nhấn mạnh nếu có thay đổi phá hủy."
                PermissionMode.ASK -> "CHẾ ĐỘ HỎI XÁC NHẬN (ASK): Trình bày kế hoạch chi tiết từng bước và đề xuất phương án trước khi người dùng đồng ý thực hiện."
            }

            val payload = JSONObject().apply {
                put("contents", contents)
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", "You are Antigravity, Google DeepMind's premier autonomous AI coding and research assistant. $permissionInstruction Maintain conversation continuity and provide production-ready solutions.")
                )))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("maxOutputTokens", 8192)
                })
            }

            // Fallback for frontier proxy IDs to ensure clean API resolution
            val effectiveModelId = when (modelId) {
                "gemini-3.8-flash-high" -> "gemini-2.0-flash"
                "claude-sonnet-4-6-thinking" -> "gemini-2.0-flash-thinking-exp-01-21"
                "claude-opus-4-6-thinking" -> "gemini-2.5-pro"
                "gpt-oss-120b-medium" -> "gemini-2.5-flash"
                "gemini-4-argon" -> "gemini-2.5-pro"
                else -> modelId
            }

            val url = if (apiKey.isNotBlank()) {
                "https://generativelanguage.googleapis.com/v1beta/models/$effectiveModelId:streamGenerateContent?key=$apiKey&alt=sse"
            } else {
                "https://generativelanguage.googleapis.com/v1beta/models/$effectiveModelId:streamGenerateContent?alt=sse"
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
        permissionMode: PermissionMode = PermissionMode.FULL,
        onChunk: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val apiKey = tokenStore.getClineApiKey()
            val endpoint = tokenStore.getClineEndpoint().trimEnd('/') + "/chat/completions"

            val msgArray = JSONArray()

            val permissionInstruction = when (permissionMode) {
                PermissionMode.FULL -> "CHẾ ĐỘ TỰ HÀNH TOÀN QUYỀN (FULL/YOLO): Bạn có toàn quyền tự hành lập kế hoạch, chỉnh sửa file, thực thi lệnh và giải quyết trọn vẹn nhiệm vụ mà không cần dừng lại xin xác nhận."
                PermissionMode.AUTO -> "CHẾ ĐỘ TỰ ĐỘNG CÂN BẰNG (AUTO): Tự động thực hiện các thao tác viết mã và phân tích."
                PermissionMode.ASK -> "CHẾ ĐỘ HỎI XÁC NHẬN (ASK): Luôn phác thảo các thao tác can thiệp hệ thống và hỏi ý kiến trước khi tiến hành."
            }

            // System prompt for Cline Agentic behavior
            msgArray.put(
                JSONObject()
                    .put("role", "system")
                    .put("content", "You are Cline Mobile Assistant, an expert autonomous software engineer. $permissionInstruction Maintain context across conversations and write clean, production-ready code.")
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
