package vn.intsoft.codexmobile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.intsoft.codexmobile.data.*

val AppBackground = Color(4279769627L)
val PanelBackground = Color(4280295971L)
val InputBackground = Color(4279967006L)
val BorderColor = Color(4282006847L)
val PrimaryText = Color(4293454056L)
val SecondaryText = Color(4288059803L)
val CodexGreen = Color(4279280511L)
val WarningYellow = Color(4294951175L)
val DangerRed = Color(0xFFEF4444)
val GoogleBlue = Color(0xFF4285F4)
val AntigravityPurple = Color(0xFF8B5CF6)
val ClineOrange = Color(0xFFF97316)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodexApp(viewModel: CodexViewModel) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showSnippets by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showPermissionPicker by remember { mutableStateOf(false) }
    var showActivityLogs by remember { mutableStateOf(false) }
    var showUsageDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showGoogleLoginDialog by remember { mutableStateOf(false) }
    var renameTargetThread by remember { mutableStateOf<ThreadSummary?>(null) }
    var threadActionTarget by remember { mutableStateOf<ThreadSummary?>(null) }

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.attachImage(it) }
    }

    if (showSnippets) {
        Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
            TopAppBar(
                title = { Text("Thư viện Snippets", color = PrimaryText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    TextButton(onClick = { showSnippets = false }) {
                        Text("‹ Quay lại", color = SecondaryText, fontSize = 16.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
            SnippetLibraryScreen(
                entries = state.snippets,
                currentGroupId = state.currentSnippetGroupId,
                onOpenGroup = { group -> viewModel.openSnippetGroup(group?.id) },
                onCreateGroup = { parentId, title -> viewModel.createSnippetGroup(parentId, title) },
                onCreateSnippet = { groupId, title, content -> viewModel.createSnippet(groupId, title, content) },
                onUpdate = { entry, title, content -> viewModel.updateSnippet(entry, title, content) },
                onDelete = { entry -> viewModel.deleteSnippet(entry) },
                onRun = { command ->
                    viewModel.runSnippet(command)
                    showSnippets = false
                }
            )
        }
        return
    }

    Scaffold(
        topBar = {
            Column {
                AppTopBar(
                    state = state,
                    showSnippets = false,
                    snippetTitle = "",
                    onBack = {
                        if (state.showHistory) {
                            viewModel.toggleHistory()
                        }
                    },
                    onSnippets = { showSnippets = true },
                    onRefresh = { viewModel.connect() },
                    onUsage = { showUsageDialog = true },
                    onSettings = { showSettings = true }
                )
                // Multi-Engine Selector Row
                if (!state.showHistory) {
                    EngineSelectorBar(
                        activeEngine = state.activeEngine,
                        onSelectEngine = { viewModel.selectEngine(it) }
                    )
                }
            }
        },
        containerColor = AppBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(AppBackground)
        ) {
            if (state.showHistory) {
                HistoryScreen(
                    state = state,
                    onThreadClick = { thread ->
                        viewModel.selectThread(thread.id)
                        viewModel.toggleHistory()
                    },
                    onSearchChange = { viewModel.updateHistorySearch(it) },
                    onNewThread = {
                        viewModel.startNewThread()
                        viewModel.toggleHistory()
                    },
                    onRefresh = { viewModel.refreshThreads() },
                    onOpenArchive = { showArchiveDialog = true },
                    onActionClick = { thread -> threadActionTarget = thread }
                )
            } else if (state.activeEngine == AiEngine.CODEX && !state.isConnected && state.messages.isEmpty() && !state.isInitialized) {
                ConnectScreen(
                    state = state,
                    onConnect = { viewModel.connect() },
                    onOpenSettings = { showSettings = true }
                )
            } else {
                ChatScreen(
                    state = state,
                    onInputChanged = { viewModel.updateInput(it) },
                    onSend = { viewModel.sendCurrentInput() },
                    onStop = { viewModel.interrupt() },
                    onModelClick = { showModelPicker = true },
                    onPermissionClick = { showPermissionPicker = true },
                    onPickImage = { imagePickerLauncher.launch("image/*") },
                    onTakePhoto = { /* Camera contract */ },
                    onRemoveImage = { index -> viewModel.removeAttachment(index) },
                    onCancelExcerptReply = { viewModel.cancelExcerptReply() },
                    onApprovalDecision = { id, decision -> viewModel.decideApproval(id, decision) },
                    onEditQueueTurn = { id, text -> viewModel.editQueueTurn(id, text) },
                    onDeleteQueueTurn = { id -> viewModel.deleteQueueTurn(id) },
                    onClearQueue = { viewModel.clearQueue() },
                    onOpenDrawer = { viewModel.toggleHistory() }
                )
            }
        }
    }

    // Dialog: Settings
    if (showSettings) {
        SettingsDialog(
            state = state,
            onDismiss = { showSettings = false },
            onSave = { endpoint, workspace, token, antigravityKey, clineKey, clineEndpoint ->
                viewModel.saveSettings(endpoint, workspace, token)
                viewModel.saveEngineSettings(antigravityKey, clineKey, clineEndpoint)
                showSettings = false
            },
            onClearToken = { viewModel.clearToken() },
            onSwitchAccount = { viewModel.startAccountLogin() },
            onLogoutAccount = { viewModel.logoutAccount() },
            onRefreshAccount = { viewModel.refreshAccount() },
            onOpenGoogleLogin = { showGoogleLoginDialog = true },
            onSignOutGoogle = { viewModel.signOutGoogle() },
            onOpenArchive = {
                showSettings = false
                showArchiveDialog = true
            },
            onCompletionNotificationsChanged = { viewModel.toggleCompletionNotifications(it) },
            onOpenActivityLog = {
                showSettings = false
                showActivityLogs = true
            }
        )
    }

    // Dialog: Google Sign-In Simulation / Web Flow
    if (showGoogleLoginDialog) {
        GoogleLoginDialog(
            currentUser = state.googleUser,
            onSignIn = { email, name ->
                viewModel.signInWithGoogle(email, name, "oauth_token_google_${System.currentTimeMillis()}")
                showGoogleLoginDialog = false
            },
            onDismiss = { showGoogleLoginDialog = false }
        )
    }

    // Dialog: Model Picker
    if (showModelPicker) {
        ModelPickerDialog(
            models = state.availableModels,
            selectedModelId = state.selectedModelId,
            selectedEffort = state.selectedEffort,
            activeEngine = state.activeEngine,
            onSelectModel = { modelId ->
                viewModel.selectModel(modelId)
            },
            onSelectEffort = { effort ->
                viewModel.selectEffort(effort)
            },
            onDismiss = { showModelPicker = false }
        )
    }

    // Dialog: Permission Picker
    if (showPermissionPicker) {
        PermissionPickerDialog(
            selected = state.permissionMode,
            onSelect = { mode ->
                viewModel.selectPermissionMode(mode)
                showPermissionPicker = false
            },
            onDismiss = { showPermissionPicker = false }
        )
    }

    // Dialog: Activity Log
    if (showActivityLogs) {
        ActivityLogDialog(
            logs = state.activityLogs,
            onDismiss = { showActivityLogs = false },
            onClear = { viewModel.clearActivityLogs() }
        )
    }

    // Dialog: Usage
    if (showUsageDialog) {
        state.usage?.let { usage ->
            UsageDialog(
                usage = usage,
                onDismiss = { showUsageDialog = false },
                onRefresh = { viewModel.refreshUsage() }
            )
        }
    }

    // Dialog: Archive
    if (showArchiveDialog) {
        ArchiveDialog(
            archivedThreads = state.archivedThreads,
            isLoading = state.isLoadingArchive,
            search = state.archiveSearchQuery,
            onSearchChange = { viewModel.updateArchiveSearch(it) },
            onClose = { showArchiveDialog = false },
            onUnarchive = { thread -> viewModel.unarchiveThread(thread.id) },
            onDelete = { thread -> viewModel.deleteThread(thread.id) },
            onRefresh = { viewModel.refreshArchivedThreads() }
        )
    }

    // Dialog: Actions on Thread (Rename, Archive, Delete)
    threadActionTarget?.let { thread ->
        ThreadActionsDialog(
            thread = thread,
            archived = false,
            onRename = {
                renameTargetThread = thread
                threadActionTarget = null
            },
            onArchive = {
                viewModel.archiveThread(thread.id)
                threadActionTarget = null
            },
            onDelete = {
                viewModel.deleteThread(thread.id)
                threadActionTarget = null
            },
            onDismiss = { threadActionTarget = null }
        )
    }

    // Dialog: Rename Thread
    renameTargetThread?.let { thread ->
        RenameThreadDialog(
            thread = thread,
            onRename = { newTitle ->
                viewModel.renameThread(thread.id, newTitle)
                renameTargetThread = null
            },
            onDismiss = { renameTargetThread = null }
        )
    }

    // Dialog: Account Login Challenge
    state.loginChallenge?.let { challenge ->
        AccountLoginDialog(
            challenge = challenge,
            onDismiss = { viewModel.cancelAccountLogin() }
        )
    }
}

@Composable
fun EngineSelectorBar(
    activeEngine: AiEngine,
    onSelectEngine: (AiEngine) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBackground)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AiEngine.values().forEach { engine ->
            val isSelected = engine == activeEngine
            val activeColor = when (engine) {
                AiEngine.CODEX -> CodexGreen
                AiEngine.ANTIGRAVITY -> AntigravityPurple
                AiEngine.CLINE -> ClineOrange
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) activeColor.copy(alpha = 0.25f) else InputBackground)
                    .clickable { onSelectEngine(engine) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = engine.icon, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = engine.title,
                        color = if (isSelected) activeColor else SecondaryText,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    state: CodexUiState,
    showSnippets: Boolean,
    snippetTitle: String,
    onBack: () -> Unit,
    onSnippets: () -> Unit,
    onRefresh: () -> Unit,
    onUsage: () -> Unit,
    onSettings: () -> Unit
) {
    TopAppBar(
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                val title = when {
                    showSnippets -> snippetTitle
                    state.showHistory || !state.isInitialized -> "Chats"
                    else -> state.activeThreadTitle.ifBlank { "Cuộc trò chuyện mới" }
                }
                Text(
                    text = title,
                    color = PrimaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!showSnippets && !state.showHistory) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (statusDot, statusText) = when (state.activeEngine) {
                            AiEngine.CODEX -> Pair(
                                if (state.isConnected) CodexGreen else WarningYellow,
                                if (state.isConnected) "Codex Server" else "Mất kết nối server"
                            )
                            AiEngine.ANTIGRAVITY -> Pair(AntigravityPurple, "Antigravity (Google Gemini)")
                            AiEngine.CLINE -> Pair(ClineOrange, "Cline (Universal Agent)")
                        }

                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusDot)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            color = SecondaryText,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        },
        navigationIcon = {
            if (state.showHistory || showSnippets) {
                TextButton(onClick = onBack) {
                    Text(if (showSnippets) "‹" else "‹ Chats", color = SecondaryText, fontSize = 16.sp)
                }
            }
        },
        actions = {
            if (!showSnippets && state.isInitialized && state.activeEngine == AiEngine.CODEX) {
                state.usage?.primary?.let { usageWindow ->
                    TextButton(onClick = onUsage) {
                        Text(
                            text = "${(usageWindow.usedPercent).toInt()}%",
                            color = CodexGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            if (!showSnippets) {
                TextButton(onClick = onSnippets) {
                    Text("{ }", color = CodexGreen, fontSize = 17.sp, fontFamily = FontFamily.Monospace)
                }
            }
            // Quick Reconnect Button if disconnected on Codex engine
            if (!showSnippets && state.activeEngine == AiEngine.CODEX && !state.isConnected) {
                TextButton(onClick = onRefresh) {
                    Text("Kết nối lại", color = WarningYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            TextButton(onClick = onSettings) {
                Text("⚙", color = SecondaryText, fontSize = 20.sp)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
    )
}

@Composable
private fun ConnectScreen(
    state: CodexUiState,
    onConnect: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PanelBackground),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Codex Mobile",
                    color = PrimaryText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (state.connectionStatus.isNotBlank()) state.connectionStatus else "Chưa kết nối máy chủ",
                    color = SecondaryText,
                    fontSize = 14.sp
                )
                if (state.lastErrorMessage != null) {
                    Text(
                        text = state.lastErrorMessage,
                        color = DangerRed,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cài đặt", color = PrimaryText)
                    }
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CodexGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Kết nối", color = AppBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    state: CodexUiState,
    onThreadClick: (ThreadSummary) -> Unit,
    onSearchChange: (String) -> Unit,
    onNewThread: () -> Unit,
    onRefresh: () -> Unit,
    onOpenArchive: () -> Unit,
    onActionClick: (ThreadSummary) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.historySearchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Tìm kiếm cuộc trò chuyện...", color = SecondaryText, fontSize = 14.sp) },
                shape = RoundedCornerShape(20.dp),
                colors = darkFieldColors(),
                singleLine = true
            )
            Button(
                onClick = onNewThread,
                colors = ButtonDefaults.buttonColors(containerColor = CodexGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("+ Mới", color = AppBackground, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Danh sách cuộc trò chuyện", color = SecondaryText, fontSize = 13.sp)
            TextButton(onClick = onOpenArchive) {
                Text("Lưu trữ 📦", color = CodexGreen, fontSize = 13.sp)
            }
        }

        val filteredThreads = remember(state.threads, state.historySearchQuery) {
            state.threads.filter {
                state.historySearchQuery.isBlank() || it.title.contains(state.historySearchQuery.trim(), ignoreCase = true)
            }
        }

        if (filteredThreads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Không có cuộc trò chuyện nào", color = SecondaryText)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredThreads, key = { it.id }) { thread ->
                    ThreadListRow(
                        thread = thread,
                        isSelected = thread.id == state.activeThreadId,
                        onClick = { onThreadClick(thread) },
                        onAction = { onActionClick(thread) }
                    )
                }
            }
        }
    }
}

@Composable
fun ThreadListRow(
    thread: ThreadSummary,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) PanelBackground.copy(alpha = 0.9f) else PanelBackground
        ),
        border = if (isSelected) BorderStroke(1.dp, CodexGreen) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (thread.isArchived) "📦" else "💬", fontSize = 18.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = thread.title.ifBlank { "Cuộc trò chuyện" },
                    color = PrimaryText,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (thread.snippet.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = thread.snippet,
                        color = SecondaryText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onAction) {
                Text("⋮", color = SecondaryText, fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun ChatScreen(
    state: CodexUiState,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onModelClick: () -> Unit,
    onPermissionClick: () -> Unit,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemoveImage: (Int) -> Unit,
    onCancelExcerptReply: () -> Unit,
    onApprovalDecision: (String, Boolean) -> Unit,
    onEditQueueTurn: (String, String) -> Unit,
    onDeleteQueueTurn: (String) -> Unit,
    onClearQueue: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.streamingResponse) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Messages LazyColumn
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    ChatMessageItem(message = msg)
                }

                // Streaming indicator / response
                if (state.isResponding && state.streamingResponse.isNotBlank()) {
                    item {
                        ChatMessageItem(
                            message = ChatMessage(
                                id = "streaming_current",
                                role = MessageRole.ASSISTANT,
                                content = state.streamingResponse,
                                isStreaming = true,
                                engine = state.activeEngine
                            )
                        )
                    }
                }
            }
        }

        // Approval Panel
        state.pendingApproval?.let { approval ->
            ApprovalPanel(
                approval = approval,
                onDecision = { allow -> onApprovalDecision(approval.id, allow) }
            )
        }

        // Queued Turns Panel
        if (state.queuedTurns.isNotEmpty()) {
            QueuePanel(
                turns = state.queuedTurns,
                onEdit = onEditQueueTurn,
                onDelete = onDeleteQueueTurn,
                onClear = onClearQueue
            )
        }

        // Bottom Input Composer
        Composer(
            state = state,
            onInputChanged = onInputChanged,
            onSend = onSend,
            onStop = onStop,
            onModelClick = onModelClick,
            onPermissionClick = onPermissionClick,
            onPickImage = onPickImage,
            onTakePhoto = onTakePhoto,
            onRemoveImage = onRemoveImage,
            onCancelExcerptReply = onCancelExcerptReply
        )
    }
}

@Composable
private fun ChatMessageItem(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    val isSystem = message.role == MessageRole.SYSTEM

    val alignment = if (isUser) Alignment.End else Alignment.Start
    val containerColor = when {
        isUser -> Color(4280295971L)
        isSystem -> Color(4281348144L)
        else -> AppBackground
    }

    val engineTagColor = when (message.engine) {
        AiEngine.CODEX -> CodexGreen
        AiEngine.ANTIGRAVITY -> AntigravityPurple
        AiEngine.CLINE -> ClineOrange
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.9f else 1.0f),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = containerColor),
                shape = RoundedCornerShape(12.dp),
                border = if (isUser) BorderStroke(1.dp, BorderColor) else null
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Role & Engine Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isUser) "Bạn" else if (isSystem) "Hệ thống" else "${message.engine.icon} ${message.engine.title}",
                            color = if (isUser) PrimaryText else engineTagColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    // Message text
                    SelectableChatText(text = message.content)

                    if (message.isStreaming) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("● ● ●", color = engineTagColor, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SelectableChatText(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val parts = remember(text) { parseMarkdownParts(text) }

    SelectionContainer {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            parts.forEach { part ->
                if (part.language != null) {
                    // Code Block
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = InputBackground),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = part.language.uppercase(),
                                    color = CodexGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Code", part.text))
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Sao chép", color = SecondaryText, fontSize = 11.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = part.text,
                                color = PrimaryText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    // Formatted Markdown text
                    Text(
                        text = markdownAnnotatedString(part.text),
                        color = PrimaryText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

private fun parseMarkdownParts(text: String): List<MarkdownPart> {
    val lines = text.lines()
    val parts = mutableListOf<MarkdownPart>()
    val currentText = StringBuilder()
    var inCodeBlock = false
    var currentLang = ""

    for (line in lines) {
        if (line.startsWith("```")) {
            if (inCodeBlock) {
                parts.add(MarkdownPart(currentText.toString().trimEnd(), currentLang))
                currentText.clear()
                inCodeBlock = false
                currentLang = ""
            } else {
                if (currentText.isNotEmpty()) {
                    parts.add(MarkdownPart(currentText.toString().trimEnd(), null))
                    currentText.clear()
                }
                inCodeBlock = true
                currentLang = line.removePrefix("```").trim()
            }
        } else {
            currentText.append(line).append("\n")
        }
    }
    if (currentText.isNotEmpty()) {
        parts.add(MarkdownPart(currentText.toString().trimEnd(), if (inCodeBlock) currentLang else null))
    }
    return parts.ifEmpty { listOf(MarkdownPart(text, null)) }
}

private fun markdownAnnotatedString(text: String): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.lines()
        lines.forEachIndexed { index, line ->
            var formattedLine = line
            var isHeading = false
            var headingSize = 14.sp

            when {
                formattedLine.startsWith("### ") -> {
                    formattedLine = formattedLine.removePrefix("### ")
                    isHeading = true
                    headingSize = 16.sp
                }
                formattedLine.startsWith("## ") -> {
                    formattedLine = formattedLine.removePrefix("## ")
                    isHeading = true
                    headingSize = 18.sp
                }
                formattedLine.startsWith("# ") -> {
                    formattedLine = formattedLine.removePrefix("# ")
                    isHeading = true
                    headingSize = 20.sp
                }
                formattedLine.startsWith("- ") -> {
                    formattedLine = "• " + formattedLine.removePrefix("- ")
                }
                formattedLine.startsWith("* ") -> {
                    formattedLine = "• " + formattedLine.removePrefix("* ")
                }
            }

            if (isHeading) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = headingSize))
                append(formattedLine)
                pop()
            } else {
                append(formattedLine)
            }

            if (index < lines.size - 1) {
                append("\n")
            }
        }
    }
}

@Composable
private fun ApprovalPanel(
    approval: PendingApproval,
    onDecision: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = PanelBackground),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, WarningYellow)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚠️  Yêu cầu xác nhận", color = WarningYellow, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Text(text = approval.command, color = PrimaryText, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            if (approval.reason.isNotBlank()) {
                Text(text = approval.reason, color = SecondaryText, fontSize = 12.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onDecision(false) }) {
                    Text("Từ chối", color = DangerRed)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onDecision(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = CodexGreen)
                ) {
                    Text("Cho phép", color = AppBackground, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun QueuePanel(
    turns: List<QueuedTurn>,
    onEdit: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = PanelBackground),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lệnh đang chờ (${turns.size})",
                    color = SecondaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onClear) {
                    Text("Xóa hết", color = DangerRed, fontSize = 12.sp)
                }
            }
            turns.forEach { turn ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = turn.text,
                        color = PrimaryText,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { onDelete(turn.id) }, modifier = Modifier.size(24.dp)) {
                        Text("✕", color = SecondaryText, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Composer(
    state: CodexUiState,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onModelClick: () -> Unit,
    onPermissionClick: () -> Unit,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemoveImage: (Int) -> Unit,
    onCancelExcerptReply: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBackground)
            .padding(8.dp)
    ) {
        // Excerpt Reply Bar
        state.excerptReply?.let { reply ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .background(InputBackground, RoundedCornerShape(6.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Trích dẫn: ${reply.text}",
                    color = SecondaryText,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onCancelExcerptReply, modifier = Modifier.size(20.dp)) {
                    Text("✕", color = SecondaryText, fontSize = 12.sp)
                }
            }
        }

        // Chip Bar: Model, Effort, Permissions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Model Chip
            AssistChip(
                onClick = onModelClick,
                label = {
                    Text(
                        text = state.selectedModelId + if (state.selectedEffort.isNotBlank() && state.activeEngine == AiEngine.CODEX) " (${state.selectedEffort})" else "",
                        fontSize = 11.sp,
                        color = PrimaryText
                    )
                },
                colors = AssistChipDefaults.assistChipColors(containerColor = InputBackground),
                border = BorderStroke(1.dp, BorderColor)
            )

            // Permission Mode Chip (for Codex)
            if (state.activeEngine == AiEngine.CODEX) {
                AssistChip(
                    onClick = onPermissionClick,
                    label = {
                        val modeColor = when (state.permissionMode) {
                            PermissionMode.ASK -> WarningYellow
                            PermissionMode.AUTO -> CodexGreen
                            PermissionMode.FULL -> DangerRed
                        }
                        Text(text = state.permissionMode.name, fontSize = 11.sp, color = modeColor, fontWeight = FontWeight.Bold)
                    },
                    colors = AssistChipDefaults.assistChipColors(containerColor = InputBackground),
                    border = BorderStroke(1.dp, BorderColor)
                )
            }
        }

        // Input & Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onPickImage) {
                Text("📷", fontSize = 18.sp)
            }

            OutlinedTextField(
                value = state.inputText,
                onValueChange = onInputChanged,
                modifier = Modifier.weight(1f),
                placeholder = {
                    val hint = when (state.activeEngine) {
                        AiEngine.CODEX -> "Hỏi Codex hoặc nhập lệnh..."
                        AiEngine.ANTIGRAVITY -> "Hỏi Antigravity (Gemini 2.5)..."
                        AiEngine.CLINE -> "Yêu cầu Cline (Claude 3.7)..."
                    }
                    Text(hint, color = SecondaryText, fontSize = 14.sp)
                },
                shape = RoundedCornerShape(20.dp),
                colors = darkFieldColors(),
                maxLines = 5
            )

            val sendColor = when (state.activeEngine) {
                AiEngine.CODEX -> CodexGreen
                AiEngine.ANTIGRAVITY -> AntigravityPurple
                AiEngine.CLINE -> ClineOrange
            }

            if (state.isResponding) {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(42.dp)
                ) {
                    Text("■", color = Color.White, fontSize = 16.sp)
                }
            } else {
                Button(
                    onClick = onSend,
                    enabled = state.inputText.isNotBlank() || state.attachments.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = sendColor),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(42.dp)
                ) {
                    Text("➔", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ModelPickerDialog(
    models: List<CodexModel>,
    selectedModelId: String,
    selectedEffort: String,
    activeEngine: AiEngine,
    onSelectModel: (String) -> Unit,
    onSelectEffort: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chọn Model (${activeEngine.title})", color = PrimaryText, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(models, key = { it.id }) { model ->
                    val isSelected = model.id == selectedModelId
                    val brandColor = when (activeEngine) {
                        AiEngine.CODEX -> CodexGreen
                        AiEngine.ANTIGRAVITY -> AntigravityPurple
                        AiEngine.CLINE -> ClineOrange
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectModel(model.id) },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) brandColor.copy(alpha = 0.2f) else InputBackground
                        ),
                        border = if (isSelected) BorderStroke(1.dp, brandColor) else null
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = model.name.ifBlank { model.id },
                                color = PrimaryText,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                            if (model.description.isNotBlank()) {
                                Text(text = model.description, color = SecondaryText, fontSize = 11.sp)
                            }
                        }
                    }
                }

                if (activeEngine == AiEngine.CODEX) {
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Mức độ suy luận (Reasoning Effort)", color = SecondaryText, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("low", "medium", "high").forEach { effort ->
                                val isEffortSelected = effort == selectedEffort
                                Button(
                                    onClick = { onSelectEffort(effort) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isEffortSelected) CodexGreen else InputBackground
                                    )
                                ) {
                                    Text(
                                        text = effort.replaceFirstChar { it.uppercase() },
                                        color = if (isEffortSelected) AppBackground else PrimaryText,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CodexGreen)
            ) {
                Text("Xong", color = AppBackground)
            }
        },
        containerColor = PanelBackground
    )
}

