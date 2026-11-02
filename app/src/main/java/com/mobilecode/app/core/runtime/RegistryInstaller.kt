package com.mobilecode.app.core.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Collections
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

data class NpmInstallResult(
    val installedCount: Int,
    val directVersions: Map<String, String>,
    val lockPackages: Map<String, JSONObject>
)

data class PipInstallResult(
    val name: String,
    val version: String,
    val summary: String,
    val extractedFiles: Int,
    val targetDir: File
)

object RegistryInstaller {

    private const val NPM_REGISTRY = "https://registry.npmjs.org"
    private const val PYPI_REGISTRY = "https://pypi.org/pypi"

    /**
     * Installs one or more npm packages (and their transitive dependencies) directly from
     * https://registry.npmjs.org into `<workingDir>/node_modules`.
     */
    suspend fun installNpmPackages(
        packageSpecs: List<String>,
        workingDir: File,
        maxDepth: Int = 3,
        onProgress: (String) -> Unit
    ): NpmInstallResult = withContext(Dispatchers.IO) {
        val nodeModulesDir = File(workingDir, "node_modules")
        if (!nodeModulesDir.exists()) nodeModulesDir.mkdirs()

        val visited = Collections.synchronizedSet(mutableSetOf<String>())
        val directVersions = Collections.synchronizedMap(linkedMapOf<String, String>())
        val lockPackages = Collections.synchronizedMap(linkedMapOf<String, JSONObject>())
        val semaphore = Semaphore(4)

        suspend fun resolveAndInstall(
            rawSpec: String,
            depth: Int,
            isDirect: Boolean
        ) {
            val (pkgName, requestedTag) = parseNpmSpec(rawSpec)
            if (pkgName.isBlank()) return
            if (!visited.add(pkgName)) return

            try {
                val metadata = semaphore.withPermit {
                    fetchNpmPackageVersionMetadata(pkgName, requestedTag)
                } ?: run {
                    if (isDirect) {
                        onProgress("npm ERR! 404 Not Found - GET $NPM_REGISTRY/$pkgName - Package '$pkgName' is not in the npm registry.\n")
                    }
                    return
                }

                val version = metadata.optString("version", "1.0.0")
                val dist = metadata.optJSONObject("dist")
                val tarballUrl = dist?.optString("tarball", "") ?: ""
                val integrity = dist?.optString("integrity", "") ?: ""

                if (tarballUrl.isBlank()) {
                    if (isDirect) {
                        onProgress("npm ERR! No dist tarball found for $pkgName@$version\n")
                    }
                    return
                }

                if (isDirect) {
                    directVersions[pkgName] = "^$version"
                    onProgress("GET $tarballUrl ($pkgName@$version)...\n")
                }

                val targetPkgDir = File(nodeModulesDir, pkgName)
                semaphore.withPermit {
                    downloadAndExtractTgz(tarballUrl, targetPkgDir)
                }

                val depsObj = metadata.optJSONObject("dependencies")
                lockPackages["node_modules/$pkgName"] = JSONObject().apply {
                    put("version", version)
                    put("resolved", tarballUrl)
                    if (integrity.isNotEmpty()) put("integrity", integrity)
                    if (depsObj != null && depsObj.length() > 0) {
                        put("dependencies", depsObj)
                    }
                }

                // Recursively install transitive dependencies from registry.npmjs.org
                if (depth < maxDepth && depsObj != null && depsObj.length() > 0) {
                    val depKeys = depsObj.keys().asSequence().toList()
                    coroutineScope {
                        depKeys.map { depName ->
                            val depRange = depsObj.optString(depName, "latest")
                            async {
                                resolveAndInstall("$depName@$depRange", depth + 1, isDirect = false)
                            }
                        }.awaitAll()
                    }
                }
            } catch (e: Exception) {
                if (isDirect) {
                    onProgress("npm ERR! network error fetching '$pkgName': ${e.message}\n")
                }
            }
        }

        for (spec in packageSpecs) {
            resolveAndInstall(spec, depth = 0, isDirect = true)
        }

        NpmInstallResult(
            installedCount = lockPackages.size,
            directVersions = directVersions,
            lockPackages = lockPackages
        )
    }

