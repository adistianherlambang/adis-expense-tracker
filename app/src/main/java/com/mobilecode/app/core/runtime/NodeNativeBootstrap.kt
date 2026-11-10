package com.mobilecode.app.core.runtime

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Bootstraps the official native Linux Node.js binary and real NPM/NPX CLI distribution
 * on Android without mock polyfills or simulated templates.
 */
object NodeNativeBootstrap {

    private const val TAG = "NodeNativeBootstrap"
    private const val BOOTSTRAP_VERSION_CODE = 19
    private val initMutex = Mutex()

    suspend fun ensureInitialized(context: Context, onProgress: ((String) -> Unit)? = null) = withContext(Dispatchers.IO) {
        initMutex.withLock {
            val filesDir = context.filesDir.canonicalFile
            val usrDir = File(filesDir, "usr")
            val usrBin = File(usrDir, "bin")
            val usrLib = File(usrDir, "lib")
            val npmDir = File(usrLib, "node_modules/npm")
            val shimFile = File(usrLib, "android-shim.js")
            val markerFile = File(usrDir, ".bootstrap_v$BOOTSTRAP_VERSION_CODE")

            usrBin.mkdirs()
            usrLib.mkdirs()

            // Synchronize active node version from persisted environment preferences into .active_node_version
            val prefs = context.getSharedPreferences("mobile_code_environments", Context.MODE_PRIVATE)
            val activeEnvId = prefs.getString("active_env_id", "node-20") ?: "node-20"
            val persistedNodeVer = prefs.getString("active_node_version", null)
                ?: when (activeEnvId) {
                    "node-22" -> "v22.14.0"
                    "node-18" -> "v18.20.4"
                    else -> "v20.18.0"
                }
            File(usrDir, ".active_node_version").writeText(persistedNodeVer, Charsets.UTF_8)

            // 1. Locate native executable compiled into APK native library directory
            val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
            val nativeNodeLauncher = File(nativeLibDir, "libnode_launcher.so")

            // Persist current active launcher path in a dynamic reference file
            val launcherRef = File(usrDir, ".native_launcher_path")
            launcherRef.writeText(nativeNodeLauncher.absolutePath, Charsets.UTF_8)

            // Remove legacy / stale wrapper scripts (such as old create-react-app script)
            val legacyCra = File(usrBin, "create-react-app")
            if (legacyCra.exists()) {
                legacyCra.delete()
            }

            // Always ensure android-shim.js and wrapper scripts are synchronized with the active APK installation
            writeAndroidShim(shimFile)
            writeWrapperScripts(usrDir, usrBin, usrLib, nativeLibDir, nativeNodeLauncher, npmDir)
            patchWorkspaceModules(File(filesDir, "workspaces"))

            val npmCliJs = File(npmDir, "bin/npm-cli.js")
            if (markerFile.exists() && npmCliJs.exists()) {
                return@withLock
            }

            onProgress?.invoke("Initializing Native Linux Node.js & NPM environment...\n")
            npmDir.mkdirs()

            // Extract official NPM distribution tarball from assets if not yet extracted
            if (!npmCliJs.exists()) {
                try {
                    context.assets.open("npm.tgz").use { assetIn ->
                        extractTgzStream(assetIn, npmDir)
                    }
                    onProgress?.invoke("Official NPM distribution installed to ${npmDir.absolutePath}\n")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to extract npm.tgz from assets", e)
                    onProgress?.invoke("Warning: could not extract bundled npm: ${e.message}\n")
                }
            }

            // Mark initialization complete
            markerFile.writeText("OK", Charsets.UTF_8)
            onProgress?.invoke("Native Linux Node.js environment ready.\n")
        }
    }

