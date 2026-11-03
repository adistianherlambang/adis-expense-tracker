package com.mobilecode.app.core.runtime

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Bootstraps the official native Linux Node.js binary and real NPM/NPX CLI distribution
 * on Android without mock polyfills or simulated templates.
 */
object NodeNativeBootstrap {

    private const val TAG = "NodeNativeBootstrap"
    private const val BOOTSTRAP_VERSION = 1

    suspend fun ensureInitialized(context: Context, onProgress: ((String) -> Unit)? = null) = withContext(Dispatchers.IO) {
        val filesDir = context.filesDir
        val usrDir = File(filesDir, "usr")
        val usrBin = File(usrDir, "bin")
        val usrLib = File(usrDir, "lib")
        val npmDir = File(usrLib, "node_modules/npm")
        val markerFile = File(usrDir, ".bootstrap_v$BOOTSTRAP_VERSION")

        if (markerFile.exists() && File(usrBin, "node").exists() && File(npmDir, "bin/npm-cli.js").exists()) {
            return@withContext
        }

        onProgress?.invoke("Initializing Native Linux Node.js & NPM environment...\n")
        usrBin.mkdirs()
        npmDir.mkdirs()

        // 1. Locate native executable compiled into APK native library directory
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val nativeNodeLauncher = File(nativeLibDir, "libnode_launcher.so")

        // 2. Create 'node' wrapper script in $PATH
        val nodeScript = File(usrBin, "node")
        val nodeScriptContent = """
            #!/system/bin/sh
            export LD_LIBRARY_PATH="${nativeLibDir.absolutePath}:${'$'}LD_LIBRARY_PATH"
            exec "${nativeNodeLauncher.absolutePath}" "$@"
        """.trimIndent() + "\n"
        nodeScript.writeText(nodeScriptContent, Charsets.UTF_8)
        nodeScript.setExecutable(true, false)
        nodeScript.setReadable(true, false)

        // 3. Create 'npm' wrapper script in $PATH
        val npmCliJs = File(npmDir, "bin/npm-cli.js")
        val npmScript = File(usrBin, "npm")
        val npmScriptContent = """
            #!/system/bin/sh
            DIR="${'$'}(cd "${'$'}(dirname "${'$'}0")" && pwd)"
            export PATH="${'$'}DIR:${'$'}PATH"
            export LD_LIBRARY_PATH="${nativeLibDir.absolutePath}:${'$'}LD_LIBRARY_PATH"
            export NODE_PATH="${File(usrLib, "node_modules").absolutePath}"
            exec "${nodeScript.absolutePath}" "${npmCliJs.absolutePath}" "$@"
        """.trimIndent() + "\n"
        npmScript.writeText(npmScriptContent, Charsets.UTF_8)
        npmScript.setExecutable(true, false)
        npmScript.setReadable(true, false)

        // 4. Create 'npx' wrapper script in $PATH
        val npxCliJs = File(npmDir, "bin/npx-cli.js")
        val npxScript = File(usrBin, "npx")
        val npxScriptContent = """
            #!/system/bin/sh
            DIR="${'$'}(cd "${'$'}(dirname "${'$'}0")" && pwd)"
            export PATH="${'$'}DIR:${'$'}PATH"
            export LD_LIBRARY_PATH="${nativeLibDir.absolutePath}:${'$'}LD_LIBRARY_PATH"
            export NODE_PATH="${File(usrLib, "node_modules").absolutePath}"
            exec "${nodeScript.absolutePath}" "${npxCliJs.absolutePath}" "$@"
        """.trimIndent() + "\n"
        npxScript.writeText(npxScriptContent, Charsets.UTF_8)
        npxScript.setExecutable(true, false)
        npxScript.setReadable(true, false)

        // 5. Extract official NPM distribution tarball from assets
        try {
            context.assets.open("npm.tgz").use { assetIn ->
                extractTgzStream(assetIn, npmDir)
            }
            onProgress?.invoke("Official NPM distribution installed to ${npmDir.absolutePath}\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract npm.tgz from assets", e)
            onProgress?.invoke("Warning: could not extract bundled npm: ${e.message}\n")
        }

        // Mark initialization complete
        markerFile.writeText("OK", Charsets.UTF_8)
        onProgress?.invoke("Native Linux Node.js environment ready.\n")
    }

