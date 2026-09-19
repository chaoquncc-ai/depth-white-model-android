package com.chaoqun.baimo.remote.download

object ResultVideoFinder {
    private val videoExt = Regex("""\.(mp4|webm|mov|mkv|avi)(\?|#|$)""", RegexOption.IGNORE_CASE)
    private val jsonString = Regex("\"((?:\\\\.|[^\"\\\\])*)\"")

    fun isLikelyResultUrl(url: String): Boolean {
        val low = url.trim().lowercase()
        if (low.isEmpty()) return false
        if (low.startsWith("blob:")) return true
        if (low.startsWith("data:video")) return true
        if (low.contains("/file=") || low.contains("gradio_api/file")) return true
        return videoExt.containsMatchIn(low)
    }

    fun isHttp(url: String): Boolean {
        val low = url.trim().lowercase()
        return low.startsWith("http://") || low.startsWith("https://")
    }

    fun isBlob(url: String): Boolean = url.trim().lowercase().startsWith("blob:")

    fun pickBest(urls: List<String>): String? {
        val cleaned = urls.map { it.trim() }.filter { isLikelyResultUrl(it) }
        val http = cleaned.filter { isHttp(it) }
        if (http.isNotEmpty()) return http.last()
        return cleaned.lastOrNull()
    }

    fun parseEvaluateJsonArray(raw: String?): List<String> {
        if (raw.isNullOrBlank() || raw == "null") return emptyList()
        var text = raw.trim()
        if (text.startsWith("\"") && text.endsWith("\"") && text.length >= 2) {
            text = unescapeJson(text.substring(1, text.length - 1)).trim()
        }
        if (!text.startsWith("[")) return emptyList()
        return jsonString.findAll(text).map { unescapeJson(it.groupValues[1]) }.filter { it.isNotBlank() }.toList()
    }

    fun albumMime(hint: String?, filename: String, url: String): String {
        val cleaned = hint?.substringBefore(';')?.trim().orEmpty()
        if (cleaned.startsWith("video/")) return cleaned
        if (DownloadNames.isVideo(cleaned.ifBlank { null }, filename) ||
            DownloadNames.isVideo(null, url)
        ) {
            return "video/mp4"
        }
        return cleaned.ifBlank { "video/mp4" }
    }

    fun displayName(filename: String?, url: String, mimeType: String?): String {
        val fromHeader = filename?.takeIf { it.isNotBlank() }
        val fromUrl = DownloadNames.fromUrl(url)
        val raw = fromHeader ?: fromUrl ?: "baimo-result"
        return DownloadNames.ensureExtension(raw, mimeType ?: "video/mp4")
    }

    private fun unescapeJson(value: String): String {
        return value
            .replace("\\\"", "\"")
            .replace("\\/", "/")
            .replace("\\n", "\n")
            .replace("\\\\", "\\")
    }
}
