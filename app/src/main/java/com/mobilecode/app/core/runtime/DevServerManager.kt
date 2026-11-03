package com.mobilecode.app.core.runtime

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

data class DevServerInfo(
    val port: Int,
    val rootDir: File,
    val url: String,
    val startedAt: Long,
    val name: String
)

object DevServerManager {

    private val _activeServers = MutableStateFlow<List<DevServerInfo>>(emptyList())
    val activeServers: StateFlow<List<DevServerInfo>> = _activeServers.asStateFlow()

    private val runningSockets = ConcurrentHashMap<Int, ServerSocket>()
    private val scope = CoroutineScope(Dispatchers.IO)

    @Synchronized
    fun isPortRunning(port: Int): Boolean {
        val s = runningSockets[port]
        return s != null && !s.isClosed
    }

    @Synchronized
    fun startServer(
        port: Int = 3000,
        rootDir: File,
        name: String = "Dev Server"
    ): Result<DevServerInfo> {
        if (port < 1024) {
            return Result.failure(
                IllegalArgumentException("Port $port is a privileged system port (< 1024) restricted by Android Linux kernel. Please use a port >= 1024 (e.g. 3000, 5173, 8000, 8080).")
            )
        }

        if (runningSockets.containsKey(port)) {
            stopServer(port)
        }

        return try {
            val serverSocket = ServerSocket()
            serverSocket.reuseAddress = true
            var bound = false
            val bindAttempts: List<() -> Unit> = listOf(
                { serverSocket.bind(java.net.InetSocketAddress(java.net.InetAddress.getByName("127.0.0.1"), port)) },
                { serverSocket.bind(java.net.InetSocketAddress(port)) },
                { serverSocket.bind(java.net.InetSocketAddress(java.net.InetAddress.getByName("0.0.0.0"), port)) }
            )
            for (attempt in bindAttempts) {
                try {
                    attempt()
                    bound = true
                    break
                } catch (_: Exception) {}
            }
            if (!bound) {
                serverSocket.bind(java.net.InetSocketAddress(port))
            }
            runningSockets[port] = serverSocket

            val info = DevServerInfo(
                port = port,
                rootDir = rootDir.canonicalFile,
                url = "http://127.0.0.1:$port",
                startedAt = System.currentTimeMillis(),
                name = name
            )
            _activeServers.value = _activeServers.value.filter { it.port != port } + info

            scope.launch {
                listenLoop(serverSocket, rootDir, port)
            }

            Result.success(info)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    @Synchronized
    fun stopServer(port: Int): Boolean {
        val s = runningSockets.remove(port)
        _activeServers.value = _activeServers.value.filter { it.port != port }
        return try {
            s?.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    @Synchronized
    fun registerExternalPort(
        port: Int,
        name: String = "Dev Server",
        rootDir: File = File(".")
    ) {
        val existing = _activeServers.value.find { it.port == port }
        if (existing == null) {
            val info = DevServerInfo(
                port = port,
                rootDir = rootDir.canonicalFile,
                url = "http://localhost:$port",
                startedAt = System.currentTimeMillis(),
                name = name
            )
            _activeServers.value = _activeServers.value + info
        }
    }

    fun isPortListeningLocally(port: Int): Boolean {
        if (runningSockets.containsKey(port)) return true
        return try {
            Socket().use { s ->
                s.connect(java.net.InetSocketAddress("127.0.0.1", port), 350)
                true
            }
        } catch (_: Exception) {
            try {
                Socket().use { s ->
                    s.connect(java.net.InetSocketAddress("localhost", port), 350)
                    true
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    fun scanActivePorts(rootDir: File? = null) {
        val candidatePorts = listOf(3000, 3001, 5173, 8080, 8000, 5000, 4200, 8081)
        scope.launch {
            for (p in candidatePorts) {
                if (isPortListeningLocally(p)) {
                    val label = when (p) {
                        3000, 3001 -> "React / Webpack Dev Server"
                        5173 -> "Vite Dev Server"
                        8080, 8081 -> "Web Server"
                        else -> "Live Dev Server"
                    }
                    registerExternalPort(p, label, rootDir ?: File("."))
                }
            }
        }
    }

    @Synchronized
    fun stopAllServers() {
        runningSockets.forEach { (port, socket) ->
            try { socket.close() } catch (ignored: Exception) {}
        }
        runningSockets.clear()
        _activeServers.value = emptyList()
    }

    fun openInBrowser(context: Context, port: Int) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:$port")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun listenLoop(serverSocket: ServerSocket, rootDir: File, port: Int) {
        while (!serverSocket.isClosed) {
            try {
                val client = serverSocket.accept()
                scope.launch {
                    handleClient(client, rootDir, port)
                }
            } catch (e: Exception) {
                if (serverSocket.isClosed) break
            }
        }
    }

    private fun handleClient(client: Socket, rootDir: File, port: Int) {
        try {
            client.use { socket ->
                socket.soTimeout = 10000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val output = BufferedOutputStream(socket.getOutputStream())

                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0]
                val rawPath = parts[1].substringBefore('?').substringBefore('#')
                val path = if (rawPath == "/" || rawPath.isBlank()) "/index.html" else rawPath

                var targetFile = File(rootDir, path.removePrefix("/"))
                if (!targetFile.exists() || targetFile.isDirectory) {
                    val htmlCandidate = File(rootDir, path.removePrefix("/") + ".html")
                    if (htmlCandidate.exists() && htmlCandidate.isFile) {
                        targetFile = htmlCandidate
                    } else if (targetFile.isDirectory && File(targetFile, "index.html").exists()) {
                        targetFile = File(targetFile, "index.html")
                    } else {
                        // Project structure fallbacks
                        val projectIndexCandidates = listOf(
                            File(rootDir, "index.html"),
                            File(rootDir, "public/index.html"),
                            File(rootDir, "1/public/index.html"),
                            File(rootDir, "1/build/index.html"),
                            File(rootDir, "build/index.html"),
                            File(rootDir, "dist/index.html")
                        )
                        val foundIndex = projectIndexCandidates.firstOrNull { it.exists() && it.isFile }
                        if (foundIndex != null && (path == "/index.html" || !path.contains('.'))) {
                            targetFile = foundIndex
                        }
                    }
                }

                if (targetFile.exists() && targetFile.isFile) {
                    val mime = getMimeType(targetFile.name)
                    val bytes = targetFile.readBytes()

                    val header = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: $mime; charset=utf-8\r\n")
                        append("Content-Length: ${bytes.size}\r\n")
                        append("Access-Control-Allow-Origin: *\r\n")
                        append("Connection: close\r\n")
                        append("\r\n")
                    }
                    output.write(header.toByteArray(Charsets.UTF_8))
                    if (method != "HEAD") {
                        output.write(bytes)
                    }
                    output.flush()
                } else {
                    // Serve friendly interactive directory listing & port status instead of a dull 404
                    val html = generateDirectoryListing(rootDir, rawPath, port)
                    val bytes = html.toByteArray(Charsets.UTF_8)
                    val header = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: text/html; charset=utf-8\r\n")
                        append("Content-Length: ${bytes.size}\r\n")
                        append("Access-Control-Allow-Origin: *\r\n")
                        append("Connection: close\r\n")
                        append("\r\n")
                    }
                    output.write(header.toByteArray(Charsets.UTF_8))
                    if (method != "HEAD") {
                        output.write(bytes)
                    }
                    output.flush()
                }
            }
        } catch (e: Exception) {
            // Ignore client disconnects
        }
    }

    private fun generateDirectoryListing(rootDir: File, rawPath: String, port: Int): String {
        val reqPath = if (rawPath == "/" || rawPath.isBlank()) "" else rawPath.removePrefix("/")
        val activeDir = File(rootDir, reqPath).takeIf { it.exists() && it.isDirectory } ?: rootDir
        val files = activeDir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        val isSubdir = activeDir != rootDir
        val parentHref = if (isSubdir) {
            val rel = activeDir.relativeTo(rootDir).path.replace('\\', '/')
            val p = rel.substringBeforeLast('/', "")
            if (p.isEmpty()) "/" else "/$p"
        } else null

        return buildString {
            append("<!DOCTYPE html>\n")
            append("<html lang=\"en\">\n")
            append("<head>\n")
            append("  <meta charset=\"utf-8\">\n")
            append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
            append("  <title>Mobile Code Dev Server :$port</title>\n")
            append("  <style>\n")
            append("    * { box-sizing: border-box; }\n")
            append("    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #18181b; color: #f4f4f5; margin: 0; padding: 20px; }\n")
            append("    .container { max-width: 680px; margin: 20px auto; background: #27272a; border: 1px solid #3f3f46; border-radius: 12px; padding: 24px; box-shadow: 0 10px 30px rgba(0,0,0,0.5); }\n")
            append("    .status-badge { display: inline-flex; align-items: center; gap: 8px; background: #14532d; color: #4ade80; border: 1px solid #16a34a; padding: 5px 14px; border-radius: 9999px; font-size: 12px; font-weight: 600; }\n")
            append("    .status-dot { width: 8px; height: 8px; background: #4ade80; border-radius: 50%; box-shadow: 0 0 8px #4ade80; }\n")
            append("    h1 { font-size: 22px; margin: 16px 0 8px 0; color: #fafafa; }\n")
            append("    p { font-size: 13px; color: #a1a1aa; margin: 0 0 16px 0; }\n")
            append("    code { background: #18181b; padding: 3px 8px; border-radius: 4px; color: #38bdf8; font-family: monospace; font-size: 12px; }\n")
            append("    .file-list { border: 1px solid #3f3f46; border-radius: 8px; overflow: hidden; background: #18181b; margin-top: 14px; }\n")
            append("    .file-item { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 1px solid #27272a; text-decoration: none; color: #e4e4e7; font-size: 14px; }\n")
            append("    .file-item:last-child { border-bottom: none; }\n")
            append("    .file-item:hover { background: #27272a; color: #38bdf8; }\n")
            append("    .file-info { display: flex; align-items: center; gap: 10px; }\n")
            append("    .file-size { color: #71717a; font-size: 12px; }\n")
            append("    .empty-note { padding: 24px; text-align: center; color: #71717a; font-size: 13px; }\n")
            append("  </style>\n")
            append("</head>\n")
            append("<body>\n")
            append("  <div class=\"container\">\n")
            append("    <div class=\"status-badge\"><span class=\"status-dot\"></span> 🟢 PORT $port ACTIVE &amp; RUNNING</div>\n")
            append("    <h1>Mobile Code Dev Server</h1>\n")
            append("    <p>Directory: <code>${activeDir.name}</code> (<code>${activeDir.absolutePath}</code>)</p>\n")
            append("    <div class=\"file-list\">\n")
            if (parentHref != null) {
                append("      <a class=\"file-item\" href=\"$parentHref\"><span class=\"file-info\">📁 .. (parent folder)</span></a>\n")
            }
            if (files.isEmpty()) {
                append("      <div class=\"empty-note\">Folder is empty</div>\n")
            } else {
                for (f in files) {
                    val icon = if (f.isDirectory) "📁" else "📄"
                    val itemRel = f.relativeTo(rootDir).path.replace('\\', '/')
                    val sizeText = if (f.isDirectory) "DIR" else "${f.length()} B"
                    append("      <a class=\"file-item\" href=\"/$itemRel\"><span class=\"file-info\">$icon ${f.name}</span><span class=\"file-size\">$sizeText</span></a>\n")
                }
            }
            append("    </div>\n")
            append("  </div>\n")
            append("</body>\n")
            append("</html>\n")
        }
    }

    private fun getMimeType(filename: String): String {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "html", "htm" -> "text/html"
            "js", "mjs" -> "application/javascript"
            "jsx" -> "application/javascript"
            "ts", "tsx" -> "application/javascript"
            "css" -> "text/css"
            "json" -> "application/json"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "ico" -> "image/x-icon"
            "txt", "md" -> "text/plain"
            "wasm" -> "application/wasm"
            else -> "application/octet-stream"
        }
    }
}