@Composable
private fun PermissionPickerDialog(
    selected: PermissionMode,
    onSelect: (PermissionMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chế độ cấp quyền thực thi", color = PrimaryText, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PermissionMode.values().forEach { mode ->
                    val isSelected = mode == selected
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) CodexGreen.copy(alpha = 0.2f) else InputBackground
                        ),
                        border = if (isSelected) BorderStroke(1.dp, CodexGreen) else null
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = mode.name,
                                color = when (mode) {
                                    PermissionMode.ASK -> WarningYellow
                                    PermissionMode.AUTO -> CodexGreen
                                    PermissionMode.FULL -> DangerRed
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when (mode) {
                                    PermissionMode.ASK -> "Hỏi xác nhận trước mọi hành động can thiệp file/hệ thống"
                                    PermissionMode.AUTO -> "Tự động cho phép thao tác an toàn (đọc file, tìm kiếm)"
                                    PermissionMode.FULL -> "Toàn quyền thực thi lệnh tự động không cần hỏi"
                                },
                                color = SecondaryText,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = SecondaryText)
            }
        },
        containerColor = PanelBackground
    )
}

@Composable
private fun SettingsDialog(
    state: CodexUiState,
    onDismiss: () -> Unit,
    onSave: (endpoint: String, workspace: String, token: String, antigravityKey: String, clineKey: String, clineEndpoint: String) -> Unit,
    onClearToken: () -> Unit,
    onSwitchAccount: () -> Unit,
    onLogoutAccount: () -> Unit,
    onRefreshAccount: () -> Unit,
    onOpenGoogleLogin: () -> Unit,
    onSignOutGoogle: () -> Unit,
    onOpenArchive: () -> Unit,
    onCompletionNotificationsChanged: (Boolean) -> Unit,
    onOpenActivityLog: () -> Unit
) {
    var endpoint by remember { mutableStateOf(state.endpoint) }
    var workspace by remember { mutableStateOf(state.workspace) }
    var token by remember { mutableStateOf("") }
    var antigravityKey by remember { mutableStateOf(state.antigravityApiKey) }
    var clineKey by remember { mutableStateOf(state.clineApiKey) }
    var clineEndpoint by remember { mutableStateOf(state.clineCustomEndpoint) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cài đặt hệ thống (Multi-Engine)", color = PrimaryText, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Google Account Section (Gmail for Antigravity & Cline)
                item {
                    Text("Đăng nhập Google (Gmail) - Antigravity & Cline", color = GoogleBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = InputBackground),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            if (state.googleUser != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🟢", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(state.googleUser.displayName, color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(state.googleUser.email, color = SecondaryText, fontSize = 12.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(onClick = onSignOutGoogle) {
                                    Text("Đăng xuất Google", color = DangerRed, fontSize = 11.sp)
                                }
                            } else {
                                Text("Chưa liên kết tài khoản Google", color = SecondaryText, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = onOpenGoogleLogin,
                                    colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue)
                                ) {
                                    Text("Đăng nhập Google (Gmail)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Codex Server Section
                item {
                    Divider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                    Text("Cấu hình Codex Server (OpenAI)", color = CodexGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    DarkTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = "Codex Server Endpoint (ws:// hoặc wss://)"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    DarkTextField(
                        value = workspace,
                        onValueChange = { workspace = it },
                        label = "Thư mục Workspace (Server)"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    DarkTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = "Codex Secret Token (tùy chọn)",
                        isPassword = true
                    )
                }

                // Antigravity (Google Gemini) Section
                item {
                    Divider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                    Text("Antigravity (Google AI / Vertex / Gemini)", color = AntigravityPurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    DarkTextField(
                        value = antigravityKey,
                        onValueChange = { antigravityKey = it },
                        label = "Google AI Studio API Key (tùy chọn nếu dùng Google login)",
                        isPassword = true
                    )
                }

                // Cline (Universal Agent) Section
                item {
                    Divider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                    Text("Cline (Claude 3.7 / DeepSeek / OpenRouter)", color = ClineOrange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    DarkTextField(
                        value = clineEndpoint,
                        onValueChange = { clineEndpoint = it },
                        label = "Endpoint OpenRouter hoặc API tùy chọn"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    DarkTextField(
                        value = clineKey,
                        onValueChange = { clineKey = it },
                        label = "OpenRouter / Claude API Key",
                        isPassword = true
                    )
                }

                // ChatGPT Account Section
                item {
                    Divider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                    Text("Tài khoản ChatGPT / OpenAI", color = SecondaryText, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = InputBackground),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = state.account?.email ?: "Chưa đăng nhập ChatGPT",
                                color = PrimaryText,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            state.account?.planType?.let {
                                Text("Gói: $it", color = CodexGreen, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onSwitchAccount,
                                    colors = ButtonDefaults.buttonColors(containerColor = CodexGreen)
                                ) {
                                    Text("Đăng nhập / Đổi", color = AppBackground, fontSize = 11.sp)
                                }
                                if (state.account != null) {
                                    OutlinedButton(onClick = { showLogoutConfirm = true }) {
                                        Text("Đăng xuất", color = DangerRed, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // System Actions
                item {
                    Divider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenActivityLog,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Nhật ký", color = PrimaryText, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onOpenArchive,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Lưu trữ", color = PrimaryText, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        endpoint.trim(),
                        workspace.trim(),
                        token.trim(),
                        antigravityKey.trim(),
                        clineKey.trim(),
                        clineEndpoint.trim()
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CodexGreen)
            ) {
                Text("Lưu cấu hình", color = AppBackground, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = SecondaryText)
            }
        },
        containerColor = PanelBackground
    )

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Đăng xuất tài khoản", color = PrimaryText, fontWeight = FontWeight.Bold) },
            text = { Text("Bạn có chắc chắn muốn đăng xuất tài khoản ChatGPT không?", color = SecondaryText) },
            confirmButton = {
                Button(
                    onClick = {
                        onLogoutAccount()
                        showLogoutConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Đăng xuất", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Hủy", color = SecondaryText)
                }
            },
            containerColor = PanelBackground
        )
    }
}

@Composable
fun GoogleLoginDialog(
    currentUser: GoogleUserProfile?,
    onSignIn: (email: String, name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var email by remember { mutableStateOf(currentUser?.email ?: "") }
    var name by remember { mutableStateOf(currentUser?.displayName ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Đăng nhập Google (Gmail)", color = PrimaryText, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Tài khoản Google dùng chung để kích hoạt Antigravity (Gemini 2.5) và Cline mà không cần tạo lại nhiều tài khoản:",
                    color = SecondaryText,
                    fontSize = 13.sp
                )
                DarkTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Địa chỉ Gmail (VD: user@gmail.com)"
                )
                DarkTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Họ tên hiển thị"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (email.isNotBlank()) {
                        val displayName = name.ifBlank { email.substringBefore("@") }
                        onSignIn(email.trim(), displayName.trim())
                    }
                },
                enabled = email.contains("@"),
                colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue)
            ) {
                Text("Xác nhận đăng nhập", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = SecondaryText)
            }
        },
        containerColor = PanelBackground
    )
}

@Composable
fun DarkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = SecondaryText, fontSize = 12.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = darkFieldColors(),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
    )
}