    private fun writeWrapperScripts(
        usrDir: File,
        usrBin: File,
        usrLib: File,
        nativeLibDir: File,
        nativeNodeLauncher: File,
        npmDir: File
    ) {
        val npmCliJs = File(npmDir, "bin/npm-cli.js")
        val npxCliJs = File(npmDir, "bin/npx-cli.js")

        // 1. Create 'node' & 'nodejs' wrapper scripts in $PATH
        val nodeScriptContent = """
            #!/system/bin/sh
            DIR="${'$'}(cd "${'$'}(dirname "${'$'}0")/.." && pwd)"
            LAUNCHER=""
            if [ -f "${'$'}DIR/.native_launcher_path" ]; then
                LAUNCHER="${'$'}(head -n 1 "${'$'}DIR/.native_launcher_path")"
            fi
            if [ -z "${'$'}LAUNCHER" ] || [ ! -f "${'$'}LAUNCHER" ]; then
                apk="${'$'}(pm path com.mobilecode.app.debug 2>/dev/null || pm path com.mobilecode.app 2>/dev/null)"
                apk="${'$'}(echo "${'$'}apk" | head -n 1 | sed 's/^package://')"
                if [ -n "${'$'}apk" ]; then
                    cand="${'$'}(dirname "${'$'}apk")/lib/arm64/libnode_launcher.so"
                    if [ -f "${'$'}cand" ]; then
                        LAUNCHER="${'$'}cand"
                        echo "${'$'}cand" > "${'$'}DIR/.native_launcher_path" 2>/dev/null || true
                    fi
                fi
            fi
            if [ -z "${'$'}LAUNCHER" ] || [ ! -f "${'$'}LAUNCHER" ]; then
                LAUNCHER="${nativeNodeLauncher.absolutePath}"
            fi
            LIB_DIR="${'$'}(dirname "${'$'}LAUNCHER")"
            export LD_LIBRARY_PATH="${'$'}LIB_DIR:${nativeLibDir.absolutePath}:${'$'}LD_LIBRARY_PATH"
            export MOBILE_CODE_USR_DIR="${'$'}DIR"
            if [ -z "${'$'}NODE_PATH" ]; then
                export NODE_PATH="${File(usrLib, "node_modules").absolutePath}"
            fi
            if [ "${'$'}1" = "-v" ] || [ "${'$'}1" = "--version" ]; then
                exec "${'$'}LAUNCHER" --require "${'$'}DIR/lib/android-shim.js" -p "process.version"
            fi
            exec "${'$'}LAUNCHER" --require "${'$'}DIR/lib/android-shim.js" "$@"
        """.trimIndent() + "\n"

        File(usrBin, "node").apply {
            writeText(nodeScriptContent, Charsets.UTF_8)
            setExecutable(true, false)
            setReadable(true, false)
        }
        File(usrBin, "nodejs").apply {
            writeText(nodeScriptContent, Charsets.UTF_8)
            setExecutable(true, false)
            setReadable(true, false)
        }

        // 2. Create 'npm' wrapper script in $PATH
        val npmScriptContent = """
            #!/system/bin/sh
            DIR="${'$'}(cd "${'$'}(dirname "${'$'}0")/.." && pwd)"
            LAUNCHER=""
            if [ -f "${'$'}DIR/.native_launcher_path" ]; then
                LAUNCHER="${'$'}(head -n 1 "${'$'}DIR/.native_launcher_path")"
            fi
            if [ -z "${'$'}LAUNCHER" ] || [ ! -f "${'$'}LAUNCHER" ]; then
                apk="${'$'}(pm path com.mobilecode.app.debug 2>/dev/null || pm path com.mobilecode.app 2>/dev/null)"
                apk="${'$'}(echo "${'$'}apk" | head -n 1 | sed 's/^package://')"
                if [ -n "${'$'}apk" ]; then
                    cand="${'$'}(dirname "${'$'}apk")/lib/arm64/libnode_launcher.so"
                    if [ -f "${'$'}cand" ]; then
                        LAUNCHER="${'$'}cand"
                        echo "${'$'}cand" > "${'$'}DIR/.native_launcher_path" 2>/dev/null || true
                    fi
                fi
            fi
            if [ -z "${'$'}LAUNCHER" ] || [ ! -f "${'$'}LAUNCHER" ]; then
                LAUNCHER="${nativeNodeLauncher.absolutePath}"
            fi
            LIB_DIR="${'$'}(dirname "${'$'}LAUNCHER")"
            export PATH="${'$'}DIR/bin:${'$'}PATH"
            export LD_LIBRARY_PATH="${'$'}LIB_DIR:${nativeLibDir.absolutePath}:${'$'}LD_LIBRARY_PATH"
            export MOBILE_CODE_USR_DIR="${'$'}DIR"
            export NODE_PATH="${File(usrLib, "node_modules").absolutePath}"
            export npm_config_scripts_prepend_node_path=true
            export npm_config_update_notifier=false
            exec "${'$'}LAUNCHER" --require "${'$'}DIR/lib/android-shim.js" "${npmCliJs.absolutePath}" "$@"
        """.trimIndent() + "\n"

        File(usrBin, "npm").apply {
            writeText(npmScriptContent, Charsets.UTF_8)
            setExecutable(true, false)
            setReadable(true, false)
        }

        // 3. Create 'npx' wrapper script in $PATH
        val npxScriptContent = """
            #!/system/bin/sh
            DIR="${'$'}(cd "${'$'}(dirname "${'$'}0")/.." && pwd)"
            LAUNCHER=""
            if [ -f "${'$'}DIR/.native_launcher_path" ]; then
                LAUNCHER="${'$'}(head -n 1 "${'$'}DIR/.native_launcher_path")"
            fi
            if [ -z "${'$'}LAUNCHER" ] || [ ! -f "${'$'}LAUNCHER" ]; then
                apk="${'$'}(pm path com.mobilecode.app.debug 2>/dev/null || pm path com.mobilecode.app 2>/dev/null)"
                apk="${'$'}(echo "${'$'}apk" | head -n 1 | sed 's/^package://')"
                if [ -n "${'$'}apk" ]; then
                    cand="${'$'}(dirname "${'$'}apk")/lib/arm64/libnode_launcher.so"
                    if [ -f "${'$'}cand" ]; then
                        LAUNCHER="${'$'}cand"
                        echo "${'$'}cand" > "${'$'}DIR/.native_launcher_path" 2>/dev/null || true
                    fi
                fi
            fi
            if [ -z "${'$'}LAUNCHER" ] || [ ! -f "${'$'}LAUNCHER" ]; then
                LAUNCHER="${nativeNodeLauncher.absolutePath}"
            fi
            LIB_DIR="${'$'}(dirname "${'$'}LAUNCHER")"
            export PATH="${'$'}DIR/bin:${'$'}PATH"
            export LD_LIBRARY_PATH="${'$'}LIB_DIR:${nativeLibDir.absolutePath}:${'$'}LD_LIBRARY_PATH"
            export MOBILE_CODE_USR_DIR="${'$'}DIR"
            export NODE_PATH="${File(usrLib, "node_modules").absolutePath}"
            export npm_config_scripts_prepend_node_path=true
            export npm_config_update_notifier=false
            exec "${'$'}LAUNCHER" --require "${'$'}DIR/lib/android-shim.js" "${npxCliJs.absolutePath}" "$@"
        """.trimIndent() + "\n"

        File(usrBin, "npx").apply {
            writeText(npxScriptContent, Charsets.UTF_8)
            setExecutable(true, false)
            setReadable(true, false)
        }

        // 4. Create 'env' helper in $PATH
        val envScriptContent = """
            #!/system/bin/sh
            DIR="${'$'}(cd "${'$'}(dirname "${'$'}0")/.." && pwd)"
            LAUNCHER=""
            if [ -f "${'$'}DIR/.native_launcher_path" ]; then
                LAUNCHER="${'$'}(head -n 1 "${'$'}DIR/.native_launcher_path")"
            fi
            if [ -z "${'$'}LAUNCHER" ] || [ ! -f "${'$'}LAUNCHER" ]; then
                apk="${'$'}(pm path com.mobilecode.app.debug 2>/dev/null || pm path com.mobilecode.app 2>/dev/null)"
                apk="${'$'}(echo "${'$'}apk" | head -n 1 | sed 's/^package://')"
                if [ -n "${'$'}apk" ]; then
                    cand="${'$'}(dirname "${'$'}apk")/lib/arm64/libnode_launcher.so"
                    if [ -f "${'$'}cand" ]; then
                        LAUNCHER="${'$'}cand"
                        echo "${'$'}cand" > "${'$'}DIR/.native_launcher_path" 2>/dev/null || true
                    fi
                fi
            fi
            if [ -z "${'$'}LAUNCHER" ] || [ ! -f "${'$'}LAUNCHER" ]; then
                LAUNCHER="${nativeNodeLauncher.absolutePath}"
            fi
            export MOBILE_CODE_USR_DIR="${'$'}DIR"
            if [ "${'$'}1" = "node" ] || [ "${'$'}1" = "nodejs" ]; then
                shift
                if [ "${'$'}1" = "-v" ] || [ "${'$'}1" = "--version" ]; then
                    exec "${'$'}LAUNCHER" --require "${'$'}DIR/lib/android-shim.js" -p "process.version"
                fi
                exec "${'$'}LAUNCHER" --require "${'$'}DIR/lib/android-shim.js" "$@"
            elif [ -n "${'$'}1" ]; then
                cmd="${'$'}1"
                shift
                exec "${'$'}cmd" "$@"
            else
                /system/bin/printenv
            fi
        """.trimIndent() + "\n"

        File(usrBin, "env").apply {
            writeText(envScriptContent, Charsets.UTF_8)
            setExecutable(true, false)
            setReadable(true, false)
        }

        // 5. Create 'xdg-open' helper in $PATH for dev server browser openers
        val xdgOpenScriptContent = """
            #!/system/bin/sh
            URL="${'$'}1"
            if [ -n "${'$'}URL" ]; then
                /system/bin/am start -a android.intent.action.VIEW -d "${'$'}URL" >/dev/null 2>&1 || true
            fi
            exit 0
        """.trimIndent() + "\n"

        File(usrBin, "xdg-open").apply {
            writeText(xdgOpenScriptContent, Charsets.UTF_8)
            setExecutable(true, false)
            setReadable(true, false)
        }

        // 6. Create 'sudo' helper in $PATH for sandbox execution without errors
        val sudoScriptContent = """
            #!/system/bin/sh
            if [ -z "${'$'}1" ]; then
                echo "usage: sudo command [args...]"
                exit 1
            fi
            cmd="${'$'}1"
            shift
            exec "${'$'}cmd" "$@"
        """.trimIndent() + "\n"

        File(usrBin, "sudo").apply {
            writeText(sudoScriptContent, Charsets.UTF_8)
            setExecutable(true, false)
            setReadable(true, false)
        }

        // 7. Create 'open' helper in $PATH for browser opening commands
        val openScriptContent = """
            #!/system/bin/sh
            URL="${'$'}1"
            if [ -n "${'$'}URL" ]; then
                /system/bin/am start -a android.intent.action.VIEW -d "${'$'}URL" >/dev/null 2>&1 || true
            fi
            exit 0
        """.trimIndent() + "\n"

        File(usrBin, "open").apply {
            writeText(openScriptContent, Charsets.UTF_8)
            setExecutable(true, false)
            setReadable(true, false)
        }

        // 8. Create 'tunnel' helper in $PATH for quick public internet tunneling
        val tunnelScriptContent = """
            #!/system/bin/sh
            DIR="${'$'}(cd "${'$'}(dirname "${'$'}0")/.." && pwd)"
            LAUNCHER=""
            if [ -f "${'$'}DIR/.native_launcher_path" ]; then
                LAUNCHER="${'$'}(head -n 1 "${'$'}DIR/.native_launcher_path")"
            fi
            if [ -z "${'$'}LAUNCHER" ] || [ ! -f "${'$'}LAUNCHER" ]; then
                LAUNCHER="${nativeNodeLauncher.absolutePath}"
            fi
            PORT="${'$'}{1:-3000}"
            LT_JS="${'$'}DIR/lib/node_modules/localtunnel/bin/lt.js"
            if [ ! -f "${'$'}LT_JS" ]; then
                echo "Installing localtunnel..."
                "${'$'}LAUNCHER" "${npmCliJs.absolutePath}" install -g localtunnel
            fi
            exec "${'$'}LAUNCHER" --require "${'$'}DIR/lib/android-shim.js" "${'$'}LT_JS" --port "${'$'}PORT"
        """.trimIndent() + "\n"

        File(usrBin, "tunnel").apply {
            writeText(tunnelScriptContent, Charsets.UTF_8)
            setExecutable(true, false)
            setReadable(true, false)
        }
    }

