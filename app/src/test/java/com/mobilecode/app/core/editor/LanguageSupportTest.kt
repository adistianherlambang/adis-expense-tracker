package com.mobilecode.app.core.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LanguageSupportTest {

    private val target17Languages = listOf(
        "javascript", "typescript", "html", "css", "json",
        "python", "java", "kotlin", "c", "cpp", "csharp",
        "go", "rust", "php", "dart", "swift", "sql"
    )

    @Test
    fun `test all 17 languages are registered in LanguageSupport`() {
        for (lang in target17Languages) {
            assertTrue("Language '$lang' should be known in LanguageSupport", LanguageSupport.isKnownLanguage(lang))
            val def = LanguageSupport.getLanguage(lang)
            assertEquals(lang, def.id)
            assertTrue("Keywords must not be empty for $lang", def.keywords.isNotEmpty())
        }
    }

    @Test
    fun `test detectLanguage covers all 17 language extensions`() {
        val extensionMap = mapOf(
            "js" to "javascript",
            "jsx" to "javascript",
            "ts" to "typescript",
            "tsx" to "typescript",
            "html" to "html",
            "css" to "css",
            "scss" to "css",
            "json" to "json",
            "py" to "python",
            "java" to "java",
            "kt" to "kotlin",
            "c" to "c",
            "cpp" to "cpp",
            "cs" to "csharp",
            "go" to "go",
            "rs" to "rust",
            "php" to "php",
            "dart" to "dart",
            "swift" to "swift",
            "sql" to "sql"
        )

        for ((ext, expectedLang) in extensionMap) {
            val detected = EditorViewModel.detectLanguage(ext)
            assertEquals("Extension .$ext should map to $expectedLang", expectedLang, detected)
        }
    }

    @Test
    fun `test syntax highlighting produces span styles for all 17 languages`() {
        val sampleSnippets = mapOf(
            "javascript" to "const x = 42; // number\nfunction test() { return 'hello'; }",
            "typescript" to "type Num = number;\nconst y: Num = 100;",
            "html" to "<div class=\"app\">Hello <!-- comment --></div>",
            "css" to "body { color: #fff; margin: 0px; /* css comment */ }",
            "json" to "{\n  \"name\": \"MobileCode\",\n  \"active\": true\n}",
            "python" to "def calculate(val: int) -> int:\n    # comment\n    return val * 2",
            "java" to "public class Main {\n    // comment\n    int count = 10;\n}",
            "kotlin" to "fun main() {\n    val greeting = \"hi\"\n    println(greeting)\n}",
            "c" to "int main() {\n    printf(\"hello\");\n    return 0;\n}",
            "cpp" to "#include <iostream>\nint main() {\n    std::cout << 42;\n}",
            "csharp" to "public class Program {\n    static void Main() {}\n}",
            "go" to "package main\nimport \"fmt\"\nfunc main() {\n    fmt.Println(1)\n}",
            "rust" to "fn main() {\n    let mut x = 5;\n    println!(\"{}\", x);\n}",
            "php" to "<?php\n\$name = \"World\";\necho \$name;",
            "dart" to "void main() {\n    int count = 42;\n    print(count);\n}",
            "swift" to "func greet() -> String {\n    let msg = \"hey\"\n    return msg\n}",
            "sql" to "SELECT id, name FROM users WHERE id = 1; -- comment"
        )

        for ((lang, snippet) in sampleSnippets) {
            val highlighted = LanguageSupport.highlight(snippet, lang)
            assertTrue("Highlighted text should match original for $lang", highlighted.text == snippet)
            assertTrue("Span styles should be produced for $lang", highlighted.spanStyles.isNotEmpty())
        }
    }

    @Test
    fun `test basic autocomplete produces relevant suggestions for all 17 languages`() {
        val testQueries = mapOf(
            "javascript" to ("con" to "const"),
            "typescript" to ("inter" to "interface"),
            "html" to ("di" to "div"),
            "css" to ("flex" to "flex"),
            "json" to ("tr" to "true"),
            "python" to ("de" to "def"),
            "java" to ("pub" to "public"),
            "kotlin" to ("fu" to "fun"),
            "c" to ("ret" to "return"),
            "cpp" to ("clas" to "class"),
            "csharp" to ("name" to "namespace"),
            "go" to ("fun" to "func"),
            "rust" to ("le" to "let"),
            "php" to ("ech" to "echo"),
            "dart" to ("clas" to "class"),
            "swift" to ("fun" to "func"),
            "sql" to ("sel" to "SELECT")
        )

        for ((lang, pair) in testQueries) {
            val (prefix, expectedMatch) = pair
            val completions = LanguageSupport.getCompletions(prefix, prefix.length, lang)
            assertTrue(
                "Autocomplete for $lang with prefix '$prefix' should include '$expectedMatch'",
                completions.any { it.label.equals(expectedMatch, ignoreCase = true) }
            )
        }
    }

    @Test
    fun `test document word identifier completion`() {
        val docText = "function authenticateUser(userToken) {\n    const authResult = check(userToken);\n    aut"
        val completions = LanguageSupport.getCompletions(docText, docText.length, "javascript")
        val labels = completions.map { it.label }
        assertTrue("Should suggest 'authenticateUser'", labels.contains("authenticateUser"))
        assertTrue("Should suggest 'authResult'", labels.contains("authResult"))
    }

    @Test
    fun `test comment toggling across different language comment styles`() {
        // C-Style: //
        val jsValue = TextFieldValue("const a = 10;", TextRange(5))
        val jsToggled = LanguageSupport.toggleComment(jsValue, "javascript")
        assertEquals("// const a = 10;", jsToggled.text)
        val jsUntoggled = LanguageSupport.toggleComment(jsToggled, "javascript")
        assertEquals("const a = 10;", jsUntoggled.text)

        // Python: #
        val pyValue = TextFieldValue("x = 42", TextRange(2))
        val pyToggled = LanguageSupport.toggleComment(pyValue, "python")
        assertEquals("# x = 42", pyToggled.text)
        val pyUntoggled = LanguageSupport.toggleComment(pyToggled, "python")
        assertEquals("x = 42", pyUntoggled.text)

        // SQL: --
        val sqlValue = TextFieldValue("SELECT 1;", TextRange(3))
        val sqlToggled = LanguageSupport.toggleComment(sqlValue, "sql")
        assertEquals("-- SELECT 1;", sqlToggled.text)
        val sqlUntoggled = LanguageSupport.toggleComment(sqlToggled, "sql")
        assertEquals("SELECT 1;", sqlUntoggled.text)

        // HTML: <!-- -->
        val htmlValue = TextFieldValue("<p>text</p>", TextRange(3))
        val htmlToggled = LanguageSupport.toggleComment(htmlValue, "html")
        assertEquals("<!-- <p>text</p> -->", htmlToggled.text)
        val htmlUntoggled = LanguageSupport.toggleComment(htmlToggled, "html")
        assertEquals("<p>text</p>", htmlUntoggled.text)

        // CSS: /* */
        val cssValue = TextFieldValue("color: red;", TextRange(2))
        val cssToggled = LanguageSupport.toggleComment(cssValue, "css")
        assertEquals("/* color: red; */", cssToggled.text)
        val cssUntoggled = LanguageSupport.toggleComment(cssToggled, "css")
        assertEquals("color: red;", cssUntoggled.text)
    }

    @Test
    fun `test code formatting for languages`() {
        // JSON
        val rawJson = "{\"a\":1,\"b\":[2,3]}"
        val formattedJson = LanguageSupport.formatCode(rawJson, "json")
        assertTrue("JSON formatting should indent", formattedJson.contains("\n"))

        // HTML
        val rawHtml = "<div>\n<p>\nHello\n</p>\n</div>"
        val formattedHtml = LanguageSupport.formatCode(rawHtml, "html", 4)
        assertTrue("HTML formatting should indent child elements", formattedHtml.contains("    <p>"))

        // Python
        val rawPy = "def test():\nx = 1\nreturn x"
        val formattedPy = LanguageSupport.formatCode(rawPy, "python", 4)
        assertTrue("Python formatting should indent under colon", formattedPy.contains("    x = 1"))

        // SQL
        val rawSql = "select id, name from users where id = 1"
        val formattedSql = LanguageSupport.formatCode(rawSql, "sql", 4)
        assertTrue("SQL keywords should be capitalized", formattedSql.contains("SELECT") && formattedSql.contains("FROM"))

        // C-Family
        val rawC = "if (true) {\nx = 1;\n}"
        val formattedC = LanguageSupport.formatCode(rawC, "c", 4)
        assertEquals("if (true) {\n    x = 1;\n}", formattedC)
    }

    @Test
    fun `test HTML tag auto-closing`() {
        val initial = TextFieldValue("<div", TextRange(4))
        val typed = TextFieldValue("<div>", TextRange(5))
        val result = applyEditorSmartEdits(initial, typed, "html")
        assertEquals("<div></div>", result.text)
        assertEquals(5, result.selection.start)
    }

    @Test
    fun `test DiagnosticsEngine correctly ignores apostrophes and brackets in comments`() {
        val ktFile = File("sample.kt")
        val code = """
            // This is an apostrophe's test and { unmatched bracket in comment
            /*
             * Block comment with 'quotes' and { [ ( unmatched braces
             */
            fun sample() {
                val x = 10
            }
        """.trimIndent()
        val items = DiagnosticsEngine.analyze(ktFile, code)
        assertTrue("Expected 0 diagnostic errors for comments with quotes and brackets, but got: $items", items.isEmpty())
    }

    @Test
    fun `test DiagnosticsEngine correctly handles character literals and escape sequences`() {
        val ktFile = File("sample.kt")
        val code = """
            fun sample() {
                val singleQuote = '\''
                val backslash = '\\'
                val openParen = '('
                val closeBrace = '}'
                val strWithEscapedBackslash = "hello\\"
                val normalStr = "world"
            }
        """.trimIndent()
        val items = DiagnosticsEngine.analyze(ktFile, code)
        assertTrue("Expected 0 diagnostic errors for char literals, but got: $items", items.isEmpty())
    }

    @Test
    fun `test DiagnosticsEngine correctly handles multiline and template strings`() {
        val ktFile = File("sample.kt")
        val ktCode = "val multiline = \"\"\"\n    {\n        \"json\": \"value's with apostrophe\"\n    }\n\"\"\""
        val ktItems = DiagnosticsEngine.analyze(ktFile, ktCode)
        assertTrue("Expected 0 diagnostic errors for multiline string, but got: $ktItems", ktItems.isEmpty())

        val jsFile = File("sample.js")
        val jsCode = "const name = \"World\";\nconst greeting = `Hello \${name}, it's wonderful! { unmatched`;"
        val jsItems = DiagnosticsEngine.analyze(jsFile, jsCode)
        assertTrue("Expected 0 diagnostic errors for template literal, but got: $jsItems", jsItems.isEmpty())
    }

    @Test
    fun `test DiagnosticsEngine catches real syntax errors`() {
        val ktFile = File("error.kt")

        // Unclosed brace
        val unclosedBrace = "fun test() { val a = 1;"
        val errors1 = DiagnosticsEngine.analyze(ktFile, unclosedBrace)
        assertTrue("Should detect unclosed brace", errors1.any { it.message.contains("Unclosed bracket '{'") })

        // Unexpected closing brace
        val unexpectedBrace = "fun test() {} }"
        val errors2 = DiagnosticsEngine.analyze(ktFile, unexpectedBrace)
        assertTrue("Should detect unexpected closing brace", errors2.any { it.message.contains("Unexpected closing bracket '}'") })

        // Unclosed string literal
        val unclosedString = "val text = \"unterminated string;"
        val errors3 = DiagnosticsEngine.analyze(ktFile, unclosedString)
        assertTrue("Should detect unclosed string literal", errors3.any { it.message.contains("Unclosed string literal") })
    }

    @Test
    fun `test DiagnosticsEngine reports 0 errors on target codebase files`() {
        val candidatePaths = listOf(
            "app/src/main/java/com/mobilecode/app/core/editor/DiagnosticsEngine.kt",
            "app/src/main/java/com/mobilecode/app/core/editor/MonacoWebView.kt",
            "app/src/main/java/com/mobilecode/app/core/editor/EditorViewModel.kt",
            "app/src/main/java/com/mobilecode/app/core/terminal/TerminalViewModel.kt",
            "app/src/test/java/com/mobilecode/app/core/editor/LanguageSupportTest.kt"
        )

        for (path in candidatePaths) {
            val file = File(path).let { if (it.exists()) it else File("../$path") }
            if (file.exists()) {
                val content = file.readText()
                val diagnostics = DiagnosticsEngine.analyze(file, content)
                assertTrue(
                    "Target file '${file.name}' should produce 0 diagnostic errors, but found: ${diagnostics.map { "${it.line}:${it.column} - ${it.message}" }}",
                    diagnostics.isEmpty()
                )
            }
        }
    }
}