    /**
     * Installs a Python package directly from https://pypi.org/pypi/<pkg>/json
     * into `<workingDir>/site-packages`.
     */
    suspend fun installPipPackage(
        rawSpec: String,
        workingDir: File,
        onProgress: (String) -> Unit
    ): PipInstallResult? = withContext(Dispatchers.IO) {
        val pkgName = rawSpec
            .substringBefore("==")
            .substringBefore(">=")
            .substringBefore("<=")
            .trim()
        if (pkgName.isBlank()) return@withContext null

        val url = "$PYPI_REGISTRY/${URLEncoder.encode(pkgName, "UTF-8")}/json"
        val rootJson = httpGetJson(url) ?: run {
            onProgress("ERROR: Could not find a version that satisfies the requirement $pkgName (from PyPI)\n")
            return@withContext null
        }

        val info = rootJson.optJSONObject("info") ?: JSONObject()
        val realName = info.optString("name", pkgName)
        val version = info.optString("version", "1.0.0")
        val summary = info.optString("summary", "")

        val urlsArray = rootJson.optJSONArray("urls")
        var wheelUrl: String? = null
        var wheelFilename: String? = null
        var sdistUrl: String? = null
        var sdistFilename: String? = null

        if (urlsArray != null) {
            for (i in 0 until urlsArray.length()) {
                val item = urlsArray.optJSONObject(i) ?: continue
                val packagetype = item.optString("packagetype", "")
                val fileUrl = item.optString("url", "")
                val filename = item.optString("filename", "")
                if (packagetype == "bdist_wheel" && fileUrl.isNotBlank()) {
                    // Prefer pure python wheel (py3-none-any) if available
                    if (wheelUrl == null || filename.contains("none-any")) {
                        wheelUrl = fileUrl
                        wheelFilename = filename
                    }
                } else if (packagetype == "sdist" && fileUrl.isNotBlank() && sdistUrl == null) {
                    sdistUrl = fileUrl
                    sdistFilename = filename
                }
            }
        }

        val sitePackagesDir = File(workingDir, "site-packages")
        if (!sitePackagesDir.exists()) sitePackagesDir.mkdirs()

        val chosenUrl = wheelUrl ?: sdistUrl
        val chosenFilename = wheelFilename ?: sdistFilename ?: "$realName-$version.whl"

        if (chosenUrl == null) {
            onProgress("ERROR: No downloadable wheel or source archive found on PyPI for $realName==$version\n")
            return@withContext null
        }

        onProgress("Collecting $realName==$version\n  Downloading $chosenFilename from PyPI...\n")

        val extractedFiles = if (chosenUrl.endsWith(".whl") || chosenUrl.endsWith(".zip")) {
            downloadAndExtractWheelZip(chosenUrl, sitePackagesDir)
        } else {
            val pkgTarget = File(sitePackagesDir, realName.lowercase().replace('-', '_'))
            downloadAndExtractTgz(chosenUrl, pkgTarget)
        }

        PipInstallResult(
            name = realName,
            version = version,
            summary = summary,
            extractedFiles = extractedFiles,
            targetDir = sitePackagesDir
        )
    }

    private fun parseNpmSpec(rawSpec: String): Pair<String, String> {
        val trimmed = rawSpec.trim()
        if (trimmed.startsWith("@")) {
            val secondAt = trimmed.indexOf('@', startIndex = 1)
            return if (secondAt > 1) {
                trimmed.substring(0, secondAt) to trimmed.substring(secondAt + 1)
            } else {
                trimmed to "latest"
            }
        } else {
            val atIdx = trimmed.indexOf('@')
            return if (atIdx > 0) {
                trimmed.substring(0, atIdx) to trimmed.substring(atIdx + 1)
            } else {
                trimmed to "latest"
            }
        }
    }

