package vn.intsoft.codexmobile.data

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

class CodexWebSocketClient(
    var endpoint: String,
    var token: String,
    var workspace: String,
    val listener: Listener,
    var tokenProvider: (() -> String)? = null
) {
    constructor(tokenProvider: () -> String, listener: Listener) : this(
        endpoint = "wss://codex.webdemo.name.vn/",
        token = tokenProvider(),
        workspace = "/srv/codex-workspaces",
        listener = listener,
        tokenProvider = tokenProvider
    )

    var activeThreadId: String? = null
    interface Listener {
        fun onConnectionChanged(status: String, connected: Boolean)
        fun onInitialized()
        fun onModelsLoaded(models: List<CodexModel>)
        fun onThreadsLoaded(threads: List<ThreadSummary>, archived: Boolean, searchTerm: String)
        fun onThreadLibraryChanged()
        fun onUsageLoaded(usage: UsageSnapshot)
        fun onAccountLoaded(account: CodexAccount)
        fun onAccountLoginChallenge(challenge: AccountLoginChallenge)
        fun onAccountLoginCompleted(success: Boolean, error: String?)
        fun onAccountLogoutCompleted()
        fun onThreadReady(threadId: String, title: String, model: String?, messages: List<HistoryMessage>)
        fun onAgentDelta(itemId: String, delta: String)
        fun onAgentCompleted(itemId: String, text: String)
        fun onTurnStarted(turnId: String)
        fun onTurnCompleted()
        fun onActivity(status: String)
        fun onApprovalRequested(approval: ApprovalRequest)
        fun onApprovalResolved(requestId: Any)
        fun onError(message: String)
    }

    data class ApprovalRequest(
        val requestId: Any,
        val title: String,
        val details: String
    )

    private enum class OpenRequestKind {
        NEW, RESUME
    }

    private data class ThreadListRequest(
        val archived: Boolean,
        val searchTerm: String
    )

    private val requestIds = AtomicLong(10L)
    private val openRequests = ConcurrentHashMap<Long, OpenRequestKind>()
    private val threadListRequests = ConcurrentHashMap<Long, ThreadListRequest>()
    private val threadMutationRequests = ConcurrentHashMap.newKeySet<Long>()
    private val modelListRequests = ConcurrentHashMap.newKeySet<Long>()
    private val usageRequests = ConcurrentHashMap.newKeySet<Long>()
    private val accountReadRequests = ConcurrentHashMap.newKeySet<Long>()
    private val accountLoginRequests = ConcurrentHashMap.newKeySet<Long>()
    private val accountLogoutRequests = ConcurrentHashMap.newKeySet<Long>()

    private val httpClient = OkHttpClient.Builder()
        .pingInterval(25L, TimeUnit.SECONDS)
        .connectTimeout(20L, TimeUnit.SECONDS)
        .readTimeout(0L, TimeUnit.MILLISECONDS)
        .build()

    private var reconnectExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val reconnectScheduled = AtomicBoolean(false)
    private val reconnectAttempts = AtomicLong(0L)
    private var webSocket: WebSocket? = null
    private var manualDisconnect = false

    fun connect() {
        manualDisconnect = false
        if (webSocket != null) return

        try {
            tokenProvider?.invoke()?.let { latestToken ->
                if (latestToken.isNotBlank()) {
                    this.token = latestToken.trim()
                }
            }

            if (reconnectExecutor.isShutdown) {
                reconnectExecutor = Executors.newSingleThreadScheduledExecutor()
            }

            listener.onConnectionChanged("Đang kết nối…", false)
            val cleanUrl = endpoint.trim().let { ep ->
                when {
                    ep.startsWith("http://", ignoreCase = true) -> "ws://" + ep.substring(7)
                    ep.startsWith("https://", ignoreCase = true) -> "wss://" + ep.substring(8)
                    !ep.startsWith("ws://", ignoreCase = true) && !ep.startsWith("wss://", ignoreCase = true) -> "wss://$ep"
                    else -> ep
                }
            }
            val requestBuilder = Request.Builder().url(cleanUrl)
            if (token.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer ${token.trim()}")
            }
            webSocket = httpClient.newWebSocket(requestBuilder.build(), SocketListener())
        } catch (t: Throwable) {
            val errorMsg = t.localizedMessage ?: "Chưa kết nối server"
            listener.onConnectionChanged("Lỗi: $errorMsg", false)
        }
    }

    fun connect(newEndpoint: String? = null, newWorkspace: String? = null) {
        if (!newEndpoint.isNullOrBlank()) this.endpoint = newEndpoint
        if (!newWorkspace.isNullOrBlank()) this.workspace = newWorkspace
        connect()
    }

    fun disconnect() {
        manualDisconnect = true
        reconnectScheduled.set(false)
        webSocket?.close(1000, "Client disconnected")
        webSocket = null
        reconnectExecutor.shutdownNow()
        listener.onConnectionChanged("Đã ngắt kết nối", false)
    }

    fun refreshModels() {
        val id = requestIds.getAndIncrement()
        modelListRequests.add(id)
        val params = JSONObject().put("limit", 50).put("includeHidden", false)
        sendRequest("model/list", id, params)
    }

    fun refreshThreads(archived: Boolean = false, searchTerm: String = "") {
        val id = requestIds.getAndIncrement()
        threadListRequests[id] = ThreadListRequest(archived, searchTerm)
        val params = JSONObject()
            .put("limit", 50)
            .put("archived", archived)
            .put("sortKey", "recency_at")
            .put("sortDirection", "desc")
            .put("sourceKinds", JSONArray().put("cli").put("vscode").put("appServer"))
        if (searchTerm.isNotBlank()) {
            params.put("searchTerm", searchTerm.trim())
        }
        sendRequest("thread/list", id, params)
    }

    fun renameThread(threadId: String, name: String) {
        val params = JSONObject().put("threadId", threadId).put("name", name)
        sendThreadMutation("thread/name/set", params)
    }

    fun archiveThread(threadId: String) {
        val params = JSONObject().put("threadId", threadId)
        sendThreadMutation("thread/archive", params)
    }

    fun unarchiveThread(threadId: String) {
        val params = JSONObject().put("threadId", threadId)
        sendThreadMutation("thread/unarchive", params)
    }

    fun deleteThread(threadId: String) {
        val params = JSONObject().put("threadId", threadId)
        sendThreadMutation("thread/delete", params)
    }

    fun refreshArchivedThreads(searchTerm: String = "") {
        refreshThreads(archived = true, searchTerm = searchTerm)
    }

    fun startAccountLogin() {
        val id = requestIds.getAndIncrement()
        accountLoginRequests.add(id)
        val params = JSONObject()
            .put("type", "chatgpt")
            .put("useHostedLoginSuccessPage", true)
        sendRequest("account/login/start", id, params)
    }

    private fun sendThreadMutation(method: String, params: JSONObject) {
        val id = requestIds.getAndIncrement()
        threadMutationRequests.add(id)
        sendRequest(method, id, params)
    }

    fun refreshUsage() {
        val id = requestIds.getAndIncrement()
        usageRequests.add(id)
        sendRequest("account/rateLimits/read", id, JSONObject.NULL)
    }

    fun refreshAccount() {
        val id = requestIds.getAndIncrement()
        accountReadRequests.add(id)
        val params = JSONObject().put("refreshToken", false)
        sendRequest("account/read", id, params)
    }

    fun startDeviceCodeLogin() {
        val id = requestIds.getAndIncrement()
        accountLoginRequests.add(id)
        val params = JSONObject().put("type", "chatgptDeviceCode")
        sendRequest("account/login/start", id, params)
    }

    fun cancelAccountLogin(loginId: String) {
        val id = requestIds.getAndIncrement()
        val params = JSONObject().put("loginId", loginId)
        sendRequest("account/login/cancel", id, params)
    }

    fun logoutAccount() {
        val id = requestIds.getAndIncrement()
        accountLogoutRequests.add(id)
        sendRequest("account/logout", id, JSONObject.NULL)
    }

    fun startNewThread(model: String?, mode: PermissionMode) {
        val id = requestIds.getAndIncrement()
        openRequests[id] = OpenRequestKind.NEW
        val params = JSONObject()
            .put("cwd", workspace)
            .put("serviceName", "codex_android")
        applyMode(params, mode, false)
        if (!model.isNullOrBlank()) {
            params.put("model", model)
        }
        sendRequest("thread/start", id, params)
        listener.onActivity("Đang tạo cuộc trò chuyện…")
    }

    fun resumeThread(threadId: String, model: String?, mode: PermissionMode) {
        val id = requestIds.getAndIncrement()
        openRequests[id] = OpenRequestKind.RESUME
        val params = JSONObject()
            .put("threadId", threadId)
            .put("cwd", workspace)
        applyMode(params, mode, false)
        if (!model.isNullOrBlank()) {
            params.put("model", model)
        }
        sendRequest("thread/resume", id, params)
        listener.onActivity("Đang mở lịch sử…")
    }

    fun startTurn(
        threadId: String,
        text: String,
        model: String?,
        effort: String?,
        mode: PermissionMode,
        imageDataUrls: List<String> = emptyList()
    ): Boolean {
        val input = JSONArray()
        if (text.isNotBlank()) {
            input.put(JSONObject().put("type", "text").put("text", text))
        }
        for (img in imageDataUrls) {
            input.put(JSONObject().put("type", "image_url").put("image_url", img))
        }
        val params = JSONObject().put("threadId", threadId).put("input", input)
        applyMode(params, mode, true)
        if (!model.isNullOrBlank()) {
            params.put("model", model)
        }
        if (!effort.isNullOrBlank()) {
            params.put("effort", effort)
        }
        return sendRequest("turn/start", requestIds.getAndIncrement(), params)
    }

    fun interruptTurn(threadId: String, turnId: String): Boolean {
        val params = JSONObject().put("threadId", threadId).put("turnId", turnId)
        return sendRequest("turn/interrupt", requestIds.getAndIncrement(), params)
    }

    fun startNewThread(threadId: String? = null, model: String? = null, mode: PermissionMode = PermissionMode.ASK) {
        startNewThread(model, mode)
    }

    fun loadThreadHistory(threadId: String, model: String? = null, mode: PermissionMode = PermissionMode.ASK) {
        activeThreadId = threadId
        resumeThread(threadId, model, mode)
    }

    fun sendPrompt(
        prompt: String,
        model: String? = null,
        effort: String? = null,
        permission: PermissionMode = PermissionMode.ASK,
        imageDataUrls: List<String> = emptyList()
    ): Boolean {
        val tid = activeThreadId
        return if (tid != null) {
            startTurn(tid, prompt, model, effort, permission, imageDataUrls)
        } else {
            startNewThread(model, permission)
            true
        }
    }

    fun interrupt() {
        val tid = activeThreadId
        if (tid != null) {
            interruptTurn(tid, "")
        }
    }

    fun decideApproval(requestId: Any, allow: Boolean): Boolean {
        return resolveApproval(requestId, if (allow) "accept" else "reject")
    }

    fun resolveApproval(requestId: Any, decision: String): Boolean {
        val response = JSONObject()
            .put("id", requestId)
            .put("result", JSONObject().put("decision", decision))
        return webSocket?.send(response.toString()) ?: false
    }

    private fun initialize() {
        val params = JSONObject().put(
            "clientInfo",
            JSONObject()
                .put("name", "codex_android")
                .put("title", "Codex Mobile")
                .put("version", "0.5.2")
        )
        sendRequest("initialize", 1L, params)
    }

    private fun afterInitialize() {
        webSocket?.send(JSONObject().put("method", "initialized").put("params", JSONObject()).toString())
        listener.onInitialized()
        refreshModels()
        refreshThreads(false, "")
        refreshUsage()
        refreshAccount()
    }

    private fun sendRequest(method: String, id: Long, params: Any): Boolean {
        val request = JSONObject().put("method", method).put("id", id).put("params", params)
        return webSocket?.send(request.toString()) ?: false
    }

    private fun applyMode(params: JSONObject, mode: PermissionMode, forTurn: Boolean): JSONObject {
        when (mode) {
            PermissionMode.ASK -> {
                params.put("approvalPolicy", "on-request")
                params.put("approvalsReviewer", "user")
                if (forTurn) {
                    params.put("sandboxPolicy", JSONObject().put("type", "workspaceWrite").put("networkAccess", false))
                } else {
                    params.put("sandbox", "workspace-write")
                }
            }
            PermissionMode.AUTO -> {
                params.put("approvalPolicy", "on-request")
                params.put("approvalsReviewer", "auto_review")
                if (forTurn) {
                    params.put("sandboxPolicy", JSONObject().put("type", "workspaceWrite").put("networkAccess", false))
                } else {
                    params.put("sandbox", "workspace-write")
                }
            }
            PermissionMode.FULL -> {
                params.put("approvalPolicy", "never")
                params.put("approvalsReviewer", "user")
                if (forTurn) {
                    params.put("sandboxPolicy", JSONObject().put("type", "dangerFullAccess"))
                } else {
                    params.put("sandbox", "danger-full-access")
                }
            }
        }
        return params
    }

    fun handleMessage(text: String) {
        val json = try { JSONObject(text) } catch (_: Throwable) {
            listener.onError("Server gửi JSON không hợp lệ")
            return
        }

        if (json.has("id") && json.has("method")) {
            handleServerRequest(json)
            return
        }
        if (json.has("id")) {
            handleResponse(json)
            return
        }

        when (json.optString("method")) {
            "thread/status/changed" -> {
                val status = json.optJSONObject("params")?.optJSONObject("status")?.optString("type")
                if (status == "idle") listener.onActivity("Sẵn sàng")
            }
            "turn/started" -> {
                val turnId = json.optJSONObject("params")?.optJSONObject("turn")?.optString("id").orEmpty()
                if (turnId.isNotBlank()) listener.onTurnStarted(turnId)
                listener.onActivity("Codex đang xử lý…")
            }
            "item/started" -> {
                val item = json.optJSONObject("params")?.optJSONObject("item")
                val itemType = item?.optString("type")
                if (itemType == "commandExecution") {
                    val cmd = item.optString("command")
                    listener.onActivity("Đang chạy: ${cmd.take(120)}")
                } else if (itemType == "fileChange") {
                    listener.onActivity("Đang chuẩn bị thay đổi file…")
                }
            }
            "item/agentMessage/delta" -> {
                val p = json.optJSONObject("params")
                if (p != null) {
                    val itemId = p.optString("itemId")
                    val delta = p.optString("delta")
                    listener.onAgentDelta(itemId, delta)
                }
            }
            "item/completed" -> {
                val item = json.optJSONObject("params")?.optJSONObject("item")
                when (item?.optString("type")) {
                    "agentMessage" -> listener.onAgentCompleted(item.optString("id"), item.optString("text"))
                    "fileChange" -> listener.onActivity("Đã xử lý thay đổi file")
                    "commandExecution" -> {
                        val code = if (item.has("exitCode")) item.optInt("exitCode") else null
                        listener.onActivity(if (code == null) "Lệnh đã kết thúc" else "Lệnh kết thúc · exit $code")
                    }
                }
            }
            "turn/completed" -> {
                listener.onTurnCompleted()
                refreshThreads(false, "")
                refreshUsage()
            }
            "account/rateLimits/updated" -> {
                val limits = json.optJSONObject("params")?.optJSONObject("rateLimits")
                if (limits != null) listener.onUsageLoaded(parseUsage(limits))
            }
            "account/updated" -> refreshAccount()
            "account/login/completed" -> {
                val p = json.optJSONObject("params")
                if (p != null) {
                    val success = p.optBoolean("success")
                    val error = p.optString("error").takeIf { it.isNotBlank() }
                    listener.onAccountLoginCompleted(success, error)
                    if (success) {
                        refreshAccount()
                        refreshModels()
                        refreshUsage()
                        refreshThreads(false, "")
                    }
                }
            }
            "serverRequest/resolved" -> {
                val reqId = json.optJSONObject("params")?.opt("requestId")
                if (reqId != null) listener.onApprovalResolved(reqId)
            }
            "thread/archived", "thread/unarchived", "thread/name/updated" -> {
                listener.onThreadLibraryChanged()
            }
            "error" -> {
                val msg = json.optJSONObject("params")?.optJSONObject("error")?.optString("message")
                    ?: json.optJSONObject("error")?.optString("message")
                    ?: "Codex báo lỗi"
                listener.onError(msg)
            }
        }
    }

    private fun handleResponse(message: JSONObject) {
        val id = message.optLong("id")
        val errorObj = message.optJSONObject("error")
        if (errorObj != null) {
            if (accountLoginRequests.remove(id)) {
                startDeviceCodeLogin()
                return
            }
            val errorMsg = errorObj.optString("message").ifBlank { "Lỗi server: $errorObj" }
            listener.onError(errorMsg)
            return
        }

        if (id == 1L) {
            afterInitialize()
            return
        }

        if (modelListRequests.remove(id)) {
            val modelsArray = message.optJSONObject("result")?.optJSONArray("models") ?: JSONArray()
            listener.onModelsLoaded(parseModels(modelsArray))
            return
        }

        val threadReq = threadListRequests.remove(id)
        if (threadReq != null) {
            val threadsArray = message.optJSONObject("result")?.optJSONArray("threads") ?: JSONArray()
            listener.onThreadsLoaded(parseThreads(threadsArray), threadReq.archived, threadReq.searchTerm)
            return
        }

        val openKind = openRequests.remove(id)
        if (openKind != null) {
            val result = message.optJSONObject("result")
            if (result != null) {
                parseOpenedThread(result)
            }
            return
        }

        if (usageRequests.remove(id)) {
            val result = message.optJSONObject("result")
            val limits = result?.optJSONObject("rateLimits") ?: result
            if (limits != null) listener.onUsageLoaded(parseUsage(limits))
            return
        }

        if (accountReadRequests.remove(id)) {
            val accountObj = message.optJSONObject("result")?.optJSONObject("account")
            if (accountObj != null) listener.onAccountLoaded(parseAccount(accountObj))
            return
        }

        if (accountLoginRequests.remove(id)) {
            val result = message.optJSONObject("result")
            if (result != null) listener.onAccountLoginChallenge(parseAccountLogin(result))
            return
        }

        if (accountLogoutRequests.remove(id)) {
            listener.onAccountLogoutCompleted()
            refreshAccount()
            return
        }

        if (threadMutationRequests.remove(id)) {
            refreshThreads(false, "")
        }
    }

    private fun handleServerRequest(message: JSONObject) {
        val method = message.optString("method")
        if (method != "item/commandExecution/requestApproval" && method != "item/fileChange/requestApproval") {
            listener.onError("App chưa hỗ trợ yêu cầu từ server: $method")
            return
        }

        val params = message.optJSONObject("params") ?: JSONObject()
        val isCommand = method == "item/commandExecution/requestApproval"
        val command = params.optString("command")
        val reason = params.optString("reason")
        val cwd = params.optString("cwd")

        val sb = StringBuilder()
        if (reason.isNotBlank()) sb.append(reason)
        if (command.isNotBlank()) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append(command)
        }
        if (cwd.isNotBlank()) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append("Thư mục: $cwd")
        }

        val title = if (isCommand) "Yêu cầu chạy lệnh terminal" else "Yêu cầu thay đổi file"
        val details = if (sb.isNotEmpty()) sb.toString() else "Cần phê duyệt từ người dùng"
        val reqId = message.opt("id") ?: 0

        listener.onApprovalRequested(ApprovalRequest(reqId, title, details))
    }

    private fun parseModels(modelsArray: JSONArray): List<CodexModel> {
        val result = mutableListOf<CodexModel>()
        for (i in 0 until modelsArray.length()) {
            val obj = modelsArray.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val displayName = obj.optString("displayName").ifBlank { id }
            val defaultEffort = obj.optString("defaultEffort")
            val isDefault = obj.optBoolean("isDefault")

            val efforts = mutableListOf<String>()
            val effortsArray = obj.optJSONArray("efforts")
            if (effortsArray != null) {
                for (j in 0 until effortsArray.length()) {
                    val e = effortsArray.optString(j)
                    if (e.isNotBlank()) efforts.add(e)
                }
            }
            if (id.isNotBlank()) {
                result.add(CodexModel(id, displayName, defaultEffort, efforts, isDefault))
            }
        }
        return result
    }

    private fun parseThreads(threadsArray: JSONArray): List<ThreadSummary> {
        val result = mutableListOf<ThreadSummary>()
        for (i in 0 until threadsArray.length()) {
            val obj = threadsArray.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val title = obj.optString("name").ifBlank { obj.optString("title", "Cuộc trò chuyện") }
            val preview = obj.optString("preview").takeIf { it.isNotBlank() }
            val updatedAt = obj.optLong("updatedAt", System.currentTimeMillis() / 1000)
            val cwd = obj.optString("cwd")
            if (id.isNotBlank()) {
                result.add(ThreadSummary(id, title, preview, updatedAt, cwd))
            }
        }
        return result
    }

    private fun parseOpenedThread(result: JSONObject) {
        val thread = result.optJSONObject("thread") ?: result
        val threadId = thread.optString("id")
        activeThreadId = threadId
        val model = thread.optString("model").takeIf { it.isNotBlank() }
        val turns = thread.optJSONArray("turns") ?: JSONArray()

        val history = parseHistory(turns)
        var title = thread.optString("name").takeIf { it.isNotBlank() }
            ?: history.firstOrNull { it.isUser }?.text?.take(80)
            ?: "Cuộc trò chuyện mới"

        listener.onThreadReady(threadId, title, model, history)
    }

    private fun parseHistory(turns: JSONArray): List<HistoryMessage> {
        val messages = mutableListOf<HistoryMessage>()
        for (i in 0 until turns.length()) {
            val turn = turns.optJSONObject(i) ?: continue
            val items = turn.optJSONArray("items") ?: continue
            for (j in 0 until items.length()) {
                val item = items.optJSONObject(j) ?: continue
                val id = item.optString("id")
                when (item.optString("type")) {
                    "userMessage" -> {
                        val content = item.optJSONArray("content")
                        val text = if (content != null) {
                            (0 until content.length()).mapNotNull { idx ->
                                content.optJSONObject(idx)?.optString("text")
                            }.joinToString("\n")
                        } else {
                            item.optString("text")
                        }
                        if (text.isNotBlank()) messages.add(HistoryMessage(id, true, text))
                    }
                    "agentMessage" -> {
                        val text = item.optString("text")
                        val phase = item.optString("phase")
                        if (text.isNotBlank() && (phase.isBlank() || phase == "final_answer")) {
                            messages.add(HistoryMessage(id, false, text))
                        }
                    }
                }
            }
        }
        return messages
    }

    private fun parseUsage(limits: JSONObject): UsageSnapshot {
        val primaryObj = limits.optJSONObject("primary")
        val primary = primaryObj?.let {
            UsageWindow(
                it.optInt("usedPercent"),
                if (it.has("windowDurationMins")) it.optLong("windowDurationMins") else null,
                if (it.has("resetsAt")) it.optLong("resetsAt") else null
            )
        }
        val secondaryObj = limits.optJSONObject("secondary")
        val secondary = secondaryObj?.let {
            UsageWindow(
                it.optInt("usedPercent"),
                if (it.has("windowDurationMins")) it.optLong("windowDurationMins") else null,
                if (it.has("resetsAt")) it.optLong("resetsAt") else null
            )
        }
        val creditBalance = limits.optString("creditBalance").takeIf { it.isNotBlank() }
        val creditsUnlimited = limits.optBoolean("creditsUnlimited")
        val planType = limits.optString("planType").takeIf { it.isNotBlank() }

        return UsageSnapshot(primary, secondary, creditBalance, creditsUnlimited, planType)
    }

    private fun parseAccount(accountObj: JSONObject): CodexAccount {
        val email = accountObj.optString("email")
        val planType = accountObj.optString("planType").takeIf { it.isNotBlank() }
        val type = accountObj.optString("type", "chatgpt")
        return CodexAccount(email, planType, type)
    }

    private fun parseAccountLogin(result: JSONObject): AccountLoginChallenge {
        val loginId = result.optString("loginId")
        val authUrl = result.optString("authUrl")
        val verificationUrl = result.optString("verificationUrl")
        val verificationUri = result.optString("verificationUri")
        val url = result.optString("url")
        val finalUrl = authUrl.ifBlank { verificationUrl.ifBlank { verificationUri.ifBlank { url } } }
        val userCode = result.optString("userCode")
        return AccountLoginChallenge(loginId, finalUrl, userCode)
    }

    fun scheduleReconnect() {
        if (manualDisconnect || reconnectExecutor.isShutdown || !reconnectScheduled.compareAndSet(false, true)) {
            return
        }
        val attempt = reconnectAttempts.incrementAndGet()
        val delay = (attempt * 2L).coerceAtMost(30L)
        listener.onConnectionChanged("Mất kết nối, thử lại sau ${delay}s…", false)

        reconnectExecutor.schedule({
            reconnectScheduled.set(false)
            if (!manualDisconnect) {
                webSocket = null
                connect()
            }
        }, delay, TimeUnit.SECONDS)
    }

    private inner class SocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            reconnectAttempts.set(0L)
            reconnectScheduled.set(false)
            listener.onConnectionChanged("Đang xác thực…", true)
            initialize()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            this@CodexWebSocketClient.webSocket = null
            if (!manualDisconnect) {
                listener.onConnectionChanged("Kết nối đóng", false)
                scheduleReconnect()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            this@CodexWebSocketClient.webSocket = null
            if (!manualDisconnect) {
                val code = response?.code
                val msg = when (code) {
                    401 -> "401 Unauthorized: Sai hoặc thiếu Token Bearer"
                    403 -> "403 Forbidden: Bị từ chối truy cập"
                    404 -> "404 Not Found: Không tìm thấy WebSocket"
                    502, 503 -> "Lỗi $code: Máy chủ Codex chưa phản hồi"
                    else -> t.localizedMessage ?: "Lỗi kết nối socket"
                }
                listener.onError(msg)
                listener.onConnectionChanged(msg, false)
                scheduleReconnect()
            }
        }
    }
}