@Composable
private fun ActivityLogDialog(
    logs: List<ActivityLogEntry>,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Nhật ký hoạt động", color = PrimaryText, fontWeight = FontWeight.Bold)
                TextButton(onClick = onClear) {
                    Text("Xóa log", color = DangerRed, fontSize = 12.sp)
                }
            }
        },
        text = {
            if (logs.isEmpty()) {
                Text("Chưa có sự kiện nào", color = SecondaryText)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(logs) { log ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = InputBackground),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = log.summary,
                                    color = PrimaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = log.timestamp,
                                    color = SecondaryText,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = SecondaryText)
            }
        },
        containerColor = PanelBackground
    )
}

@Composable
private fun UsageDialog(
    usage: UsageSnapshot,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hạn ngạch & Sử dụng", color = PrimaryText, fontWeight = FontWeight.Bold)
                TextButton(onClick = onRefresh) {
                    Text("Làm mới", color = CodexGreen, fontSize = 12.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                usage.primary?.let { UsageWindowCard(title = "Cửa sổ ngắn hạn (5 giờ)", window = it) }
                usage.secondary?.let { UsageWindowCard(title = "Cửa sổ dài hạn (7 ngày)", window = it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = SecondaryText)
            }
        },
        containerColor = PanelBackground
    )
}

