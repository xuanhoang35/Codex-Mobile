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
    val description: String = "",
    val engine: AiEngine = AiEngine.CODEX
)

data class CodexAccount(
    val id: String,
    val email: String,
    val planType: String,
    val provider: String = "openai"
)

data class HistoryMessage(
    val role: String,
    val content: String,
    val engine: String = "codex"
)

data class ThreadSummary(
    val id: String,
    val title: String,
    val snippet: String,
    val updatedAt: Long,
    val isArchived: Boolean = false,
    val engine: String = "codex"
)

data class UsageWindow(
    val usedPercent: Float,
    val resetsInHours: Int? = null
)

data class UsageSnapshot(
    val primary: UsageWindow? = null,
    val secondary: UsageWindow? = null
)

data class AccountLoginChallenge(
    val userCode: String,
    val verificationUri: String
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