    private fun fetchNpmPackageVersionMetadata(pkgName: String, rawVersionOrTag: String): JSONObject? {
        val encodedName = if (pkgName.startsWith("@")) {
            "@" + URLEncoder.encode(pkgName.substring(1), "UTF-8").replace("%2F", "/")
        } else {
            URLEncoder.encode(pkgName, "UTF-8")
        }

        val cleanExact = rawVersionOrTag.trim()
            .removePrefix("^")
            .removePrefix("~")
            .removePrefix(">=")
            .removePrefix("=")
            .trim()

        // 1. If exact version (e.g., "4.21.2") or "latest", query /<pkg>/<versionOrLatest> directly (very fast!)
        val isSimpleVersionOrTag = cleanExact == "latest" || cleanExact.matches(Regex("^[0-9]+\\.[0-9]+\\.[0-9]+(-[A-Za-z0-9.-]+)?$"))
        if (isSimpleVersionOrTag) {
            val directUrl = "$NPM_REGISTRY/$encodedName/$cleanExact"
            val directJson = httpGetJson(directUrl)
            if (directJson != null && directJson.has("dist")) {
                return directJson
            }
        }

        // 2. Fallback to /<pkg>/latest for complex semver ranges (e.g., ">= 1.0.0 < 2.0.0" or "2.x")
        val latestUrl = "$NPM_REGISTRY/$encodedName/latest"
        return httpGetJson(latestUrl)
    }