@Composable
private fun UsageWindowCard(title: String, window: UsageWindow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = InputBackground),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = SecondaryText, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (window.usedPercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (window.usedPercent > 85) DangerRed else CodexGreen,
                trackColor = BorderColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Đã dùng: ${(window.usedPercent).toInt()}%", color = PrimaryText, fontSize = 11.sp)
                window.resetsInHours?.let {
                    Text("Làm mới sau: ${it}h", color = SecondaryText, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ArchiveDialog(
    archivedThreads: List<ThreadSummary>,
    isLoading: Boolean,
    search: String,
    onSearchChange: (String) -> Unit,
    onClose: () -> Unit,
    onUnarchive: (ThreadSummary) -> Unit,
    onDelete: (ThreadSummary) -> Unit,
    onRefresh: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Lưu trữ cuộc trò chuyện", color = PrimaryText, fontWeight = FontWeight.Bold)
                TextButton(onClick = onRefresh) {
                    Text("Làm mới", color = CodexGreen, fontSize = 12.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Tìm kiếm...", color = SecondaryText, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = darkFieldColors(),
                    singleLine = true
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CodexGreen)
                    }
                } else if (archivedThreads.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("Không có mục nào trong lưu trữ", color = SecondaryText)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(archivedThreads, key = { it.id }) { thread ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = InputBackground),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = thread.title,
                                            color = PrimaryText,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    TextButton(onClick = { onUnarchive(thread) }) {
                                        Text("Khôi phục", color = CodexGreen, fontSize = 11.sp)
                                    }
                                    IconButton(onClick = { onDelete(thread) }) {
                                        Text("🗑", fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Đóng", color = SecondaryText)
            }
        },
        containerColor = PanelBackground
    )
}

@Composable
private fun ThreadActionsDialog(
    thread: ThreadSummary,
    archived: Boolean,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(thread.title, color = PrimaryText, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRename,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = InputBackground)
                ) {
                    Text("✏️  Đổi tên cuộc trò chuyện", color = PrimaryText)
                }
                Button(
                    onClick = onArchive,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = InputBackground)
                ) {
                    Text(if (archived) "📦  Bỏ lưu trữ" else "📦  Lưu trữ", color = PrimaryText)
                }
                Button(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.2f))
                ) {
                    Text("🗑  Xóa cuộc trò chuyện", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = SecondaryText)
            }
        },
        containerColor = PanelBackground
    )
}

