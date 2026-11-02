# PROJECT: MOBILE CODE
## Technical Architecture, Specification & Implementation Roadmap

See complete specification in the project brain artifact or this document.

### Subsystems Overview
1. **Host App & UI Layer**: Jetpack Compose, Material 3 base customized with VS Code Dark+ styling (`#1e1e1e`, `#252526`, `#333333`, `#007acc`, `#3c3c3c`).
2. **Editor Subsystem**: Monaco Editor hosted in an optimized Android WebView, connected via bidirectional JavaScript interface (`@JavascriptInterface`).
3. **Terminal Subsystem**: Interactive PTY-backed Linux Android shell (`/system/bin/sh`) rendered with xterm.js, multi-session tabs, physical & virtual coding keyboard.
4. **Filesystem Subsystem**: Single source of truth workspace storage supporting internal app sandbox and SAF (Storage Access Framework).
5. **Git Subsystem**: Integrated Git CLI with Source Control UI (staging, unstaging, commit, branch switching).
6. **Development Runtime**: Support for Node.js and Python environments on Android ARM64 Bionic.
7. **Command Palette & Search**: Full keyboard-driven `Ctrl+Shift+P` command execution and multi-file search.

