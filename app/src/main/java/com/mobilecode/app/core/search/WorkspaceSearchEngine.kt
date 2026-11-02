package com.mobilecode.app.core.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class SearchMatch(
    val file: File,
    val lineNumber: Int,
    val lineContent: String,
    val matchStart: Int,
    val matchEnd: Int
)

data class SearchResult(
    val file: File,
    val matches: List<SearchMatch>
)

class WorkspaceSearchEngine(private val workspaceDir: File) {

    suspend fun search(
        query: String,
        isRegex: Boolean = false,
        matchCase: Boolean = false,
        matchWholeWord: Boolean = false
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isEmpty()) return@withContext emptyList()

        val results = mutableListOf<SearchResult>()
        val pattern = try {
            val q = if (isRegex) query else Regex.escape(query)
            val bounded = if (matchWholeWord) "\\b$q\\b" else q
            val options = if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
            Regex(bounded, options)
        } catch (e: Exception) {
            return@withContext emptyList()
        }

        fun scanDir(dir: File) {
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (file.name.startsWith(".") || file.name == "node_modules" || file.name == "build") {
                    continue
                }
                if (file.isDirectory) {
                    scanDir(file)
                } else if (file.isFile && file.length() < 2 * 1024 * 1024) { // Max 2MB per text file
                    try {
                        val matchesInFile = mutableListOf<SearchMatch>()
                        file.useLines { lines ->
                            lines.forEachIndexed { index, line ->
                                pattern.findAll(line).forEach { matchResult ->
                                    matchesInFile.add(
                                        SearchMatch(
                                            file = file,
                                            lineNumber = index + 1,
                                            lineContent = line.trim(),
                                            matchStart = matchResult.range.first,
                                            matchEnd = matchResult.range.last
                                        )
                                    )
                                }
                            }
                        }
                        if (matchesInFile.isNotEmpty()) {
                            results.add(SearchResult(file = file, matches = matchesInFile))
                        }
                    } catch (e: Exception) {
                        // Skip binary or unreadable files
                    }
                }
            }
        }

        scanDir(workspaceDir)
        results
    }
}

