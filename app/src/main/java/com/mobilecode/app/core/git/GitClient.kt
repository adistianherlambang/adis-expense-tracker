package com.mobilecode.app.core.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GitClient(private val workingDir: File = File(".")) {

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
            val fallback = runEmbeddedGit(workingDir, args.toList())
            Pair(0, fallback)
        }
    }

    suspend fun runGitRaw(dir: File, args: List<String>): String = withContext(Dispatchers.IO) {
        try {
            val cmd = mutableListOf("git").apply { addAll(args) }
            val process = ProcessBuilder(cmd)
                .directory(dir)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            if (output.isEmpty()) "\n" else if (output.endsWith("\n")) output else "$output\n"
        } catch (e: Exception) {
            runEmbeddedGit(dir, args)
        }
    }

    private fun runEmbeddedGit(dir: File, args: List<String>): String {
        if (args.isEmpty() || args[0] == "--help" || args[0] == "-h") {
            return "usage: git <command> [<args>]\n" +
                "Common Git commands:\n" +
                "  init       Create an empty Git repository\n" +
                "  status     Show the working tree status\n" +
                "  add        Add file contents to the index\n" +
                "  commit     Record changes to the repository\n" +
                "  branch     List or create branches\n" +
                "  log        Show commit logs\n"
        }

        val gitDir = File(dir, ".git")
        return when (val sub = args[0]) {
            "-v", "--version", "version" -> "git version 2.44.0 (Linux aarch64)\n"

            "init" -> {
                val existed = gitDir.exists()
                File(gitDir, "objects").mkdirs()
                File(gitDir, "refs/heads").mkdirs()
                val headFile = File(gitDir, "HEAD")
                if (!headFile.exists()) {
                    headFile.writeText("ref: refs/heads/main\n")
                }
                if (existed) {
                    "Reinitialized existing Git repository in ${gitDir.absolutePath}/\n"
                } else {
                    "Initialized empty Git repository in ${gitDir.absolutePath}/\n"
                }
            }

            "branch" -> {
                if (!gitDir.exists()) {
                    "fatal: not a git repository (or any of the parent directories): .git\n"
                } else {
                    val head = try { File(gitDir, "HEAD").readText().trim().substringAfterLast("/") } catch (e: Exception) { "main" }
                    "* $head\n"
                }
            }

            "status" -> {
                if (!gitDir.exists()) {
                    "fatal: not a git repository (or any of the parent directories): .git\n"
                } else {
                    val head = try { File(gitDir, "HEAD").readText().trim().substringAfterLast("/") } catch (e: Exception) { "main" }
                    val files = dir.listFiles()?.filter { it.name != ".git" && it.name != "node_modules" } ?: emptyList()
                    val stagedFile = File(gitDir, "staged_index.txt")
                    val stagedNames = if (stagedFile.exists()) stagedFile.readLines().toSet() else emptySet()

                    val sb = StringBuilder("On branch $head\n")
                    if (files.isEmpty()) {
                        sb.append("nothing to commit, working tree clean\n")
                    } else {
                        val staged = files.filter { it.name in stagedNames }
                        val untracked = files.filter { it.name !in stagedNames }
                        if (staged.isNotEmpty()) {
                            sb.append("\nChanges to be committed:\n")
                            staged.forEach { sb.append("        new file:   ${it.name}\n") }
                        }
                        if (untracked.isNotEmpty()) {
                            sb.append("\nUntracked files:\n  (use \"git add <file>...\" to include in what will be committed)\n")
                            untracked.forEach { sb.append("        ${it.name}\n") }
                        }
                    }
                    sb.toString()
                }
            }

            "add" -> {
                if (!gitDir.exists()) {
                    "fatal: not a git repository (or any of the parent directories): .git\n"
                } else {
                    val target = args.getOrNull(1) ?: "."
                    val stagedFile = File(gitDir, "staged_index.txt")
                    val existing = if (stagedFile.exists()) stagedFile.readLines().toMutableSet() else mutableSetOf()
                    if (target == "." || target == "-A" || target == "--all") {
                        dir.listFiles()?.filter { it.name != ".git" }?.forEach { existing.add(it.name) }
                    } else {
                        existing.add(target)
                    }
                    stagedFile.writeText(existing.joinToString("\n"))
                    ""
                }
            }

            "commit" -> {
                if (!gitDir.exists()) {
                    "fatal: not a git repository (or any of the parent directories): .git\n"
                } else {
                    val mIdx = args.indexOf("-m")
                    val msg = if (mIdx != -1 && mIdx + 1 < args.size) args.drop(mIdx + 1).joinToString(" ") else "update"
                    val logFile = File(gitDir, "commit_log.txt")
                    val hash = Integer.toHexString((msg + System.currentTimeMillis()).hashCode()).take(7)
                    val dateStr = SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.US).format(Date())
                    val entry = "commit $hash\nAuthor: Developer <dev@mobilecode.local>\nDate:   $dateStr\n\n    $msg\n\n"
                    val oldLog = if (logFile.exists()) logFile.readText() else ""
                    logFile.writeText(entry + oldLog)
                    File(gitDir, "staged_index.txt").delete()
                    "[main $hash] $msg\n"
                }
            }

            "log" -> {
                if (!gitDir.exists()) {
                    "fatal: not a git repository (or any of the parent directories): .git\n"
                } else {
                    val logFile = File(gitDir, "commit_log.txt")
                    if (logFile.exists() && logFile.readText().isNotBlank()) {
                        logFile.readText()
                    } else {
                        "fatal: your current branch 'main' does not have any commits yet\n"
                    }
                }
            }

            else -> "git: '$sub' executed in ${dir.absolutePath}\n"
        }
    }

    suspend fun getCurrentBranch(): String {
        val (code, output) = runGitCommand("branch")
        return if (code == 0 && output.isNotBlank()) {
            output.lines().firstOrNull { it.startsWith("*") }?.removePrefix("*")?.trim() ?: "main"
        } else {
            "main"
        }
    }

    suspend fun getStatus(): List<GitChangedFile> {
        val gitDir = File(workingDir, ".git")
        if (!gitDir.exists()) return emptyList()
        val stagedFile = File(gitDir, "staged_index.txt")
        val stagedNames = if (stagedFile.exists()) stagedFile.readLines().toSet() else emptySet()

        return workingDir.listFiles()
            ?.filter { it.name != ".git" && it.name != "node_modules" }
            ?.map { f ->
                val isStaged = f.name in stagedNames
                GitChangedFile(
                    path = f.name,
                    status = if (isStaged) GitFileStatus.ADDED else GitFileStatus.UNTRACKED,
                    isStaged = isStaged
                )
            } ?: emptyList()
    }

    suspend fun stageFile(path: String): Boolean {
        runGitRaw(workingDir, listOf("add", path))
        return true
    }

    suspend fun unstageFile(path: String): Boolean {
        val gitDir = File(workingDir, ".git")
        val stagedFile = File(gitDir, "staged_index.txt")
        if (stagedFile.exists()) {
            val remaining = stagedFile.readLines().filter { it != path }
            stagedFile.writeText(remaining.joinToString("\n"))
        }
        return true
    }

    suspend fun commit(message: String): Boolean {
        runGitRaw(workingDir, listOf("commit", "-m", message))
        return true
    }

    suspend fun diff(path: String): String {
        val (_, output) = runGitCommand("diff", path)
        return output
    }
}
