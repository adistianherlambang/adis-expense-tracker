package com.mobilecode.app.core.filesystem

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class WorkspaceManager(private val context: Context) {

    val appContext: Context = context.applicationContext

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentWorkspaceDir = MutableStateFlow<File?>(null)
    val currentWorkspaceDir: StateFlow<File?> = _currentWorkspaceDir.asStateFlow()

    private val _fileTree = MutableStateFlow<List<FileNode>>(emptyList())
    val fileTree: StateFlow<List<FileNode>> = _fileTree.asStateFlow()

    companion object {
        private const val PREFS_NAME = "mobile_code_workspace_prefs"
        private const val KEY_LAST_WORKSPACE_PATH = "last_workspace_path"
    }

    init {
        // Ensure sandbox directories exist and automatically restore the last opened workspace if available
        getSandboxRoot()
        getSandboxTmpDirectory()
        restoreLastWorkspaceSync()
    }

    /**
     * Root directory of the isolated application sandbox.
     * All workspaces, runtime packages, and temporary files are strictly confined within this root.
     */
    fun getSandboxRoot(): File {
        val root = appContext.filesDir.canonicalFile
        if (!root.exists()) {
            root.mkdirs()
        }
        return root
    }

    fun getSandboxTmpDirectory(): File {
        val tmp = File(getSandboxRoot(), "tmp").canonicalFile
        if (!tmp.exists()) {
            tmp.mkdirs()
        }
        return tmp
    }

    fun getWorkspacesDirectory(): File {
        val dir = File(getSandboxRoot(), "workspaces").canonicalFile
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Verifies that [candidate] resolves inside the isolated sandbox root, preventing path traversal.
     */
    fun isInsideSandbox(candidate: File): Boolean {
        return try {
            val sandboxPath = getSandboxRoot().canonicalPath
            val targetPath = candidate.canonicalPath
            targetPath == sandboxPath || targetPath.startsWith("$sandboxPath${File.separator}")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clamps any file path to remain within the isolated sandbox root.
     */
    fun confineToSandbox(candidate: File, fallbackDir: File = getWorkspacesDirectory()): File {
        return try {
            val canonical = candidate.canonicalFile
            if (isInsideSandbox(canonical)) canonical else fallbackDir.canonicalFile
        } catch (e: Exception) {
            fallbackDir
        }
    }

    /**
     * Restores the previously opened workspace folder on startup if one was persisted.
     */
    fun restoreLastWorkspaceSync(): File? {
        val savedPath = prefs.getString(KEY_LAST_WORKSPACE_PATH, null) ?: return null
        val savedDir = File(savedPath)
        if (savedDir.exists() && savedDir.isDirectory && isInsideSandbox(savedDir)) {
            _currentWorkspaceDir.value = savedDir.canonicalFile
            refreshTreeSync()
            return savedDir.canonicalFile
        } else {
            prefs.edit().remove(KEY_LAST_WORKSPACE_PATH).apply()
            return null
        }
    }

    fun listAvailableProjects(): List<File> {
        val root = getWorkspacesDirectory()
        return root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    suspend fun openWorkspace(dir: File): Boolean = withContext(Dispatchers.IO) {
        val safeDir = confineToSandbox(dir)
        if (safeDir.exists() && safeDir.isDirectory) {
            _currentWorkspaceDir.value = safeDir
            prefs.edit().putString(KEY_LAST_WORKSPACE_PATH, safeDir.canonicalPath).apply()
            refreshTreeSync()
            true
        } else {
            false
        }
    }

    suspend fun createNewProject(name: String): File? = withContext(Dispatchers.IO) {
        val cleanName = name.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
        if (cleanName.isEmpty()) return@withContext null

        val newDir = confineToSandbox(File(getWorkspacesDirectory(), cleanName))
        if (!newDir.exists()) {
            newDir.mkdirs()
        }
        _currentWorkspaceDir.value = newDir
        prefs.edit().putString(KEY_LAST_WORKSPACE_PATH, newDir.canonicalPath).apply()
        refreshTreeSync()
        newDir
    }

    suspend fun closeWorkspace() = withContext(Dispatchers.IO) {
        _currentWorkspaceDir.value = null
        _fileTree.value = emptyList()
        prefs.edit().remove(KEY_LAST_WORKSPACE_PATH).apply()
    }

    fun clearWorkspaceState() {
        _currentWorkspaceDir.value = null
        _fileTree.value = emptyList()
    }

    fun refreshTreeSync() {
        val root = _currentWorkspaceDir.value
        if (root == null || !root.exists() || !isInsideSandbox(root)) {
            _fileTree.value = emptyList()
            return
        }
        _fileTree.value = buildTree(root, 0)
    }

    suspend fun refreshTree() = withContext(Dispatchers.IO) {
        refreshTreeSync()
    }

    private fun buildTree(directory: File, level: Int): List<FileNode> {
        if (!isInsideSandbox(directory)) return emptyList()
        val files = directory.listFiles() ?: return emptyList()
        return files
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .map { file ->
                val children = if (file.isDirectory) buildTree(file, level + 1) else emptyList()
                FileNode(
                    file = file,
                    name = file.name,
                    isDirectory = file.isDirectory,
                    isExpanded = false,
                    children = children,
                    level = level
                )
            }
    }

    suspend fun readFile(file: File): String = withContext(Dispatchers.IO) {
        try {
            if (isInsideSandbox(file) && file.exists() && file.isFile) {
                file.readText(Charsets.UTF_8)
            } else {
                ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    suspend fun writeFile(file: File, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isInsideSandbox(file)) return@withContext false
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            file.writeText(content, Charsets.UTF_8)
            refreshTreeSync()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun createFile(parentDir: File, name: String): File? = withContext(Dispatchers.IO) {
        try {
            val newFile = File(parentDir, name).canonicalFile
            if (!isInsideSandbox(newFile)) return@withContext null
            newFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
            if (!newFile.exists()) {
                newFile.createNewFile()
            }
            refreshTreeSync()
            newFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun createDirectory(parentDir: File, name: String): File? = withContext(Dispatchers.IO) {
        try {
            val newDir = File(parentDir, name).canonicalFile
            if (!isInsideSandbox(newDir)) return@withContext null
            if (newDir.mkdirs() || newDir.exists()) {
                refreshTreeSync()
                newDir
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun deleteFile(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isInsideSandbox(file)) return@withContext false
            val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
            if (deleted) refreshTreeSync()
            deleted
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun renameFile(file: File, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isInsideSandbox(file)) return@withContext false
            val target = File(file.parentFile, newName).canonicalFile
            if (!isInsideSandbox(target)) return@withContext false
            val renamed = file.renameTo(target)
            if (renamed) refreshTreeSync()
            renamed
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
