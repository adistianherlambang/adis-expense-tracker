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
        if (isPortRunning(port)) {
            val existing = _activeServers.value.find { it.port == port }
            if (existing != null) return Result.success(existing)
            stopServer(port)
        }

        return try {
            val serverSocket = ServerSocket(port)
            serverSocket.reuseAddress = true
            runningSockets[port] = serverSocket

            val info = DevServerInfo(
                port = port,
                rootDir = rootDir.canonicalFile,
                url = "http://localhost:$port",
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
        return try {
            s?.close()
            _activeServers.value = _activeServers.value.filter { it.port != port }
            true
        } catch (e: Exception) {
            false
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
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://localhost:$port")).apply {
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
                    handleClient(client, rootDir)
                }
            } catch (e: Exception) {
                if (serverSocket.isClosed) break
            }
        }
    }

    private fun handleClient(client: Socket, rootDir: File) {
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
                    if (htmlCandidate.exists()) {
                        targetFile = htmlCandidate
                    } else {
                        // SPA fallback to index.html if route doesn't match a static file
                        val indexFile = File(rootDir, "index.html")
                        if (indexFile.exists()) {
                            targetFile = indexFile
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
                    val notFound = "<html><body><h1>404 Not Found</h1><p>File $rawPath was not found in ${rootDir.name}</p></body></html>"
                    val bytes = notFound.toByteArray(Charsets.UTF_8)
                    val header = buildString {
                        append("HTTP/1.1 404 Not Found\r\n")
                        append("Content-Type: text/html; charset=utf-8\r\n")
                        append("Content-Length: ${bytes.size}\r\n")
                        append("Connection: close\r\n")
                        append("\r\n")
                    }
                    output.write(header.toByteArray(Charsets.UTF_8))
                    output.write(bytes)
                    output.flush()
                }
            }
        } catch (e: Exception) {
            // Ignore client disconnects
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

