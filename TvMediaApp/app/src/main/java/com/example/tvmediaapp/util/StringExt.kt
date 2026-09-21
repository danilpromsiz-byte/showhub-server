package com.example.tvmediaapp.util

/**
 * Robust HTML entity unescaping extension for Kotlin strings.
 * Decodes decimal (&#233; -> é), hex (&#xE9; -> é), and common named HTML entities.
 */
fun String?.unescapeHtml(): String {
    if (this == null || this.isBlank()) return ""
    var str = this
    
    // Quick exit if no entities
    if (!str.contains("&")) return str.trim()

    // Pass 1: Common named HTML entities
    str = str.replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–")
        .replace("&laquo;", "«")
        .replace("&raquo;", "»")
        .replace("&hellip;", "…")

    // Pass 2: Hex entities (e.g. &#x00E9; or &#xE9;)
    val hexRegex = Regex("&#x([0-9a-fA-F]+);")
    str = hexRegex.replace(str) { matchResult ->
        try {
            val code = matchResult.groupValues[1].toInt(16)
            code.toChar().toString()
        } catch (_: Exception) {
            matchResult.value
        }
    }

    // Pass 3: Decimal entities (e.g. &#233;)
    val decRegex = Regex("&#([0-9]+);")
    str = decRegex.replace(str) { matchResult ->
        try {
            val code = matchResult.groupValues[1].toInt(10)
            code.toChar().toString()
        } catch (_: Exception) {
            matchResult.value
        }
    }

    // Pass 4: In case of double encoding (e.g. &amp;#233; -> &#233; -> é)
    if (str.contains("&#") || str.contains("&amp;")) {
        str = str.replace("&amp;", "&")
        str = hexRegex.replace(str) { matchResult ->
            try {
                matchResult.groupValues[1].toInt(16).toChar().toString()
            } catch (_: Exception) {
                matchResult.value
            }
        }
        str = decRegex.replace(str) { matchResult ->
            try {
                matchResult.groupValues[1].toInt(10).toChar().toString()
            } catch (_: Exception) {
                matchResult.value
            }
        }
    }

    return str.trim()
}
