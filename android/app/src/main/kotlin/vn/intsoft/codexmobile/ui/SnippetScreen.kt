package vn.intsoft.codexmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.intsoft.codexmobile.data.SnippetEntry
import vn.intsoft.codexmobile.data.SnippetKind

val SnippetBackground = Color(4279769627L)
val SnippetPanel = Color(4280295971L)
val SnippetInput = Color(4279309076L)
val SnippetBorder = Color(4282006847L)
val SnippetText = Color(4293454056L)
val SnippetSecondary = Color(4288059803L)
val SnippetGreen = Color(4279280511L)

@Composable
fun SnippetLibraryScreen(
    entries: List<SnippetEntry>,
    currentGroupId: String?,
    onOpenGroup: (SnippetEntry?) -> Unit,
    onCreateGroup: (parentGroupId: String?, title: String) -> Unit,
    onCreateSnippet: (groupId: String?, title: String, content: String) -> Unit,
    onUpdate: (entry: SnippetEntry, title: String, content: String) -> Unit,
    onDelete: (SnippetEntry) -> Unit,
    onRun: (String) -> Unit
) {
    var query by remember(currentGroupId) { mutableStateOf("") }
    var createKind by remember { mutableStateOf<SnippetKind?>(null) }
    var showCreateChoice by remember { mutableStateOf(false) }
    var selectedSnippet by remember { mutableStateOf<SnippetEntry?>(null) }
    var actionEntry by remember { mutableStateOf<SnippetEntry?>(null) }
    var editEntry by remember { mutableStateOf<SnippetEntry?>(null) }
    var deleteEntry by remember { mutableStateOf<SnippetEntry?>(null) }

    val visibleEntries = remember(entries, currentGroupId, query) {
        entries
            .filter { it.parentId == currentGroupId }
            .filter { entry ->
                query.isBlank() || entry.title.contains(query.trim(), ignoreCase = true)
            }
            .sortedWith(
                compareBy<SnippetEntry> { it.kind != SnippetKind.GROUP }
                    .thenBy { it.title.lowercase() }
            )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SnippetBackground)
    ) {
        // Search & Add Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { showCreateChoice = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SnippetPanel)
            ) {
                Text("+", color = SnippetText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Tìm kiếm snippet...", color = SnippetSecondary, fontSize = 14.sp) },
                shape = RoundedCornerShape(22.dp),
                colors = snippetFieldColors(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )
        }

        if (visibleEntries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "{ }",
                        color = Color(4283783516L),
                        fontSize = 28.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = if (query.isBlank()) "Tạo snippet từ các lệnh của bạn" else "Không tìm thấy snippet",
                        color = SnippetSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    if (query.isBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Lưu trữ và chạy lại các câu lệnh thường dùng",
                            color = SnippetSecondary.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(visibleEntries, key = { it.id }) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (entry.kind == SnippetKind.GROUP) {
                                    onOpenGroup(entry)
                                } else {
                                    selectedSnippet = entry
                                }
                            },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = SnippetPanel)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (entry.kind == SnippetKind.GROUP) "📁" else "⚡",
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.title,
                                    color = SnippetText,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (entry.kind == SnippetKind.SNIPPET && entry.content.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = entry.content,
                                        color = SnippetSecondary,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(onClick = { actionEntry = entry }) {
                                Text("⋮", color = SnippetSecondary, fontSize = 20.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Create Choice (Group vs Snippet)
    if (showCreateChoice) {
        AlertDialog(
            onDismissRequest = { showCreateChoice = false },
            title = { Text("Tạo mới", color = SnippetText, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showCreateChoice = false
                            createKind = SnippetKind.GROUP
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SnippetInput)
                    ) {
                        Text("📁  Tạo nhóm mới", color = SnippetText)
                    }
                    Button(
                        onClick = {
                            showCreateChoice = false
                            createKind = SnippetKind.SNIPPET
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SnippetInput)
                    ) {
                        Text("⚡  Tạo Snippet câu lệnh", color = SnippetText)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCreateChoice = false }) {
                    Text("Đóng", color = SnippetSecondary)
                }
            },
            containerColor = SnippetPanel
        )
    }

    // Dialog: Create Dialog
    createKind?.let { kind ->
        SnippetEditorDialog(
            kind = kind,
            initialTitle = "",
            initialContent = "",
            onSave = { title, content ->
                if (kind == SnippetKind.GROUP) {
                    onCreateGroup(currentGroupId, title)
                } else {
                    onCreateSnippet(currentGroupId, title, content)
                }
                createKind = null
            },
            onDismiss = { createKind = null }
        )
    }

    // Dialog: Snippet Detail Dialog
    selectedSnippet?.let { snippet ->
        SnippetDetailDialog(
            snippet = snippet,
            onRun = {
                onRun(snippet.content)
                selectedSnippet = null
            },
            onEdit = {
                editEntry = snippet
                selectedSnippet = null
            },
            onDismiss = { selectedSnippet = null }
        )
    }

    // Dialog: Actions for Entry (More menu)
    actionEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { actionEntry = null },
            title = { Text(entry.title, color = SnippetText, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (entry.kind == SnippetKind.SNIPPET) {
                        Button(
                            onClick = {
                                onRun(entry.content)
                                actionEntry = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = SnippetGreen)
                        ) {
                            Text("Chạy câu lệnh", color = SnippetBackground, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                onOpenGroup(entry)
                                actionEntry = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = SnippetInput)
                        ) {
                            Text("Mở thư mục", color = SnippetText)
                        }
                    }
                    Button(
                        onClick = {
                            editEntry = entry
                            actionEntry = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SnippetInput)
                    ) {
                        Text("Chỉnh sửa", color = SnippetText)
                    }
                    Button(
                        onClick = {
                            deleteEntry = entry
                            actionEntry = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.2f))
                    ) {
                        Text("Xóa", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { actionEntry = null }) {
                    Text("Đóng", color = SnippetSecondary)
                }
            },
            containerColor = SnippetPanel
        )
    }

    // Dialog: Edit Entry
    editEntry?.let { entry ->
        SnippetEditorDialog(
            kind = entry.kind,
            initialTitle = entry.title,
            initialContent = entry.content,
            onSave = { title, content ->
                onUpdate(entry, title, content)
                editEntry = null
            },
            onDismiss = { editEntry = null }
        )
    }

    // Dialog: Confirm Delete
    deleteEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteEntry = null },
            title = { Text("Xác nhận xóa", color = SnippetText, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Bạn có chắc chắn muốn xóa \"${entry.title}\" không?",
                    color = SnippetSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(entry)
                        deleteEntry = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Xóa", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteEntry = null }) {
                    Text("Hủy", color = SnippetSecondary)
                }
            },
            containerColor = SnippetPanel
        )
    }
}