    private fun httpGetJson(urlString: String): JSONObject? {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "npm/10.5.0 node/v20.11.0 linux arm64")
        }
        return try {
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                JSONObject(body)
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Downloads a `.tgz` / `.tar.gz` archive and extracts all real files into [targetDir],
     * stripping the top-level `package/` prefix used by npm tarballs.
     */
    private fun downloadAndExtractTgz(urlString: String, targetDir: File): Int {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "npm/10.5.0 node/v20.11.0 linux arm64")
        }
        try {
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode} for $urlString")
            }
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            targetDir.mkdirs()

            GZIPInputStream(BufferedInputStream(conn.inputStream)).use { gzipIn ->
                return extractTarStream(gzipIn, targetDir)
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun extractTarStream(tarIn: InputStream, targetDir: File): Int {
        val header = ByteArray(512)
        var extractedCount = 0
        var nextLongName: String? = null
        val targetCanonical = targetDir.canonicalPath

        while (true) {
            val readHeaderBytes = readFully(tarIn, header, 512)
            if (readHeaderBytes < 512 || isAllZero(header)) {
                break
            }

            val rawName = extractNullTerminatedString(header, 0, 100)
            val prefix = extractNullTerminatedString(header, 345, 155)
            val size = parseOctalLong(header, 124, 12)
            val typeFlag = header[156].toInt().toChar()

            val fullEntryName = nextLongName ?: if (prefix.isNotEmpty()) "$prefix/$rawName" else rawName
            nextLongName = null

            val dataBlocksSize = ((size + 511L) / 512L) * 512L
            val paddingSize = (dataBlocksSize - size).toInt()

            when (typeFlag) {
                'L' -> {
                    // GNU Tar LongLink: payload is the full path of the next entry
                    val nameBytes = ByteArray(size.toInt())
                    readFully(tarIn, nameBytes, size.toInt())
                    skipFully(tarIn, paddingSize.toLong())
                    nextLongName = extractNullTerminatedString(nameBytes, 0, nameBytes.size)
                }

                'x', 'g' -> {
                    // PAX extended header: check if it defines path=...
                    val paxBytes = ByteArray(size.toInt())
                    readFully(tarIn, paxBytes, size.toInt())
                    skipFully(tarIn, paddingSize.toLong())
                    val paxText = String(paxBytes, Charsets.UTF_8)
                    paxText.lineSequence().forEach { line ->
                        val spaceIdx = line.indexOf(' ')
                        if (spaceIdx > 0) {
                            val kv = line.substring(spaceIdx + 1)
                            if (kv.startsWith("path=")) {
                                nextLongName = kv.removePrefix("path=").trim()
                            }
                        }
                    }
                }

                '5' -> {
                    // Directory
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
                    // Regular file
                    val relPath = stripTopLevelDir(fullEntryName)
                    val outFile = if (relPath.isNotEmpty()) File(targetDir, relPath) else null
                    if (outFile != null && outFile.canonicalPath.startsWith(targetCanonical)) {
                        outFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
                        FileOutputStream(outFile).use { fos ->
                            copyFixedBytes(tarIn, fos, size)
                        }
                        extractedCount++
                    } else {
                        skipFully(tarIn, size)
                    }
                    skipFully(tarIn, paddingSize.toLong())
                }

                else -> {
                    // Symlink or other header type: skip payload
                    skipFully(tarIn, dataBlocksSize)
                }
            }
        }
        return extractedCount
    }

    private fun downloadAndExtractWheelZip(urlString: String, targetDir: File): Int {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "pip/24.0 python/3.11.8 linux aarch64")
        }
        var extractedCount = 0
        val targetCanonical = targetDir.canonicalPath
        try {
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode} for $urlString")
            }
            ZipInputStream(BufferedInputStream(conn.inputStream)).use { zipIn ->
                var entry = zipIn.nextEntry
                val buf = ByteArray(8192)
                while (entry != null) {
                    val outFile = File(targetDir, entry.name)
                    if (outFile.canonicalPath.startsWith(targetCanonical)) {
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
                            FileOutputStream(outFile).use { fos ->
                                var n: Int
                                while (zipIn.read(buf).also { n = it } != -1) {
                                    fos.write(buf, 0, n)
                                }
                            }
                            extractedCount++
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
            return extractedCount
        } finally {
            conn.disconnect()
        }
    }

    private fun stripTopLevelDir(entryName: String): String {
        val normalized = entryName.trimStart('/', '.')
        val slashIdx = normalized.indexOf('/')
        return if (slashIdx >= 0) normalized.substring(slashIdx + 1) else normalized
    }

    private fun readFully(input: InputStream, buffer: ByteArray, length: Int): Int {
        var total = 0
        while (total < length) {
            val r = input.read(buffer, total, length - total)
            if (r == -1) break
            total += r
        }
        return total
    }

    private fun skipFully(input: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val scratch = ByteArray(4096)
        while (remaining > 0) {
            val toRead = minOf(remaining, scratch.size.toLong()).toInt()
            val r = input.read(scratch, 0, toRead)
            if (r <= 0) break
            remaining -= r
        }
    }

    private fun copyFixedBytes(input: InputStream, out: FileOutputStream, size: Long) {
        var remaining = size
        val buf = ByteArray(8192)
        while (remaining > 0) {
            val toRead = minOf(remaining, buf.size.toLong()).toInt()
            val r = input.read(buf, 0, toRead)
            if (r == -1) break
            out.write(buf, 0, r)
            remaining -= r
        }
    }

    private fun isAllZero(block: ByteArray): Boolean {
        for (b in block) {
            if (b.toInt() != 0) return false
        }
        return true
    }

    private fun extractNullTerminatedString(bytes: ByteArray, offset: Int, maxLen: Int): String {
        var end = offset
        val limit = (offset + maxLen).coerceAtMost(bytes.size)
        while (end < limit && bytes[end].toInt() != 0) {
            end++
        }
        return String(bytes, offset, end - offset, Charsets.UTF_8).trim()
    }

    private fun parseOctalLong(bytes: ByteArray, offset: Int, maxLen: Int): Long {
        val str = extractNullTerminatedString(bytes, offset, maxLen).trim()
        if (str.isEmpty()) return 0L
        return try {
            str.toLong(8)
        } catch (e: Exception) {
            0L
        }
    }
}

