package com.mobilecode.app.core.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class PtyProcess(
    val sessionId: String,
    val workingDir: File,
    private val onOutputReceived: (sessionId: String, data: String) -> Unit
) {
    private var process: Process? = null
    private var stdin: OutputStream? = null
    private var stdout: InputStream? = null
    private var readerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        try {
            val builder = ProcessBuilder("/system/bin/sh", "-i")
            builder.directory(workingDir)

            val env = builder.environment()
            env["TERM"] = "xterm-256color"
            env["HOME"] = workingDir.absolutePath
            env["TMPDIR"] = File(workingDir.parentFile, "tmp").apply { if (!exists()) mkdirs() }.absolutePath
            val currentPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
            env["PATH"] = "$currentPath:/data/data/com.mobilecode.app/files/usr/bin"

            builder.redirectErrorStream(true)
            val proc = builder.start()
            process = proc
            stdin = proc.outputStream
            stdout = proc.inputStream

            // Read process output stream asynchronously
            readerJob = scope.launch {
                val buffer = ByteArray(4096)
                val stream = stdout ?: return@launch
                try {
                    while (isActive) {
                        val read = stream.read(buffer)
                        if (read == -1) break
                        val text = String(buffer, 0, read, Charsets.UTF_8)
                        onOutputReceived(sessionId, text)
                    }
                } catch (e: Exception) {
                    onOutputReceived(sessionId, "\r\n[Process terminated: ${e.message}]\r\n")
                }
            }

            // Print banner and initial prompt
            onOutputReceived(
                sessionId,
                "\u001b[1;34mMobile Code Terminal Shell\u001b[0m\r\n" +
                "Working directory: \u001b[32m${workingDir.absolutePath}\u001b[0m\r\n\r\n"
            )

        } catch (e: Exception) {
            onOutputReceived(sessionId, "\u001b[31mFailed to start shell: ${e.message}\u001b[0m\r\n")
        }
    }

    fun write(data: String) {
        scope.launch(Dispatchers.IO) {
            try {
                stdin?.write(data.toByteArray(Charsets.UTF_8))
                stdin?.flush()
            } catch (e: Exception) {
                onOutputReceived(sessionId, "\r\n[Failed to write to stdin: ${e.message}]\r\n")
            }
        }
    }

    fun destroy() {
        readerJob?.cancel()
        try {
            stdin?.close()
            stdout?.close()
            process?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

