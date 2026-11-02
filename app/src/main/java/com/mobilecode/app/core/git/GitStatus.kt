package com.mobilecode.app.core.git

enum class GitFileStatus {
    MODIFIED,
    ADDED,
    DELETED,
    UNTRACKED,
    RENAMED,
    CONFLICT
}

data class GitChangedFile(
    val path: String,
    val status: GitFileStatus,
    val isStaged: Boolean
) {
    val statusBadge: String
        get() = when (status) {
            GitFileStatus.MODIFIED -> "M"
            GitFileStatus.ADDED -> "A"
            GitFileStatus.DELETED -> "D"
            GitFileStatus.UNTRACKED -> "U"
            GitFileStatus.RENAMED -> "R"
            GitFileStatus.CONFLICT -> "C"
        }
}

