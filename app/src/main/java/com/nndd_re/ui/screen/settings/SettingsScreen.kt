package com.nndd_re.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.nndd_re.data.repository.NnddRepository
import com.nndd_re.util.FontEntry
import com.nndd_re.util.NetworkDiscovery
import com.nndd_re.util.ServerAddress
import com.nndd_re.util.ServerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsScreen(onConnected: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val savedHost by ServerPreferences.hostFlow(ctx).collectAsState(initial = "")
    val savedPort by ServerPreferences.portFlow(ctx).collectAsState(initial = 12345)
    val savedScheme by ServerPreferences.schemeFlow(ctx).collectAsState(initial = "http")
    val savedSkipSec by ServerPreferences.skipDurationFlow(ctx).collectAsState(initial = 10)

    var host by remember(savedHost) { mutableStateOf(savedHost) }
    var port by remember(savedPort) { mutableStateOf(savedPort.toString()) }
    var scheme by remember(savedScheme) { mutableStateOf(savedScheme) }
    var skipSec by remember(savedSkipSec) { mutableStateOf(savedSkipSec.toString()) }

    val savedFontPath by ServerPreferences.commentFontPathFlow(ctx).collectAsState(initial = "")
    val fontList by ServerPreferences.fontListFlow(ctx).collectAsState(initial = emptyList())
    var showAddFontDialog by remember { mutableStateOf(false) }
    var addFontName by remember { mutableStateOf("") }
    var addFontUrl by remember { mutableStateOf("") }
    var addFontDownloading by remember { mutableStateOf(false) }
    var fontStatus by remember { mutableStateOf("") }

    val fontsDir = remember { File(ctx.filesDir, "fonts").also { it.mkdirs() } }

    var status by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var scanResults by remember { mutableStateOf<List<String>>(emptyList()) }
    var showScanDialog by remember { mutableStateOf(false) }

    // スキップ秒数は変更後500msで自動保存
    LaunchedEffect(skipSec) {
        val v = skipSec.toIntOrNull() ?: return@LaunchedEffect
        if (v > 0) {
            kotlinx.coroutines.delay(500)
            ServerPreferences.saveSkipDuration(ctx, v)
        }
    }

    // 旧フォント (comment_font.ttf) をリストへ自動マイグレーション
    LaunchedEffect(Unit) {
        val currentList = ServerPreferences.fontListFlow(ctx).first()
        val currentPath = ServerPreferences.commentFontPathFlow(ctx).first()
        if (currentList.isEmpty() && currentPath.isNotBlank()) {
            val f = File(currentPath)
            if (f.exists()) {
                ServerPreferences.saveFontList(ctx, listOf(FontEntry(f.nameWithoutExtension, f.name)))
            }
        }
    }

    // ファイルピッカー
    val fontFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        addFontDownloading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val mimeType = ctx.contentResolver.getType(uri) ?: ""
                    val ext = if (mimeType.contains("otf", ignoreCase = true)) "otf" else "ttf"
                    val filename = "${System.currentTimeMillis()}.$ext"
                    val dest = File(fontsDir, filename)
                    ctx.contentResolver.openInputStream(uri)!!.use { input ->
                        dest.outputStream().use { out -> out.write(input.readBytes()) }
                    }
                    val entryName = addFontName.trim().ifBlank {
                        readFontFullName(dest) ?: dest.nameWithoutExtension
                    }
                    FontEntry(entryName, filename) to dest.absolutePath
                }
            }
            addFontDownloading = false
            result.onSuccess { (entry, path) ->
                val currentList = ServerPreferences.fontListFlow(ctx).first()
                ServerPreferences.saveFontList(ctx, currentList + entry)
                ServerPreferences.saveCommentFontPath(ctx, path)
                addFontName = ""
                showAddFontDialog = false
                fontStatus = "OK: ${entry.name} を追加しました（次回再生から反映）"
            }.onFailure {
                fontStatus = "エラー: ${it.message}"
                showAddFontDialog = false
            }
        }
    }

    if (showScanDialog && scanResults.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showScanDialog = false },
            title = { Text("NNDD-REを選択") },
            text = {
                LazyColumn {
                    items(scanResults) { ip ->
                        TextButton(
                            onClick = { host = ip; scheme = "http"; showScanDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(ip) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showScanDialog = false }) { Text("キャンセル") }
            }
        )
    }

    if (showAddFontDialog) {
        AlertDialog(
            onDismissRequest = { if (!addFontDownloading) showAddFontDialog = false },
            title = { Text("フォントを追加") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = addFontName,
                        onValueChange = { addFontName = it },
                        label = { Text("フォント名（省略可）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = addFontUrl,
                        onValueChange = { addFontUrl = it },
                        label = { Text("URL (.ttf / .otf)") },
                        placeholder = { Text("https://example.com/font.ttf") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                    OutlinedButton(
                        onClick = { fontFilePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !addFontDownloading
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("ファイルから選択")
                    }
                    if (addFontDownloading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("処理中...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val url = addFontUrl.trim()
                        if (url.isBlank()) return@TextButton
                        addFontDownloading = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    val ext = if (url.endsWith(".otf", ignoreCase = true)) "otf" else "ttf"
                                    val filename = "${System.currentTimeMillis()}.$ext"
                                    val dest = File(fontsDir, filename)
                                    val conn = java.net.URL(url).openConnection()
                                    conn.connectTimeout = 10_000
                                    conn.readTimeout = 60_000
                                    conn.getInputStream().use { input ->
                                        dest.outputStream().use { out -> out.write(input.readBytes()) }
                                    }
                                    val entryName = addFontName.trim().ifBlank {
                                        readFontFullName(dest) ?: url.substringAfterLast("/").substringBeforeLast(".")
                                    }
                                    FontEntry(entryName, filename) to dest.absolutePath
                                }
                            }
                            addFontDownloading = false
                            result.onSuccess { (entry, path) ->
                                val currentList = ServerPreferences.fontListFlow(ctx).first()
                                ServerPreferences.saveFontList(ctx, currentList + entry)
                                ServerPreferences.saveCommentFontPath(ctx, path)
                                addFontName = ""
                                addFontUrl = ""
                                showAddFontDialog = false
                                fontStatus = "OK: ${entry.name} を追加しました（次回再生から反映）"
                            }.onFailure {
                                fontStatus = "エラー: ${it.message}"
                                showAddFontDialog = false
                            }
                        }
                    },
                    enabled = !addFontDownloading && addFontUrl.isNotBlank()
                ) { Text("ダウンロード") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddFontDialog = false },
                    enabled = !addFontDownloading
                ) { Text("キャンセル") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("NNDD-REクライアント", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("ホスト (IP / URL)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("ポート") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = skipSec,
            onValueChange = { skipSec = it },
            label = { Text("ダブルタップスキップ秒数") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            suffix = { Text("秒") }
        )
        Spacer(Modifier.height(20.dp))

        if (status.isNotEmpty()) {
            Text(
                status,
                color = if (status.startsWith("OK")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(12.dp))
        }

        // QRスキャンボタン
        Button(
            onClick = {
                status = ""
                val scanner = GmsBarcodeScanning.getClient(ctx)
                scanner.startScan()
                    .addOnSuccessListener { barcode: Barcode ->
                        barcode.rawValue?.let { raw ->
                            val addr = ServerAddress.parse(raw)
                            if (addr != null) {
                                scheme = addr.scheme
                                host = addr.host
                                port = addr.port.toString()
                                status = "OK: QRコードから読み込みました"
                            } else {
                                status = "QRコードを接続先として読めませんでした"
                            }
                        }
                    }
                    .addOnFailureListener { /* キャンセル・失敗は無視 */ }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("QRコードでスキャン")
        }

        Spacer(Modifier.height(8.dp))

        // 自動検出ボタン
        OutlinedButton(
            onClick = {
                scanning = true
                status = ""
                scope.launch {
                    val found = NetworkDiscovery.scan(ctx, port.toIntOrNull() ?: 12345)
                    scanning = false
                    when {
                        found.isEmpty() -> status = "NNDD-REが見つかりませんでした"
                        found.size == 1 -> { host = found[0]; scheme = "http"; status = "OK: ${found[0]} を検出" }
                        else -> { scanResults = found; showScanDialog = true }
                    }
                }
            },
            enabled = !scanning && !testing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (scanning) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("スキャン中...")
            } else {
                Text("ネットワーク自動検出")
            }
        }

        Spacer(Modifier.height(8.dp))

        // ─── コメントフォント ───────────────────────────────────────
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text("コメントフォント", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))

        // Monospace（デフォルト）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { scope.launch { ServerPreferences.saveCommentFontPath(ctx, "") } }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = savedFontPath.isBlank(),
                onClick = { scope.launch { ServerPreferences.saveCommentFontPath(ctx, "") } }
            )
            Text("Monospace（システム等幅）", modifier = Modifier.weight(1f))
        }

        // インストール済みフォント一覧
        fontList.forEach { entry ->
            val fontPath = File(fontsDir, entry.filename).absolutePath
            val fileExists = remember(entry.filename) { File(fontsDir, entry.filename).exists() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (fileExists) scope.launch { ServerPreferences.saveCommentFontPath(ctx, fontPath) }
                    }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = savedFontPath == fontPath,
                    onClick = {
                        if (fileExists) scope.launch { ServerPreferences.saveCommentFontPath(ctx, fontPath) }
                    },
                    enabled = fileExists
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.name)
                    if (!fileExists) {
                        Text(
                            "ファイルが見つかりません",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                IconButton(onClick = {
                    scope.launch {
                        runCatching { File(fontsDir, entry.filename).delete() }
                        val currentList = ServerPreferences.fontListFlow(ctx).first()
                        ServerPreferences.saveFontList(ctx, currentList - entry)
                        if (savedFontPath == fontPath) {
                            ServerPreferences.saveCommentFontPath(ctx, "")
                        }
                        fontStatus = "${entry.name} を削除しました"
                    }
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "削除")
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { addFontName = ""; addFontUrl = ""; showAddFontDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("フォントを追加")
        }

        if (fontStatus.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                fontStatus,
                style = MaterialTheme.typography.bodySmall,
                color = if (fontStatus.startsWith("OK")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    val (s, h, p) = resolveAddress(scheme, host, port)
                    testing = true
                    status = ""
                    scope.launch {
                        val ok = runCatching {
                            NnddRepository(h, p, s).ping()
                        }.getOrDefault(false)
                        status = if (ok) "OK: 接続成功" else "NNDD-REが見つかりませんでした"
                        testing = false
                    }
                },
                enabled = !testing && !scanning && host.isNotBlank()
            ) {
                if (testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("接続テスト")
            }

            Button(
                onClick = {
                    val (s, h, p) = resolveAddress(scheme, host, port)
                    scope.launch {
                        ServerPreferences.save(ctx, h, p, s)
                        onConnected()
                    }
                },
                enabled = host.isNotBlank()
            ) {
                Text("保存して開く")
            }
        }
    }
}

/** ホスト欄にURLが貼られていればそれを優先し、なければ入力済みの scheme / host / port を使う */
private fun resolveAddress(scheme: String, host: String, port: String): Triple<String, String, Int> {
    val h = host.trim()
    if (h.contains("://")) {
        ServerAddress.parse(h)?.let { return Triple(it.scheme, it.host, it.port) }
    }
    return Triple(scheme, h, port.toIntOrNull() ?: if (scheme == "https") 443 else 12345)
}

private fun readFontFullName(file: File): String? = try {
    file.inputStream().use { stream ->
        val data = stream.readBytes()
        val numTables = data.uShortAt(4)
        for (i in 0 until numTables) {
            val base = 12 + i * 16
            if (String(data, base, 4, Charsets.US_ASCII) != "name") continue
            val tableOffset = data.uIntAt(base + 8)
            val count = data.uShortAt(tableOffset + 2)
            val stringsBase = tableOffset + data.uShortAt(tableOffset + 4)
            for (j in 0 until count) {
                val rec = tableOffset + 6 + j * 12
                val platformId = data.uShortAt(rec)
                val nameId = data.uShortAt(rec + 6)
                if (nameId != 4) continue
                val length = data.uShortAt(rec + 8)
                val strOffset = data.uShortAt(rec + 10)
                val charset = if (platformId == 1) Charsets.ISO_8859_1 else Charsets.UTF_16BE
                val name = String(data, stringsBase + strOffset, length, charset).trim()
                if (name.isNotBlank()) return name
            }
            break
        }
        null
    }
} catch (_: Exception) { null }

private fun ByteArray.uShortAt(i: Int) =
    ((this[i].toInt() and 0xFF) shl 8) or (this[i + 1].toInt() and 0xFF)

private fun ByteArray.uIntAt(i: Int) =
    ((this[i].toInt() and 0xFF) shl 24) or
    ((this[i + 1].toInt() and 0xFF) shl 16) or
    ((this[i + 2].toInt() and 0xFF) shl 8) or
    (this[i + 3].toInt() and 0xFF)
