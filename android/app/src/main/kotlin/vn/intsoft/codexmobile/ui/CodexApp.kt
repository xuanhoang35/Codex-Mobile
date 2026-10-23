package vn.intsoft.codexmobile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.text.style.TextAlign
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
    var renameTargetThread by remember { mutableStateOf<ThreadSummary?>(null) }
    var threadActionTarget by remember { mutableStateOf<ThreadSummary?>(null) }

    // Auto-launch browser for OAuth / Device Code
    LaunchedEffect(state.loginChallenge) {
        val challenge = state.loginChallenge ?: return@LaunchedEffect
        if (challenge.userCode.isNotBlank()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Codex Code", challenge.userCode))
            Toast.makeText(context, "Mã xác thực ChatGPT: ${challenge.userCode} (Đã tự động sao chép)", Toast.LENGTH_LONG).show()
        }
        if (challenge.verificationUri.isNotBlank()) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(challenge.verificationUri)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } catch (_: Throwable) { }
        }
    }

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
                        state = state,
                        onSelectEngine = { viewModel.selectEngine(it) },
                        onOpenAntigravityLogin = {
                            viewModel.openAntigravityBrowserLogin(context)
                        },
                        onOpenClineLogin = {
                            viewModel.openClineBrowserLogin(context)
                        },
                        onOpenCodexLogin = { viewModel.startAccountLogin() },
                        onSignOutAntigravity = { viewModel.signOutAntigravity() },
                        onSignOutCline = { viewModel.signOutCline() },
                        onSignOutCodex = { viewModel.logoutAccount() }
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
                    onOpenDrawer = { viewModel.toggleHistory() },
                    onOpenAntigravityLogin = {
                        viewModel.openAntigravityBrowserLogin(context)
                    },
                    onOpenClineLogin = {
                        viewModel.openClineBrowserLogin(context)
                    },
                    onOpenCodexLogin = {
                        viewModel.startAccountLogin()
                    },
                    onConnectCodex = {
                        viewModel.connect()
                    }
                )
            }
        }
    }

    // Dialog: Settings
    if (showSettings) {
        SettingsDialog(
            state = state,
            onDismiss = { showSettings = false },
            onSave = { endpoint, workspace, token ->
                viewModel.saveSettings(endpoint, workspace, token)
                showSettings = false
            },
            onClearToken = { viewModel.clearToken() },
            onSwitchAccount = { viewModel.startAccountLogin() },
            onLogoutAccount = { viewModel.logoutAccount() },
            onRefreshAccount = { viewModel.refreshAccount() },
            onOpenAntigravityLogin = {
                showSettings = false
                viewModel.openAntigravityBrowserLogin(context)
            },
            onSignOutAntigravity = { viewModel.signOutAntigravity() },
            onOpenClineLogin = {
                showSettings = false
                viewModel.openClineBrowserLogin(context)
            },
            onSignOutCline = { viewModel.signOutCline() },
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
            onViewUsage = {
                showModelPicker = false
                showUsageDialog = true
            },
            onDismiss = { showModelPicker = false }
        )
    }

    // Dialog: Usage
    if (showUsageDialog) {
        val activeUsage = state.usage ?: when (state.activeEngine) {
            AiEngine.ANTIGRAVITY -> UsageSnapshot.createAntigravitySnapshot(state.antigravityUser?.email)
            AiEngine.CLINE -> UsageSnapshot.createClineSnapshot(state.clineUser?.email)
            AiEngine.CODEX -> UsageSnapshot(statusMessage = "Chưa kết nối Codex Server")
        }
        UsageDialog(
            activeEngine = state.activeEngine,
            usage = activeUsage,
            onDismiss = { showUsageDialog = false },
            onRefresh = { viewModel.refreshUsage() }
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
    state: CodexUiState,
    onSelectEngine: (AiEngine) -> Unit,
    onOpenAntigravityLogin: () -> Unit,
    onOpenClineLogin: () -> Unit,
    onOpenCodexLogin: () -> Unit,
    onSignOutAntigravity: () -> Unit,
    onSignOutCline: () -> Unit,
    onSignOutCodex: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiEngine.values().forEach { engine ->
                val isSelected = engine == state.activeEngine
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
                        EngineBrandIcon(engine = engine, size = 18.dp)
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

        // Active Engine Specific Account Strip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
            when (state.activeEngine) {
                AiEngine.ANTIGRAVITY -> {
                    val user = state.antigravityUser
                    if (user != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(InputBackground)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AntigravityBrandIcon(size = 16.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = user.email,
                                    color = PrimaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            TextButton(
                                onClick = onSignOutAntigravity,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Đăng xuất", color = DangerRed, fontSize = 11.sp)
                            }
                        }
                    } else {
                        Button(
                            onClick = onOpenAntigravityLogin,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AntigravityPurple),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AntigravityBrandIcon(size = 16.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Đăng nhập Google (Gmail) cho Antigravity", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                AiEngine.CLINE -> {
                    val user = state.clineUser
                    if (user != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(InputBackground)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ClineBrandIcon(size = 16.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = user.email,
                                    color = PrimaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            TextButton(
                                onClick = onSignOutCline,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Đăng xuất", color = DangerRed, fontSize = 11.sp)
                            }
                        }
                    } else {
                        Button(
                            onClick = onOpenClineLogin,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = ClineOrange),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ClineBrandIcon(size = 16.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Đăng nhập Google (Gmail) cho Cline", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                AiEngine.CODEX -> {
                    val account = state.account
                    if (account != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(InputBackground)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CodexBrandIcon(size = 16.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = account.email,
                                    color = PrimaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            TextButton(
                                onClick = onSignOutCodex,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Đăng xuất", color = DangerRed, fontSize = 11.sp)
                            }
                        }
                    } else {
                        Button(
                            onClick = onOpenCodexLogin,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = CodexGreen),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CodexBrandIcon(size = 16.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Đăng nhập OpenAI / ChatGPT qua Trình duyệt", color = AppBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
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
                        val (statusDot, statusText, statusTextColor) = when (state.activeEngine) {
                            AiEngine.CODEX -> when {
                                !state.isConnected -> Triple(DangerRed, "Mất kết nối tới server", DangerRed)
                                state.account == null -> Triple(WarningYellow, "Chưa đăng nhập", WarningYellow)
                                else -> Triple(CodexGreen, "Sẵn sàng", CodexGreen)
                            }
                            AiEngine.ANTIGRAVITY -> {
                                val authed = state.antigravityUser != null || state.antigravityApiKey.isNotBlank()
                                if (authed) Triple(CodexGreen, "Sẵn sàng", CodexGreen)
                                else Triple(WarningYellow, "Chưa đăng nhập Google", WarningYellow)
                            }
                            AiEngine.CLINE -> {
                                val authed = state.clineUser != null || state.clineApiKey.isNotBlank()
                                if (authed) Triple(CodexGreen, "Sẵn sàng", CodexGreen)
                                else Triple(WarningYellow, "Chưa đăng nhập Google", WarningYellow)
                            }
                        }

                        val isOk = statusText == "Sẵn sàng"

                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusDot)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            color = statusTextColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isOk) FontWeight.Bold else FontWeight.Medium
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
            if (!showSnippets) {
                val usageWindow = state.usage?.primary
                if (usageWindow != null) {
                    val activeColor = when (state.activeEngine) {
                        AiEngine.CODEX -> CodexGreen
                        AiEngine.ANTIGRAVITY -> AntigravityPurple
                        AiEngine.CLINE -> ClineOrange
                    }
                    TextButton(onClick = onUsage) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${(usageWindow.usedPercent).toInt()}%",
                                color = activeColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    TextButton(onClick = onUsage) {
                        Text(
                            text = "Usage",
                            color = SecondaryText,
                            fontSize = 12.sp
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
    onOpenDrawer: () -> Unit,
    onOpenAntigravityLogin: () -> Unit = {},
    onOpenClineLogin: () -> Unit = {},
    onOpenCodexLogin: () -> Unit = {},
    onConnectCodex: () -> Unit = {}
) {
    val listState = rememberLazyListState()

    val isCodexDisconnected = state.activeEngine == AiEngine.CODEX && !state.isConnected
    val isAuthed = when (state.activeEngine) {
        AiEngine.ANTIGRAVITY -> state.antigravityUser != null || state.antigravityApiKey.isNotBlank()
        AiEngine.CLINE -> state.clineUser != null || state.clineApiKey.isNotBlank()
        AiEngine.CODEX -> state.isConnected && state.account != null
    }

    LaunchedEffect(state.messages.size, state.streamingResponse) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Main Viewport (Messages or Centered "Vui lòng đăng nhập")
        Box(modifier = Modifier.weight(1f)) {
            if (state.messages.isEmpty()) {
                if (!isAuthed) {
                    // Viết vào giữa màn hình là vui lòng đăng nhập, tránh ảnh hưởng tới đoạn đang chat
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(0.92f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = InputBackground),
                            border = BorderStroke(1.dp, if (isCodexDisconnected) DangerRed.copy(alpha = 0.5f) else BorderColor)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                EngineBrandIcon(engine = state.activeEngine, size = 56.dp)
                                Spacer(modifier = Modifier.height(16.dp))

                                val titleText = if (isCodexDisconnected) "Mất kết nối tới server" else "Vui lòng đăng nhập"
                                Text(
                                    text = titleText,
                                    color = if (isCodexDisconnected) DangerRed else PrimaryText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                val subtitleText = when (state.activeEngine) {
                                    AiEngine.CODEX -> if (isCodexDisconnected)
                                        "Không thể kết nối đến máy chủ Codex. Vui lòng kiểm tra lại mạng Tailscale và thử kết nối lại."
                                    else
                                        "Vui lòng đăng nhập tài khoản OpenAI / ChatGPT để bắt đầu sử dụng Codex."
                                    AiEngine.ANTIGRAVITY -> "Vui lòng đăng nhập tài khoản Google (Gmail) để kích hoạt Antigravity và sử dụng tất cả mô hình AI."
                                    AiEngine.CLINE -> "Vui lòng đăng nhập tài khoản Google (Gmail) hoặc cấu hình API Key để kích hoạt Cline Universal Agent."
                                }
                                Text(
                                    text = subtitleText,
                                    color = SecondaryText,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(20.dp))

                                val (buttonText, buttonColor, buttonAction) = when (state.activeEngine) {
                                    AiEngine.CODEX -> if (isCodexDisconnected) {
                                        Triple("⚡ Thử kết nối lại máy chủ", CodexGreen, onConnectCodex)
                                    } else {
                                        Triple("🌐 Đăng nhập OpenAI / ChatGPT", CodexGreen, onOpenCodexLogin)
                                    }
                                    AiEngine.ANTIGRAVITY -> Triple("🌐 Đăng nhập bằng Google (Gmail)", AntigravityPurple, onOpenAntigravityLogin)
                                    AiEngine.CLINE -> Triple("🌐 Đăng nhập bằng Google (Gmail)", ClineOrange, onOpenClineLogin)
                                }

                                Button(
                                    onClick = buttonAction,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                                ) {
                                    Text(
                                        text = buttonText,
                                        color = if (buttonColor == CodexGreen) AppBackground else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            EngineBrandIcon(engine = state.activeEngine, size = 48.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Bắt đầu cuộc trò chuyện mới",
                                color = PrimaryText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Nhập câu hỏi hoặc câu lệnh ở khung bên dưới",
                                color = SecondaryText,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
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

                    // In-stream warning if unauthenticated during active chat
                    if (!isAuthed) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = InputBackground),
                                border = BorderStroke(1.dp, if (isCodexDisconnected) DangerRed.copy(alpha = 0.5f) else BorderColor)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val titleNotice = if (isCodexDisconnected) "⚠️ Mất kết nối tới server" else "🔒 Vui lòng đăng nhập"
                                    Text(
                                        text = titleNotice,
                                        color = if (isCodexDisconnected) DangerRed else WarningYellow,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val subNotice = if (isCodexDisconnected)
                                        "Mất kết nối với máy chủ Codex. Vui lòng kết nối lại để tiếp tục trò chuyện."
                                    else
                                        "Vui lòng đăng nhập để tiếp tục gửi câu lệnh vào cuộc trò chuyện này."
                                    Text(
                                        text = subNotice,
                                        color = SecondaryText,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    val (btnText, btnColor, btnAction) = when (state.activeEngine) {
                                        AiEngine.CODEX -> if (isCodexDisconnected) {
                                            Triple("⚡ Thử kết nối lại", CodexGreen, onConnectCodex)
                                        } else {
                                            Triple("🌐 Đăng nhập OpenAI / ChatGPT", CodexGreen, onOpenCodexLogin)
                                        }
                                        AiEngine.ANTIGRAVITY -> Triple("🌐 Đăng nhập bằng Google (Gmail)", AntigravityPurple, onOpenAntigravityLogin)
                                        AiEngine.CLINE -> Triple("🌐 Đăng nhập bằng Google (Gmail)", ClineOrange, onOpenClineLogin)
                                    }
                                    Button(
                                        onClick = btnAction,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = btnText,
                                            color = if (btnColor == CodexGreen) AppBackground else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
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
            onCancelExcerptReply = onCancelExcerptReply,
            onOpenAntigravityLogin = onOpenAntigravityLogin,
            onOpenClineLogin = onOpenClineLogin,
            onOpenCodexLogin = onOpenCodexLogin,
            onConnectCodex = onConnectCodex
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
                        if (!isUser && !isSystem) {
                            EngineBrandIcon(engine = message.engine, size = 14.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = if (isUser) "Bạn" else if (isSystem) "Hệ thống" else message.engine.title,
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
    onCancelExcerptReply: () -> Unit,
    onOpenAntigravityLogin: () -> Unit = {},
    onOpenClineLogin: () -> Unit = {},
    onOpenCodexLogin: () -> Unit = {},
    onConnectCodex: () -> Unit = {}
) {
    val isCodexDisconnected = state.activeEngine == AiEngine.CODEX && !state.isConnected
    val isAuthed = when (state.activeEngine) {
        AiEngine.ANTIGRAVITY -> state.antigravityUser != null || state.antigravityApiKey.isNotBlank()
        AiEngine.CLINE -> state.clineUser != null || state.clineApiKey.isNotBlank()
        AiEngine.CODEX -> state.isConnected && state.account != null
    }

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

        // Chip Bar: Model, Effort, Permissions (Active for ALL engines)
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

            // Permission Mode Chip (Active for Codex, Antigravity, and Cline)
            AssistChip(
                onClick = onPermissionClick,
                leadingIcon = {
                    val iconText = when (state.permissionMode) {
                        PermissionMode.ASK -> "🛡️"
                        PermissionMode.AUTO -> "⚡"
                        PermissionMode.FULL -> "🔥"
                    }
                    Text(iconText, fontSize = 12.sp)
                },
                label = {
                    val modeColor = when (state.permissionMode) {
                        PermissionMode.ASK -> WarningYellow
                        PermissionMode.AUTO -> CodexGreen
                        PermissionMode.FULL -> DangerRed
                    }
                    val modeTitle = when (state.permissionMode) {
                        PermissionMode.ASK -> "ASK"
                        PermissionMode.AUTO -> "AUTO"
                        PermissionMode.FULL -> "FULL (YOLO)"
                    }
                    Text(text = modeTitle, fontSize = 11.sp, color = modeColor, fontWeight = FontWeight.Bold)
                },
                colors = AssistChipDefaults.assistChipColors(containerColor = InputBackground),
                border = BorderStroke(1.dp, BorderColor)
            )
        }

        // Input & Actions (CLEAN - No intrusive yellow banner card!)
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
                    val hint = when {
                        isCodexDisconnected -> "Mất kết nối tới server..."
                        !isAuthed -> "Vui lòng đăng nhập để bắt đầu trò chuyện..."
                        state.activeEngine == AiEngine.CODEX -> "Hỏi Codex hoặc nhập lệnh..."
                        state.activeEngine == AiEngine.ANTIGRAVITY -> "Hỏi Antigravity (Gemini 3.8)..."
                        state.activeEngine == AiEngine.CLINE -> "Yêu cầu Cline (DeepSeek / Claude)..."
                        else -> "Nhập tin nhắn..."
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
                val btnBgColor = when {
                    isCodexDisconnected -> DangerRed
                    !isAuthed -> WarningYellow
                    else -> sendColor
                }
                val btnIcon = when {
                    isCodexDisconnected -> "⚡"
                    !isAuthed -> "🔒"
                    else -> "➔"
                }
                Button(
                    onClick = {
                        if (isCodexDisconnected) {
                            onConnectCodex()
                        } else if (!isAuthed) {
                            when (state.activeEngine) {
                                AiEngine.ANTIGRAVITY -> onOpenAntigravityLogin()
                                AiEngine.CLINE -> onOpenClineLogin()
                                AiEngine.CODEX -> onOpenCodexLogin()
                            }
                        } else {
                            onSend()
                        }
                    },
                    enabled = isCodexDisconnected || !isAuthed || state.inputText.isNotBlank() || state.attachments.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = btnBgColor),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(42.dp)
                ) {
                    Text(btnIcon, color = if (btnBgColor == CodexGreen) AppBackground else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
    onViewUsage: () -> Unit,
    onDismiss: () -> Unit
) {
    var filterCategory by remember { mutableStateOf("ALL") }

    val displayedModels = remember(models, filterCategory) {
        when (filterCategory) {
            "FREE" -> models.filter { it.isFree }
            "PRO" -> models.filter { !it.isFree }
            else -> models
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EngineBrandIcon(engine = activeEngine, size = 20.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Model (${activeEngine.title})", color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Category Filter Tabs for Cline
                if (activeEngine == AiEngine.CLINE) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = filterCategory == "ALL",
                                onClick = { filterCategory = "ALL" },
                                label = { Text("Tất cả", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = filterCategory == "FREE",
                                onClick = { filterCategory = "FREE" },
                                label = {
                                    Text(
                                        "🟢 Free (${models.count { it.isFree }})",
                                        color = Color(0xFF22C55E),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            )
                            FilterChip(
                                selected = filterCategory == "PRO",
                                onClick = { filterCategory = "PRO" },
                                label = { Text("⚡ Pro (${models.count { !it.isFree }})", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                items(displayedModels, key = { it.id }) { model ->
                    val isSelected = model.id == selectedModelId
                    val isAvailable = model.isAvailable
                    val brandColor = when (activeEngine) {
                        AiEngine.CODEX -> CodexGreen
                        AiEngine.ANTIGRAVITY -> AntigravityPurple
                        AiEngine.CLINE -> ClineOrange
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isAvailable) { onSelectModel(model.id) },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) brandColor.copy(alpha = 0.2f) else InputBackground
                        ),
                        border = if (isSelected) BorderStroke(1.dp, brandColor) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .then(if (!isAvailable) Modifier.alpha(0.45f) else Modifier)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = model.name.ifBlank { model.id },
                                        color = PrimaryText,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                    model.badge?.let { badge ->
                                        Spacer(modifier = Modifier.width(6.dp))
                                        val (badgeBg, badgeFg) = when (badge) {
                                            "FREE" -> Pair(Color(0xFF22C55E).copy(alpha = 0.2f), Color(0xFF22C55E))
                                            "High" -> Pair(Color(0xFF64748B).copy(alpha = 0.2f), Color(0xFF94A3B8))
                                            "Notice" -> Pair(Color(0xFF38BDF8).copy(alpha = 0.2f), Color(0xFF38BDF8))
                                            "Unavailable" -> Pair(DangerRed.copy(alpha = 0.2f), DangerRed)
                                            "PRO" -> Pair(ClineOrange.copy(alpha = 0.2f), ClineOrange)
                                            else -> Pair(InputBackground, SecondaryText)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(badgeBg)
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = if (badge == "Notice" || badge == "Unavailable") "$badge ⓘ" else badge,
                                                color = badgeFg,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                if (model.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = model.description, color = SecondaryText, fontSize = 11.sp)
                                }
                            }

                            if (isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("✓", color = brandColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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

                // View Usage Button (matching the Antigravity / IDE Model Selector screenshot!)
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = BorderColor)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                onDismiss()
                                onViewUsage()
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📈", fontSize = 15.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "View Usage",
                                color = PrimaryText,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                        Text("›", color = SecondaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                                    PermissionMode.AUTO -> "Tự động cho phép thao tác an toàn (đọc file, tìm kiếm, phân tích)"
                                    PermissionMode.FULL -> "Toàn quyền tự hành mạnh mẽ (FULL / YOLO) - Tự động viết code hoàn chỉnh, sửa file và giải quyết bài toán xuyên suốt không cần dừng lại xin xác nhận"
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
    onSave: (endpoint: String, workspace: String, token: String) -> Unit,
    onClearToken: () -> Unit,
    onSwitchAccount: () -> Unit,
    onLogoutAccount: () -> Unit,
    onRefreshAccount: () -> Unit,
    onOpenAntigravityLogin: () -> Unit,
    onSignOutAntigravity: () -> Unit,
    onOpenClineLogin: () -> Unit,
    onSignOutCline: () -> Unit,
    onOpenArchive: () -> Unit,
    onCompletionNotificationsChanged: (Boolean) -> Unit,
    onOpenActivityLog: () -> Unit
) {
    var endpoint by remember { mutableStateOf(state.endpoint) }
    var workspace by remember { mutableStateOf(state.workspace) }
    var token by remember { mutableStateOf("") }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cài đặt hệ thống (Multi-Engine)", color = PrimaryText, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Antigravity (Google Gemini) Section
                item {
                    Text("🪐 Antigravity (Google Gemini / DeepMind)", color = AntigravityPurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = InputBackground),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            if (state.antigravityUser != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🟢", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(state.antigravityUser.displayName, color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(state.antigravityUser.email, color = SecondaryText, fontSize = 12.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(onClick = onSignOutAntigravity) {
                                    Text("Đăng xuất Google (Antigravity)", color = DangerRed, fontSize = 11.sp)
                                }
                            } else {
                                Text("Chưa đăng nhập Google cho Antigravity", color = SecondaryText, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = onOpenAntigravityLogin,
                                    colors = ButtonDefaults.buttonColors(containerColor = AntigravityPurple),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("🌐 Đăng nhập Google (Gmail) qua Trình duyệt", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Cline (Universal Agent) Section
                item {
                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                    Text("🤖 Cline (Claude 3.7 / Universal Agent)", color = ClineOrange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = InputBackground),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            if (state.clineUser != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🟢", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(state.clineUser.displayName, color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(state.clineUser.email, color = SecondaryText, fontSize = 12.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(onClick = onSignOutCline) {
                                    Text("Đăng xuất Google (Cline)", color = DangerRed, fontSize = 11.sp)
                                }
                            } else {
                                Text("Chưa đăng nhập Google cho Cline", color = SecondaryText, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = onOpenClineLogin,
                                    colors = ButtonDefaults.buttonColors(containerColor = ClineOrange),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("🌐 Đăng nhập Google (Gmail) qua Trình duyệt", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Codex Server Section
                item {
                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                    Text("⚡ Cấu hình Codex Server (OpenAI)", color = CodexGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                    val tokenHint = if (state.hasToken) "Đã lưu Token (để trống nếu không đổi)" else "Nhập Bearer Token từ server"
                    DarkTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = "Codex Secret Token ($tokenHint)",
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
                        token.trim()
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
    activeEngine: AiEngine,
    usage: UsageSnapshot,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    val brandColor = when (activeEngine) {
        AiEngine.CODEX -> CodexGreen
        AiEngine.ANTIGRAVITY -> AntigravityPurple
        AiEngine.CLINE -> ClineOrange
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EngineBrandIcon(engine = activeEngine, size = 22.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hạn ngạch & Thời gian", color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                TextButton(onClick = onRefresh) {
                    Text("Làm mới", color = brandColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Plan & Account card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = InputBackground)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = usage.planType ?: "${activeEngine.title} Standard Tier",
                            color = brandColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        usage.creditBalance?.let {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(it, color = SecondaryText, fontSize = 11.sp)
                        }
                        usage.resetCountdownText?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "⏳ $it",
                                color = PrimaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Primary window card
                usage.primary?.let {
                    val defaultTitle = when (activeEngine) {
                        AiEngine.CODEX -> "Cửa sổ ngắn hạn (5 giờ)"
                        AiEngine.ANTIGRAVITY -> "Hạn mức Token Tuần (Google DeepMind)"
                        AiEngine.CLINE -> "Lượt gọi Free Models Hôm nay (OpenRouter)"
                    }
                    UsageWindowCard(
                        title = it.label ?: defaultTitle,
                        window = it,
                        brandColor = brandColor
                    )
                }

                // Secondary window card
                usage.secondary?.let {
                    val defaultTitle = when (activeEngine) {
                        AiEngine.CODEX -> "Cửa sổ dài hạn (7 ngày)"
                        AiEngine.ANTIGRAVITY -> "Hạn ngạch Yêu cầu Ngày"
                        AiEngine.CLINE -> "Hạn mức Định mức Tuần"
                    }
                    UsageWindowCard(
                        title = it.label ?: defaultTitle,
                        window = it,
                        brandColor = brandColor
                    )
                }

                // Extra details
                usage.statusMessage.takeIf { it.isNotBlank() }?.let { msg ->
                    Text(
                        text = "• $msg",
                        color = SecondaryText,
                        fontSize = 11.sp
                    )
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
private fun UsageWindowCard(
    title: String,
    window: UsageWindow,
    brandColor: Color = CodexGreen
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = InputBackground),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = SecondaryText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = "Còn ${window.remainingPercent}%",
                    color = if (window.usedPercent > 85) DangerRed else brandColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (window.usedPercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (window.usedPercent > 85) DangerRed else brandColor,
                trackColor = BorderColor
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Đã dùng: ${(window.usedPercent).toInt()}%", color = PrimaryText, fontSize = 11.sp)
                window.resetsInHours?.let {
                    val timeText = if (it >= 24) "${it / 24} ngày ${it % 24}h" else "${it}h"
                    Text("Làm mới sau: $timeText", color = SecondaryText, fontSize = 11.sp)
                }
            }
            window.detail?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(it, color = SecondaryText.copy(alpha = 0.85f), fontSize = 10.sp)
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
                if (challenge.userCode.isNotBlank()) {
                    Text(
                        text = "Vui lòng mở liên kết sau trong trình duyệt và nhập mã xác thực (mã đã được tự động sao chép):",
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
                } else {
                    Text(
                        text = "Trình duyệt đang mở trang xác thực tài khoản ChatGPT / OpenAI. Sau khi bạn chọn tài khoản và chấp thuận đăng nhập, ứng dụng sẽ tự động hoàn tất kết nối.",
                        color = SecondaryText,
                        fontSize = 13.sp
                    )
                }

                Button(
                    onClick = {
                        if (challenge.verificationUri.isNotBlank()) {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(challenge.verificationUri)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(browserIntent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CodexGreen)
                ) {
                    Text(
                        text = if (challenge.userCode.isNotBlank()) "Mở lại trình duyệt đăng nhập" else "Mở lại trang đăng nhập ChatGPT",
                        color = AppBackground,
                        fontWeight = FontWeight.Bold
                    )
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
