package vn.intsoft.codexmobile.ui

import android.app.Application
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

    private val _uiState = MutableStateFlow(CodexUiState())
    val uiState: StateFlow<CodexUiState> = _uiState.asStateFlow()

    private val webSocketClient = CodexWebSocketClient(
        tokenProvider = { tokenStore.getToken() },
        listener = object : CodexWebSocketClient.Listener {
            override fun onConnecting() {
                _uiState.update { it.copy(connectionStatus = "Đang kết nối...") }
            }

            override fun onConnected() {
                _uiState.update {
                    it.copy(
                        isConnected = true,
                        isInitialized = true,
                        connectionStatus = "Đã kết nối",
                        lastErrorMessage = null
                    )
                }
                refreshThreads()
            }

            override fun onDisconnected(reason: String) {
                _uiState.update {
                    it.copy(
                        isConnected = false,
                        connectionStatus = "Mất kết nối: $reason"
                    )
                }
            }

            override fun onError(error: String) {
                _uiState.update {
                    it.copy(
                        lastErrorMessage = error,
                        connectionStatus = "Lỗi kết nối"
                    )
                }
            }

            override fun onMessageChunk(chunk: String) {
                _uiState.update {
                    it.copy(
                        isResponding = true,
                        streamingResponse = it.streamingResponse + chunk
                    )
                }
            }

            override fun onMessageComplete(fullText: String) {
                val currentStreaming = _uiState.value.streamingResponse
                val finalText = fullText.ifBlank { currentStreaming }
                if (finalText.isNotBlank()) {
                    val assistantMsg = ChatMessage(
                        id = UUID.randomUUID().toString(),
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

            override fun onApprovalRequired(id: String, command: String, reason: String) {
                _uiState.update {
                    it.copy(pendingApproval = PendingApproval(id, command, reason))
                }
            }

            override fun onUsageUpdated(usage: UsageSnapshot) {
                _uiState.update { it.copy(usage = usage) }
            }

            override fun onAccountUpdated(account: CodexAccount?) {
                _uiState.update { it.copy(account = account) }
            }

            override fun onLoginChallenge(challenge: AccountLoginChallenge?) {
                _uiState.update { it.copy(loginChallenge = challenge) }
            }

            override fun onThreadsUpdated(threads: List<ThreadSummary>) {
                _uiState.update { it.copy(threads = threads) }
            }

            override fun onArchivedThreadsUpdated(threads: List<ThreadSummary>) {
                _uiState.update { it.copy(archivedThreads = threads, isLoadingArchive = false) }
            }

            override fun onHistoryMessagesLoaded(messages: List<ChatMessage>) {
                _uiState.update { it.copy(messages = messages) }
            }
        }
    )

    init {
        loadPersistedState()
        connect()
    }

    private fun loadPersistedState() {
        val initialEngine = tokenStore.getActiveEngine()
        val googleUser = tokenStore.getGoogleUser()
        val antigravityKey = tokenStore.getAntigravityApiKey()
        val clineKey = tokenStore.getClineApiKey()
        val clineEndpoint = tokenStore.getClineEndpoint()

        val models = multiEngineClient.getDefaultModelsForEngine(initialEngine)

        _uiState.update {
            it.copy(
                activeEngine = initialEngine,
                googleUser = googleUser,
                antigravityApiKey = antigravityKey,
                clineApiKey = clineKey,
                clineCustomEndpoint = clineEndpoint,
                availableModels = models,
                selectedModelId = initialEngine.defaultModel,
                queuedTurns = queueStore.getAll().map { r -> QueuedTurn(r.id, r.text) },
                snippets = snippetStore.getAll()
            )
        }
    }

    // -------------------------------------------------------------------------
    // Multi-Engine Selection & Google Sign-In
    // -------------------------------------------------------------------------
    fun selectEngine(engine: AiEngine) {
        tokenStore.saveActiveEngine(engine)
        val models = multiEngineClient.getDefaultModelsForEngine(engine)
        _uiState.update {
            it.copy(
                activeEngine = engine,
                availableModels = models,
                selectedModelId = engine.defaultModel,
                connectionStatus = when (engine) {
                    AiEngine.CODEX -> if (it.isConnected) "Đã kết nối (Codex)" else "Mất kết nối server"
                    AiEngine.ANTIGRAVITY -> "Sẵn sàng (Google Gemini / DeepMind)"
                    AiEngine.CLINE -> "Sẵn sàng (Universal Agent / Claude)"
                }
            )
        }
    }

    fun signInWithGoogle(email: String, displayName: String, token: String?) {
        val user = GoogleUserProfile(
            email = email,
            displayName = displayName,
            idToken = token,
            accessToken = token
        )
        tokenStore.saveGoogleUser(user)
        _uiState.update { it.copy(googleUser = user) }
    }

    fun signOutGoogle() {
        tokenStore.saveGoogleUser(null)
        _uiState.update { it.copy(googleUser = null) }
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
        viewModelScope.launch {
            val state = _uiState.value
            webSocketClient.connect(state.endpoint, state.workspace)
        }
    }

    fun disconnect() {
        webSocketClient.disconnect()
    }

    fun saveSettings(endpoint: String, workspace: String, token: String) {
        if (token.isNotBlank()) {
            tokenStore.saveToken(token)
        }
        val cleanEndpoint = endpoint.trim()
        val cleanWorkspace = workspace.trim()

        _uiState.update {
            it.copy(
                endpoint = cleanEndpoint,
                workspace = cleanWorkspace
            )
        }

        viewModelScope.launch {
            webSocketClient.disconnect()
            webSocketClient.connect(cleanEndpoint, cleanWorkspace)
        }
    }

    fun clearToken() {
        tokenStore.clearToken()
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
                // Google DeepMind / Gemini execution
                viewModelScope.launch {
                    multiEngineClient.streamAntigravity(
                        modelId = state.selectedModelId,
                        messages = state.messages,
                        newPrompt = text,
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
                            _uiState.update {
                                it.copy(
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
                // Cline Universal Agent / Claude / DeepSeek
                viewModelScope.launch {
                    multiEngineClient.streamCline(
                        modelId = state.selectedModelId,
                        messages = state.messages,
                        newPrompt = text,
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
                            _uiState.update {
                                it.copy(
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
        webSocketClient.startAccountLogin()
    }

    fun cancelAccountLogin() {
        _uiState.update { it.copy(loginChallenge = null) }
    }

    fun logoutAccount() {
        webSocketClient.logoutAccount()
    }

    fun refreshAccount() {
        webSocketClient.refreshAccount()
    }

    fun refreshUsage() {
        webSocketClient.refreshUsage()
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
        webSocketClient.disconnect()
    }
}
