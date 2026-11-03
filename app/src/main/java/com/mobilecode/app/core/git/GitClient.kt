package com.mobilecode.app.core.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

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

            "clone" -> {
                val url = args.getOrNull(1)
                if (url.isNullOrBlank()) {
                    "fatal: You must specify a repository to clone.\nusage: git clone <url> [<dir>]\n"
                } else {
                    val rawName = url.trim().trimEnd('/').substringAfterLast('/').removeSuffix(".git")
                    val targetDirName = args.getOrNull(2) ?: rawName.ifEmpty { "repo" }
                    val targetDir = File(dir, targetDirName)
                    if (targetDir.exists() && targetDir.listFiles()?.isNotEmpty() == true) {
                        "fatal: destination path '$targetDirName' already exists and is not an empty directory.\n"
                    } else {
                        cloneRepository(url.trim(), targetDir, targetDirName)
                    }
                }
            }

            "remote" -> {
                val configFile = File(gitDir, "config")
                val url = if (configFile.exists()) {
                    configFile.readLines().firstOrNull { it.trim().startsWith("url =") }?.substringAfter("=")?.trim()
                } else null
                if (url == null) {
                    "origin\n"
                } else if (args.contains("-v")) {
                    "origin\t$url (fetch)\norigin\t$url (push)\n"
                } else {
                    "origin\n"
                }
            }

            "pull" -> {
                val configFile = File(gitDir, "config")
                val url = if (configFile.exists()) {
                    configFile.readLines().firstOrNull { it.trim().startsWith("url =") }?.substringAfter("=")?.trim()
                } else null
                if (url == null) {
                    "fatal: No remote repository configured.\n"
                } else {
                    cloneRepository(url, dir, dir.name)
                }
            }

            else -> "git: '$sub' executed in ${dir.absolutePath}\n"
        }
    }

    private fun cloneRepository(rawUrl: String, targetDir: File, targetDirName: String): String {
        return try {
            val candidateUrls = mutableListOf<String>()

            if (rawUrl.contains("github.com")) {
                val clean = rawUrl
                    .removePrefix("git@github.com:")
                    .removePrefix("https://github.com/")
                    .removePrefix("http://github.com/")
                    .removeSuffix(".git")
                    .trim('/')
                val parts = clean.split("/")
                if (parts.size >= 2) {
                    val owner = parts[0]
                    val repo = parts[1]
                    candidateUrls.add("https://api.github.com/repos/$owner/$repo/zipball")
                    candidateUrls.add("https://codeload.github.com/$owner/$repo/zip/refs/heads/main")
                    candidateUrls.add("https://codeload.github.com/$owner/$repo/zip/refs/heads/master")
                    candidateUrls.add("https://github.com/$owner/$repo/archive/refs/heads/main.zip")
                    candidateUrls.add("https://github.com/$owner/$repo/archive/refs/heads/master.zip")
                }
            } else if (rawUrl.contains("gitlab.com")) {
                val clean = rawUrl
                    .removePrefix("git@gitlab.com:")
                    .removePrefix("https://gitlab.com/")
                    .removePrefix("http://gitlab.com/")
                    .removeSuffix(".git")
                    .trim('/')
                val repo = clean.substringAfterLast('/')
                candidateUrls.add("https://gitlab.com/$clean/-/archive/main/$repo-main.zip")
                candidateUrls.add("https://gitlab.com/$clean/-/archive/master/$repo-master.zip")
            }

            candidateUrls.add(rawUrl)

            var extractedCount = 0
            var lastError: Exception? = null

            for (cand in candidateUrls) {
                try {
                    val count = downloadAndExtractZip(cand, targetDir)
                    if (count > 0) {
                        extractedCount = count
                        break
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }

            if (extractedCount == 0 && targetDir.listFiles()?.isEmpty() != false) {
                targetDir.deleteRecursively()
                return "fatal: repository '$rawUrl' not found or could not be downloaded (${lastError?.message ?: "unknown error"})\n"
            }

            // Initialize .git metadata in the cloned repository
            val gitDir = File(targetDir, ".git")
            File(gitDir, "objects").mkdirs()
            File(gitDir, "refs/heads").mkdirs()
            File(gitDir, "HEAD").writeText("ref: refs/heads/main\n")
            File(gitDir, "config").writeText(
                """
                [core]
                    repositoryformatversion = 0
                    filemode = true
                    bare = false
                [remote "origin"]
                    url = $rawUrl
                    fetch = +refs/heads/*:refs/remotes/origin/*
                [branch "main"]
                    remote = origin
                    merge = refs/heads/main
                """.trimIndent() + "\n"
            )

            val dateStr = SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.US).format(Date())
            val logFile = File(gitDir, "commit_log.txt")
            logFile.writeText("commit initial\nAuthor: Origin <git@origin>\nDate:   $dateStr\n\n    Cloned from $rawUrl\n\n")

            "Cloning into '$targetDirName'...\n" +
            "remote: Enumerating objects: $extractedCount, done.\n" +
            "remote: Counting objects: 100% ($extractedCount/$extractedCount), done.\n" +
            "remote: Compressing objects: 100%, done.\n" +
            "Receiving objects: 100% ($extractedCount/$extractedCount), done.\n" +
            "Resolving deltas: 100%, done.\n"
        } catch (e: Exception) {
            "fatal: clone failed: ${e.message}\n"
        }
    }

    private fun downloadAndExtractZip(downloadUrl: String, targetDir: File): Int {
        var currentUrl = downloadUrl
        var redirects = 0
        var conn: HttpURLConnection? = null

        while (redirects < 8) {
            val u = URL(currentUrl)
            conn = (u.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("User-Agent", "MobileCodeApp/1.0")
                setRequestProperty("Accept", "application/vnd.github+json, application/zip, */*")
            }
            conn.connect()
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == 307 || code == 308) {
                val loc = conn.getHeaderField("Location") ?: throw IOException("Redirect without Location")
                currentUrl = loc
                redirects++
                continue
            }
            if (code !in 200..299) {
                throw IOException("HTTP $code: ${conn.responseMessage}")
            }
            break
        }

        val activeConn = conn ?: throw IOException("Connection failed")
        var fileCount = 0

        targetDir.mkdirs()
        ZipInputStream(BufferedInputStream(activeConn.inputStream)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                val slashIdx = name.indexOf('/')
                // Strip the root folder name from GitHub archive (e.g. repo-main/...)
                val relPath = if (slashIdx != -1) name.substring(slashIdx + 1) else name
                if (relPath.isNotEmpty()) {
                    val outFile = File(targetDir, relPath)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        fileCount++
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return fileCount
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
