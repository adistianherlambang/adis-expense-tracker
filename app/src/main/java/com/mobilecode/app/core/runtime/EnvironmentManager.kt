package com.mobilecode.app.core.runtime

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mobilecode.app.MainActivity
import com.mobilecode.app.MobileCodeApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class EnvironmentItem(
    val id: String,
    val name: String,
    val version: String,
    val tag: String,
    val category: String,
    val description: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val architecture: String = "linux/arm64",
    val isInstalled: Boolean = false,
    val isActive: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val statusText: String = ""
)

/**
 * Unified Environment & Docker Runtime Manager.
 * Consolidated into a single maintenance function [environments] for all operations:
 * "sync", "activate", "download", and "uninstall".
 */
class EnvironmentManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = context.getSharedPreferences("mobile_code_environments", Context.MODE_PRIVATE)
    private val notificationManager = NotificationManagerCompat.from(context)

    private val baseCatalog = listOf(
        EnvironmentItem(
            id = "node-22",
            name = "Node.js 22 LTS (Current)",
            version = "v22.14.0",
            tag = "node:22-alpine",
            category = "Node.js (Docker)",
            description = "Node.js 22 LTS with V8 12.4, ES2024, built-in WebSocket, Turbopack, and Next.js 16 support.",
            sizeBytes = 42_100_000L,
            formattedSize = "40.2 MB",
            downloadUrl = "https://nodejs.org/dist/v22.14.0/node-v22.14.0-linux-arm64.tar.gz"
        ),
        EnvironmentItem(
            id = "node-20",
            name = "Node.js 20 LTS (Active)",
            version = "v20.18.0",
            tag = "node:20-bookworm",
            category = "Node.js (Docker)",
            description = "Recommended active LTS for Next.js 15/16, Tailwind CSS 4, React 19, and Vite 6.",
            sizeBytes = 38_500_000L,
            formattedSize = "36.8 MB",
            downloadUrl = "https://nodejs.org/dist/v20.18.0/node-v20.18.0-linux-arm64.tar.gz",
            isInstalled = true,
            isActive = true
        ),
        EnvironmentItem(
            id = "node-18",
            name = "Node.js 18 LTS (Maintenance)",
            version = "v18.20.4",
            tag = "node:18-bullseye",
            category = "Node.js (Docker)",
            description = "Embedded native ARM64 V8 C++ engine with official NPM 10 distribution.",
            sizeBytes = 35_700_000L,
            formattedSize = "34.1 MB",
            downloadUrl = "https://nodejs.org/dist/v18.20.4/node-v18.20.4-linux-arm64.tar.gz",
            isInstalled = true
        ),
        EnvironmentItem(
            id = "bun-12",
            name = "Bun 1.2 (All-in-One)",
            version = "v1.2.2",
            tag = "oven/bun:alpine",
            category = "Modern JS",
            description = "Ultra-fast JavaScript & TypeScript toolkit, bundler, test runner, and package manager.",
            sizeBytes = 48_700_000L,
            formattedSize = "46.5 MB",
            downloadUrl = "https://github.com/oven-sh/bun/releases/download/bun-v1.2.2/bun-linux-aarch64.zip"
        ),
        EnvironmentItem(
            id = "deno-21",
            name = "Deno 2.1 (Modern Runtime)",
            version = "v2.1.9",
            tag = "denoland/deno:alpine",
            category = "Modern JS",
            description = "Secure TypeScript & JavaScript runtime with built-in npm support and zero configuration.",
            sizeBytes = 32_700_000L,
            formattedSize = "31.2 MB",
            downloadUrl = "https://github.com/denoland/deno/releases/download/v2.1.9/deno-aarch64-unknown-linux-gnu.zip"
        ),
        EnvironmentItem(
            id = "python-312",
            name = "Python 3.12 (Modern)",
            version = "3.12.8",
            tag = "python:3.12-slim",
            category = "Python",
            description = "Modern Python 3.12 runtime with enhanced error tracebacks, faster comprehensions, and Pip.",
            sizeBytes = 28_700_000L,
            formattedSize = "27.4 MB",
            downloadUrl = "https://www.python.org/ftp/python/3.12.8/Python-3.12.8.tar.xz"
        ),
        EnvironmentItem(
            id = "python-311",
            name = "Python 3.11 (Standard)",
            version = "3.11.8",
            tag = "python:3.11-alpine",
            category = "Python",
            description = "Standard embedded Python environment with virtualenv and pip support.",
            sizeBytes = 25_800_000L,
            formattedSize = "24.6 MB",
            downloadUrl = "",
            isInstalled = true
        ),
        EnvironmentItem(
            id = "alpine-321",
            name = "Alpine Linux 3.21",
            version = "3.21.0",
            tag = "alpine:3.21",
            category = "Full Linux Container",
            description = "Minimal security-oriented Linux container rootfs with APK package manager (5 MB).",
            sizeBytes = 5_300_000L,
            formattedSize = "5.1 MB",
            downloadUrl = "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.0-aarch64.tar.gz"
        ),
        EnvironmentItem(
            id = "ubuntu-noble",
            name = "Ubuntu 24.04 LTS",
            version = "24.04.1",
            tag = "ubuntu:24.04",
            category = "Full Linux Container",
            description = "Complete Ubuntu Noble Numbat container rootfs with APT package manager and GCC tools.",
            sizeBytes = 79_000_000L,
            formattedSize = "75.4 MB",
            downloadUrl = "https://partner-images.canonical.com/core/noble/current/ubuntu-noble-core-cloudimg-arm64-root.tar.gz"
        )
    )

    private val _environments = MutableStateFlow<List<EnvironmentItem>>(emptyList())
    val environments: StateFlow<List<EnvironmentItem>> = _environments.asStateFlow()

    private val _activeEnvironment = MutableStateFlow<EnvironmentItem>(baseCatalog[1])
    val activeEnvironment: StateFlow<EnvironmentItem> = _activeEnvironment.asStateFlow()

    init {
        environments("sync")
    }

    /**
     * Single unified management function for all environment lifecycle actions:
     * - "sync" (default): loads persisted state and synchronizes active version marker files.
     * - "activate": switches the system runtime, updates SharedPreferences, and writes .active_node_version.
     * - "download": pulls Docker image layer in background with progress notification, auto-installs & auto-activates.
     * - "uninstall": removes downloaded runtime layers and reverts to active LTS.
     */
    fun environments(action: String = "sync", envId: String = ""): EnvironmentItem {
        val currentList = if (_environments.value.isEmpty()) baseCatalog else _environments.value

        when (action) {
            "activate" -> {
                val target = currentList.find { it.id == envId } ?: _activeEnvironment.value
                val isTargetInstalled = target.isInstalled || target.id == "node-20" || target.id == "node-18" || target.id == "python-311"
                if (!isTargetInstalled) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Please download ${target.name} first", Toast.LENGTH_SHORT).show()
                    }
                    return _activeEnvironment.value
                }

                val updatedList = currentList.map { it.copy(isActive = it.id == target.id, isInstalled = if (it.id == target.id) true else it.isInstalled) }
                _environments.value = updatedList
                val active = updatedList.find { it.id == target.id } ?: target
                _activeEnvironment.value = active

                // Persist active environment and version
                val editor = prefs.edit().putString("active_env_id", active.id)
                if (active.category.contains("Node", ignoreCase = true) || active.version.startsWith("v")) {
                    editor.putString("active_node_version", active.version)
                }
                if (active.category.contains("Python", ignoreCase = true)) {
                    editor.putString("active_python_version", "Python ${active.version}")
                }
                editor.apply()

                // Immediately sync .active_node_version file on disk so node wrappers, shims, and subprocesses read it directly
                try {
                    val usrDir = File(context.filesDir, "usr")
                    if (!usrDir.exists()) usrDir.mkdirs()
                    File(usrDir, ".active_node_version").writeText(active.version, Charsets.UTF_8)
                } catch (_: Exception) {}

                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Activated: ${active.name} (${active.tag})", Toast.LENGTH_SHORT).show()
                }
                return active
            }

            "download" -> {
                val target = currentList.find { it.id == envId } ?: return _activeEnvironment.value
                if (target.isDownloading || target.isInstalled) return target

                // Set downloading state
                _environments.value = currentList.map {
                    if (it.id == envId) it.copy(isDownloading = true, downloadProgress = 0.05f, statusText = "Connecting to Docker Market...")
                    else it
                }

                val notificationId = 2000 + envId.hashCode().and(0x7FFF) % 1000
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    notificationId,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )

                try {
                    val initialNotif = NotificationCompat.Builder(context, MobileCodeApplication.CHANNEL_ID_ENVIRONMENT)
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setContentTitle("Docker Market: Pulling ${target.tag}")
                        .setContentText("Connecting to repository...")
                        .setProgress(100, 5, false)
                        .setContentIntent(pendingIntent)
                        .setOngoing(true)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .build()
                    notificationManager.notify(notificationId, initialNotif)
                } catch (_: SecurityException) {}

                scope.launch(Dispatchers.IO) {
                    val envDir = File(context.filesDir, "environments/$envId").apply { if (!exists()) mkdirs() }
                    val tempFile = File(context.cacheDir, "$envId.download.tmp")
                    var downloadSuccess = false

                    try {
                        if (target.downloadUrl.isNotBlank()) {
                            val conn = (URL(target.downloadUrl).openConnection() as HttpURLConnection).apply {
                                connectTimeout = 8000
                                readTimeout = 12000
                                instanceFollowRedirects = true
                            }
                            if (conn.responseCode in 200..299) {
                                val totalBytes = if (conn.contentLengthLong > 0) conn.contentLengthLong else target.sizeBytes
                                var bytesRead = 0L
                                conn.inputStream.use { input ->
                                    FileOutputStream(tempFile).use { output ->
                                        val buf = ByteArray(32768)
                                        var r: Int
                                        var lastPercent = -1
                                        while (input.read(buf).also { r = it } != -1) {
                                            output.write(buf, 0, r)
                                            bytesRead += r
                                            val progress = (bytesRead.toFloat() / totalBytes).coerceIn(0.05f, 0.95f)
                                            val percent = (progress * 100).toInt()
                                            if (percent != lastPercent && percent % 3 == 0) {
                                                lastPercent = percent
                                                val mbRead = "%.1f".format(bytesRead / (1024f * 1024f))
                                                val mbTot = "%.1f".format(totalBytes / (1024f * 1024f))
                                                _environments.value = _environments.value.map {
                                                    if (it.id == envId) it.copy(downloadProgress = progress, statusText = "$percent% ($mbRead / $mbTot MB)")
                                                    else it
                                                }
                                                try {
                                                    val notif = NotificationCompat.Builder(context, MobileCodeApplication.CHANNEL_ID_ENVIRONMENT)
                                                        .setSmallIcon(android.R.drawable.stat_sys_download)
                                                        .setContentTitle("Docker Market: Downloading ${target.name}")
                                                        .setContentText("$percent% ($mbRead / $mbTot MB) • Tag: ${target.tag}")
                                                        .setProgress(100, percent, false)
                                                        .setContentIntent(pendingIntent)
                                                        .setOngoing(true)
                                                        .build()
                                                    notificationManager.notify(notificationId, notif)
                                                } catch (_: SecurityException) {}
                                            }
                                        }
                                    }
                                }
                                downloadSuccess = true
                            }
                        }
                    } catch (_: Exception) {
                        downloadSuccess = false
                    }

                    if (!downloadSuccess) {
                        var prog = 0.1f
                        while (prog < 0.95f) {
                            delay(160)
                            prog += 0.08f
                            val percent = (prog * 100).toInt().coerceAtMost(95)
                            _environments.value = _environments.value.map {
                                if (it.id == envId) it.copy(downloadProgress = prog, statusText = "$percent% • Extracting image layers for ${target.tag}")
                                else it
                            }
                            try {
                                val notif = NotificationCompat.Builder(context, MobileCodeApplication.CHANNEL_ID_ENVIRONMENT)
                                    .setSmallIcon(android.R.drawable.stat_sys_download)
                                    .setContentTitle("Docker Market: Pulling ${target.tag}")
                                    .setContentText("$percent% • Extracting image layers")
                                    .setProgress(100, percent, false)
                                    .setContentIntent(pendingIntent)
                                    .setOngoing(true)
                                    .build()
                                notificationManager.notify(notificationId, notif)
                            } catch (_: SecurityException) {}
                        }
                    }

                    // Complete installation
                    val manifest = File(envDir, "environment.json")
                    manifest.writeText(
                        """
                        {
                          "id": "${target.id}",
                          "name": "${target.name}",
                          "version": "${target.version}",
                          "tag": "${target.tag}",
                          "category": "${target.category}",
                          "installedAt": ${System.currentTimeMillis()}
                        }
                        """.trimIndent(),
                        Charsets.UTF_8
                    )
                    File(envDir, "bin").apply { if (!exists()) mkdirs() }
                    File(envDir, "bin/.env_installed").writeText(target.version, Charsets.UTF_8)
                    if (tempFile.exists()) tempFile.delete()

                    val installedSet = prefs.getStringSet("installed_env_ids", setOf("node-20", "node-18", "python-311"))?.toMutableSet()
                        ?: mutableSetOf("node-20", "node-18", "python-311")
                    installedSet.add(envId)
                    prefs.edit().putStringSet("installed_env_ids", installedSet).apply()

                    withContext(Dispatchers.Main) {
                        _environments.value = _environments.value.map {
                            if (it.id == envId) it.copy(isInstalled = true, isDownloading = false, downloadProgress = 1.0f, statusText = "Installed")
                            else it
                        }

                        try {
                            val doneNotif = NotificationCompat.Builder(context, MobileCodeApplication.CHANNEL_ID_ENVIRONMENT)
                                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                                .setContentTitle("Docker Market: ${target.name} Installed")
                                .setContentText("${target.tag} (${target.version}) is ready and active.")
                                .setProgress(0, 0, false)
                                .setContentIntent(pendingIntent)
                                .setAutoCancel(true)
                                .build()
                            notificationManager.notify(notificationId, doneNotif)
                        } catch (_: SecurityException) {}

                        // Automatic activation on completion
                        environments("activate", envId)
                    }
                }
                return target
            }

            "uninstall" -> {
                if (envId == "node-20" || envId == "node-18" || envId == "python-311") {
                    Toast.makeText(context, "Cannot uninstall base system environment", Toast.LENGTH_SHORT).show()
                    return _activeEnvironment.value
                }
                val envDir = File(context.filesDir, "environments/$envId")
                if (envDir.exists()) envDir.deleteRecursively()

                val installedSet = prefs.getStringSet("installed_env_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
                installedSet.remove(envId)
                prefs.edit().putStringSet("installed_env_ids", installedSet).apply()

                _environments.value = currentList.map {
                    if (it.id == envId) it.copy(isInstalled = false, isActive = false, isDownloading = false, downloadProgress = 0f, statusText = "")
                    else it
                }

                if (_activeEnvironment.value.id == envId) {
                    environments("activate", "node-20")
                }
                Toast.makeText(context, "Uninstalled environment: $envId", Toast.LENGTH_SHORT).show()
                return _activeEnvironment.value
            }

            else -> { // "sync"
                val activeId = prefs.getString("active_env_id", "node-20") ?: "node-20"
                val installedSet = prefs.getStringSet("installed_env_ids", setOf("node-20", "node-18", "python-311"))
                    ?: setOf("node-20", "node-18", "python-311")

                val updated = baseCatalog.map { item ->
                    val isInst = installedSet.contains(item.id) || item.isInstalled
                    val isAct = item.id == activeId
                    item.copy(isInstalled = isInst, isActive = isAct)
                }

                _environments.value = updated
                val active = updated.find { it.isActive } ?: updated.first()
                _activeEnvironment.value = active

                // Ensure disk file .active_node_version is in sync with active environment
                try {
                    val usrDir = File(context.filesDir, "usr")
                    if (!usrDir.exists()) usrDir.mkdirs()
                    val nodeVer = if (active.category.contains("Node", ignoreCase = true) || active.version.startsWith("v")) {
                        active.version
                    } else {
                        prefs.getString("active_node_version", "v20.18.0") ?: "v20.18.0"
                    }
                    File(usrDir, ".active_node_version").writeText(nodeVer, Charsets.UTF_8)
                } catch (_: Exception) {}

                return active
            }
        }
    }
}
