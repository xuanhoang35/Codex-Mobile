package vn.intsoft.codexmobile.ui

import vn.intsoft.codexmobile.data.*

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val isStreaming: Boolean = false,
    val attachments: List<ImageAttachment> = emptyList(),
    val engine: AiEngine = AiEngine.CODEX
)

data class ImageAttachment(
    val uri: String,
    val base64Data: String? = null,
    val mimeType: String = "image/jpeg"
)

data class QueuedTurn(
    val id: String,
    val text: String
)

data class ActivityLogEntry(
    val id: String,
    val timestamp: String,
    val summary: String,
    val details: String = ""
)

data class PendingApproval(
    val id: String,
    val command: String,
    val reason: String = ""
)

data class MarkdownPart(
    val text: String,
    val language: String? = null
)

data class ExcerptReply(
    val text: String
)

data class CodexUiState(
    val isConnected: Boolean = false,
    val isInitialized: Boolean = false,
    val connectionStatus: String = "Sẵn sàng",
    val lastErrorMessage: String? = null,
    val endpoint: String = "wss://intsoft-codebank-vn.tailb240ce.ts.net:8443/",
    val workspace: String = "/srv/codex-workspaces",
    val activeEngine: AiEngine = AiEngine.CODEX,
    val antigravityUser: GoogleUserProfile? = null,
    val clineUser: GoogleUserProfile? = null,
    val googleUser: GoogleUserProfile? = null,
    val antigravityApiKey: String = "",
    val clineApiKey: String = "",
    val clineCustomEndpoint: String = "https://openrouter.ai/api/v1",
    val threads: List<ThreadSummary> = emptyList(),
    val archivedThreads: List<ThreadSummary> = emptyList(),
    val activeThreadId: String? = null,
    val activeThreadTitle: String = "Cuộc trò chuyện mới",
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isResponding: Boolean = false,
    val streamingResponse: String = "",
    val availableModels: List<CodexModel> = emptyList(),
    val selectedModelId: String = "gpt-4o",
    val selectedEffort: String = "medium",
    val permissionMode: PermissionMode = PermissionMode.ASK,
    val attachments: List<ImageAttachment> = emptyList(),
    val excerptReply: ExcerptReply? = null,
    val pendingApproval: PendingApproval? = null,
    val queuedTurns: List<QueuedTurn> = emptyList(),
    val snippets: List<SnippetEntry> = emptyList(),
    val currentSnippetGroupId: String? = null,
    val activityLogs: List<ActivityLogEntry> = emptyList(),
    val usage: UsageSnapshot? = null,
    val account: CodexAccount? = null,
    val loginChallenge: AccountLoginChallenge? = null,
    val showHistory: Boolean = false,
    val historySearchQuery: String = "",
    val archiveSearchQuery: String = "",
    val isLoadingArchive: Boolean = false,
    val completionNotificationsEnabled: Boolean = true
)
