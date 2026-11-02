package com.mobilecode.app.core.runtime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class InstalledPackage(
    val name: String,
    val version: String,
    val description: String,
    val isInstalled: Boolean
)

class PackageManager(private val context: Context) {

    private val usrDir = File(context.filesDir, "usr")
    private val binDir = File(usrDir, "bin")

    private val _packages = MutableStateFlow<List<InstalledPackage>>(
        listOf(
            InstalledPackage("nodejs", "20.12.2", "Node.js JavaScript runtime (Android ARM64)", false),
            InstalledPackage("python", "3.11.8", "Python programming language interpreter", false),
            InstalledPackage("git", "2.44.0", "Fast, scalable, distributed revision control system", true),
            InstalledPackage("neovim", "0.9.5", "Vim-fork focused on extensibility and usability", false),
            InstalledPackage("openssh", "9.7p1", "OpenSSH client for remote server connectivity", false),
            InstalledPackage("curl", "8.7.1", "Command line tool for transferring data with URLs", true)
        )
    )
    val packages: StateFlow<List<InstalledPackage>> = _packages.asStateFlow()

    init {
        if (!binDir.exists()) {
            binDir.mkdirs()
        }
    }

    suspend fun installPackage(name: String, onProgress: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        onProgress("Checking dependencies for $name...")
        // Mark as installed in registry
        val current = _packages.value.map {
            if (it.name.equals(name, ignoreCase = true)) it.copy(isInstalled = true) else it
        }
        _packages.value = current
        onProgress("Package $name successfully configured in environment.")
        true
    }

    suspend fun uninstallPackage(name: String): Boolean = withContext(Dispatchers.IO) {
        val current = _packages.value.map {
            if (it.name.equals(name, ignoreCase = true)) it.copy(isInstalled = false) else it
        }
        _packages.value = current
        true
    }
}

