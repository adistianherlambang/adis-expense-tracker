package com.mobilecode.app.core.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import com.mobilecode.app.core.designsystem.VsCodeTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.util.Locale

/**
 * Kind of autocomplete suggestion for UI icons and categorization.
 */
enum class CompletionKind {
    KEYWORD,
    TYPE,
    FUNCTION,
    VARIABLE,
    SNIPPET
}

data class CompletionItem(
    val label: String,
    val insertText: String,
    val kind: CompletionKind,
    val detail: String = ""
)

/**
 * Metadata and syntax rules for an editor language.
 */
data class LanguageDef(
    val id: String,
    val name: String,
    val commentSingle: String = "//",
    val commentBlockStart: String? = "/*",
    val commentBlockEnd: String? = "*/",
    val keywords: Set<String>,
    val types: Set<String>,
    val builtins: Set<String> = emptySet(),
    val snippets: List<CompletionItem> = emptyList()
)

object LanguageSupport {

    private val jsonPretty = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    // 1. JavaScript
    private val LANG_JAVASCRIPT = LanguageDef(
        id = "javascript",
        name = "JavaScript",
        commentSingle = "//",
        commentBlockStart = "/*",
        commentBlockEnd = "*/",
        keywords = setOf(
            "async", "await", "break", "case", "catch", "class", "const", "continue",
            "debugger", "default", "delete", "do", "else", "export", "extends", "finally",
            "for", "function", "get", "if", "import", "in", "instanceof", "let", "new",
            "of", "return", "set", "static", "super", "switch", "this", "throw", "try",
            "typeof", "var", "void", "while", "with", "yield", "true", "false", "null",
            "undefined", "NaN", "Infinity"
        ),
        types = setOf(
            "Object", "Array", "String", "Number", "Boolean", "Function", "Symbol", "BigInt",
            "Map", "Set", "WeakMap", "WeakSet", "Promise", "RegExp", "Date", "Error"
        ),
        builtins = setOf(
            "console", "window", "document", "global", "process", "fetch", "setTimeout",
            "setInterval", "clearTimeout", "clearInterval", "require", "module", "exports",
            "Math", "JSON", "parseInt", "parseFloat"
        ),
        snippets = listOf(
            CompletionItem("clg", "console.log();", CompletionKind.SNIPPET, "console.log()"),
            CompletionItem("fn", "function () {\n    \n}", CompletionKind.SNIPPET, "function declaration"),
            CompletionItem("afn", "() => {\n    \n}", CompletionKind.SNIPPET, "arrow function"),
            CompletionItem("fori", "for (let i = 0; i < ; i++) {\n    \n}", CompletionKind.SNIPPET, "for loop")
        )
    )

    // 2. TypeScript
    private val LANG_TYPESCRIPT = LanguageDef(
        id = "typescript",
        name = "TypeScript",
        commentSingle = "//",
        commentBlockStart = "/*",
        commentBlockEnd = "*/",
        keywords = LANG_JAVASCRIPT.keywords + setOf(
            "type", "interface", "enum", "implements", "declare", "namespace", "module",
            "abstract", "as", "is", "keyof", "readonly", "override", "satisfies", "infer",
            "never", "any", "unknown", "public", "private", "protected"
        ),
        types = LANG_JAVASCRIPT.types + setOf(
            "string", "number", "boolean", "symbol", "bigint", "void", "object",
            "Record", "Partial", "Required", "Readonly", "Pick", "Omit", "Exclude",
            "Extract", "NonNullable", "ReturnType", "InstanceType", "Parameters", "Awaited"
        ),
        builtins = LANG_JAVASCRIPT.builtins,
        snippets = LANG_JAVASCRIPT.snippets + listOf(
            CompletionItem("interface", "interface IName {\n    \n}", CompletionKind.SNIPPET, "interface"),
            CompletionItem("type", "type Name = ;\n", CompletionKind.SNIPPET, "type alias")
        )
    )

    // 3. HTML
    private val LANG_HTML = LanguageDef(
        id = "html",
        name = "HTML",
        commentSingle = "<!--",
        commentBlockStart = "<!--",
        commentBlockEnd = "-->",
        keywords = setOf(
            "html", "head", "body", "title", "meta", "link", "style", "script",
            "div", "span", "p", "h1", "h2", "h3", "h4", "h5", "h6", "a", "button",
            "input", "form", "label", "select", "option", "textarea", "table", "thead",
            "tbody", "tr", "th", "td", "ul", "ol", "li", "img", "svg", "path",
            "header", "footer", "nav", "section", "article", "main", "aside",
            "video", "audio", "source", "canvas", "iframe", "DOCTYPE"
        ),
        types = setOf(
            "class", "id", "src", "href", "style", "type", "name", "value",
            "placeholder", "onClick", "onChange", "target", "rel", "width", "height",
            "alt", "title", "disabled", "checked", "role", "aria-label", "aria-hidden"
        ),
        builtins = emptySet(),
        snippets = listOf(
            CompletionItem("html5", "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n    <meta charset=\"UTF-8\">\n    <title>Title</title>\n</head>\n<body>\n    \n</body>\n</html>", CompletionKind.SNIPPET, "HTML5 boilerplate"),
            CompletionItem("div", "<div>\n    \n</div>", CompletionKind.SNIPPET, "div tag"),
            CompletionItem("a", "<a href=\"\"></a>", CompletionKind.SNIPPET, "anchor link")
        )
    )

