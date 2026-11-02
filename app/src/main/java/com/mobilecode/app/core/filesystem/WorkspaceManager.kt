package com.mobilecode.app.core.filesystem

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class WorkspaceManager(private val context: Context) {

    private val _currentWorkspaceDir = MutableStateFlow<File>(getDefaultWorkspaceDirectory())
    val currentWorkspaceDir: StateFlow<File> = _currentWorkspaceDir.asStateFlow()

    private val _fileTree = MutableStateFlow<List<FileNode>>(emptyList())
    val fileTree: StateFlow<List<FileNode>> = _fileTree.asStateFlow()

    private fun getDefaultWorkspaceDirectory(): File {
        val dir = File(context.filesDir, "workspaces/my-mobile-app")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun setWorkspaceDirectory(dir: File) = withContext(Dispatchers.IO) {
        if (dir.exists() && dir.isDirectory) {
            _currentWorkspaceDir.value = dir
            refreshTree()
        }
    }

    fun initializeDefaultWorkspaceIfNeeded() {
        val workspaceDir = _currentWorkspaceDir.value
        val files = workspaceDir.listFiles()
        if (files == null || files.isEmpty()) {
            copyAssetFolder("sample_project", workspaceDir)
        }
        refreshTreeSync()
    }

    private fun copyAssetFolder(assetPath: String, destDir: File) {
        try {
            val assetManager = context.assets
            val assets = assetManager.list(assetPath) ?: return
            if (!destDir.exists()) destDir.mkdirs()

            for (asset in assets) {
                val fullAssetPath = if (assetPath.isEmpty()) asset else "$assetPath/$asset"
                val subAssets = assetManager.list(fullAssetPath)
                if (subAssets != null && subAssets.isNotEmpty()) {
                    copyAssetFolder(fullAssetPath, File(destDir, asset))
                } else {
                    val destFile = File(destDir, asset)
                    assetManager.open(fullAssetPath).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun refreshTreeSync() {
        val root = _currentWorkspaceDir.value
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
        if (file.exists() && file.isFile) {
            file.readText(Charsets.UTF_8)
        } else {
            ""
        }
    }

    suspend fun writeFile(file: File, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!file.parentFile?.exists()!!) {
                file.parentFile?.mkdirs()
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
            if (newFile.createNewFile()) {
                refreshTreeSync()
                newFile
            } else {
                null
            }
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

