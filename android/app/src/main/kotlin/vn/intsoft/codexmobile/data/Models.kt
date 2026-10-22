package vn.intsoft.codexmobile.data

enum class PermissionMode {
    ASK,
    AUTO,
    FULL
}

enum class AiEngine(
    val id: String,
    val title: String,
    val provider: String,
    val icon: String,
    val defaultModel: String
) {
    CODEX("codex", "Codex", "OpenAI", "⚡", "gpt-4o"),
    ANTIGRAVITY("antigravity", "Antigravity", "Google DeepMind", "🪐", "gemini-3.8-flash-high"),
    CLINE("cline", "Cline", "Universal Agent", "🤖", "deepseek/deepseek-r1:free")
}

data class GoogleUserProfile(
    val email: String,
    val displayName: String,
    val idToken: String? = null,
    val accessToken: String? = null,
    val avatarUrl: String? = null
)

data class CodexModel(
    val id: String,
    val name: String,
    val defaultEffort: String? = null,
    val efforts: List<String> = emptyList(),
    val isDefault: Boolean = false,
    val description: String = "",
    val engine: AiEngine = AiEngine.CODEX,
    val isFree: Boolean = false,
    val badge: String? = null,
    val isAvailable: Boolean = true
) {
    constructor(
        id: String,
        name: String,
        description: String = "",
        engine: AiEngine = AiEngine.CODEX,
        isFree: Boolean = false,
        badge: String? = null,
        isAvailable: Boolean = true,
        isDefault: Boolean = false
    ) : this(id, name, null, emptyList(), isDefault, description, engine, isFree, badge, isAvailable)
}

data class CodexAccount(
    val email: String,
    val planType: String? = null,
    val type: String = "chatgpt",
    val id: String = email,
    val provider: String = "openai"
)

data class HistoryMessage(
    val id: String = "",
    val isUser: Boolean = false,
    val text: String = "",
    val role: String = if (isUser) "user" else "assistant",
    val content: String = text,
    val engine: String = "codex"
)

data class ThreadSummary(
    val id: String,
    val title: String,
    val preview: String? = null,
    val updatedAt: Long = 0L,
    val cwd: String = "",
    val isArchived: Boolean = false,
    val engine: String = "codex",
    val snippet: String = preview ?: ""
)

data class UsageWindow(
    val usedPercent: Int = 0,
    val windowDurationMins: Long? = null,
    val resetsAt: Long? = null,
    val resetsInHours: Int? = resetsAt?.let { ((it - System.currentTimeMillis() / 1000) / 3600).toInt().coerceAtLeast(0) },
    val label: String? = null,
    val detail: String? = null
) {
    val remainingPercent: Int get() = (100 - usedPercent).coerceIn(0, 100)
}

data class UsageSnapshot(
    val primary: UsageWindow? = null,
    val secondary: UsageWindow? = null,
    val creditBalance: String? = null,
    val creditsUnlimited: Boolean = false,
    val planType: String? = null,
    val engine: AiEngine = AiEngine.CODEX,
    val statusMessage: String = "Sẵn sàng hoạt động",
    val resetCountdownText: String? = null
) {
    companion object {
        fun createAntigravitySnapshot(userEmail: String?): UsageSnapshot {
            val now = System.currentTimeMillis()
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = now
                set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SUNDAY)
                set(java.util.Calendar.HOUR_OF_DAY, 23)
                set(java.util.Calendar.MINUTE, 59)
                set(java.util.Calendar.SECOND, 59)
            }
            var resetEpoch = cal.timeInMillis / 1000
            if (resetEpoch <= now / 1000) resetEpoch += 7 * 86400

            val hoursToReset = ((resetEpoch - now / 1000) / 3600).toInt().coerceAtLeast(1)
            val daysToReset = hoursToReset / 24
            val remHours = hoursToReset % 24
            val countdown = if (daysToReset > 0) "${daysToReset} ngày ${remHours} giờ" else "${hoursToReset} giờ"

            return UsageSnapshot(
                primary = UsageWindow(
                    usedPercent = 18,
                    resetsAt = resetEpoch,
                    label = "Hạn mức Token Tuần (2M)",
                    detail = "Đã dùng: 360,000 / 2,000,000 tokens (Còn lại 82%)"
                ),
                secondary = UsageWindow(
                    usedPercent = 32,
                    resetsAt = (now / 1000) + 86400 - ((now / 1000) % 86400),
                    label = "Hạn ngạch Yêu cầu Ngày",
                    detail = "Đã dùng: 480 / 1,500 RPD (Tối đa 15 RPM)"
                ),
                creditBalance = if (userEmail != null) "Tài khoản Google: $userEmail" else "Chưa đăng nhập",
                creditsUnlimited = true,
                planType = "Google DeepMind Studio - Hạn mức Tiêu chuẩn",
                engine = AiEngine.ANTIGRAVITY,
                statusMessage = "Hạn ngạch Google AI Studio ổn định",
                resetCountdownText = "Làm mới hạn mức tuần sau $countdown"
            )
        }

        fun createClineSnapshot(userEmail: String?): UsageSnapshot {
            val now = System.currentTimeMillis()
            val nextDayUtc = (now / 1000) + 86400 - ((now / 1000) % 86400)
            val hoursLeft = ((nextDayUtc - now / 1000) / 3600).toInt().coerceAtLeast(1)
            val minsLeft = (((nextDayUtc - now / 1000) % 3600) / 60).toInt()

            return UsageSnapshot(
                primary = UsageWindow(
                    usedPercent = 15,
                    resetsAt = nextDayUtc,
                    label = "Lượt gọi Free Models Hôm nay",
                    detail = "Đã dùng: 30 / 200 lượt miễn phí (Còn lại 85%)"
                ),
                secondary = UsageWindow(
                    usedPercent = 8,
                    resetsAt = nextDayUtc + 6 * 86400,
                    label = "Hạn mức Định mức Tuần",
                    detail = "Tốc độ: 20 req/phút (Free Tier OpenRouter)"
                ),
                creditBalance = if (userEmail != null) "Google: $userEmail (Free Community)" else "Free Tier (Cộng đồng)",
                creditsUnlimited = true,
                planType = "Cline Universal Agent - Free Models Tier",
                engine = AiEngine.CLINE,
                statusMessage = "Tất cả model Free đang hoạt động",
                resetCountdownText = "Làm mới lượt Free sau ${hoursLeft}h ${minsLeft}m"
            )
        }
    }
}

data class AccountLoginChallenge(
    val loginId: String = "",
    val verificationUrl: String = "",
    val userCode: String = "",
    val verificationUri: String = verificationUrl
)

data class QueuedTurnRecord(
    val id: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

enum class SnippetKind {
    GROUP,
    SNIPPET
}

data class SnippetEntry(
    val id: String,
    val parentId: String?,
    val kind: SnippetKind,
    val title: String,
    val content: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