    fun getUsrBin(context: Context): File {
        return File(context.filesDir, "usr/bin")
    }

    fun isNativeNodeAvailable(context: Context): Boolean {
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        return File(nativeLibDir, "libnode_launcher.so").exists()
    }

    private fun extractTgzStream(inputStream: InputStream, targetDir: File) {
        GZIPInputStream(BufferedInputStream(inputStream)).use { gzIn ->
            extractTar(gzIn, targetDir)
        }
    }

    private fun extractTar(tarIn: InputStream, targetDir: File) {
        val header = ByteArray(512)
        val targetCanonical = targetDir.canonicalPath
        var nextLongName: String? = null

        while (true) {
            val bytesRead = readFully(tarIn, header, 512)
            if (bytesRead < 512) break
            if (isAllZero(header)) break

            val rawName = parseTarString(header, 0, 100)
            val fullEntryName = nextLongName ?: rawName
            nextLongName = null

            val size = parseTarOctal(header, 124, 12)
            val typeFlag = header[156].toInt().toChar()
            val paddingSize = ((512 - (size % 512)) % 512).toInt()
            val dataBlocksSize = size + paddingSize

            when (typeFlag) {
                'L' -> {
                    val nameBytes = ByteArray(size.toInt())
                    readFully(tarIn, nameBytes, size.toInt())
                    skipFully(tarIn, paddingSize.toLong())
                    nextLongName = parseTarString(nameBytes, 0, nameBytes.size)
                }
                '5' -> {
                    val relPath = stripTopLevelDir(fullEntryName)
                    if (relPath.isNotEmpty()) {
                        val outDir = File(targetDir, relPath)
                        if (outDir.canonicalPath.startsWith(targetCanonical)) {
                            outDir.mkdirs()
                        }
                    }
                    skipFully(tarIn, dataBlocksSize)
                }
                '0', '\u0000' -> {
                    val relPath = stripTopLevelDir(fullEntryName)
                    val outFile = if (relPath.isNotEmpty()) File(targetDir, relPath) else null
                    if (outFile != null && outFile.canonicalPath.startsWith(targetCanonical)) {
                        outFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
                        FileOutputStream(outFile).use { fos ->
                            copyFixedBytes(tarIn, fos, size)
                        }
                    } else {
                        skipFully(tarIn, size)
                    }
                    skipFully(tarIn, paddingSize.toLong())
                }
                else -> {
                    skipFully(tarIn, dataBlocksSize)
                }
            }
        }
    }

    private fun stripTopLevelDir(entryName: String): String {
        val normalized = entryName.trimStart('/', '.')
        val slashIdx = normalized.indexOf('/')
        return if (slashIdx >= 0) normalized.substring(slashIdx + 1) else ""
    }

    private fun readFully(inStream: InputStream, b: ByteArray, len: Int): Int {
        var n = 0
        while (n < len) {
            val count = inStream.read(b, n, len - n)
            if (count < 0) break
            n += count
        }
        return n
    }

    private fun skipFully(inStream: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        while (remaining > 0) {
            val skipped = inStream.skip(remaining)
            if (skipped <= 0) {
                if (inStream.read() == -1) break
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun copyFixedBytes(inStream: InputStream, outStream: FileOutputStream, count: Long) {
        val buf = ByteArray(8192)
        var remaining = count
        while (remaining > 0) {
            val toRead = if (remaining < buf.size) remaining.toInt() else buf.size
            val n = inStream.read(buf, 0, toRead)
            if (n < 0) break
            outStream.write(buf, 0, n)
            remaining -= n
        }
    }

    private fun isAllZero(b: ByteArray): Boolean {
        for (v in b) {
            if (v != 0.toByte()) return false
        }
        return true
    }

    private fun parseTarString(b: ByteArray, offset: Int, length: Int): String {
        var end = offset
        while (end < offset + length && b[end] != 0.toByte()) {
            end++
        }
        return String(b, offset, end - offset, Charsets.UTF_8).trim()
    }

    private fun parseTarOctal(b: ByteArray, offset: Int, length: Int): Long {
        var result = 0L
        for (i in offset until offset + length) {
            val c = b[i].toInt().toChar()
            if (c in '0'..'7') {
                result = (result shl 3) + (c - '0')
            } else if (c == ' ' || c == '\u0000') {
                if (result > 0) break
            }
        }
        return result
    }
}