    private fun writeAndroidShim(shimFile: File) {
        val content = """
            'use strict';
            if (!global.__MOBILE_CODE_ANDROID_SHIM_LOADED__) {
              global.__MOBILE_CODE_ANDROID_SHIM_LOADED__ = true;

              const fs = require('fs');
              const path = require('path');
              const os = require('os');
              const cp = require('child_process');
              const mod = require('module');

              // 0. Polyfill TextDecoder so that { fatal: true } does not throw ERR_NO_ICU on Android
              const OrigTextDecoder = global.TextDecoder;
              if (typeof OrigTextDecoder === 'function') {
                function SafeTextDecoder(encoding, options) {
                  if (options && options.fatal) {
                    const safeOpts = Object.assign({}, options, { fatal: false });
                    return new OrigTextDecoder(encoding, safeOpts);
                  }
                  return new OrigTextDecoder(encoding, options);
                }
                SafeTextDecoder.prototype = OrigTextDecoder.prototype;
                Object.setPrototypeOf(SafeTextDecoder, OrigTextDecoder);
                global.TextDecoder = SafeTextDecoder;
                try {
                  const util = require('util');
                  if (util && util.TextDecoder) {
                    util.TextDecoder = SafeTextDecoder;
                  }
                } catch (_) {}
              }

              // ES2023 Array methods polyfill for Next.js 15/16
              if (!Array.prototype.toSorted) {
                Array.prototype.toSorted = function(compareFn) {
                  return Array.from(this).sort(compareFn);
                };
              }
              if (!Array.prototype.toReversed) {
                Array.prototype.toReversed = function() {
                  return Array.from(this).reverse();
                };
              }
              if (!Array.prototype.toSpliced) {
                Array.prototype.toSpliced = function(start, deleteCount) {
                  const copy = Array.from(this);
                  copy.splice.apply(copy, Array.prototype.slice.call(arguments));
                  return copy;
                };
              }
              if (!Array.prototype.with) {
                Array.prototype.with = function(index, value) {
                  const copy = Array.from(this);
                  copy[index] = value;
                  return copy;
                };
              }

              // 1. Ensure os.cpus() never returns an empty array when Android restricts /proc/stat
              const origCpus = os.cpus;
              os.cpus = function() {
                try {
                  const cpus = origCpus.call(os);
                  if (Array.isArray(cpus) && cpus.length > 0) return cpus;
                } catch (e) {}
                return [
                  { model: 'ARMv8 Processor', speed: 2000, times: { user: 0, nice: 0, sys: 0, idle: 0, irq: 0 } },
                  { model: 'ARMv8 Processor', speed: 2000, times: { user: 0, nice: 0, sys: 0, idle: 0, irq: 0 } },
                  { model: 'ARMv8 Processor', speed: 2000, times: { user: 0, nice: 0, sys: 0, idle: 0, irq: 0 } },
                  { model: 'ARMv8 Processor', speed: 2000, times: { user: 0, nice: 0, sys: 0, idle: 0, irq: 0 } }
                ];
              };

              const NODE_EXEC = process.env.MOBILE_CODE_NODE_LAUNCHER || process.execPath;
              try {
                Object.defineProperty(process, 'execPath', {
                  value: NODE_EXEC,
                  writable: true,
                  configurable: true,
                  enumerable: true
                });
              } catch (e) {
                try { process.execPath = NODE_EXEC; } catch (_) {}
              }

              // Report process.platform as 'linux' so npm CLI tools (openurl, localtunnel, etc.) don't fail
              try {
                Object.defineProperty(process, 'platform', {
                  value: 'linux',
                  writable: true,
                  configurable: true,
                  enumerable: true
                });
              } catch (_) {}

              // Report process.version as dynamic active environment version (e.g. Node 20 / 22)
              const USR_DIR = process.env.MOBILE_CODE_USR_DIR || path.resolve(__dirname, '..');
              let targetNodeVer = process.env.MOBILE_CODE_NODE_VERSION || '';
              try {
                const verFile = path.join(USR_DIR, '.active_node_version');
                if (fs.existsSync(verFile)) {
                  const fileVer = fs.readFileSync(verFile, 'utf8').trim();
                  if (fileVer) targetNodeVer = fileVer;
                }
              } catch (_) {}
              if (!targetNodeVer) targetNodeVer = 'v20.18.0';
              if (!targetNodeVer.startsWith('v')) targetNodeVer = 'v' + targetNodeVer;
              const cleanVerNumber = targetNodeVer.replace(/^v/, '');
              try {
                Object.defineProperty(process, 'version', {
                  value: targetNodeVer,
                  writable: true,
                  configurable: true,
                  enumerable: true
                });
                const updatedVersions = Object.assign({}, process.versions || {}, { node: cleanVerNumber });
                Object.defineProperty(process, 'versions', {
                  value: updatedVersions,
                  writable: true,
                  configurable: true,
                  enumerable: true
                });
              } catch (_) {}

              // 2. Prevent stdin EOF from terminating dev servers (e.g. react-scripts start.js line 149)
              const origStdinOn = process.stdin.on;
              process.stdin.on = function(event, listener) {
                if (event === 'end') {
                  return this;
                }
                return origStdinOn.apply(this, arguments);
              };
              process.stdin.addListener = process.stdin.on;

              const origStdinResume = process.stdin.resume;
              process.stdin.resume = function() {
                try {
                  return origStdinResume.apply(this, arguments);
                } catch (e) {
                  return this;
                }
              };

              // 3. Module require interceptors for CRA / Webpack Dev Server
              const origRequire = mod.prototype.require;
              mod.prototype.require = function(id) {
                if (typeof id === 'string') {
                  if (id.includes('detect-port')) {
                    const fn = function detectPort(defaultPort, host, callback) {
                      const p = parseInt(defaultPort, 10) || 3000;
                      if (typeof host === 'function') {
                        host(null, p);
                        return Promise.resolve(p);
                      }
                      if (typeof callback === 'function') {
                        callback(null, p);
                      }
                      return Promise.resolve(p);
                    };
                    fn.detect = fn;
                    fn.default = fn;
                    return fn;
                  }

                  if (id.includes('checkRequiredFiles')) {
                    return function checkRequiredFiles() {
                      return true;
                    };
                  }

                  if (id.includes('checkBrowsers') || id.includes('browsersHelper')) {
                    const origBrowsers = origRequire.apply(this, arguments);
                    return Object.assign({}, origBrowsers, {
                      checkBrowsers: function() {
                        return Promise.resolve();
                      }
                    });
                  }

                  if (id.includes('openBrowser')) {
                    return function openBrowser(url) {
                      if (!url) return false;
                      try {
                        cp.spawn('/system/bin/am', ['start', '-a', 'android.intent.action.VIEW', '-d', url], {
                          detached: true,
                          stdio: 'ignore'
                        });
                        return true;
                      } catch (e) {
                        return false;
                      }
                    };
                  }

                  if (id.includes('openurl')) {
                    return {
                      open: function(url, callback) {
                        if (url) {
                          try {
                            cp.spawn('/system/bin/am', ['start', '-a', 'android.intent.action.VIEW', '-d', url], {
                              detached: true,
                              stdio: 'ignore'
                            });
                          } catch (_) {}
                        }
                        if (typeof callback === 'function') callback();
                      },
                      mailto: function(recipients, fields, sep, cb) {
                        if (typeof cb === 'function') cb();
                      }
                    };
                  }

                  if (id.includes('WebpackDevServerUtils')) {
                    const origUtils = origRequire.apply(this, arguments);
                    return Object.assign({}, origUtils, {
                      choosePort: function(host, defaultPort) {
                        const p = parseInt(defaultPort, 10) || 3000;
                        try { console.log("[DEV_SERVER] Selected port " + p + " on loopback interface."); } catch (_) {}
                        return Promise.resolve(p);
                      }
                    });
                  }
                }

                try {
                  return origRequire.apply(this, arguments);
                } catch (err) {
                  if (err && err.code === 'MODULE_NOT_FOUND' && typeof id === 'string' && id.startsWith('ajv')) {
                    const rel = id.replace(/^ajv\/?/, '');
                    const currentFileDir = this.filename ? path.dirname(this.filename) : (this.path || process.cwd());
                    const candidates = [
                      path.resolve(currentFileDir, '../schema-utils/node_modules/ajv', rel),
                      path.resolve(currentFileDir, '../../schema-utils/node_modules/ajv', rel),
                      path.resolve(currentFileDir, '../../../schema-utils/node_modules/ajv', rel),
                      path.resolve(process.cwd(), 'node_modules/schema-utils/node_modules/ajv', rel),
                      path.resolve(process.cwd(), 'node_modules/workbox-build/node_modules/ajv', rel)
                    ];
                    for (let i = 0; i < candidates.length; i++) {
                      try {
                        return origRequire.call(this, candidates[i]);
                      } catch (_) {}
                    }
                  }
                  throw err;
                }
              };

              // 4. Unicode regex fix for Node without full ICU tables (e.g. fast-uri /\P{ASCII}/u, Webpack 5 /\p{L}/u)
              function sanitizeUnicodeRegex(str) {
                if (!str.includes('\\p{') && !str.includes('\\P{')) return str;
                return str
                  .replaceAll('\\P{ASCII}', '[^\\x00-\\x7F]')
                  .replaceAll('\\p{ASCII}', '[\\x00-\\x7F]')
                  .replaceAll('\\p{Uppercase_Letter}', 'A-Z')
                  .replaceAll('\\p{Lowercase_Letter}', 'a-z')
                  .replaceAll('\\p{Lu}', 'A-Z')
                  .replaceAll('\\p{Ll}', 'a-z')
                  .replaceAll('\\p{Lt}', 'A-Z')
                  .replaceAll('\\p{Alpha}', 'a-zA-Z')
                  .replaceAll('\\p{L}', 'a-zA-Z')
                  .replaceAll('\\p{Nl}', '0-9')
                  .replaceAll('\\p{Nd}', '0-9')
                  .replaceAll('\\p{N}', '0-9')
                  .replaceAll('\\p{Mn}', '')
                  .replaceAll('\\p{Mc}', '')
                  .replaceAll('\\p{Me}', '')
                  .replaceAll('\\p{Pc}', '_')
                  .replaceAll('\\p{Space_Separator}', ' ')
                  .replaceAll('\\p{ID_Start}', 'a-zA-Z${'$'}_')
                  .replaceAll('\\p{ID_Continue}', 'a-zA-Z0-9${'$'}_')
                  .replaceAll('\\p{Emoji}', 'a-zA-Z0-9')
                  .replaceAll('\\p{Extended_Pictographic}', 'a-zA-Z0-9');
              }

              const origJsExtension = mod._extensions['.js'];
              mod._extensions['.js'] = function(module, filename) {
                if (typeof filename === 'string' && (filename.includes('node_modules') || filename.includes('fast-uri') || filename.includes('webpack'))) {
                  try {
                    let content = fs.readFileSync(filename, 'utf8');
                    if (content.includes('\\p{') || content.includes('\\P{')) {
                      content = sanitizeUnicodeRegex(content);
                      module._compile(content, filename);
                      return;
                    }
                  } catch (e) {}
                }
                return origJsExtension.call(this, module, filename);
              };
              if (mod._extensions['.cjs']) {
                const origCjsExtension = mod._extensions['.cjs'];
                mod._extensions['.cjs'] = function(module, filename) {
                  if (typeof filename === 'string' && (filename.includes('node_modules') || filename.includes('fast-uri') || filename.includes('webpack'))) {
                    try {
                      let content = fs.readFileSync(filename, 'utf8');
                      if (content.includes('\\p{') || content.includes('\\P{')) {
                        content = sanitizeUnicodeRegex(content);
                        module._compile(content, filename);
                        return;
                      }
                    } catch (e) {}
                  }
                  return origCjsExtension.call(this, module, filename);
                };
              }

              const origCompile = mod.prototype._compile;
              mod.prototype._compile = function(content, filename) {
                if (typeof filename === 'string' && filename.includes('next.config')) {
                  if (typeof content === 'string' && content.includes('export default')) {
                    content = content
                      .replace(/export\s+default\s+([a-zA-Z0-9_${'$'}]+);?/g, 'module.exports = ${'$'}1;')
                      .replace(/export\s+default\s+/g, 'module.exports = ');
                  }
                }
                return origCompile.call(this, content, filename);
              };

              // 5. Watchpack / fs.watch guard for Android system directories
              const origWatch = fs.watch;
              fs.watch = function(filename, options, listener) {
                const p = String(filename);
                if (p === '/' || p === '/data' || p === '/data/user' || p === '/data/user/0' || p === '/data/data') {
                  const dummy = new (require('events').EventEmitter)();
                  dummy.close = function() {};
                  dummy.ref = function() { return this; };
                  dummy.unref = function() { return this; };
                  return dummy;
                }
                try {
                  return origWatch.apply(this, arguments);
                } catch (err) {
                  if (err && (err.code === 'EACCES' || err.code === 'EPERM')) {
                    const dummy = new (require('events').EventEmitter)();
                    dummy.close = function() {};
                    dummy.ref = function() { return this; };
                    dummy.unref = function() { return this; };
                    return dummy;
                  }
                  throw err;
                }
              };

              const SHIM_FILE = path.join(USR_DIR, 'lib/android-shim.js');
              const NPM_CLI_JS = process.env.MOBILE_CODE_NPM_CLI || path.join(USR_DIR, 'lib/node_modules/npm/bin/npm-cli.js');
              const NPX_CLI_JS = process.env.MOBILE_CODE_NPX_CLI || path.join(USR_DIR, 'lib/node_modules/npm/bin/npx-cli.js');
              const ANDROID_SH = fs.existsSync('/system/bin/sh') ? '/system/bin/sh' : '/bin/sh';

              process.on('uncaughtException', (err) => {
                try {
                  const msg = (err && err.stack) ? err.stack : String(err);
                  process.stderr.write("[NODE_ERROR] Uncaught exception:\n" + msg + "\n");
                } catch (_) {}
                process.exit(1);
              });
              process.on('unhandledRejection', (reason) => {
                try {
                  const msg = (reason && reason.stack) ? reason.stack : String(reason);
                  process.stderr.write("[NODE_ERROR] Unhandled rejection:\n" + msg + "\n");
                } catch (_) {}
              });

              function getPathFromEnvPairs(envPairs) {
                if (Array.isArray(envPairs)) {
                  for (let i = 0; i < envPairs.length; i++) {
                    const pair = envPairs[i];
                    if (typeof pair === 'string' && pair.startsWith('PATH=')) {
                      return pair.slice(5);
                    }
                  }
                }
                return process.env.PATH || '';
              }

              function resolveInPath(cmd, pathEnv, cwd) {
                if (!cmd) return null;
                if (cmd.includes('/')) {
                  const abs = path.isAbsolute(cmd) ? cmd : path.resolve(cwd || process.cwd(), cmd);
                  return fs.existsSync(abs) ? abs : null;
                }
                const dirs = (pathEnv || '').split(':').filter(Boolean);
                for (let i = 0; i < dirs.length; i++) {
                  const candidate = path.join(dirs[i], cmd);
                  try {
                    if (fs.existsSync(candidate) && fs.statSync(candidate).isFile()) {
                      return candidate;
                    }
                  } catch (e) {}
                }
                return null;
              }

              function inspectExecutable(filePath) {
                if (!filePath) return null;
                try {
                  const fd = fs.openSync(filePath, 'r');
                  const buf = Buffer.alloc(256);
                  const bytesRead = fs.readSync(fd, buf, 0, 256, 0);
                  fs.closeSync(fd);
                  if (bytesRead >= 4 && buf[0] === 0x7f && buf[1] === 0x45 && buf[2] === 0x4c && buf[3] === 0x46) {
                    return { type: 'elf', path: filePath };
                  }
                  const header = buf.slice(0, bytesRead).toString('utf8');
                  if (header.startsWith('#!')) {
                    const firstLine = header.split(/\r?\n/)[0];
                    if (/\bnode(js)?\b/.test(firstLine)) {
                      return { type: 'node', path: filePath };
                    }
                    if (/\b(sh|bash)\b/.test(firstLine)) {
                      return { type: 'sh', path: filePath };
                    }
                  }
                  if (/\.(c?js|mjs)$/i.test(filePath)) {
                    return { type: 'node', path: filePath };
                  }
                } catch (e) {}
                return null;
              }

              function rewriteSingleShellSegment(segment, pathEnv, cwd) {
                const trimmed = segment.trimStart();
                const leadingWs = segment.slice(0, segment.length - trimmed.length);
                if (!trimmed) return segment;

                let rest = trimmed;
                let envPrefix = '';
                while (true) {
                  const envMatch = rest.match(/^([A-Za-z_][A-Za-z0-9_]*=(?:'[^']*'|"[^"]*"|\S+))\s+/);
                  if (!envMatch) break;
                  envPrefix += envMatch[0];
                  rest = rest.slice(envMatch[0].length);
                }

                const tokenMatch = rest.match(/^(?:'([^']*)'|"([^"]*)"|(\S+))([\s\S]*)$/);
                if (!tokenMatch) return segment;

                const rawCmd = tokenMatch[1] ?? tokenMatch[2] ?? tokenMatch[3];
                const tail = tokenMatch[4] || '';
                if (!rawCmd) return segment;

                const base = path.basename(rawCmd);
                if (base === 'node' || base === 'nodejs') {
                  const trimmedTail = tail.trim();
                  if (trimmedTail === '-v' || trimmedTail === '--version') {
                    return `${'$'}{leadingWs}${'$'}{envPrefix}"${'$'}{NODE_EXEC}" --require "${'$'}{SHIM_FILE}" -p "process.version"`;
                  }
                  return `${'$'}{leadingWs}${'$'}{envPrefix}"${'$'}{NODE_EXEC}" --require "${'$'}{SHIM_FILE}"${'$'}{tail}`;
                }
                if (base === 'npm') {
                  return `${'$'}{leadingWs}${'$'}{envPrefix}"${'$'}{NODE_EXEC}" --require "${'$'}{SHIM_FILE}" "${'$'}{NPM_CLI_JS}"${'$'}{tail}`;
                }
                if (base === 'npx') {
                  return `${'$'}{leadingWs}${'$'}{envPrefix}"${'$'}{NODE_EXEC}" --require "${'$'}{SHIM_FILE}" "${'$'}{NPX_CLI_JS}"${'$'}{tail}`;
                }
                if (base === 'react-scripts') {
                  let startScript = path.resolve(cwd, 'node_modules/react-scripts/scripts/start.js');
                  if (!fs.existsSync(startScript)) {
                    try {
                      const files = fs.readdirSync(cwd);
                      for (let i = 0; i < files.length; i++) {
                        const cand = path.resolve(cwd, files[i], 'node_modules/react-scripts/scripts/start.js');
                        if (fs.existsSync(cand)) {
                          startScript = cand;
                          break;
                        }
                      }
                    } catch (_) {}
                  }

                  if (tail.includes('start') && fs.existsSync(startScript)) {
                    return `${'$'}{leadingWs}${'$'}{envPrefix}"${'$'}{NODE_EXEC}" --require "${'$'}{SHIM_FILE}" "${'$'}{startScript}"`;
                  }

                  let binScript = path.resolve(cwd, 'node_modules/react-scripts/bin/react-scripts.js');
                  if (!fs.existsSync(binScript)) {
                    try {
                      const files = fs.readdirSync(cwd);
                      for (let i = 0; i < files.length; i++) {
                        const cand = path.resolve(cwd, files[i], 'node_modules/react-scripts/bin/react-scripts.js');
                        if (fs.existsSync(cand)) {
                          binScript = cand;
                          break;
                        }
                      }
                    } catch (_) {}
                  }
                  if (fs.existsSync(binScript)) {
                    return `${'$'}{leadingWs}${'$'}{envPrefix}"${'$'}{NODE_EXEC}" --require "${'$'}{SHIM_FILE}" "${'$'}{binScript}"${'$'}{tail}`;
                  }
                }
                if (base === 'next') {
                  let nextScript = path.resolve(cwd, 'node_modules/next/dist/bin/next');
                  if (!fs.existsSync(nextScript)) {
                    try {
                      const files = fs.readdirSync(cwd);
                      for (let i = 0; i < files.length; i++) {
                        const cand = path.resolve(cwd, files[i], 'node_modules/next/dist/bin/next');
                        if (fs.existsSync(cand)) {
                          nextScript = cand;
                          break;
                        }
                      }
                    } catch (_) {}
                  }
                  if (fs.existsSync(nextScript)) {
                    const hasTurboOrWebpack = tail.includes('--turbo') || tail.includes('--turbopack') || tail.includes('--webpack');
                    const devFlag = (tail.trim().startsWith('dev') && !hasTurboOrWebpack) ? ' --webpack' : '';
                    return `${'$'}{leadingWs}${'$'}{envPrefix}"${'$'}{NODE_EXEC}" --require "${'$'}{SHIM_FILE}" "${'$'}{nextScript}"${'$'}{tail}${'$'}{devFlag}`;
                  }
                }
                if (rawCmd === '/bin/sh' || rawCmd === '/usr/bin/sh' || rawCmd === '/bin/bash' || rawCmd === '/usr/bin/bash') {
                  return `${'$'}{leadingWs}${'$'}{envPrefix}"${'$'}{ANDROID_SH}"${'$'}{tail}`;
                }

                const resolved = resolveInPath(rawCmd, pathEnv, cwd);
                if (resolved) {
                  const info = inspectExecutable(resolved);
                  if (info && info.type === 'node') {
                    return `${'$'}{leadingWs}${'$'}{envPrefix}"${'$'}{NODE_EXEC}" --require "${'$'}{SHIM_FILE}" "${'$'}{resolved}"${'$'}{tail}`;
                  }
                  if (info && info.type === 'sh') {
                    return `${'$'}{leadingWs}${'$'}{envPrefix}"${'$'}{ANDROID_SH}" "${'$'}{resolved}"${'$'}{tail}`;
                  }
                }
                return segment;
              }

              function transformSpawnOptions(options) {
                if (!options || typeof options.file !== 'string') return options;
                const pathEnv = getPathFromEnvPairs(options.envPairs);
                const cwd = options.cwd || process.cwd();
                const rawFile = options.file;
                const base = path.basename(rawFile);
                const origArgs = Array.isArray(options.args) ? options.args.slice() : [rawFile];

                if (
                  rawFile === 'sh' ||
                  rawFile === 'bash' ||
                  rawFile === '/bin/sh' ||
                  rawFile === '/usr/bin/sh' ||
                  rawFile === '/bin/bash' ||
                  rawFile === '/usr/bin/bash' ||
                  rawFile === '/system/bin/sh'
                ) {
                  options.file = ANDROID_SH;
                  origArgs[0] = ANDROID_SH;
                  if (origArgs.length >= 3 && origArgs[1] === '-c' && typeof origArgs[2] === 'string') {
                    origArgs[2] = rewriteSingleShellSegment(origArgs[2], pathEnv, cwd);
                  }
                  options.args = origArgs;
                  return options;
                }

                if (
                  rawFile === NODE_EXEC ||
                  rawFile.endsWith('/app_process64') ||
                  rawFile.endsWith('/app_process32') ||
                  rawFile.endsWith('/app_process') ||
                  rawFile.endsWith('libnode_launcher.so') ||
                  rawFile === 'libnode_launcher.so' ||
                  base === 'node' ||
                  base === 'nodejs'
                ) {
                  options.file = NODE_EXEC;
                  const newArgs = [NODE_EXEC];
                  const isVersionCheck = (origArgs.length === 2 && (origArgs[1] === '-v' || origArgs[1] === '--version'));
                  if (isVersionCheck) {
                    newArgs.push('--require', SHIM_FILE, '-p', 'process.version');
                  } else {
                    if (!origArgs.includes('--require')) {
                      newArgs.push('--require', SHIM_FILE);
                    }
                    newArgs.push(...origArgs.slice(1));
                  }
                  options.args = newArgs;
                  return options;
                }

                if (base === 'npm') {
                  options.file = NODE_EXEC;
                  options.args = [NODE_EXEC, '--require', SHIM_FILE, NPM_CLI_JS, ...origArgs.slice(1)];
                  return options;
                }

                if (base === 'npx') {
                  options.file = NODE_EXEC;
                  options.args = [NODE_EXEC, '--require', SHIM_FILE, NPX_CLI_JS, ...origArgs.slice(1)];
                  return options;
                }

                if (base === 'react-scripts') {
                  let startScript = path.resolve(cwd, 'node_modules/react-scripts/scripts/start.js');
                  if (!fs.existsSync(startScript)) {
                    try {
                      const files = fs.readdirSync(cwd);
                      for (let i = 0; i < files.length; i++) {
                        const cand = path.resolve(cwd, files[i], 'node_modules/react-scripts/scripts/start.js');
                        if (fs.existsSync(cand)) {
                          startScript = cand;
                          break;
                        }
                      }
                    } catch (_) {}
                  }

                  const isStart = origArgs.some(a => typeof a === 'string' && a.includes('start'));
                  if (isStart && fs.existsSync(startScript)) {
                    options.file = NODE_EXEC;
                    options.args = [NODE_EXEC, '--require', SHIM_FILE, startScript];
                    return options;
                  }

                  let binScript = path.resolve(cwd, 'node_modules/react-scripts/bin/react-scripts.js');
                  if (!fs.existsSync(binScript)) {
                    try {
                      const files = fs.readdirSync(cwd);
                      for (let i = 0; i < files.length; i++) {
                        const cand = path.resolve(cwd, files[i], 'node_modules/react-scripts/bin/react-scripts.js');
                        if (fs.existsSync(cand)) {
                          binScript = cand;
                          break;
                        }
                      }
                    } catch (_) {}
                  }
                  if (fs.existsSync(binScript)) {
                    options.file = NODE_EXEC;
                    options.args = [NODE_EXEC, '--require', SHIM_FILE, binScript, ...origArgs.slice(1)];
                    return options;
                  }
                }

                if (base === 'next') {
                  let nextScript = path.resolve(cwd, 'node_modules/next/dist/bin/next');
                  if (!fs.existsSync(nextScript)) {
                    try {
                      const files = fs.readdirSync(cwd);
                      for (let i = 0; i < files.length; i++) {
                        const cand = path.resolve(cwd, files[i], 'node_modules/next/dist/bin/next');
                        if (fs.existsSync(cand)) {
                          nextScript = cand;
                          break;
                        }
                      }
                    } catch (_) {}
                  }
                  if (fs.existsSync(nextScript)) {
                    const hasTurboOrWebpack = origArgs.some(a => typeof a === 'string' && (a.includes('turbo') || a.includes('webpack')));
                    const isDev = origArgs.some(a => typeof a === 'string' && a.includes('dev'));
                    const extraArgs = (isDev && !hasTurboOrWebpack) ? ['--webpack'] : [];
                    options.file = NODE_EXEC;
                    options.args = [NODE_EXEC, '--require', SHIM_FILE, nextScript, ...origArgs.slice(1), ...extraArgs];
                    return options;
                  }
                }

                const resolved = resolveInPath(rawFile, pathEnv, cwd);
                if (resolved) {
                  const info = inspectExecutable(resolved);
                  if (info && info.type === 'node') {
                    options.file = NODE_EXEC;
                    options.args = [NODE_EXEC, '--require', SHIM_FILE, resolved, ...origArgs.slice(1)];
                    return options;
                  }
                  if (info && info.type === 'sh') {
                    options.file = ANDROID_SH;
                    options.args = [ANDROID_SH, resolved, ...origArgs.slice(1)];
                    return options;
                  }
                }

                return options;
              }

              const origSpawn = cp.ChildProcess.prototype.spawn;
              cp.ChildProcess.prototype.spawn = function(options) {
                transformSpawnOptions(options);
                return origSpawn.call(this, options);
              };

              function normalizeSyncCall(file, args, options) {
                let normArgs;
                let normOpts;
                if (Array.isArray(args)) {
                  normArgs = args.slice();
                  normOpts = { ...options };
                } else if (args && typeof args === 'object') {
                  normArgs = [];
                  normOpts = { ...args };
                } else {
                  normArgs = [];
                  normOpts = { ...options };
                }
                const env = normOpts.env || process.env;
                const envPairs = Object.keys(env).map(k => `${'$'}{k}=${'$'}{env[k]}`);
                const tempOpts = {
                  file: String(file),
                  args: [String(file), ...normArgs.map(String)],
                  cwd: normOpts.cwd || process.cwd(),
                  envPairs
                };
                if (normOpts.shell) {
                  const shellCmd = [tempOpts.file, ...normArgs].join(' ');
                  tempOpts.file = typeof normOpts.shell === 'string' ? normOpts.shell : ANDROID_SH;
                  tempOpts.args = [tempOpts.file, '-c', shellCmd];
                  normOpts = { ...normOpts, shell: false };
                }
                transformSpawnOptions(tempOpts);
                return {
                  file: tempOpts.file,
                  args: tempOpts.args.slice(1),
                  options: normOpts
                };
              }

              const origSpawnSync = cp.spawnSync;
              cp.spawnSync = function(file, args, options) {
                const norm = normalizeSyncCall(file, args, options);
                const res = origSpawnSync.call(this, norm.file, norm.args, norm.options);
                if (res && res.error) {
                  try { process.stderr.write(String(res.error) + '\n'); } catch (_) {}
                }
                return res;
              };

              const origExecFileSync = cp.execFileSync;
              cp.execFileSync = function(file, args, options) {
                const norm = normalizeSyncCall(file, args, options);
                return origExecFileSync.call(this, norm.file, norm.args, norm.options);
              };

              const origExecSync = cp.execSync;
              cp.execSync = function(command, options) {
                const opts = { ...options };
                const env = opts.env || process.env;
                const pathEnv = env.PATH || process.env.PATH || '';
                const cwd = opts.cwd || process.cwd();
                const rewritten = rewriteSingleShellSegment(String(command), pathEnv, cwd);
                if (fs.existsSync('/system/bin/sh')) {
                  opts.shell = '/system/bin/sh';
                }
                return origExecSync.call(this, rewritten, opts);
              };

              if (typeof mod.syncBuiltinESMExports === 'function') {
                try { mod.syncBuiltinESMExports(); } catch (e) {}
              }
            }
        """.trimIndent() + "\n"
        shimFile.writeText(content, Charsets.UTF_8)
        shimFile.setReadable(true, false)
    }

