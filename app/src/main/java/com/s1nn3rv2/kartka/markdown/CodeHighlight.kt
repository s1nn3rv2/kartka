package com.s1nn3rv2.kartka.markdown

enum class Token { KEYWORD, STRING, NUMBER, COMMENT }

data class CodeToken(val start: Int, val end: Int, val token: Token)

/**
 * One generic highlighter for every language: comments, strings, numbers and a shared keyword list. Not a real
 * parser, but enough to make code in notes readable.
 */
object CodeHighlight {
    // ponytail: one keyword list for all languages; add per-language lists if a word is colored wrongly too often.
    private val keywords = """
        if else for while do return function fun const let var val class interface enum struct import export from
        package def lambda try catch finally throw throws new delete this self super public private protected internal
        static final abstract override async await yield break continue switch case default when match in is as of
        typeof instanceof void type impl trait mod use pub mut fn go func defer chan range then fi elif done esac
        local with not and or pass raise except where object companion suspend data sealed open lateinit extends
        implements namespace using select insert update into values create table drop alter join on group order by
        having limit set
    """.split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
    private val literals = setOf("true", "false", "null", "None", "nil", "undefined", "True", "False", "NaN")
    private val hashComments = setOf(
        "bash", "sh", "shell", "zsh", "fish", "python", "py", "ruby", "rb", "yaml", "yml", "toml", "perl", "r",
        "dockerfile", "makefile", "nix", "conf", "ini", "properties", "powershell", "ps1",
    )
    private val plain = setOf("text", "txt", "plain", "md", "markdown", "output", "log")

    private fun pattern(hash: Boolean) = Regex(
        """(?<c>/\*[\s\S]*?(?:\*/|$)|//[^\n]*${if (hash) """|#[^\n]*""" else ""})""" +
            """|(?<s>"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'|`(?:\\.|[^`\\])*`)""" +
            """|(?<n>\b(?:0[xX][\da-fA-F]+|\d[\d_]*(?:\.\d+)?)\b)""" +
            """|(?<w>\b[A-Za-z_]\w*\b)""",
    )
    private val withHash = pattern(hash = true)
    private val withoutHash = pattern(hash = false)

    /** Tokens of [code] in order; empty when there is no language or it is plain text. */
    fun tokens(code: String, language: String?): List<CodeToken> {
        val lang = language?.trim()?.substringBefore(' ')?.lowercase()
        if (lang.isNullOrEmpty() || lang in plain) return emptyList()
        val sql = lang == "sql"
        val out = ArrayList<CodeToken>()
        for (m in (if (lang in hashComments) withHash else withoutHash).findAll(code)) {
            val token = when {
                m.groups["c"] != null -> Token.COMMENT
                m.groups["s"] != null -> Token.STRING
                m.groups["n"] != null -> Token.NUMBER
                m.value in literals -> Token.NUMBER
                m.value in keywords || (sql && m.value.lowercase() in keywords) -> Token.KEYWORD
                else -> continue
            }
            out += CodeToken(m.range.first, m.range.last + 1, token)
        }
        return out
    }
}
