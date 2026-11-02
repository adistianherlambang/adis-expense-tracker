package com.mobilecode.app.core.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GitClient(private val workingDir: File) {

    private suspend fun runGitCommand(vararg args: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            val cmd = mutableListOf("git").apply { addAll(args) }
            val process = ProcessBuilder(cmd)
                .directory(workingDir)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            Pair(exitCode, output)
        } catch (e: Exception) {
            Pair(-1, "Git error: ${e.message}")
        }
    }

    suspend fun getCurrentBranch(): String {
        val (code, output) = runGitCommand("branch", "--show-current")
        return if (code == 0 && output.isNotBlank()) output.trim() else "main"
    }

    suspend fun getStatus(): List<GitChangedFile> {
        val (code, output) = runGitCommand("status", "--porcelain=v1")
        if (code != 0) return emptyList()

        val results = mutableListOf<GitChangedFile>()
        output.lines().forEach { line ->
            if (line.length >= 3) {
                val indexStatus = line[0]
                val workTreeStatus = line[1]
                val path = line.substring(3).trim()

                // Check staged
                if (indexStatus != ' ' && indexStatus != '?') {
                    val status = when (indexStatus) {
                        'M' -> GitFileStatus.MODIFIED
                        'A' -> GitFileStatus.ADDED
                        'D' -> GitFileStatus.DELETED
                        'R' -> GitFileStatus.RENAMED
                        else -> GitFileStatus.MODIFIED
                    }
                    results.add(GitChangedFile(path = path, status = status, isStaged = true))
                }

                // Check unstaged
                if (workTreeStatus != ' ') {
                    val status = when (workTreeStatus) {
                        'M' -> GitFileStatus.MODIFIED
                        'D' -> GitFileStatus.DELETED
                        '?' -> GitFileStatus.UNTRACKED
                        else -> GitFileStatus.MODIFIED
                    }
                    results.add(GitChangedFile(path = path, status = status, isStaged = false))
                }
            }
        }
        return results
    }

    suspend fun stageFile(path: String): Boolean {
        val (code, _) = runGitCommand("add", path)
        return code == 0
    }

    suspend fun unstageFile(path: String): Boolean {
        val (code, _) = runGitCommand("reset", "HEAD", path)
        return code == 0
    }

    suspend fun commit(message: String): Boolean {
        val (code, _) = runGitCommand("commit", "-m", message)
        return code == 0
    }

    suspend fun diff(path: String): String {
        val (_, output) = runGitCommand("diff", path)
        return output
    }
}