@Composable
private fun RenameThreadDialog(
    thread: ThreadSummary,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(thread.title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Đổi tên cuộc trò chuyện", color = PrimaryText, fontWeight = FontWeight.Bold) },
        text = {
            DarkTextField(
                value = title,
                onValueChange = { title = it },
                label = "Tiêu đề mới"
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) onRename(title.trim())
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CodexGreen)
            ) {
                Text("Lưu", color = AppBackground, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = SecondaryText)
            }
        },
        containerColor = PanelBackground
    )
}

@Composable
private fun AccountLoginDialog(
    challenge: AccountLoginChallenge,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Đăng nhập OpenAI / ChatGPT", color = PrimaryText, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Vui lòng mở liên kết sau trong trình duyệt và nhập mã xác thực:",
                    color = SecondaryText,
                    fontSize = 13.sp
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = InputBackground),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = challenge.userCode,
                            color = CodexGreen,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Button(
                    onClick = {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(challenge.verificationUri))
                        context.startActivity(browserIntent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CodexGreen)
                ) {
                    Text("Mở trình duyệt đăng nhập", color = AppBackground, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = SecondaryText)
            }
        },
        containerColor = PanelBackground
    )
}

@Composable
fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CodexGreen,
    unfocusedBorderColor = BorderColor,
    focusedTextColor = PrimaryText,
    unfocusedTextColor = PrimaryText,
    focusedContainerColor = InputBackground,
    unfocusedContainerColor = InputBackground,
    cursorColor = CodexGreen
)