    // 4. CSS
    private val LANG_CSS = LanguageDef(
        id = "css",
        name = "CSS",
        commentSingle = "/*",
        commentBlockStart = "/*",
        commentBlockEnd = "*/",
        keywords = setOf(
            "color", "background", "background-color", "font-size", "font-family", "font-weight",
            "margin", "margin-top", "margin-bottom", "margin-left", "margin-right",
            "padding", "padding-top", "padding-bottom", "padding-left", "padding-right",
            "border", "border-radius", "border-color", "display", "flex", "grid", "position",
            "top", "bottom", "left", "right", "width", "height", "min-width", "max-width",
            "overflow", "z-index", "opacity", "transform", "transition", "animation",
            "box-shadow", "text-align", "justify-content", "align-items", "flex-direction",
            "gap", "cursor", "important", "media", "keyframes"
        ),
        types = setOf(
            "none", "block", "inline", "inline-block", "flex", "grid", "absolute", "relative",
            "fixed", "sticky", "center", "solid", "transparent", "auto", "inherit", "initial"
        ),
        snippets = listOf(
            CompletionItem("flexcenter", "display: flex;\njustify-content: center;\nalign-items: center;", CompletionKind.SNIPPET, "flex center"),
            CompletionItem("media", "@media (max-width: 768px) {\n    \n}", CompletionKind.SNIPPET, "media query")
        )
    )

    // 5. JSON
    private val LANG_JSON = LanguageDef(
        id = "json",
        name = "JSON",
        commentSingle = "//",
        keywords = setOf("true", "false", "null"),
        types = emptySet(),
        snippets = listOf(
            CompletionItem("kv", "\"key\": \"value\"", CompletionKind.SNIPPET, "key-value pair")
        )
    )

    // 6. Python
    private val LANG_PYTHON = LanguageDef(
        id = "python",
        name = "Python",
        commentSingle = "#",
        commentBlockStart = "\"\"\"",
        commentBlockEnd = "\"\"\"",
        keywords = setOf(
            "and", "as", "assert", "async", "await", "break", "case", "class", "continue",
            "def", "del", "elif", "else", "except", "finally", "for", "from", "global",
            "if", "import", "in", "is", "lambda", "match", "nonlocal", "not", "or",
            "pass", "raise", "return", "try", "while", "with", "yield", "True", "False", "None"
        ),
        types = setOf(
            "int", "float", "str", "bool", "list", "dict", "set", "tuple", "bytes",
            "Exception", "ValueError", "TypeError", "KeyError", "IndexError"
        ),
        builtins = setOf(
            "print", "len", "range", "enumerate", "zip", "map", "filter", "sum", "min", "max",
            "abs", "round", "all", "any", "sorted", "reversed", "super", "type", "isinstance",
            "issubclass", "open", "input", "self", "cls"
        ),
        snippets = listOf(
            CompletionItem("def", "def function_name():\n    pass", CompletionKind.SNIPPET, "function definition"),
            CompletionItem("class", "class ClassName:\n    def __init__(self):\n        pass", CompletionKind.SNIPPET, "class definition"),
            CompletionItem("main", "if __name__ == '__main__':\n    main()", CompletionKind.SNIPPET, "main block")
        )
    )

