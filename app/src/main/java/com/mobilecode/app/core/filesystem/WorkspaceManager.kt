package com.mobilecode.app.core.filesystem

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class WorkspaceManager(private val context: Context) {

    private val _currentWorkspaceDir = MutableStateFlow<File?>(null)
    val currentWorkspaceDir: StateFlow<File?> = _currentWorkspaceDir.asStateFlow()

    private val _fileTree = MutableStateFlow<List<FileNode>>(emptyList())
    val fileTree: StateFlow<List<FileNode>> = _fileTree.asStateFlow()

    fun getWorkspacesDirectory(): File {
        val dir = File(context.filesDir, "workspaces")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun listAvailableProjects(): List<File> {
        val root = getWorkspacesDirectory()
        return root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    suspend fun openWorkspace(dir: File): Boolean = withContext(Dispatchers.IO) {
        if (dir.exists() && dir.isDirectory) {
            _currentWorkspaceDir.value = dir
            refreshTreeSync()
            true
        } else {
            false
        }
    }

    suspend fun createNewProject(name: String): File? = withContext(Dispatchers.IO) {
        val cleanName = name.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
        if (cleanName.isEmpty()) return@withContext null

        val newDir = File(getWorkspacesDirectory(), cleanName)
        if (!newDir.exists()) {
            newDir.mkdirs()
        }
        _currentWorkspaceDir.value = newDir
        refreshTreeSync()
        newDir
    }

    suspend fun closeWorkspace() = withContext(Dispatchers.IO) {
        _currentWorkspaceDir.value = null
        _fileTree.value = emptyList()
    }

    fun refreshTreeSync() {
        val root = _currentWorkspaceDir.value
        if (root == null || !root.exists()) {
            _fileTree.value = emptyList()
            return
        }
        _fileTree.value = buildTree(root, 0)
    }

    suspend fun refreshTree() = withContext(Dispatchers.IO) {
        refreshTreeSync()
    }

    private fun buildTree(directory: File, level: Int): List<FileNode> {
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
            if (file.exists() && file.isFile) {
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
            val newFile = File(parentDir, name)
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
            val newDir = File(parentDir, name)
            if (newDir.mkdirs()) {
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
            val target = File(file.parentFile, newName)
            val renamed = file.renameTo(target)
            if (renamed) refreshTreeSync()
            renamed
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
