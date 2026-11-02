# Mobile Code (Android IDE)

> Full-featured Android Development Environment with Visual Studio Code Desktop UX.

Mobile Code brings a genuine desktop-like programming workflow to Android smartphones, tablets, and desktop mode (Samsung DeX):
- **Monaco Code Editor**: Web-based editor with syntax highlighting, line numbers, indentation, multi-tab editing, and dirty indicators.
- **Integrated Terminal**: Powered by real Linux Android PTY shell processes and xterm.js, supporting interactive CLI commands (`pwd`, `ls`, `mkdir`, `cat`, etc.).
- **Filesystem & Workspaces**: Full workspace project management with internal app storage and Android Storage Access Framework (SAF).
- **Git Integration**: Full Source Control UI for staging, unstaging, commit, branch switching, and diffs using Git CLI backend.
- **Development Runtimes**: Architecture for running Node.js and Python environments directly on Android.
- **Input System**: Native physical hardware keyboard shortcuts (`Ctrl+S`, `Ctrl+Shift+P`, `Ctrl+\``, `Ctrl+B`) + touch-optimized 4-page Programming Accessory Bar.
- **Visual Studio Code Dark+ Theme**: Exact color palette, compact desktop density, and Consolas monospace font hierarchy.

## Architecture & Documentation
- [Technical Architecture & Specifications](docs/ARCHITECTURE.md)
- Design system: Dark+ (`#1e1e1e`, `#252526`, `#333333`, `#007acc`, `#3c3c3c`)
- Core modules: `core-editor`, `core-terminal`, `core-filesystem`, `core-git`, `core-runtime`, `core-keyboard`, `core-search`, `core-settings`
- UI layer: `TitleBar`, `ActivityBar`, `SidebarContainer`, `EditorArea`, `BottomPanel`, `StatusBar`, `CommandPalette`

## Building the Project
Requirements:
- Android SDK 34/35 (`compileSdk = 35`, `minSdk = 26`)
- JDK 21
- Jetpack Compose with Material 3 styling
