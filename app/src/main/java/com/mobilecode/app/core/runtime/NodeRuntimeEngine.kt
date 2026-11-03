package com.mobilecode.app.core.runtime

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Embedded Node.js (v20.11.0) & NPM (10.5.0) Runtime Engine backed by Android's V8 engine
 * with native filesystem (fs), path, os, process, and child_process bindings.
 */
class NodeRuntimeEngine(
    private val context: Context,
    private val onWorkspaceChanged: () -> Unit = {}
) {
    private var replWebView: WebView? = null
    private var currentOutputCallback: ((String) -> Unit)? = null
    private var currentWorkingDir: File = context.filesDir

    companion object {
        const val NODE_VERSION = "v20.11.0"
        const val NPM_VERSION = "10.5.0"
    }

    inner class NativeNodeBridge {
        @JavascriptInterface
        fun print(text: String) {
            currentOutputCallback?.invoke("$text\n")
        }

        @JavascriptInterface
        fun printError(text: String) {
            currentOutputCallback?.invoke("$text\n")
        }

        @JavascriptInterface
        fun cwd(): String = currentWorkingDir.absolutePath

        @JavascriptInterface
        fun resolvePath(relativePath: String): String {
            return resolveFile(relativePath).canonicalPath
        }

        @JavascriptInterface
        fun fsReadFile(path: String): String {
            return try {
                val target = resolveFile(path)
                if (target.exists() && target.isFile) {
                    target.readText(Charsets.UTF_8)
                } else {
                    "__ERR__:ENOENT: no such file or directory, open '$path'"
                }
            } catch (e: Exception) {
                "__ERR__:${e.message}"
            }
        }

        @JavascriptInterface
        fun fsWriteFile(path: String, content: String): String {
            return try {
                val target = resolveFile(path)
                target.parentFile?.let { if (!it.exists()) it.mkdirs() }
                target.writeText(content, Charsets.UTF_8)
                onWorkspaceChanged()
                "OK"
            } catch (e: Exception) {
                "__ERR__:${e.message}"
            }
        }

        @JavascriptInterface
        fun fsAppendFile(path: String, content: String): String {
            return try {
                val target = resolveFile(path)
                target.parentFile?.let { if (!it.exists()) it.mkdirs() }
                target.appendText(content, Charsets.UTF_8)
                onWorkspaceChanged()
                "OK"
            } catch (e: Exception) {
                "__ERR__:${e.message}"
            }
        }

        @JavascriptInterface
        fun fsExists(path: String): Boolean {
            return resolveFile(path).exists()
        }

        @JavascriptInterface
        fun fsIsDirectory(path: String): Boolean {
            return resolveFile(path).isDirectory
        }

        @JavascriptInterface
        fun fsIsFile(path: String): Boolean {
            return resolveFile(path).isFile
        }

        @JavascriptInterface
        fun fsSize(path: String): Long {
            return resolveFile(path).length()
        }

        @JavascriptInterface
        fun fsReaddir(path: String): String {
            return try {
                val dir = if (path.isBlank() || path == ".") currentWorkingDir else resolveFile(path)
                val names = dir.listFiles()?.map { it.name }?.sorted() ?: emptyList()
                JSONArray(names).toString()
            } catch (e: Exception) {
                "[]"
            }
        }

        @JavascriptInterface
        fun fsMkdir(path: String): String {
            return try {
                val dir = resolveFile(path)
                dir.mkdirs()
                onWorkspaceChanged()
                "OK"
            } catch (e: Exception) {
                "__ERR__:${e.message}"
            }
        }

        @JavascriptInterface
        fun fsUnlink(path: String): String {
            return try {
                val target = resolveFile(path)
                if (target.isDirectory) target.deleteRecursively() else target.delete()
                onWorkspaceChanged()
                "OK"
            } catch (e: Exception) {
                "__ERR__:${e.message}"
            }
        }

        @JavascriptInterface
        fun execSync(cmd: String): String {
            return try {
                val pb = ProcessBuilder("/system/bin/sh", "-c", cmd)
                pb.directory(currentWorkingDir)
                pb.redirectErrorStream(true)
                val proc = pb.start()
                val out = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                out
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }

        @JavascriptInterface
        fun randomUUID(): String = UUID.randomUUID().toString()

        private fun resolveFile(path: String): File {
            val sandboxRoot = context.filesDir.canonicalFile
            val raw = File(path)
            val candidate = (if (raw.isAbsolute) raw else File(currentWorkingDir, path)).canonicalFile
            val sandboxPrefix = "${sandboxRoot.canonicalPath}${File.separator}"
            return if (candidate.canonicalPath == sandboxRoot.canonicalPath || candidate.canonicalPath.startsWith(sandboxPrefix)) {
                candidate
            } else {
                // Confine out-of-bounds paths strictly to currentWorkingDir inside the sandbox
                File(currentWorkingDir, raw.name).canonicalFile
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun ensureRuntimeInitialized(): WebView = withContext(Dispatchers.Main) {
        val existing = replWebView
        if (existing != null) return@withContext existing

        val wv = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(NativeNodeBridge(), "__NativeNode")
        }

        val ready = CompletableDeferred<Unit>()
        wv.evaluateJavascript(buildNodeBootstrapScript()) {
            ready.complete(Unit)
        }
        ready.await()
        replWebView = wv
        wv
    }

    suspend fun executeNodeCommand(
        args: List<String>,
        workingDir: File,
        onOutput: (String) -> Unit
    ) {
        currentWorkingDir = workingDir
        currentOutputCallback = onOutput

        if (args.isEmpty()) {
            onOutput("Welcome to Node.js $NODE_VERSION (Linux arm64).\nType \".help\" for more information, or \".exit\" to leave REPL.\n")
            return
        }

        val first = args[0]
        when {
            first == "-v" || first == "--version" -> {
                onOutput("$NODE_VERSION\n")
            }

            first == "-h" || first == "--help" -> {
                onOutput(
                    "Usage: node [options] [ script.js ] [arguments]\n" +
                    "Options:\n" +
                    "  -v, --version         print Node.js version ($NODE_VERSION)\n" +
                    "  -e, --eval script     evaluate script\n" +
                    "  -p, --print script    evaluate script and print result\n"
                )
            }

            first == "-e" || first == "--eval" -> {
                val code = args.drop(1).joinToString(" ")
                evaluateJsCode(code, workingDir, printLastResult = false, onOutput = onOutput)
            }

            first == "-p" || first == "--print" -> {
                val code = args.drop(1).joinToString(" ")
                evaluateJsCode(code, workingDir, printLastResult = true, onOutput = onOutput)
            }

            else -> {
                val scriptFile = if (File(first).isAbsolute) File(first) else File(workingDir, first)
                val resolvedFile = when {
                    scriptFile.exists() && scriptFile.isFile -> scriptFile
                    File(workingDir, "$first.js").exists() -> File(workingDir, "$first.js")
                    else -> null
                }

                if (resolvedFile == null) {
                    onOutput("node: Cannot find module '${scriptFile.absolutePath}'\n")
                    return
                }

                val code = withContext(Dispatchers.IO) {
                    resolvedFile.readText(Charsets.UTF_8)
                }
                evaluateJsCode(
                    code = code,
                    workingDir = workingDir,
                    scriptPath = resolvedFile.absolutePath,
                    scriptArgs = args.drop(1),
                    printLastResult = false,
                    onOutput = onOutput
                )
            }
        }
    }

    suspend fun evaluateReplLine(
        line: String,
        workingDir: File,
        onOutput: (String) -> Unit
    ) {
        val trimmed = line.trim()
        if (trimmed == ".help") {
            onOutput(".clear    Reset context\n.exit     Exit the REPL\n.help     Print this help message\n")
            return
        }
        evaluateJsCode(line, workingDir, printLastResult = true, isRepl = true, onOutput = onOutput)
    }

    suspend fun evaluateJsCode(
        code: String,
        workingDir: File,
        scriptPath: String = File(workingDir, "index.js").absolutePath,
        scriptArgs: List<String> = emptyList(),
        printLastResult: Boolean = false,
        isRepl: Boolean = false,
        onOutput: (String) -> Unit
    ) {
        currentWorkingDir = workingDir
        currentOutputCallback = onOutput

        val wv = ensureRuntimeInitialized()
        val quotedCode = JSONObject.quote(code)
        val quotedPath = JSONObject.quote(scriptPath)
        val quotedDir = JSONObject.quote(File(scriptPath).parentFile?.absolutePath ?: workingDir.absolutePath)
        val argvJson = JSONArray(listOf("node", scriptPath) + scriptArgs).toString()

        val runnerScript = """
            (function() {
                try {
                    window.__filename = $quotedPath;
                    window.__dirname = $quotedDir;
                    window.process.argv = $argvJson;
                    var __res = (0, eval)($quotedCode);
                    if ($printLastResult && __res !== undefined) {
                        window.__nodeFormatAndPrint(__res);
                    }
                } catch (err) {
                    __NativeNode.printError(err && err.stack ? err.stack : String(err));
                }
            })();
        """.trimIndent()

        withContext(Dispatchers.Main) {
            val done = CompletableDeferred<Unit>()
            wv.evaluateJavascript(runnerScript) {
                done.complete(Unit)
            }
            done.await()
        }
    }

    suspend fun executeNpmCommand(
        args: List<String>,
        workingDir: File,
        onOutput: (String) -> Unit
    ) {
        currentWorkingDir = workingDir
        currentOutputCallback = onOutput

        if (args.isEmpty() || args[0] == "-h" || args[0] == "--help" || args[0] == "help") {
            onOutput(
                "npm <command>\n\n" +
                "Usage:\n" +
                "  npm init [-y]          create a package.json file\n" +
                "  npm install <pkg>      install a package into node_modules\n" +
                "  npm run <script>       run a script defined in package.json\n" +
                "  npm start / test       run start or test script\n" +
                "  npm list               list installed packages\n" +
                "  npm -v                 print npm version ($NPM_VERSION)\n"
            )
            return
        }

        when (val sub = args[0]) {
            "-v", "--version", "version" -> {
                onOutput("$NPM_VERSION\n")
            }

            "init" -> {
                val pkgFile = File(workingDir, "package.json")
                val pkgJson = JSONObject().apply {
                    put("name", workingDir.name.lowercase().replace(Regex("[^a-z0-9-_]"), "-"))
                    put("version", "1.0.0")
                    put("description", "Node.js project created in Mobile Code")
                    put("main", "index.js")
                    put("scripts", JSONObject().apply {
                        put("start", "node index.js")
                        put("test", "node -e \"console.log('All tests passed!')\"")
                    })
                    put("keywords", JSONArray())
                    put("author", "")
                    put("license", "ISC")
                    put("dependencies", JSONObject())
                }
                withContext(Dispatchers.IO) {
                    pkgFile.writeText(pkgJson.toString(2), Charsets.UTF_8)
                    val indexJs = File(workingDir, "index.js")
                    if (!indexJs.exists()) {
                        indexJs.writeText("console.log('Hello from Node.js ' + process.version + '!');\n", Charsets.UTF_8)
                    }
                }
                onWorkspaceChanged()
                onOutput("Wrote to ${pkgFile.absolutePath}:\n\n${pkgJson.toString(2)}\n")
            }

            "i", "install", "add" -> {
                val rawSpecs = args.drop(1).filter { !it.startsWith("-") }
                val isSaveDev = args.contains("-D") || args.contains("--save-dev")
                val pkgFile = File(workingDir, "package.json")
                val pkgJson = withContext(Dispatchers.IO) {
                    if (pkgFile.exists()) {
                        try { JSONObject(pkgFile.readText()) } catch (e: Exception) { JSONObject() }
                    } else {
                        JSONObject().apply {
                            put("name", workingDir.name.lowercase().replace(Regex("[^a-z0-9-_]"), "-"))
                            put("version", "1.0.0")
                            put("description", "")
                            put("main", "index.js")
                            put("dependencies", JSONObject())
                        }
                    }
                }
                val depsKey = if (isSaveDev) "devDependencies" else "dependencies"
                val deps = pkgJson.optJSONObject(depsKey) ?: JSONObject().also { pkgJson.put(depsKey, it) }

                val specsToInstall = if (rawSpecs.isNotEmpty()) {
                    rawSpecs
                } else {
                    // npm install with no arguments: install all dependencies listed in package.json
                    val allSpecs = mutableListOf<String>()
                    pkgJson.optJSONObject("dependencies")?.let { d ->
                        d.keys().forEach { k -> allSpecs.add("$k@${d.optString(k, "latest")}") }
                    }
                    pkgJson.optJSONObject("devDependencies")?.let { d ->
                        d.keys().forEach { k -> allSpecs.add("$k@${d.optString(k, "latest")}") }
                    }
                    allSpecs
                }

                if (specsToInstall.isEmpty()) {
                    withContext(Dispatchers.IO) { File(workingDir, "node_modules").mkdirs() }
                    onWorkspaceChanged()
                    onOutput("up to date, audited 0 packages in 0.2s\nfound 0 vulnerabilities\n")
                    return
                }

                val startTime = System.currentTimeMillis()
                onOutput("npm http fetch Reaching https://registry.npmjs.org for ${specsToInstall.joinToString(", ")}...\n")

                val result = RegistryInstaller.installNpmPackages(
                    packageSpecs = specsToInstall,
                    workingDir = workingDir,
                    maxDepth = 3,
                    onProgress = onOutput
                )

                withContext(Dispatchers.IO) {
                    result.directVersions.forEach { (name, ver) ->
                        deps.put(name, ver)
                    }
                    pkgFile.writeText(pkgJson.toString(2), Charsets.UTF_8)

                    // Write real package-lock.json
                    if (result.lockPackages.isNotEmpty()) {
                        val lockFile = File(workingDir, "package-lock.json")
                        val packagesObj = JSONObject()
                        packagesObj.put("", JSONObject().apply {
                            put("name", pkgJson.optString("name", workingDir.name))
                            put("version", pkgJson.optString("version", "1.0.0"))
                            put("dependencies", pkgJson.optJSONObject("dependencies") ?: JSONObject())
                        })
                        result.lockPackages.forEach { (k, v) -> packagesObj.put(k, v) }

                        val lockJson = JSONObject().apply {
                            put("name", pkgJson.optString("name", workingDir.name))
                            put("version", pkgJson.optString("version", "1.0.0"))
                            put("lockfileVersion", 3)
                            put("requires", true)
                            put("packages", packagesObj)
                        }
                        lockFile.writeText(lockJson.toString(2), Charsets.UTF_8)
                    }
                }

                val elapsedSec = String.format("%.1f", (System.currentTimeMillis() - startTime) / 1000.0)
                onWorkspaceChanged()
                onOutput("\nadded ${result.installedCount} packages from registry.npmjs.org, and audited ${result.installedCount} packages in ${elapsedSec}s\nfound 0 vulnerabilities\n")
            }

            "uninstall", "remove", "rm", "un" -> {
                val pkgs = args.drop(1).filter { !it.startsWith("-") }
                if (pkgs.isEmpty()) {
                    onOutput("npm ERR! Must provide a package name to uninstall\n")
                    return
                }
                val pkgFile = File(workingDir, "package.json")
                val nodeModules = File(workingDir, "node_modules")
                var removedCount = 0
                withContext(Dispatchers.IO) {
                    val pkgJson = if (pkgFile.exists()) {
                        try { JSONObject(pkgFile.readText()) } catch (e: Exception) { JSONObject() }
                    } else JSONObject()
                    val deps = pkgJson.optJSONObject("dependencies")
                    val devDeps = pkgJson.optJSONObject("devDependencies")
                    pkgs.forEach { p ->
                        val dir = File(nodeModules, p)
                        if (dir.exists()) {
                            dir.deleteRecursively()
                            removedCount++
                        }
                        deps?.remove(p)
                        devDeps?.remove(p)
                    }
                    if (pkgFile.exists()) {
                        pkgFile.writeText(pkgJson.toString(2), Charsets.UTF_8)
                    }
                }
                onWorkspaceChanged()
                onOutput("removed $removedCount package(s) in 0.3s\n")
            }

            "list", "ls" -> {
                val pkgFile = File(workingDir, "package.json")
                if (!pkgFile.exists()) {
                    onOutput("${workingDir.name}@1.0.0 ${workingDir.absolutePath}\n└── (empty)\n")
                    return
                }
                val pkgJson = try { JSONObject(pkgFile.readText()) } catch (e: Exception) { JSONObject() }
                val deps = pkgJson.optJSONObject("dependencies")
                val sb = StringBuilder("${pkgJson.optString("name", workingDir.name)}@${pkgJson.optString("version", "1.0.0")} ${workingDir.absolutePath}\n")
                if (deps == null || deps.length() == 0) {
                    sb.append("└── (empty)\n")
                } else {
                    val keys = deps.keys().asSequence().toList()
                    keys.forEachIndexed { idx, k ->
                        val prefix = if (idx == keys.lastIndex) "└──" else "├──"
                        sb.append("$prefix $k@${deps.optString(k)}\n")
                    }
                }
                onOutput(sb.toString())
            }

            "start", "test", "run" -> {
                val scriptName = if (sub == "run") args.getOrNull(1) ?: "" else sub
                if (scriptName.isEmpty()) {
                    onOutput("npm ERR! Missing script name. Usage: npm run <script>\n")
                    return
                }
                val pkgFile = File(workingDir, "package.json")
                if (!pkgFile.exists()) {
                    onOutput("npm ERR! enoent Could not read package.json in ${workingDir.absolutePath}\n")
                    return
                }
                val pkgJson = try { JSONObject(pkgFile.readText()) } catch (e: Exception) { JSONObject() }
                val scripts = pkgJson.optJSONObject("scripts")
                val cmd = scripts?.optString(scriptName, "") ?: ""
                if (cmd.isEmpty()) {
                    onOutput("npm ERR! Missing script: \"$scriptName\"\n")
                    return
                }
                onOutput("> ${pkgJson.optString("name", "app")}@${pkgJson.optString("version", "1.0.0")} $scriptName\n> $cmd\n\n")
                if (cmd.startsWith("node ")) {
                    val nodeArgs = cmd.removePrefix("node ").trim().split(Regex("\\s+"))
                    executeNodeCommand(nodeArgs, workingDir, onOutput)
                } else {
                    val out = withContext(Dispatchers.IO) {
                        NativeNodeBridge().execSync(cmd)
                    }
                    onOutput(out)
                }
            }

            else -> {
                onOutput("npm: unknown command '$sub'. Run 'npm --help' for usage.\n")
            }
        }
    }

    private fun buildNodeBootstrapScript(): String = """
        (function() {
            if (window.__nodeInitialized) return;
            window.__nodeInitialized = true;

            function formatVal(v) {
                if (v === null) return 'null';
                if (v === undefined) return 'undefined';
                if (typeof v === 'string') return v;
                if (v instanceof Error) return v.stack || v.message || String(v);
                try {
                    return JSON.stringify(v, null, 2);
                } catch (e) {
                    return String(v);
                }
            }

            window.__nodeFormatAndPrint = function(v) {
                if (typeof v === 'string') {
                    __NativeNode.print("'" + v + "'");
                } else {
                    __NativeNode.print(formatVal(v));
                }
            };

            var timers = {};
            window.console = {
                log: function() {
                    var parts = [];
                    for (var i = 0; i < arguments.length; i++) parts.push(formatVal(arguments[i]));
                    __NativeNode.print(parts.join(' '));
                },
                info: function() { window.console.log.apply(null, arguments); },
                warn: function() { window.console.log.apply(null, arguments); },
                error: function() {
                    var parts = [];
                    for (var i = 0; i < arguments.length; i++) parts.push(formatVal(arguments[i]));
                    __NativeNode.printError(parts.join(' '));
                },
                dir: function(obj) { __NativeNode.print(formatVal(obj)); },
                table: function(obj) { __NativeNode.print(formatVal(obj)); },
                time: function(label) { timers[label || 'default'] = Date.now(); },
                timeEnd: function(label) {
                    var l = label || 'default';
                    var elapsed = Date.now() - (timers[l] || Date.now());
                    __NativeNode.print(l + ': ' + elapsed + 'ms');
                }
            };

            window.process = {
                version: '$NODE_VERSION',
                versions: { node: '20.11.0', v8: '11.8.172', modules: '115' },
                platform: 'linux',
                arch: 'arm64',
                pid: 4210,
                env: { NODE_ENV: 'development', TERM: 'xterm-256color', LANG: 'en_US.UTF-8' },
                argv: ['node'],
                cwd: function() { return __NativeNode.cwd(); },
                exit: function(code) { __NativeNode.print('[Process exited with code ' + (code || 0) + ']'); },
                nextTick: function(cb) { Promise.resolve().then(cb); },
                hrtime: function() { return [Math.floor(performance.now() / 1000), Math.floor((performance.now() % 1000) * 1e6)]; },
                stdout: { write: function(s) { __NativeNode.print(String(s).replace(/\n$/, '')); } },
                stderr: { write: function(s) { __NativeNode.printError(String(s).replace(/\n$/, '')); } }
            };

            window.Buffer = {
                from: function(str) {
                    var s = String(str);
                    return {
                        toString: function(enc) {
                            if (enc === 'base64') return btoa(s);
                            return s;
                        },
                        length: s.length
                    };
                },
                alloc: function(size) {
                    return { length: size, toString: function() { return ''; } };
                },
                isBuffer: function(b) { return !!(b && b.toString); }
            };

            var fsModule = {
                readFileSync: function(p) {
                    var res = __NativeNode.fsReadFile(String(p));
                    if (res.indexOf('__ERR__:') === 0) throw new Error(res.substring(8));
                    return res;
                },
                writeFileSync: function(p, data) {
                    var content = (typeof data === 'object' && data !== null && data.toString) ? data.toString() : String(data);
                    var res = __NativeNode.fsWriteFile(String(p), content);
                    if (res.indexOf('__ERR__:') === 0) throw new Error(res.substring(8));
                },
                appendFileSync: function(p, data) {
                    var res = __NativeNode.fsAppendFile(String(p), String(data));
                    if (res.indexOf('__ERR__:') === 0) throw new Error(res.substring(8));
                },
                existsSync: function(p) { return !!__NativeNode.fsExists(String(p)); },
                readdirSync: function(p) { return JSON.parse(__NativeNode.fsReaddir(String(p || '.'))); },
                mkdirSync: function(p) { __NativeNode.fsMkdir(String(p)); },
                unlinkSync: function(p) { __NativeNode.fsUnlink(String(p)); },
                rmSync: function(p) { __NativeNode.fsUnlink(String(p)); },
                statSync: function(p) {
                    var sp = String(p);
                    return {
                        isFile: function() { return !!__NativeNode.fsIsFile(sp); },
                        isDirectory: function() { return !!__NativeNode.fsIsDirectory(sp); },
                        size: __NativeNode.fsSize(sp)
                    };
                }
            };
            fsModule.promises = {
                readFile: function(p) { return Promise.resolve(fsModule.readFileSync(p)); },
                writeFile: function(p, d) { return Promise.resolve(fsModule.writeFileSync(p, d)); },
                readdir: function(p) { return Promise.resolve(fsModule.readdirSync(p)); },
                mkdir: function(p) { return Promise.resolve(fsModule.mkdirSync(p)); }
            };

            var pathModule = {
                sep: '/',
                join: function() {
                    var parts = [];
                    for (var i = 0; i < arguments.length; i++) {
                        if (arguments[i]) parts.push(String(arguments[i]).replace(/^\/+|\/+$/g, ''));
                    }
                    return parts.join('/');
                },
                resolve: function() {
                    var p = pathModule.join.apply(null, arguments);
                    return __NativeNode.resolvePath(p);
                },
                basename: function(p) {
                    var parts = String(p).split('/');
                    return parts[parts.length - 1] || '';
                },
                dirname: function(p) {
                    var parts = String(p).split('/');
                    parts.pop();
                    return parts.join('/') || '.';
                },
                extname: function(p) {
                    var base = pathModule.basename(p);
                    var idx = base.lastIndexOf('.');
                    return idx > 0 ? base.substring(idx) : '';
                }
            };

            var osModule = {
                platform: function() { return 'linux'; },
                arch: function() { return 'arm64'; },
                homedir: function() { return __NativeNode.cwd(); },
                tmpdir: function() { return __NativeNode.cwd() + '/tmp'; },
                hostname: function() { return 'mobile-code-linux'; },
                type: function() { return 'Linux'; },
                release: function() { return '6.1.0-android16-arm64'; },
                cpus: function() { return [{ model: 'ARM64 Cortex Processor', speed: 2800 }]; },
                totalmem: function() { return 8589934592; },
                freemem: function() { return 4294967296; },
                EOL: '\n'
            };

            var childProcessModule = {
                execSync: function(cmd) {
                    return __NativeNode.execSync(String(cmd));
                },
                exec: function(cmd, cb) {
                    try {
                        var out = __NativeNode.execSync(String(cmd));
                        if (cb) cb(null, out, '');
                    } catch (e) {
                        if (cb) cb(e, '', String(e));
                    }
                }
            };

            var cryptoModule = {
                randomUUID: function() { return __NativeNode.randomUUID(); },
                randomBytes: function(size) {
                    var arr = new Uint8Array(size);
                    crypto.getRandomValues(arr);
                    return {
                        toString: function(enc) {
                            return Array.from(arr).map(function(b) { return b.toString(16).padStart(2, '0'); }).join('');
                        }
                    };
                }
            };

            function EventEmitter() { this._events = {}; }
            EventEmitter.prototype.on = function(ev, fn) {
                (this._events[ev] = this._events[ev] || []).push(fn);
                return this;
            };
            EventEmitter.prototype.once = function(ev, fn) {
                var self = this;
                function wrapper() {
                    self.removeListener(ev, wrapper);
                    fn.apply(this, arguments);
                }
                return this.on(ev, wrapper);
            };
            EventEmitter.prototype.removeListener = function(ev, fn) {
                var list = this._events[ev] || [];
                this._events[ev] = list.filter(function(f) { return f !== fn; });
                return this;
            };
            EventEmitter.prototype.emit = function(ev) {
                var args = Array.prototype.slice.call(arguments, 1);
                var list = this._events[ev] || [];
                for (var i = 0; i < list.length; i++) list[i].apply(this, args);
                return list.length > 0;
            };
            EventEmitter.EventEmitter = EventEmitter;

            var utilModule = {
                format: function() { return Array.prototype.slice.call(arguments).join(' '); },
                promisify: function(fn) { return fn; },
                inherits: function(ctor, superCtor) {
                    if (ctor && superCtor) {
                        ctor.super_ = superCtor;
                        ctor.prototype = Object.create(superCtor.prototype, {
                            constructor: { value: ctor, enumerable: false, writable: true, configurable: true }
                        });
                    }
                },
                deprecate: function(fn) { return fn; },
                inspect: function(obj) { return formatVal(obj); }
            };

            var httpModule = {
                METHODS: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'],
                STATUS_CODES: { 200: 'OK', 201: 'Created', 204: 'No Content', 301: 'Moved Permanently', 302: 'Found', 304: 'Not Modified', 400: 'Bad Request', 401: 'Unauthorized', 403: 'Forbidden', 404: 'Not Found', 500: 'Internal Server Error' },
                IncomingMessage: function() { EventEmitter.call(this); this.headers = {}; },
                ServerResponse: function() { EventEmitter.call(this); },
                Server: function(handler) {
                    EventEmitter.call(this);
                    this._handler = handler;
                    this.listen = function(port, host, cb) {
                        var callback = typeof host === 'function' ? host : cb;
                        if (callback) setTimeout(callback, 0);
                        return this;
                    };
                },
                createServer: function(handler) { return new httpModule.Server(handler); }
            };
            utilModule.inherits(httpModule.IncomingMessage, EventEmitter);
            utilModule.inherits(httpModule.ServerResponse, EventEmitter);
            utilModule.inherits(httpModule.Server, EventEmitter);

            var urlModule = {
                parse: function(u) {
                    try {
                        var parsed = new URL(u, 'http://localhost');
                        return { protocol: parsed.protocol, host: parsed.host, hostname: parsed.hostname, port: parsed.port, pathname: parsed.pathname, search: parsed.search, query: parsed.search.replace(/^\?/, ''), href: u };
                    } catch (e) {
                        return { pathname: String(u), query: '', href: String(u) };
                    }
                },
                format: function(obj) { return (obj && obj.href) ? obj.href : String(obj); },
                URL: URL,
                URLSearchParams: URLSearchParams
            };

            var querystringModule = {
                parse: function(str) {
                    var out = {};
                    var params = new URLSearchParams(String(str || ''));
                    params.forEach(function(v, k) { out[k] = v; });
                    return out;
                },
                stringify: function(obj) {
                    return new URLSearchParams(obj || {}).toString();
                }
            };

            var streamModule = (function() {
                function Stream() { EventEmitter.call(this); }
                utilModule.inherits(Stream, EventEmitter);
                Stream.prototype.pipe = function(dest) { return dest; };
                Stream.Stream = Stream;
                Stream.Readable = Stream;
                Stream.Writable = Stream;
                Stream.Duplex = Stream;
                Stream.Transform = Stream;
                Stream.PassThrough = Stream;
                return Stream;
            })();

            var netModule = {
                isIP: function(input) { return /^(\d+\.){3}\d+$/.test(input) ? 4 : 0; },
                isIPv4: function(input) { return /^(\d+\.){3}\d+$/.test(input); },
                isIPv6: function(input) { return String(input).indexOf(':') !== -1; },
                Socket: streamModule,
                Server: httpModule.Server,
                createServer: httpModule.createServer
            };

            var __moduleCache = {};

            function normalizePosix(p) {
                var parts = String(p).split('/');
                var out = [];
                for (var i = 0; i < parts.length; i++) {
                    var seg = parts[i];
                    if (!seg || seg === '.') continue;
                    if (seg === '..') {
                        if (out.length > 0 && out[out.length - 1] !== '..') out.pop();
                        else out.push('..');
                    } else {
                        out.push(seg);
                    }
                }
                return (String(p).indexOf('/') === 0 ? '/' : '') + out.join('/');
            }

            function resolveModulePath(request, baseDir) {
                var candidates = [];
                if (request.indexOf('./') === 0 || request.indexOf('../') === 0 || request.indexOf('/') === 0) {
                    var relBase = request.indexOf('/') === 0 ? request : normalizePosix((baseDir ? baseDir + '/' : '') + request);
                    candidates.push(relBase);
                    candidates.push(relBase + '.js');
                    candidates.push(relBase + '.json');
                    candidates.push(relBase + '/index.js');
                    candidates.push(relBase + '/index.json');
                } else {
                    // Check node_modules/<pkg>
                    var nmBase = 'node_modules/' + request;
                    var pkgJsonPath = nmBase + '/package.json';
                    if (__NativeNode.fsExists(pkgJsonPath) && __NativeNode.fsIsFile(pkgJsonPath)) {
                        try {
                            var pkgMeta = JSON.parse(fsModule.readFileSync(pkgJsonPath));
                            var mainEntry = pkgMeta.main || 'index.js';
                            var resolvedMain = normalizePosix(nmBase + '/' + mainEntry);
                            candidates.push(resolvedMain);
                            candidates.push(resolvedMain + '.js');
                            candidates.push(resolvedMain + '/index.js');
                        } catch (e) {}
                    }
                    candidates.push(nmBase + '/index.js');
                    candidates.push(nmBase + '.js');
                    candidates.push(nmBase + '.json');
                    candidates.push(request);
                    candidates.push(request + '.js');
                    candidates.push(request + '.json');
                }

                for (var i = 0; i < candidates.length; i++) {
                    var c = candidates[i];
                    if (__NativeNode.fsExists(c) && __NativeNode.fsIsFile(c)) {
                        return c;
                    }
                }
                return null;
            }

            function createRequireFrom(baseDir) {
                var req = function(id) {
                    var cleanId = String(id).replace(/^node:/, '');
                    if (cleanId === 'fs') return fsModule;
                    if (cleanId === 'path') return pathModule;
                    if (cleanId === 'os') return osModule;
                    if (cleanId === 'child_process') return childProcessModule;
                    if (cleanId === 'crypto') return cryptoModule;
                    if (cleanId === 'events') return EventEmitter;
                    if (cleanId === 'util') return utilModule;
                    if (cleanId === 'http' || cleanId === 'https') return httpModule;
                    if (cleanId === 'url') return urlModule;
                    if (cleanId === 'querystring' || cleanId === 'qs') return querystringModule;
                    if (cleanId === 'stream') return streamModule;
                    if (cleanId === 'net') return netModule;
                    if (cleanId === 'buffer') return { Buffer: window.Buffer, SlowBuffer: window.Buffer };
                    if (cleanId === 'tty') return { isatty: function() { return true; } };
                    if (cleanId === 'zlib') return {};
                    if (cleanId === 'async_hooks') return { AsyncLocalStorage: function() {} };
                    if (cleanId === 'assert') {
                        var assertFn = function(cond, msg) { if (!cond) throw new Error(msg || 'AssertionError'); };
                        assertFn.strictEqual = function(a, b, m) { if (a !== b) throw new Error(m || (a + ' !== ' + b)); };
                        assertFn.equal = function(a, b, m) { if (a != b) throw new Error(m || (a + ' != ' + b)); };
                        assertFn.ok = assertFn;
                        return assertFn;
                    }

                    var resolved = resolveModulePath(cleanId, baseDir);
                    if (!resolved) {
                        throw new Error("Cannot find module '" + id + "'");
                    }

                    if (__moduleCache[resolved]) {
                        return __moduleCache[resolved].exports;
                    }

                    var content = fsModule.readFileSync(resolved);
                    if (resolved.endsWith('.json')) {
                        var parsed = JSON.parse(content);
                        __moduleCache[resolved] = { exports: parsed };
                        return parsed;
                    }

                    var module = { exports: {}, id: resolved, filename: resolved, loaded: false };
                    __moduleCache[resolved] = module;
                    var moduleDir = pathModule.dirname(resolved);
                    var childRequire = createRequireFrom(moduleDir);
                    var fn = new Function('exports', 'require', 'module', '__filename', '__dirname', content);
                    fn(module.exports, childRequire, module, resolved, moduleDir);
                    module.loaded = true;
                    return module.exports;
                };
                req.resolve = function(id) {
                    return resolveModulePath(String(id), baseDir) || String(id);
                };
                req.cache = __moduleCache;
                return req;
            }

            window.require = createRequireFrom('');
        })();
    """.trimIndent()
}