    // 7. Java
    private val LANG_JAVA = LanguageDef(
        id = "java",
        name = "Java",
        commentSingle = "//",
        commentBlockStart = "/*",
        commentBlockEnd = "*/",
        keywords = setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new",
            "package", "private", "protected", "public", "record", "return", "sealed",
            "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while",
            "permits", "true", "false", "null"
        ),
        types = setOf(
            "String", "Integer", "Boolean", "Double", "Float", "Long", "Short", "Byte",
            "Character", "Object", "Class", "System", "Math", "Thread", "Runnable",
            "List", "ArrayList", "Map", "HashMap", "Set", "HashSet", "Collections", "Arrays"
        ),
        builtins = setOf("System.out.println", "System.err.println", "Math.max", "Math.min"),
        snippets = listOf(
            CompletionItem("sout", "System.out.println();", CompletionKind.SNIPPET, "System.out.println()"),
            CompletionItem("psvm", "public static void main(String[] args) {\n    \n}", CompletionKind.SNIPPET, "main method")
        )
    )

    // 8. Kotlin
    private val LANG_KOTLIN = LanguageDef(
        id = "kotlin",
        name = "Kotlin",
        commentSingle = "//",
        commentBlockStart = "/*",
        commentBlockEnd = "*/",
        keywords = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun",
            "if", "in", "interface", "is", "null", "object", "package", "return",
            "super", "this", "throw", "true", "try", "typealias", "val", "var",
            "when", "while", "by", "catch", "constructor", "data", "enum", "finally",
            "get", "import", "init", "open", "operator", "override", "private",
            "protected", "public", "sealed", "set", "suspend", "tailrec", "vararg",
            "lateinit", "const", "companion", "inline"
        ),
        types = setOf(
            "String", "Int", "Boolean", "Double", "Float", "Long", "Short", "Byte",
            "Char", "Unit", "Nothing", "Any", "List", "MutableList", "Map", "MutableMap",
            "Set", "MutableSet", "Array", "Sequence", "Pair", "Triple"
        ),
        builtins = setOf(
            "println", "print", "require", "check", "error", "run", "let", "also", "apply", "with"
        ),
        snippets = listOf(
            CompletionItem("fun", "fun functionName() {\n    \n}", CompletionKind.SNIPPET, "function declaration"),
            CompletionItem("sout", "println()", CompletionKind.SNIPPET, "println()"),
            CompletionItem("data", "data class Name(val id: String)", CompletionKind.SNIPPET, "data class")
        )
    )

    // 9. C
    private val LANG_C = LanguageDef(
        id = "c",
        name = "C",
        commentSingle = "//",
        commentBlockStart = "/*",
        commentBlockEnd = "*/",
        keywords = setOf(
            "auto", "break", "case", "char", "const", "continue", "default", "do",
            "double", "else", "enum", "extern", "float", "for", "goto", "if",
            "inline", "int", "long", "register", "restrict", "return", "short",
            "signed", "sizeof", "static", "struct", "switch", "typedef", "union",
            "unsigned", "void", "volatile", "while", "_Bool", "NULL", "true", "false"
        ),
        types = setOf(
            "size_t", "uint8_t", "uint16_t", "uint32_t", "uint64_t", "int8_t",
            "int16_t", "int32_t", "int64_t", "FILE", "ssize_t"
        ),
        builtins = setOf(
            "printf", "scanf", "malloc", "free", "calloc", "realloc", "exit",
            "memcpy", "memset", "strlen", "strcpy", "strcmp", "fopen", "fclose"
        ),
        snippets = listOf(
            CompletionItem("main", "int main(int argc, char *argv[]) {\n    \n    return 0;\n}", CompletionKind.SNIPPET, "main() function"),
            CompletionItem("printf", "printf(\"\\n\");", CompletionKind.SNIPPET, "printf()")
        )
    )

    // 10. C++
    private val LANG_CPP = LanguageDef(
        id = "cpp",
        name = "C++",
        commentSingle = "//",
        commentBlockStart = "/*",
        commentBlockEnd = "*/",
        keywords = LANG_C.keywords + setOf(
            "class", "public", "private", "protected", "virtual", "override", "final",
            "constexpr", "consteval", "concept", "requires", "template", "typename",
            "namespace", "using", "new", "delete", "operator", "friend", "this",
            "throw", "try", "catch", "explicit", "mutable", "noexcept", "static_cast",
            "dynamic_cast", "const_cast", "reinterpret_cast", "decltype", "auto",
            "nullptr", "co_await", "co_return", "co_yield"
        ),
        types = LANG_C.types + setOf(
            "std", "string", "vector", "map", "unordered_map", "set", "unordered_set",
            "pair", "tuple", "unique_ptr", "shared_ptr", "optional", "variant"
        ),
        builtins = LANG_C.builtins + setOf(
            "cout", "cin", "endl", "make_unique", "make_shared"
        ),
        snippets = listOf(
            CompletionItem("main", "#include <iostream>\n\nint main() {\n    std::cout << \"Hello World!\" << std::endl;\n    return 0;\n}", CompletionKind.SNIPPET, "C++ boilerplate"),
            CompletionItem("cout", "std::cout <<  << std::endl;", CompletionKind.SNIPPET, "std::cout")
        )
    )

    // 11. C#
    private val LANG_CSHARP = LanguageDef(
        id = "csharp",
        name = "C#",
        commentSingle = "//",
        commentBlockStart = "/*",
        commentBlockEnd = "*/",
        keywords = setOf(
            "abstract", "as", "async", "await", "base", "bool", "break", "byte",
            "case", "catch", "char", "checked", "class", "const", "continue",
            "decimal", "default", "delegate", "do", "double", "else", "enum",
            "event", "explicit", "extern", "false", "finally", "fixed", "float",
            "for", "foreach", "goto", "if", "implicit", "in", "int", "interface",
            "internal", "is", "lock", "long", "namespace", "new", "null", "object",
            "operator", "out", "override", "params", "private", "protected", "public",
            "readonly", "ref", "return", "sbyte", "sealed", "short", "sizeof",
            "stackalloc", "static", "string", "struct", "switch", "this", "throw",
            "true", "try", "typeof", "uint", "ulong", "unchecked", "unsafe", "ushort",
            "using", "virtual", "void", "volatile", "while", "yield", "record", "var",
            "get", "set", "init", "value"
        ),
        types = setOf(
            "Console", "String", "Math", "List", "Dictionary", "HashSet", "Task",
            "Action", "Func", "IEnumerable", "Exception", "Array"
        ),
        builtins = setOf("Console.WriteLine", "Console.ReadLine"),
        snippets = listOf(
            CompletionItem("cw", "Console.WriteLine();", CompletionKind.SNIPPET, "Console.WriteLine()"),
            CompletionItem("prop", "public string Name { get; set; }", CompletionKind.SNIPPET, "auto property")
        )
    )

    // 12. Go
    private val LANG_GO = LanguageDef(
        id = "go",
        name = "Go",
        commentSingle = "//",
        commentBlockStart = "/*",
        commentBlockEnd = "*/",
        keywords = setOf(
            "break", "case", "chan", "const", "continue", "default", "defer", "else",
            "fallthrough", "for", "func", "go", "goto", "if", "import", "interface",
            "map", "package", "range", "return", "select", "struct", "switch",
            "type", "var", "nil", "true", "false", "iota"
        ),
        types = setOf(
            "int", "int8", "int16", "int32", "int64", "uint", "uint8", "uint16",
            "uint32", "uint64", "uintptr", "float32", "float64", "complex64",
            "complex128", "string", "bool", "byte", "rune", "error", "any"
        ),
        builtins = setOf(
            "append", "cap", "close", "complex", "copy", "delete", "imag", "len",
            "make", "new", "panic", "print", "println", "real", "recover", "fmt"
        ),
        snippets = listOf(
            CompletionItem("main", "package main\n\nimport \"fmt\"\n\nfunc main() {\n    fmt.Println(\"Hello, World!\")\n}", CompletionKind.SNIPPET, "main package"),
            CompletionItem("fn", "func name() {\n    \n}", CompletionKind.SNIPPET, "func declaration"),
            CompletionItem("iferr", "if err != nil {\n    return err\n}", CompletionKind.SNIPPET, "error check")
        )
    )

    // 13. Rust
    private val LANG_RUST = LanguageDef(
        id = "rust",
        name = "Rust",
        commentSingle = "//",
        commentBlockStart = "/*",
        commentBlockEnd = "*/",
        keywords = setOf(
            "as", "async", "await", "break", "const", "continue", "crate", "dyn",
            "else", "enum", "extern", "false", "fn", "for", "if", "impl", "in",
            "let", "loop", "match", "mod", "move", "mut", "pub", "ref", "return",
            "self", "Self", "static", "struct", "super", "trait", "true", "type",
            "unsafe", "use", "where", "while", "yield"
        ),
        types = setOf(
            "i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32", "u64",
            "u128", "usize", "f32", "f64", "bool", "char", "str", "String", "Vec",
            "Option", "Some", "None", "Result", "Ok", "Err", "Box", "Rc", "Arc"
        ),
        builtins = setOf(
            "println!", "print!", "eprintln!", "format!", "panic!", "vec!", "todo!"
        ),
        snippets = listOf(
            CompletionItem("fn", "fn name() {\n    \n}", CompletionKind.SNIPPET, "function declaration"),
            CompletionItem("main", "fn main() {\n    println!(\"Hello, world!\");\n}", CompletionKind.SNIPPET, "main() function"),
            CompletionItem("println", "println!(\"{}\", );", CompletionKind.SNIPPET, "println! macro")
        )
    )

    // 14. PHP
    private val LANG_PHP = LanguageDef(
        id = "php",
        name = "PHP",
        commentSingle = "//",
        commentBlockStart = "/*",
        commentBlockEnd = "*/",
        keywords = setOf(
            "abstract", "and", "array", "as", "break", "callable", "case", "catch",
            "class", "clone", "const", "continue", "declare", "default", "die", "do",
            "echo", "else", "elseif", "empty", "eval", "exit", "extends", "final",
            "finally", "fn", "for", "foreach", "function", "global", "goto", "if",
            "implements", "include", "include_once", "instanceof", "interface",
            "isset", "list", "match", "namespace", "new", "or", "print", "private",
            "protected", "public", "readonly", "require", "require_once", "return",
            "static", "switch", "throw", "trait", "try", "unset", "use", "var",
            "while", "yield", "true", "false", "null"
        ),
        types = setOf(
            "string", "int", "float", "bool", "iterable", "mixed", "void", "never"
        ),
        builtins = setOf(
            "count", "strlen", "var_dump", "array_push", "array_pop", "json_encode",
            "json_decode", "str_replace", "explode", "implode", "header"
        ),
        snippets = listOf(
            CompletionItem("php", "<?php\n\n", CompletionKind.SNIPPET, "PHP opening tag"),
            CompletionItem("fn", "function name() {\n    \n}", CompletionKind.SNIPPET, "function declaration"),
            CompletionItem("echo", "echo \"\";", CompletionKind.SNIPPET, "echo statement")
        )
    )

    // 15. Dart
    private val LANG_DART = LanguageDef(
        id = "dart",
        name = "Dart",
        commentSingle = "//",
        commentBlockStart = "/*",
        commentBlockEnd = "*/",
        keywords = setOf(
            "abstract", "as", "assert", "async", "await", "break", "case", "catch",
            "class", "const", "continue", "covariant", "default", "deferred", "do",
            "dynamic", "else", "enum", "export", "extends", "extension", "external",
            "factory", "false", "final", "finally", "for", "get", "hide", "if",
            "implements", "import", "in", "interface", "is", "late", "library",
            "mixin", "new", "null", "on", "operator", "part", "required", "rethrow",
            "return", "set", "show", "static", "super", "switch", "sync", "this",
            "throw", "true", "try", "typedef", "var", "void", "while", "with", "yield"
        ),
        types = setOf(
            "int", "double", "num", "String", "bool", "List", "Map", "Set",
            "Future", "Stream", "Iterable", "Object", "Function", "DateTime", "Duration",
            "Widget", "BuildContext", "State"
        ),
        builtins = setOf("print", "Uri"),
        snippets = listOf(
            CompletionItem("main", "void main() {\n    print('Hello, World!');\n}", CompletionKind.SNIPPET, "main() function"),
            CompletionItem("stless", "class Name extends StatelessWidget {\n  @override\n  Widget build(BuildContext context) {\n    return Container();\n  }\n}", CompletionKind.SNIPPET, "StatelessWidget")
        )
    )

    // 16. Swift
    private val LANG_SWIFT = LanguageDef(
        id = "swift",
        name = "Swift",
        commentSingle = "//",
        commentBlockStart = "/*",
        commentBlockEnd = "*/",
        keywords = setOf(
            "associatedtype", "async", "await", "actor", "break", "case", "catch",
            "class", "continue", "default", "defer", "deinit", "do", "else", "enum",
            "extension", "fallthrough", "false", "fileprivate", "final", "for",
            "func", "guard", "if", "import", "in", "init", "inout", "internal",
            "is", "let", "mutating", "nil", "nonmutating", "open", "operator",
            "override", "precedencegroup", "private", "protocol", "public", "repeat",
            "rethrows", "return", "self", "Self", "some", "static", "struct",
            "subscript", "super", "switch", "throw", "throws", "true", "try",
            "typealias", "var", "where", "while"
        ),
        types = setOf(
            "Int", "Double", "Float", "Bool", "String", "Character", "Array",
            "Dictionary", "Set", "Optional", "Result", "Any", "AnyObject", "Void",
            "Task", "View"
        ),
        builtins = setOf("print", "fatalError", "min", "max"),
        snippets = listOf(
            CompletionItem("func", "func name() {\n    \n}", CompletionKind.SNIPPET, "func declaration"),
            CompletionItem("guard", "guard let value = optional else { return }", CompletionKind.SNIPPET, "guard let statement")
        )
    )

    // 17. SQL
    private val LANG_SQL = LanguageDef(
        id = "sql",
        name = "SQL",
        commentSingle = "--",
        commentBlockStart = "/*",
        commentBlockEnd = "*/",
        keywords = setOf(
            "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET",
            "DELETE", "CREATE", "TABLE", "DROP", "ALTER", "ADD", "COLUMN", "INDEX",
            "VIEW", "TRIGGER", "DATABASE", "SCHEMA", "JOIN", "INNER", "LEFT", "RIGHT",
            "FULL", "OUTER", "CROSS", "ON", "GROUP", "BY", "ORDER", "ASC", "DESC",
            "HAVING", "LIMIT", "OFFSET", "UNION", "ALL", "DISTINCT", "AS", "AND",
            "OR", "NOT", "IN", "BETWEEN", "LIKE", "IS", "NULL", "EXISTS", "CASE",
            "WHEN", "THEN", "ELSE", "END", "PRIMARY", "KEY", "FOREIGN", "REFERENCES",
            "CHECK", "DEFAULT", "UNIQUE", "AUTO_INCREMENT", "COUNT", "SUM", "AVG",
            "MIN", "MAX", "BEGIN", "COMMIT", "ROLLBACK", "TRANSACTION"
        ),
        types = setOf(
            "INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT", "VARCHAR", "CHAR",
            "TEXT", "BOOLEAN", "DATE", "DATETIME", "TIMESTAMP", "TIME", "FLOAT",
            "DOUBLE", "DECIMAL", "NUMERIC", "BLOB", "JSON"
        ),
        builtins = setOf("NOW()", "CURRENT_TIMESTAMP"),
        snippets = listOf(
            CompletionItem("select", "SELECT * FROM table_name WHERE 1=1;", CompletionKind.SNIPPET, "SELECT query"),
            CompletionItem("insert", "INSERT INTO table_name (col1, col2) VALUES ('val1', 'val2');", CompletionKind.SNIPPET, "INSERT query"),
            CompletionItem("createtable", "CREATE TABLE table_name (\n    id INT PRIMARY KEY AUTO_INCREMENT,\n    name VARCHAR(255) NOT NULL\n);", CompletionKind.SNIPPET, "CREATE TABLE")
        )
    )

    private val languagesMap = listOf(
        LANG_JAVASCRIPT,
        LANG_TYPESCRIPT,
        LANG_HTML,
        LANG_CSS,
        LANG_JSON,
        LANG_PYTHON,
        LANG_JAVA,
        LANG_KOTLIN,
        LANG_C,
        LANG_CPP,
        LANG_CSHARP,
        LANG_GO,
        LANG_RUST,
        LANG_PHP,
        LANG_DART,
        LANG_SWIFT,
        LANG_SQL
    ).associateBy { it.id }

    fun getLanguage(id: String): LanguageDef {
        return languagesMap[id.lowercase()] ?: LANG_JAVASCRIPT
    }

    fun isKnownLanguage(id: String): Boolean {
        return languagesMap.containsKey(id.lowercase())
    }

    // Common tokenizers
    private val stringRegex = Regex("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'|`([^`\\\\]|\\\\.)*`")
    private val pythonStringRegex = Regex("\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'")
    private val numberRegex = Regex("\\b0x[0-9a-fA-F]+\\b|\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b")
    private val functionCallRegex = Regex("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?=\\()")
    private val htmlTagRegex = Regex("</?[a-zA-Z0-9\\-]+|/?>")
    private val htmlAttrRegex = Regex("\\b[a-zA-Z0-9\\-]+(?=\\=)")
    private val cssPropertyRegex = Regex("\\b[a-zA-Z\\-]+(?=\\s*\\:)")
    private val cssUnitRegex = Regex("\\b\\d+(px|em|rem|%|vh|vw|s|ms|deg)\\b|#[0-9a-fA-F]{3,8}")

    // Comment regexes per language category
    private val cStyleCommentRegex = Regex("//.*|/\\*[\\s\\S]*?\\*/")
    private val pythonCommentRegex = Regex("#.*")
    private val sqlCommentRegex = Regex("--.*|/\\*[\\s\\S]*?\\*/")
    private val htmlCommentRegex = Regex("<!--[\\s\\S]*?-->")
    private val cssCommentRegex = Regex("/\\*[\\s\\S]*?\\*/")

    // Precompiled regex caches for performance
    private val keywordRegexCache = mutableMapOf<String, Regex>()
    private val typeRegexCache = mutableMapOf<String, Regex>()

    private fun getKeywordRegex(lang: LanguageDef): Regex {
        return keywordRegexCache.getOrPut(lang.id) {
            val pattern = if (lang.id == "sql") {
                "(?i)\\b(" + lang.keywords.joinToString("|") { Regex.escape(it) } + ")\\b"
            } else {
                "\\b(" + lang.keywords.joinToString("|") { Regex.escape(it) } + ")\\b"
            }
            Regex(pattern)
        }
    }

    private fun getTypeRegex(lang: LanguageDef): Regex {
        return typeRegexCache.getOrPut(lang.id) {
            val words = lang.types + lang.builtins
            if (words.isEmpty()) {
                Regex("\\b(none_type_match)\\b")
            } else {
                val pattern = if (lang.id == "sql") {
                    "(?i)\\b(" + words.joinToString("|") { Regex.escape(it) } + ")\\b"
                } else {
                    "\\b(" + words.joinToString("|") { Regex.escape(it) } + ")\\b"
                }
                Regex(pattern)
            }
        }
    }

    /**
     * Highlights syntax according to the language definition with VS Code Dark+ color palette.
     */
    fun highlight(
        text: String,
        languageId: String,
        matchedBrackets: Pair<Int, Int>? = null
    ): AnnotatedString {
        if (text.isEmpty() || text.length > 50000) {
            return AnnotatedString(text)
        }

        val lang = getLanguage(languageId)
        val isSql = lang.id == "sql"
        val isHtml = lang.id == "html"
        val isCss = lang.id == "css"
        val isPython = lang.id == "python"

        return buildAnnotatedString {
            append(text)

            // 1. Keywords
            getKeywordRegex(lang).findAll(text).forEach { match ->
                addStyle(
                    SpanStyle(color = VsCodeTheme.SyntaxKeyword, fontWeight = FontWeight.SemiBold),
                    match.range.first,
                    match.range.last + 1
                )
            }

            // 2. Types & Built-ins
            getTypeRegex(lang).findAll(text).forEach { match ->
                addStyle(
                    SpanStyle(color = VsCodeTheme.SyntaxType),
                    match.range.first,
                    match.range.last + 1
                )
            }

            // 3. Functions & Methods
            functionCallRegex.findAll(text).forEach { match ->
                addStyle(
                    SpanStyle(color = VsCodeTheme.SyntaxFunction),
                    match.range.first,
                    match.range.last + 1
                )
            }

            // 4. Numbers & Units
            numberRegex.findAll(text).forEach { match ->
                addStyle(
                    SpanStyle(color = VsCodeTheme.SyntaxNumber),
                    match.range.first,
                    match.range.last + 1
                )
            }
            if (isCss) {
                cssUnitRegex.findAll(text).forEach { match ->
                    addStyle(
                        SpanStyle(color = VsCodeTheme.SyntaxNumber),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }

            // 5. HTML / CSS specialized rules
            if (isHtml) {
                htmlTagRegex.findAll(text).forEach { match ->
                    addStyle(
                        SpanStyle(color = Color(0xFF569CD6), fontWeight = FontWeight.Bold),
                        match.range.first,
                        match.range.last + 1
                    )
                }
                htmlAttrRegex.findAll(text).forEach { match ->
                    addStyle(
                        SpanStyle(color = Color(0xFF9CDCFE)),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            } else if (isCss) {
                cssPropertyRegex.findAll(text).forEach { match ->
                    addStyle(
                        SpanStyle(color = Color(0xFF9CDCFE)),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }

            // 6. Strings (higher priority)
            val activeStringRegex = if (isPython) pythonStringRegex else stringRegex
            activeStringRegex.findAll(text).forEach { match ->
                addStyle(
                    SpanStyle(color = VsCodeTheme.SyntaxString),
                    match.range.first,
                    match.range.last + 1
                )
            }

            // 7. Comments (highest syntax priority)
            val commentRegex = when {
                isHtml -> htmlCommentRegex
                isCss -> cssCommentRegex
                isSql -> sqlCommentRegex
                isPython -> pythonCommentRegex
                lang.id == "php" -> Regex("//.*|#.*|/\\*[\\s\\S]*?\\*/")
                else -> cStyleCommentRegex
            }
            commentRegex.findAll(text).forEach { match ->
                addStyle(
                    SpanStyle(color = VsCodeTheme.SyntaxComment),
                    match.range.first,
                    match.range.last + 1
                )
            }

            // 8. Matching bracket pair highlighting around cursor
            if (matchedBrackets != null) {
                val (openIdx, closeIdx) = matchedBrackets
                val bracketStyle = SpanStyle(
                    color = Color(0xFFFFD700),
                    background = Color(0xFF3A3D41),
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline
                )
                if (openIdx in text.indices) {
                    addStyle(bracketStyle, openIdx, openIdx + 1)
                }
                if (closeIdx in text.indices) {
                    addStyle(bracketStyle, closeIdx, closeIdx + 1)
                }
            }
        }
    }

    /**
     * Finds the identifier word token currently preceding or surrounding the cursor offset.
     */
    fun findWordAtOffset(text: String, offset: Int): Pair<String, Int> {
        val safeOffset = offset.coerceIn(0, text.length)
        if (safeOffset == 0) return "" to 0

        var start = safeOffset - 1
        while (start >= 0 && (text[start].isLetterOrDigit() || text[start] == '_' || text[start] == '$')) {
            start--
        }
        val wordStart = start + 1
        val word = text.substring(wordStart, safeOffset)
        return word to wordStart
    }

    /**
     * Computes contextual autocomplete suggestions:
     * - Language keywords
     * - Language types and built-in classes
     * - Document identifier words
     * - Snippets
     */
    fun getCompletions(text: String, cursorOffset: Int, languageId: String): List<CompletionItem> {
        val (currentWord, _) = findWordAtOffset(text, cursorOffset)
        if (currentWord.length < 1) return emptyList()

        val lang = getLanguage(languageId)
        val lowerWord = currentWord.lowercase(Locale.ROOT)
        val results = mutableListOf<CompletionItem>()
        val seen = mutableSetOf<String>()

        // 1. Snippets matching prefix
        lang.snippets.forEach { snippet ->
            if (snippet.label.lowercase().startsWith(lowerWord) && seen.add(snippet.label)) {
                results.add(snippet)
            }
        }

        // 2. Keywords
        lang.keywords.forEach { kw ->
            if (kw.lowercase().startsWith(lowerWord) && seen.add(kw)) {
                results.add(CompletionItem(kw, kw, CompletionKind.KEYWORD, "keyword"))
            }
        }

        // 3. Types
        lang.types.forEach { tp ->
            if (tp.lowercase().startsWith(lowerWord) && seen.add(tp)) {
                results.add(CompletionItem(tp, tp, CompletionKind.TYPE, "type"))
            }
        }

        // 4. Built-ins
        lang.builtins.forEach { bi ->
            if (bi.lowercase().startsWith(lowerWord) && seen.add(bi)) {
                results.add(CompletionItem(bi, bi, CompletionKind.FUNCTION, "built-in"))
            }
        }

        // 5. Document-level word completions
        val docWordRegex = Regex("\\b[a-zA-Z_][a-zA-Z0-9_]{2,}\\b")
        docWordRegex.findAll(text).forEach { match ->
            val w = match.value
            if (w.lowercase().startsWith(lowerWord) && seen.add(w) && w != currentWord) {
                results.add(CompletionItem(w, w, CompletionKind.VARIABLE, "identifier"))
            }
            if (results.size >= 25) return results.take(15)
        }

        return results.take(15)
    }

    /**
     * Toggles single/multi-line comment for the current cursor position or active selection.
     */
    fun toggleComment(value: TextFieldValue, languageId: String): TextFieldValue {
        val text = value.text
        if (text.isEmpty()) return value

        val lang = getLanguage(languageId)
        val selStart = value.selection.min.coerceIn(0, text.length)
        val selEnd = value.selection.max.coerceIn(0, text.length)

        // Find the lines covered by the selection
        val lineStartIdx = text.lastIndexOf('\n', (selStart - 1).coerceAtLeast(0)) + 1
        val lineEndIdx = if (selStart == selEnd) {
            val nextNewline = text.indexOf('\n', selStart)
            if (nextNewline == -1) text.length else nextNewline
        } else {
            val nextNewline = text.indexOf('\n', (selEnd - 1).coerceAtLeast(0))
            if (nextNewline == -1) text.length else nextNewline
        }

        val region = text.substring(lineStartIdx, lineEndIdx)
        val lines = region.split("\n")

        val prefix = when (lang.id) {
            "html" -> "<!-- "
            "css" -> "/* "
            "sql" -> "-- "
            "python" -> "# "
            else -> "// "
        }
        val suffix = when (lang.id) {
            "html" -> " -->"
            "css" -> " */"
            else -> ""
        }

        // Check if all non-empty lines are already commented
        val allCommented = lines.filter { it.trim().isNotEmpty() }.all { line ->
            val trimmed = line.trimStart()
            if (suffix.isNotEmpty()) {
                trimmed.startsWith(prefix.trim()) && trimmed.endsWith(suffix.trim())
            } else {
                trimmed.startsWith(prefix.trim())
            }
        }

        val toggledLines = lines.map { line ->
            val indent = line.takeWhile { it == ' ' || it == '\t' }
            val content = line.substring(indent.length)
            if (content.isEmpty()) {
                line
            } else if (allCommented) {
                // Uncomment
                var un = content
                if (un.startsWith(prefix)) {
                    un = un.removePrefix(prefix)
                } else if (un.startsWith(prefix.trim())) {
                    un = un.removePrefix(prefix.trim()).trimStart()
                }
                if (suffix.isNotEmpty()) {
                    if (un.endsWith(suffix)) {
                        un = un.removeSuffix(suffix)
                    } else if (un.endsWith(suffix.trim())) {
                        un = un.removeSuffix(suffix.trim()).trimEnd()
                    }
                }
                "$indent$un"
            } else {
                // Comment
                "$indent$prefix$content$suffix"
            }
        }

        val replacement = toggledLines.joinToString("\n")
        val updatedText = text.substring(0, lineStartIdx) + replacement + text.substring(lineEndIdx)
        val cursorDelta = replacement.length - region.length
        val newCursor = (selEnd + cursorDelta).coerceIn(0, updatedText.length)

        return TextFieldValue(updatedText, TextRange(newCursor))
    }

    /**
     * Language-aware code formatting for all 17 languages:
     * - JSON: Standard JSON hierarchy indentation
     * - HTML: Tag open/close balance indentation
     * - Python: Colon block indentation
     * - SQL: Keyword capitalization & clause structure
     * - C-family: Curly brace hierarchy indentation
     */
    fun formatCode(text: String, languageId: String, indentSize: Int = 4): String {
        if (text.isBlank()) return text
        val lang = getLanguage(languageId)

        return when (lang.id) {
            "json" -> formatJson(text, indentSize)
            "html" -> formatHtml(text, indentSize)
            "python" -> formatPython(text, indentSize)
            "sql" -> formatSql(text, indentSize)
            else -> formatCStyle(text, indentSize)
        }
    }

    private fun formatJson(text: String, indentSize: Int): String {
        return try {
            val element: JsonElement = jsonPretty.parseToJsonElement(text)
            jsonPretty.encodeToString(JsonElement.serializer(), element)
        } catch (_: Exception) {
            formatCStyle(text, indentSize)
        }
    }

    private fun formatHtml(text: String, indentSize: Int): String {
        val voidElements = setOf(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr", "!doctype"
        )
        val lines = text.split("\n")
        val sb = StringBuilder()
        var indentLevel = 0
        val unit = " ".repeat(indentSize)

        lines.forEachIndexed { idx, rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) {
                if (idx < lines.lastIndex) sb.append("\n")
                return@forEachIndexed
            }

            val isClosing = trimmed.startsWith("</")
            if (isClosing) {
                indentLevel = (indentLevel - 1).coerceAtLeast(0)
            }

            sb.append(unit.repeat(indentLevel)).append(trimmed)
            if (idx < lines.lastIndex) sb.append("\n")

            // Check if opens a tag that is not self-closing or void
            val isOpen = trimmed.startsWith("<") && !trimmed.startsWith("</") && !trimmed.startsWith("<!--")
            if (isOpen) {
                val tagMatch = Regex("<([a-zA-Z0-9\\-]+)").find(trimmed)
                val tagName = tagMatch?.groupValues?.get(1)?.lowercase() ?: ""
                val isSelfClosing = trimmed.endsWith("/>") || tagName in voidElements
                val hasMatchingCloseOnSameLine = trimmed.contains("</$tagName>")
                if (!isSelfClosing && !hasMatchingCloseOnSameLine && tagName.isNotEmpty()) {
                    indentLevel++
                }
            }
        }
        return sb.toString()
    }

    private fun formatPython(text: String, indentSize: Int): String {
        val lines = text.split("\n")
        val sb = StringBuilder()
        var indentLevel = 0
        val unit = " ".repeat(indentSize)

        lines.forEachIndexed { idx, rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) {
                if (idx < lines.lastIndex) sb.append("\n")
                return@forEachIndexed
            }

            // Dedent keywords in Python: elif, else, except, finally
            val dedentsLine = trimmed.startsWith("elif ") || trimmed.startsWith("else:") ||
                    trimmed.startsWith("except") || trimmed.startsWith("finally:") ||
                    trimmed.startsWith(")") || trimmed.startsWith("]") || trimmed.startsWith("}")
            val effectiveIndent = if (dedentsLine) (indentLevel - 1).coerceAtLeast(0) else indentLevel

            sb.append(unit.repeat(effectiveIndent)).append(trimmed)
            if (idx < lines.lastIndex) sb.append("\n")

            if (trimmed.endsWith(":")) {
                indentLevel = effectiveIndent + 1
            } else if (trimmed.startsWith("return") || trimmed.startsWith("pass") ||
                trimmed.startsWith("break") || trimmed.startsWith("continue") || trimmed.startsWith("raise")
            ) {
                // Line ends a sub-block; allow natural indentation to hold or relax
                indentLevel = effectiveIndent
            } else {
                indentLevel = effectiveIndent
            }
        }
        return sb.toString()
    }

    private fun formatSql(text: String, indentSize: Int): String {
        val lang = LANG_SQL
        var formatted = text

        // Capitalize all SQL keywords
        lang.keywords.forEach { kw ->
            val pattern = "(?i)\\b" + Regex.escape(kw) + "\\b"
            formatted = formatted.replace(Regex(pattern), kw.uppercase())
        }
        lang.types.forEach { tp ->
            val pattern = "(?i)\\b" + Regex.escape(tp) + "\\b"
            formatted = formatted.replace(Regex(pattern), tp.uppercase())
        }

        // Clause-based indentation
        val majorClauses = listOf("FROM", "WHERE", "GROUP BY", "ORDER BY", "HAVING", "LIMIT", "LEFT JOIN", "RIGHT JOIN", "INNER JOIN", "JOIN")
        val lines = formatted.split("\n")
        val sb = StringBuilder()
        val unit = " ".repeat(indentSize)

        lines.forEachIndexed { idx, rawLine ->
            var trimmed = rawLine.trim()
            var currentIndent = 0
            majorClauses.forEach { clause ->
                if (trimmed.startsWith(clause, ignoreCase = true)) {
                    currentIndent = 1
                }
            }
            if (trimmed.startsWith("AND ") || trimmed.startsWith("OR ")) {
                currentIndent = 2
            }
            sb.append(unit.repeat(currentIndent)).append(trimmed)
            if (idx < lines.lastIndex) sb.append("\n")
        }
        return sb.toString()
    }

    private fun formatCStyle(text: String, indentSize: Int): String {
        val lines = text.split("\n")
        val sb = StringBuilder()
        var indentLevel = 0
        val unit = " ".repeat(indentSize)

        lines.forEachIndexed { idx, rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) {
                if (idx < lines.lastIndex) sb.append("\n")
                return@forEachIndexed
            }

            // Decrease indent before printing if starts with closing bracket
            if (trimmed.startsWith("}") || trimmed.startsWith(")") || trimmed.startsWith("]")) {
                indentLevel = (indentLevel - 1).coerceAtLeast(0)
            } else if (trimmed.startsWith("case ") || trimmed.startsWith("default:")) {
                // switch-case unindent
                indentLevel = (indentLevel - 1).coerceAtLeast(0)
            }

            sb.append(unit.repeat(indentLevel)).append(trimmed)
            if (idx < lines.lastIndex) sb.append("\n")

            // Check if line ends with opening bracket or block starter
            val opensBlock = trimmed.endsWith("{") || trimmed.endsWith("(") || trimmed.endsWith("[")
            if (opensBlock) {
                indentLevel++
            } else if (trimmed.startsWith("case ") || trimmed.startsWith("default:")) {
                indentLevel++
            }
        }
        return sb.toString()
    }
}