@Composable
private fun SnippetEditorDialog(
    kind: SnippetKind,
    initialTitle: String,
    initialContent: String,
    onSave: (title: String, content: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var content by remember { mutableStateOf(initialContent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (kind == SnippetKind.GROUP) "Nhóm snippet" else "Snippet lệnh",
                color = SnippetText,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tiêu đề", color = SnippetSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = snippetFieldColors()
                )
                if (kind == SnippetKind.SNIPPET) {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Nội dung lệnh (Prompt)", color = SnippetSecondary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 240.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = snippetFieldColors()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title.trim(), content.trim())
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SnippetGreen)
            ) {
                Text("Lưu", color = SnippetBackground, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = SnippetSecondary)
            }
        },
        containerColor = SnippetPanel
    )
}

@Composable
private fun SnippetDetailDialog(
    snippet: SnippetEntry,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(snippet.title, color = SnippetText, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SnippetInput, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = snippet.content.ifBlank { "(Trống)" },
                        color = SnippetText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRun,
                    colors = ButtonDefaults.buttonColors(containerColor = SnippetGreen)
                ) {
                    Text("Chạy", color = SnippetBackground, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(snippet.content))
                }) {
                    Text("Sao chép", color = SnippetGreen)
                }
                TextButton(onClick = onEdit) {
                    Text("Sửa", color = SnippetSecondary)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = SnippetSecondary)
            }
        },
        containerColor = SnippetPanel
    )
}

@Composable
private fun snippetFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SnippetGreen,
    unfocusedBorderColor = SnippetBorder,
    focusedTextColor = SnippetText,
    unfocusedTextColor = SnippetText,
    focusedContainerColor = SnippetInput,
    unfocusedContainerColor = SnippetInput,
    cursorColor = SnippetGreen
)

