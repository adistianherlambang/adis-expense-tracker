# Monaco Editor & xterm.js JavaScript Bridge proguard preservation
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class com.mobilecode.app.core.editor.EditorBridge { *; }
-keep class com.mobilecode.app.core.terminal.TerminalBridge { *; }
-keepattributes JavascriptInterface
-keepattributes *Annotation*

