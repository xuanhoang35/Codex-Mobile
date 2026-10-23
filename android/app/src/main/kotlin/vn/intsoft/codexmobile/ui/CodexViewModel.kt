package vn.intsoft.codexmobile.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.intsoft.codexmobile.data.*
import java.io.InputStream
import java.util.UUID

class CodexViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStore = SecureTokenStore(application)
    private val queueStore = QueueStore(application)
    private val snippetStore = SnippetStore(application)
    private val multiEngineClient = MultiEngineClient(tokenStore)
    private var oauthServer: OAuthCallbackServer? = null

    private val _uiState = MutableStateFlow(CodexUiState())
    val uiState: StateFlow<CodexUiState> = _uiState.asStateFlow()

    private val webSocketClient = CodexWebSocketClient(
        tokenProvider = { tokenStore.getToken() ?: "" },
        listener = object : CodexWebSocketClient.Listener {
            override fun onConnectionChanged(status: String, connected: Boolean) {
                _uiState.update {
                    it.copy(
                        isConnected = connected,
                        connectionStatus = status,
                        lastErrorMessage = if (connected) null else it.lastErrorMessage
                    )
                }
            }

            override fun onInitialized() {
                _uiState.update {
                    it.copy(
                        isInitialized = true,
                        isConnected = true,
                        connectionStatus = "Đã kết nối"
                    )
                }
                refreshThreads()
            }

            override fun onModelsLoaded(models: List<CodexModel>) {
                _uiState.update { state ->
                    val cur = state.selectedModelId
                    val def = models.firstOrNull { it.isDefault }?.id ?: models.firstOrNull()?.id ?: cur
                    state.copy(
                        availableModels = models,
                        selectedModelId = if (cur.isBlank()) def else cur
                    )
                }
            }

            override fun onThreadsLoaded(threads: List<ThreadSummary>, archived: Boolean, searchTerm: String) {
                _uiState.update {
                    if (archived) {
                        it.copy(archivedThreads = threads, isLoadingArchive = false)
                    } else {
                        it.copy(threads = threads)
                    }
                }
            }

            override fun onThreadLibraryChanged() {
                refreshThreads()
            }

            override fun onUsageLoaded(usage: UsageSnapshot) {
                _uiState.update { it.copy(usage = usage) }
            }

            override fun onAccountLoaded(account: CodexAccount) {
                _uiState.update { it.copy(account = account) }
            }

            override fun onAccountLoginChallenge(challenge: AccountLoginChallenge) {
                _uiState.update { it.copy(loginChallenge = challenge) }
            }

            override fun onAccountLoginCompleted(success: Boolean, error: String?) {
                _uiState.update {
                    it.copy(
                        loginChallenge = null,
                        lastErrorMessage = if (!success) error ?: "Đăng nhập thất bại" else null
                    )
                }
            }

            override fun onAccountLogoutCompleted() {
                _uiState.update { it.copy(account = null) }
            }

            override fun onThreadReady(threadId: String, title: String, model: String?, messages: List<HistoryMessage>) {
                _uiState.update { state ->
                    state.copy(
                        activeThreadId = threadId,
                        activeThreadTitle = title,
                        messages = messages.map { h ->
                            ChatMessage(
                                id = h.id.ifBlank { UUID.randomUUID().toString() },
                                role = if (h.isUser) MessageRole.USER else MessageRole.ASSISTANT,
                                content = h.text,
                                engine = AiEngine.CODEX
                            )
                        },
                        isResponding = false,
                        streamingResponse = ""
                    )
                }
            }

            override fun onAgentDelta(itemId: String, delta: String) {
                _uiState.update {
                    it.copy(
                        isResponding = true,
                        streamingResponse = it.streamingResponse + delta
                    )
                }
            }

            override fun onAgentCompleted(itemId: String, text: String) {
                val current = _uiState.value.streamingResponse
                val finalText = text.ifBlank { current }
                if (finalText.isNotBlank()) {
                    val assistantMsg = ChatMessage(
                        id = itemId.ifBlank { UUID.randomUUID().toString() },
                        role = MessageRole.ASSISTANT,
                        content = finalText,
                        engine = _uiState.value.activeEngine
                    )
                    _uiState.update {
                        it.copy(
                            messages = it.messages + assistantMsg,
                            isResponding = false,
                            streamingResponse = ""
                        )
                    }
                } else {
                    _uiState.update { it.copy(isResponding = false, streamingResponse = "") }
                }
            }

            override fun onTurnStarted(turnId: String) {
                _uiState.update { it.copy(isResponding = true) }
            }

            override fun onTurnCompleted() {
                _uiState.update { it.copy(isResponding = false) }
            }

            override fun onActivity(status: String) {
                _uiState.update { it.copy(connectionStatus = status) }
            }

            override fun onApprovalRequested(approval: CodexWebSocketClient.ApprovalRequest) {
                _uiState.update {
                    it.copy(
                        pendingApproval = PendingApproval(
                            id = approval.requestId.toString(),
                            command = approval.title,
                            reason = approval.details
                        )
                    )
                }
            }

            override fun onApprovalResolved(requestId: Any) {
                _uiState.update { it.copy(pendingApproval = null) }
            }

            override fun onError(message: String) {
                _uiState.update {
                    it.copy(
                        lastErrorMessage = message,
                        connectionStatus = "Lỗi kết nối: $message"
                    )
                }
            }
        }
    )

    init {
        loadPersistedState()
        connect()
    }

    private fun loadPersistedState() {
        val initialEngine = tokenStore.getActiveEngine()
        val antigravityUser = tokenStore.getAntigravityGoogleUser()
        val clineUser = tokenStore.getClineGoogleUser()
        val antigravityKey = tokenStore.getAntigravityApiKey()
        val clineKey = tokenStore.getClineApiKey()
        val clineEndpoint = tokenStore.getClineEndpoint()
        val savedEndpoint = tokenStore.getCodexEndpoint()
        val savedWorkspace = tokenStore.getCodexWorkspace()
        val hasCodexToken = !tokenStore.getToken().isNullOrBlank()

        webSocketClient.endpoint = savedEndpoint
        webSocketClient.workspace = savedWorkspace
        webSocketClient.token = tokenStore.getToken() ?: ""

        val models = multiEngineClient.getDefaultModelsForEngine(initialEngine)
        val initialUsage = when (initialEngine) {
            AiEngine.ANTIGRAVITY -> UsageSnapshot.createAntigravitySnapshot(antigravityUser?.email)
            AiEngine.CLINE -> UsageSnapshot.createClineSnapshot(clineUser?.email)
            AiEngine.CODEX -> null
        }

        _uiState.update {
            it.copy(
                activeEngine = initialEngine,
                antigravityUser = antigravityUser,
                clineUser = clineUser,
                googleUser = if (initialEngine == AiEngine.CLINE) clineUser else antigravityUser,
                antigravityApiKey = antigravityKey,
                clineApiKey = clineKey,
                clineCustomEndpoint = clineEndpoint,
                endpoint = savedEndpoint,
                workspace = savedWorkspace,
                hasToken = hasCodexToken,
                availableModels = models,
                selectedModelId = initialEngine.defaultModel,
                usage = initialUsage,
                queuedTurns = queueStore.getAll().map { r -> QueuedTurn(r.id, r.text) },
                snippets = snippetStore.getAll()
            )
        }
    }

    // -------------------------------------------------------------------------
    // Multi-Engine Selection & Google Sign-In (Antigravity & Cline riêng biệt)
    // -------------------------------------------------------------------------
    fun selectEngine(engine: AiEngine) {
        tokenStore.saveActiveEngine(engine)
        val models = multiEngineClient.getDefaultModelsForEngine(engine)
        val engineUsage = when (engine) {
            AiEngine.ANTIGRAVITY -> UsageSnapshot.createAntigravitySnapshot(_uiState.value.antigravityUser?.email)
            AiEngine.CLINE -> UsageSnapshot.createClineSnapshot(_uiState.value.clineUser?.email)
            AiEngine.CODEX -> _uiState.value.usage
        }
        _uiState.update {
            val userForEngine = when (engine) {
                AiEngine.CODEX -> null
                AiEngine.ANTIGRAVITY -> it.antigravityUser
                AiEngine.CLINE -> it.clineUser
            }
            it.copy(
                activeEngine = engine,
                googleUser = userForEngine,
                availableModels = models,
                selectedModelId = engine.defaultModel,
                usage = engineUsage,
                connectionStatus = when (engine) {
                    AiEngine.CODEX -> when {
                        !it.isConnected -> "Mất kết nối tới server"
                        it.account == null -> "Chưa đăng nhập"
                        else -> "Sẵn sàng"
                    }
                    AiEngine.ANTIGRAVITY -> if (it.antigravityUser != null || it.antigravityApiKey.isNotBlank()) "Sẵn sàng" else "Chưa đăng nhập"
                    AiEngine.CLINE -> if (it.clineUser != null || it.clineApiKey.isNotBlank()) "Sẵn sàng" else "Chưa đăng nhập"
                }
            )
        }
    }

    fun refreshUsage() {
        when (_uiState.value.activeEngine) {
            AiEngine.CODEX -> {
                if (_uiState.value.isConnected) {
                    webSocketClient.refreshUsage()
                }
            }
            AiEngine.ANTIGRAVITY -> {
                _uiState.update {
                    it.copy(usage = UsageSnapshot.createAntigravitySnapshot(it.antigravityUser?.email))
                }
            }
            AiEngine.CLINE -> {
                _uiState.update {
                    it.copy(usage = UsageSnapshot.createClineSnapshot(it.clineUser?.email))
                }
            }
        }
    }

    fun openAntigravityBrowserLogin(context: Context) {
        try {
            val url = "https://aistudio.google.com/api-keys"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
            val fallback = "https://accounts.google.com/signin"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fallback)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun openClineBrowserLogin(context: Context) {
        try {
            val url = "https://openrouter.ai/keys"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
        }
    }

    fun signInAntigravityGoogle(email: String, displayName: String, apiKeyOrToken: String = "") {
        val cleanEmail = email.trim()
        val user = GoogleUserProfile(
            email = cleanEmail.ifBlank { "google_user@gmail.com" },
            displayName = displayName.trim().ifBlank { cleanEmail.substringBefore("@") },
            accessToken = apiKeyOrToken.trim()
        )
        tokenStore.saveAntigravityGoogleUser(user)
        if (apiKeyOrToken.isNotBlank()) {
            tokenStore.saveAntigravityApiKey(apiKeyOrToken.trim())
        }
        _uiState.update {
            it.copy(
                antigravityUser = user,
                googleUser = if (it.activeEngine == AiEngine.ANTIGRAVITY) user else it.googleUser,
                antigravityApiKey = if (apiKeyOrToken.isNotBlank()) apiKeyOrToken.trim() else it.antigravityApiKey,
                connectionStatus = "Sẵn sàng"
            )
        }
    }

    fun signOutAntigravity() {
        tokenStore.saveAntigravityGoogleUser(null)
        tokenStore.saveAntigravityApiKey("")
        _uiState.update {
            it.copy(
                antigravityUser = null,
                antigravityApiKey = "",
                googleUser = if (it.activeEngine == AiEngine.ANTIGRAVITY) null else it.googleUser,
                connectionStatus = if (it.activeEngine == AiEngine.ANTIGRAVITY) "Chưa đăng nhập" else it.connectionStatus
            )
        }
    }

    fun signInClineGoogle(email: String, displayName: String, apiKeyOrToken: String = "") {
        val cleanEmail = email.trim()
        val user = GoogleUserProfile(
            email = cleanEmail.ifBlank { "cline_user@gmail.com" },
            displayName = displayName.trim().ifBlank { cleanEmail.substringBefore("@") },
            accessToken = apiKeyOrToken.trim()
        )
        tokenStore.saveClineGoogleUser(user)
        if (apiKeyOrToken.isNotBlank()) {
            tokenStore.saveClineApiKey(apiKeyOrToken.trim())
        }
        _uiState.update {
            it.copy(
                clineUser = user,
                googleUser = if (it.activeEngine == AiEngine.CLINE) user else it.googleUser,
                clineApiKey = if (apiKeyOrToken.isNotBlank()) apiKeyOrToken.trim() else it.clineApiKey,
                connectionStatus = "Sẵn sàng"
            )
        }
    }

    fun signOutCline() {
        tokenStore.saveClineGoogleUser(null)
        tokenStore.saveClineApiKey("")
        _uiState.update {
            it.copy(
                clineUser = null,
                clineApiKey = "",
                googleUser = if (it.activeEngine == AiEngine.CLINE) null else it.googleUser,
                connectionStatus = if (it.activeEngine == AiEngine.CLINE) "Chưa đăng nhập" else it.connectionStatus
            )
        }
    }

    fun handleAuthDeepLink(uri: Uri) {
        val engine = uri.getQueryParameter("engine")?.lowercase()
        val email = uri.getQueryParameter("email") ?: uri.getQueryParameter("user") ?: ""
        val token = uri.getQueryParameter("token") ?: uri.getQueryParameter("key") ?: ""
        val name = uri.getQueryParameter("name") ?: email.substringBefore("@")

        if (engine == "cline") {
            signInClineGoogle(email, name, token)
        } else {
            signInAntigravityGoogle(email, name, token)
        }
    }

    fun signInWithGoogle(email: String, displayName: String, token: String?) =
        signInAntigravityGoogle(email, displayName, token ?: "")

    fun signOutGoogle() {
        if (_uiState.value.activeEngine == AiEngine.CLINE) signOutCline() else signOutAntigravity()
    }

    fun saveEngineSettings(antigravityKey: String, clineKey: String, clineEndpoint: String) {
        tokenStore.saveAntigravityApiKey(antigravityKey)
        tokenStore.saveClineApiKey(clineKey)
        tokenStore.saveClineEndpoint(clineEndpoint)
        _uiState.update {
            it.copy(
                antigravityApiKey = antigravityKey,
                clineApiKey = clineKey,
                clineCustomEndpoint = clineEndpoint
            )
        }
    }

    // -------------------------------------------------------------------------
    // Network & Session
    // -------------------------------------------------------------------------
    fun connect() {
        if (_uiState.value.activeEngine != AiEngine.CODEX) return
        viewModelScope.launch {
            val state = _uiState.value
            webSocketClient.token = tokenStore.getToken() ?: ""
            webSocketClient.connect(state.endpoint, state.workspace)
        }
    }

    fun disconnect() {
        webSocketClient.disconnect()
    }

    fun saveSettings(endpoint: String, workspace: String, token: String) {
        val cleanEndpoint = endpoint.trim().let { ep ->
            when {
                ep.startsWith("http://", ignoreCase = true) -> "ws://" + ep.substring(7)
                ep.startsWith("https://", ignoreCase = true) -> "wss://" + ep.substring(8)
                !ep.startsWith("ws://", ignoreCase = true) && !ep.startsWith("wss://", ignoreCase = true) -> "wss://$ep"
                else -> ep
            }
        }
        val cleanWorkspace = workspace.trim()

        if (token.isNotBlank()) {
            tokenStore.saveToken(token.trim())
            webSocketClient.token = token.trim()
        }
        tokenStore.saveCodexEndpoint(cleanEndpoint)
        tokenStore.saveCodexWorkspace(cleanWorkspace)

        val hasCodexToken = !tokenStore.getToken().isNullOrBlank()

        _uiState.update {
            it.copy(
                endpoint = cleanEndpoint,
                workspace = cleanWorkspace,
                hasToken = hasCodexToken,
                connectionStatus = "Đang kết nối lại máy chủ…"
            )
        }

        viewModelScope.launch {
            webSocketClient.disconnect()
            kotlinx.coroutines.delay(150)
            webSocketClient.connect(cleanEndpoint, cleanWorkspace)
        }
    }

    fun clearToken() {
        tokenStore.clearToken()
        webSocketClient.token = ""
        _uiState.update { it.copy(hasToken = false) }
    }

    // -------------------------------------------------------------------------
    // Chat & Universal Prompt Dispatch
    // -------------------------------------------------------------------------
    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendCurrentInput() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isBlank() && state.attachments.isEmpty()) return

        // ---------------------------------------------------------------------
        // Authentication Guard (Giống ChatGPT: Bắt buộc đăng nhập trước khi chat)
        // ---------------------------------------------------------------------
        when (state.activeEngine) {
            AiEngine.ANTIGRAVITY -> {
                val isAuthed = state.antigravityUser != null || state.antigravityApiKey.isNotBlank()
                if (!isAuthed) {
                    _uiState.update {
                        it.copy(
                            lastErrorMessage = "Yêu cầu đăng nhập tài khoản Google (Gmail) để kích hoạt Antigravity trước khi trò chuyện."
                        )
                    }
                    return
                }
            }
            AiEngine.CLINE -> {
                val isAuthed = state.clineUser != null || state.clineApiKey.isNotBlank()
                if (!isAuthed) {
                    _uiState.update {
                        it.copy(
                            lastErrorMessage = "Yêu cầu đăng nhập tài khoản Google (Gmail) hoặc cấu hình API Key để kích hoạt Cline trước khi trò chuyện."
                        )
                    }
                    return
                }
            }
            AiEngine.CODEX -> {
                if (!state.isConnected && state.account == null) {
                    _uiState.update {
                        it.copy(
                            lastErrorMessage = "Vui lòng kết nối tài khoản Codex Server trước khi gửi câu lệnh."
                        )
                    }
                    connect()
                    return
                }
            }
        }

        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = text,
            attachments = state.attachments,
            engine = state.activeEngine
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                inputText = "",
                attachments = emptyList(),
                excerptReply = null,
                isResponding = true,
                streamingResponse = ""
            )
        }

        when (state.activeEngine) {
            AiEngine.CODEX -> {
                // OpenAI Codex execution via WebSocket
                if (!_uiState.value.isConnected) {
                    connect()
                }
                webSocketClient.sendPrompt(
                    prompt = text,
                    model = state.selectedModelId,
                    effort = state.selectedEffort,
                    permission = state.permissionMode
                )
            }
            AiEngine.ANTIGRAVITY -> {
                // Google DeepMind / Gemini execution with autonomous permission mode
                viewModelScope.launch {
                    multiEngineClient.streamAntigravity(
                        modelId = state.selectedModelId,
                        messages = state.messages,
                        newPrompt = text,
                        permissionMode = state.permissionMode,
                        onChunk = { chunk ->
                            _uiState.update {
                                it.copy(streamingResponse = it.streamingResponse + chunk)
                            }
                        },
                        onComplete = { fullText ->
                            val assistantMsg = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                role = MessageRole.ASSISTANT,
                                content = fullText,
                                engine = AiEngine.ANTIGRAVITY
                            )
                            _uiState.update {
                                it.copy(
                                    messages = it.messages + assistantMsg,
                                    isResponding = false,
                                    streamingResponse = ""
                                )
                            }
                        },
                        onError = { error ->
                            val errorMsg = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                role = MessageRole.ASSISTANT,
                                content = "⚠️ Không nhận được phản hồi:\n$error\n\nVui lòng thử lại hoặc chọn mô hình khác.",
                                engine = AiEngine.ANTIGRAVITY
                            )
                            _uiState.update {
                                it.copy(
                                    messages = it.messages + errorMsg,
                                    isResponding = false,
                                    streamingResponse = "",
                                    lastErrorMessage = error
                                )
                            }
                        }
                    )
                }
            }
            AiEngine.CLINE -> {
                // Cline Universal Agent with autonomous permission mode
                viewModelScope.launch {
                    multiEngineClient.streamCline(
                        modelId = state.selectedModelId,
                        messages = state.messages,
                        newPrompt = text,
                        permissionMode = state.permissionMode,
                        onChunk = { chunk ->
                            _uiState.update {
                                it.copy(streamingResponse = it.streamingResponse + chunk)
                            }
                        },
                        onComplete = { fullText ->
                            val assistantMsg = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                role = MessageRole.ASSISTANT,
                                content = fullText,
                                engine = AiEngine.CLINE
                            )
                            _uiState.update {
                                it.copy(
                                    messages = it.messages + assistantMsg,
                                    isResponding = false,
                                    streamingResponse = ""
                                )
                            }
                        },
                        onError = { error ->
                            val errorMsg = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                role = MessageRole.ASSISTANT,
                                content = "⚠️ Không nhận được phản hồi:\n$error\n\nVui lòng thử lại hoặc chọn mô hình khác.",
                                engine = AiEngine.CLINE
                            )
                            _uiState.update {
                                it.copy(
                                    messages = it.messages + errorMsg,
                                    isResponding = false,
                                    streamingResponse = "",
                                    lastErrorMessage = error
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    fun interrupt() {
        if (_uiState.value.activeEngine == AiEngine.CODEX) {
            webSocketClient.interrupt()
        }
        _uiState.update { it.copy(isResponding = false, streamingResponse = "") }
    }

    fun selectModel(modelId: String) {
        _uiState.update { it.copy(selectedModelId = modelId) }
    }

    fun selectEffort(effort: String) {
        _uiState.update { it.copy(selectedEffort = effort) }
    }

    fun selectPermissionMode(mode: PermissionMode) {
        _uiState.update { it.copy(permissionMode = mode) }
    }

    fun decideApproval(id: String, allow: Boolean) {
        webSocketClient.decideApproval(id, allow)
        _uiState.update { it.copy(pendingApproval = null) }
    }

    fun attachImage(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                val base64 = if (bytes != null) Base64.encodeToString(bytes, Base64.NO_WRAP) else null
                val attachment = ImageAttachment(uri.toString(), base64)
                _uiState.update { it.copy(attachments = it.attachments + attachment) }
            } catch (e: Exception) {
                // handle image read failure
            }
        }
    }

    fun removeAttachment(index: Int) {
        val current = _uiState.value.attachments.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _uiState.update { it.copy(attachments = current) }
        }
    }

    fun cancelExcerptReply() {
        _uiState.update { it.copy(excerptReply = null) }
    }

    // -------------------------------------------------------------------------
    // Threads & History
    // -------------------------------------------------------------------------
    fun toggleHistory() {
        _uiState.update { it.copy(showHistory = !it.showHistory) }
    }

    fun selectThread(threadId: String) {
        val target = _uiState.value.threads.firstOrNull { it.id == threadId }
        _uiState.update {
            it.copy(
                activeThreadId = threadId,
                activeThreadTitle = target?.title ?: "Cuộc trò chuyện",
                messages = emptyList()
            )
        }
        webSocketClient.loadThreadHistory(threadId)
    }

    fun startNewThread() {
        val newId = UUID.randomUUID().toString()
        _uiState.update {
            it.copy(
                activeThreadId = newId,
                activeThreadTitle = "Cuộc trò chuyện mới",
                messages = emptyList()
            )
        }
        webSocketClient.startNewThread(newId)
    }

    fun renameThread(threadId: String, newTitle: String) {
        webSocketClient.renameThread(threadId, newTitle)
        _uiState.update { state ->
            state.copy(
                threads = state.threads.map { if (it.id == threadId) it.copy(title = newTitle) else it },
                activeThreadTitle = if (state.activeThreadId == threadId) newTitle else state.activeThreadTitle
            )
        }
    }

    fun archiveThread(threadId: String) {
        webSocketClient.archiveThread(threadId)
        _uiState.update { state ->
            val archived = state.threads.firstOrNull { it.id == threadId }?.copy(isArchived = true)
            state.copy(
                threads = state.threads.filter { it.id != threadId },
                archivedThreads = if (archived != null) state.archivedThreads + archived else state.archivedThreads
            )
        }
    }

    fun unarchiveThread(threadId: String) {
        webSocketClient.unarchiveThread(threadId)
        _uiState.update { state ->
            val unarchived = state.archivedThreads.firstOrNull { it.id == threadId }?.copy(isArchived = false)
            state.copy(
                archivedThreads = state.archivedThreads.filter { it.id != threadId },
                threads = if (unarchived != null) state.threads + unarchived else state.threads
            )
        }
    }

    fun deleteThread(threadId: String) {
        webSocketClient.deleteThread(threadId)
        _uiState.update { state ->
            state.copy(
                threads = state.threads.filter { it.id != threadId },
                archivedThreads = state.archivedThreads.filter { it.id != threadId },
                messages = if (state.activeThreadId == threadId) emptyList() else state.messages,
                activeThreadId = if (state.activeThreadId == threadId) null else state.activeThreadId
            )
        }
    }

    fun refreshThreads() {
        if (!_uiState.value.isConnected) {
            connect()
        }
        webSocketClient.refreshThreads()
    }

    fun refreshArchivedThreads() {
        _uiState.update { it.copy(isLoadingArchive = true) }
        if (!_uiState.value.isConnected) {
            connect()
        }
        webSocketClient.refreshArchivedThreads()
    }

    fun updateHistorySearch(query: String) {
        _uiState.update { it.copy(historySearchQuery = query) }
    }

    fun updateArchiveSearch(query: String) {
        _uiState.update { it.copy(archiveSearchQuery = query) }
    }

    // -------------------------------------------------------------------------
    // Account & Usage
    // -------------------------------------------------------------------------
    fun startAccountLogin() {
        if (!_uiState.value.isConnected) {
            connect()
            _uiState.update { it.copy(connectionStatus = "Đang kết nối lại máy chủ Codex…") }
        }
        oauthServer?.stop()
        oauthServer = OAuthCallbackServer { code, state ->
            android.util.Log.d("CodexViewModel", "Captured OAuth code on 127.0.0.1:1455: $code")
            viewModelScope.launch {
                kotlinx.coroutines.delay(1200)
                webSocketClient.refreshAccount()
                webSocketClient.refreshModels()
                webSocketClient.refreshUsage()
            }
        }.also { it.start() }

        webSocketClient.startAccountLogin()
    }

    fun cancelAccountLogin() {
        oauthServer?.stop()
        _uiState.update { it.copy(loginChallenge = null) }
    }

    fun logoutAccount() {
        webSocketClient.logoutAccount()
    }

    fun refreshAccount() {
        webSocketClient.refreshAccount()
    }

    fun toggleCompletionNotifications(enabled: Boolean) {
        _uiState.update { it.copy(completionNotificationsEnabled = enabled) }
    }

    fun clearActivityLogs() {
        _uiState.update { it.copy(activityLogs = emptyList()) }
    }

    // -------------------------------------------------------------------------
    // Queue & Snippets
    // -------------------------------------------------------------------------
    fun editQueueTurn(id: String, newText: String) {
        queueStore.update(id, newText)
        _uiState.update {
            it.copy(queuedTurns = queueStore.getAll().map { r -> QueuedTurn(r.id, r.text) })
        }
    }

    fun deleteQueueTurn(id: String) {
        queueStore.delete(id)
        _uiState.update {
            it.copy(queuedTurns = queueStore.getAll().map { r -> QueuedTurn(r.id, r.text) })
        }
    }

    fun clearQueue() {
        queueStore.clear()
        _uiState.update { it.copy(queuedTurns = emptyList()) }
    }

    fun openSnippetGroup(groupId: String?) {
        _uiState.update { it.copy(currentSnippetGroupId = groupId) }
    }

    fun createSnippetGroup(parentId: String?, title: String) {
        snippetStore.createGroup(parentId, title)
        _uiState.update { it.copy(snippets = snippetStore.getAll()) }
    }

    fun createSnippet(groupId: String?, title: String, content: String) {
        snippetStore.createSnippet(groupId, title, content)
        _uiState.update { it.copy(snippets = snippetStore.getAll()) }
    }

    fun updateSnippet(entry: SnippetEntry, title: String, content: String) {
        snippetStore.update(entry.id, title, content)
        _uiState.update { it.copy(snippets = snippetStore.getAll()) }
    }

    fun deleteSnippet(entry: SnippetEntry) {
        snippetStore.delete(entry.id)
        _uiState.update { it.copy(snippets = snippetStore.getAll()) }
    }

    fun runSnippet(command: String) {
        _uiState.update { it.copy(inputText = command) }
        sendCurrentInput()
    }

    override fun onCleared() {
        super.onCleared()
        oauthServer?.stop()
        webSocketClient.disconnect()
    }

    companion object {
        fun factory(context: android.content.Context) = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return CodexViewModel(context.applicationContext as Application) as T
            }
        }
    }
}