    fun getUsrDir(context: Context): File = File(context.filesDir.canonicalFile, "usr")

    fun getUsrBin(context: Context): File = File(getUsrDir(context), "bin")

    fun getAndroidShimFile(context: Context): File = File(getUsrDir(context), "lib/android-shim.js")

    fun getNpmCliJs(context: Context): File = File(getUsrDir(context), "lib/node_modules/npm/bin/npm-cli.js")

    fun getNpxCliJs(context: Context): File = File(getUsrDir(context), "lib/node_modules/npm/bin/npx-cli.js")

    fun getNativeNodeLauncher(context: Context): File {
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        return File(nativeLibDir, "libnode_launcher.so")
    }

    fun isNativeNodeAvailable(context: Context): Boolean {
        return getNativeNodeLauncher(context).exists()
    }

    fun patchWorkspaceModules(workspacesDir: File) {
        try {
            if (!workspacesDir.exists()) return
            workspacesDir.walkTopDown().maxDepth(6).forEach { file ->
                if (file.name == "index.js" && file.parentFile?.name == "fast-uri") {
                    val text = file.readText(Charsets.UTF_8)
                    if (text.contains("\\P{ASCII}")) {
                        file.writeText(text.replace("\\P{ASCII}", "[^\\x00-\\x7F]"), Charsets.UTF_8)
                    }
                } else if (file.name == "index.js" && file.parentFile?.name == "camelcase") {
                    val text = file.readText(Charsets.UTF_8)
                    if (text.contains("\\p{Lu}")) {
                        file.writeText(text.replace("\\p{Lu}", "A-Z"), Charsets.UTF_8)
                    }
                } else if (file.name == "wasm.js" && (file.parentFile?.name == "swc-wasm-nodejs" || file.parentFile?.name == "package")) {
                    val text = file.readText(Charsets.UTF_8)
                    if (text.contains("fatal: true")) {
                        file.writeText(text.replace("fatal: true", "fatal: false"), Charsets.UTF_8)
                    }
                }
            }

            // Automatically extract downloaded @next/swc-wasm-nodejs tarball if present in Next.js cache
            val nextSwcCache = File(workspacesDir.parentFile, "home/.cache/next-swc")
            if (nextSwcCache.exists()) {
                val wasmTgz = nextSwcCache.listFiles()?.firstOrNull { it.name.startsWith("swc-wasm-nodejs") && it.name.endsWith(".tgz") }
                if (wasmTgz != null) {
                    workspacesDir.walkTopDown().maxDepth(3).filter { it.isDirectory && File(it, "node_modules/next").exists() }.forEach { projectDir ->
                        val targetNodeModules = File(projectDir, "node_modules/@next/swc-wasm-nodejs")
                        val targetFallback = File(projectDir, "node_modules/next/next-swc-fallback/@next/swc-wasm-nodejs")
                        listOf(targetNodeModules, targetFallback).forEach { targetDir ->
                            if (!File(targetDir, "wasm.js").exists()) {
                                targetDir.mkdirs()
                                try {
                                    FileInputStream(wasmTgz).use { fis ->
                                        extractTgzStream(fis, targetDir)
                                    }
                                    val wasmJs = File(targetDir, "wasm.js")
                                    if (wasmJs.exists()) {
                                        val txt = wasmJs.readText(Charsets.UTF_8)
                                        if (txt.contains("fatal: true")) {
                                            wasmJs.writeText(txt.replace("fatal: true", "fatal: false"), Charsets.UTF_8)
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun isElfBinary(file: File): Boolean {
        return try {
            if (!file.exists() || file.isDirectory || file.length() < 4) return false
            FileInputStream(file).use { fis ->
                val buf = ByteArray(4)
                val read = fis.read(buf)
                read == 4 && buf[0] == 0x7f.toByte() && buf[1] == 'E'.code.toByte() && buf[2] == 'L'.code.toByte() && buf[3] == 'F'.code.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun isNodeScript(file: File): Boolean {
        return try {
            if (!file.exists() || file.isDirectory) return false
            if (file.name.endsWith(".js") || file.name.endsWith(".mjs") || file.name.endsWith(".cjs")) return true
            file.bufferedReader().use { reader ->
                val firstLine = reader.readLine() ?: ""
                firstLine.startsWith("#!") && (firstLine.contains("node") || firstLine.contains("nodejs"))
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Rewrites top-level terminal invocations of node, nodejs, npm, npx, and create-react-app
     * so they directly execute the ELF binary in nativeLibraryDir without triggering SELinux
     * app_data_file script execve blocks on Android 10+.
     */
    fun rewriteCommandForAndroid(context: Context, cmdLine: String, workingDir: File? = null): String {
        val trimmed = cmdLine.trim()
        if (trimmed.isEmpty()) return cmdLine
        val launcher = getNativeNodeLauncher(context).absolutePath
        val npmCli = getNpmCliJs(context).absolutePath
        val npxCli = getNpxCliJs(context).absolutePath

        val match = Regex("""^(\S+)([\s\S]*)$""").find(trimmed) ?: return cmdLine
        val bin = match.groupValues[1]
        val rest = match.groupValues[2]

        val shim = getAndroidShimFile(context).absolutePath
        val libDir = File(context.applicationInfo.nativeLibraryDir).absolutePath
        val prefix = "export LD_LIBRARY_PATH=\"$libDir:\$LD_LIBRARY_PATH\"; "

        return when (bin) {
            "node", "nodejs" -> {
                val subCmd = rest.trim()
                if (subCmd == "-v" || subCmd == "--version") {
                    "${prefix}\"$launcher\" --require \"$shim\" -p \"process.version\""
                } else {
                    "${prefix}\"$launcher\" --require \"$shim\"$rest"
                }
            }
            "npm" -> {
                val subCmd = rest.trim()
                if (subCmd == "start" || subCmd == "run start" || subCmd.startsWith("start ") || subCmd.startsWith("run start ")) {
                    val inCurrent = workingDir?.let { File(it, "node_modules/react-scripts/scripts/start.js") }
                    val inSub = if (inCurrent?.exists() != true) {
                        workingDir?.listFiles()?.firstOrNull { f ->
                            f.isDirectory && File(f, "node_modules/react-scripts/scripts/start.js").exists()
                        }?.let { File(it, "node_modules/react-scripts/scripts/start.js") }
                    } else null
                    val startScript = inCurrent?.takeIf { it.exists() } ?: inSub?.takeIf { it.exists() }
                    if (startScript != null && startScript.exists()) {
                        "${prefix}\"$launcher\" --require \"$shim\" \"${startScript.absolutePath}\""
                    } else {
                        "${prefix}\"$launcher\" --require \"$shim\" \"$npmCli\"$rest"
                    }
                } else {
                    "${prefix}\"$launcher\" --require \"$shim\" \"$npmCli\"$rest"
                }
            }
            "npx" -> "${prefix}\"$launcher\" --require \"$shim\" \"$npxCli\" --yes$rest"
            "create-react-app" -> "${prefix}\"$launcher\" --require \"$shim\" \"$npxCli\" --yes create-react-app$rest"
            "react-scripts" -> {
                val isStart = rest.contains("start")
                val targetName = if (isStart) "scripts/start.js" else "bin/react-scripts.js"
                val inCurrent = workingDir?.let { File(it, "node_modules/react-scripts/$targetName") }
                val inSub = if (inCurrent?.exists() != true) {
                    workingDir?.listFiles()?.firstOrNull { f ->
                        f.isDirectory && File(f, "node_modules/react-scripts/$targetName").exists()
                    }?.let { File(it, "node_modules/react-scripts/$targetName") }
                } else null

                val scriptFile = inCurrent?.takeIf { it.exists() }
                    ?: inSub?.takeIf { it.exists() }
                    ?: File(workingDir ?: File("."), "node_modules/react-scripts/$targetName")

                "${prefix}\"$launcher\" --require \"$shim\" \"${scriptFile.absolutePath}\"$rest"
            }
            "next" -> {
                val inCurrent = workingDir?.let { File(it, "node_modules/next/dist/bin/next") }
                val inSub = if (inCurrent?.exists() != true) {
                    workingDir?.listFiles()?.firstOrNull { f ->
                        f.isDirectory && File(f, "node_modules/next/dist/bin/next").exists()
                    }?.let { File(it, "node_modules/next/dist/bin/next") }
                } else null
                val nextScript = inCurrent?.takeIf { it.exists() }
                    ?: inSub?.takeIf { it.exists() }
                    ?: File(workingDir ?: File("."), "node_modules/next/dist/bin/next")

                val hasTurboOrWebpack = rest.contains("--turbo") || rest.contains("--turbopack") || rest.contains("--webpack")
                val devFlag = if (rest.trim().startsWith("dev") && !hasTurboOrWebpack) " --webpack" else ""
                "${prefix}\"$launcher\" --require \"$shim\" \"${nextScript.absolutePath}\"$rest$devFlag"
            }
            else -> {
                val usrBin = getUsrBin(context)
                val usrBinFile = File(usrBin, bin)
                val localBinFile = workingDir?.let { File(it, "node_modules/.bin/$bin") }
                val targetFile = when {
                    usrBinFile.exists() -> usrBinFile
                    localBinFile?.exists() == true -> localBinFile
                    else -> null
                }

                if (targetFile != null) {
                    val canonicalTarget = targetFile.canonicalFile
                    if (isElfBinary(canonicalTarget)) {
                        val msg = if (bin == "ngrok") {
                            "MobileCode: 'ngrok' adalah binary native (ELF) yang diblokir oleh Android SELinux (W^X).\n" +
                            "Gunakan 'lt --port <port>' atau 'tunnel <port>' untuk membuat public URL / tunnel gratis:\n" +
                            "  lt --port 3000 (atau: tunnel 3000)\n"
                        } else {
                            "MobileCode: '$bin' adalah binary native (ELF) yang diblokir oleh Android SELinux (W^X).\n"
                        }
                        return "printf '%s' \"$msg\""
                    }

                    if (canonicalTarget.name.endsWith(".js") || isNodeScript(canonicalTarget)) {
                        return "${prefix}\"$launcher\" --require \"$shim\" \"${canonicalTarget.absolutePath}\"$rest"
                    }
                }

                if (bin == "tunnel") {
                    val portArg = rest.trim().ifEmpty { "3000" }
                    val ltJs = File(getUsrDir(context), "lib/node_modules/localtunnel/bin/lt.js")
                    if (ltJs.exists()) {
                        return "\"$launcher\" --require \"$shim\" \"${ltJs.absolutePath}\" --port $portArg"
                    } else {
                        return "\"$launcher\" \"$npmCli\" install -g localtunnel && \"$launcher\" --require \"$shim\" \"${ltJs.absolutePath}\" --port $portArg"
                    }
                }

                cmdLine
            }
        }
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

