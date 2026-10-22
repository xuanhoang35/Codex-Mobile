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
    ANTIGRAVITY("antigravity", "Antigravity", "Google DeepMind", "🪐", "gemini-2.5-pro"),
    CLINE("cline", "Cline", "Universal Agent", "🤖", "claude-3-7-sonnet")
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
    val engine: AiEngine = AiEngine.CODEX
) {
    constructor(id: String, name: String, description: String = "", engine: AiEngine = AiEngine.CODEX) :
        this(id, name, null, emptyList(), false, description, engine)
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
    val resetsInHours: Int? = resetsAt?.let { ((it - System.currentTimeMillis() / 1000) / 3600).toInt().coerceAtLeast(0) }
)

data class UsageSnapshot(
    val primary: UsageWindow? = null,
    val secondary: UsageWindow? = null,
    val creditBalance: String? = null,
    val creditsUnlimited: Boolean = false,
    val planType: String? = null
)

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
