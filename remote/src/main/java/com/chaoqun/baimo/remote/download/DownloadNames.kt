package com.chaoqun.baimo.remote.download

object DownloadNames {
    fun fromContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val utf8 = Regex(
            """filename\*=(?:UTF-8''|utf-8'')([^;]+)""",
            RegexOption.IGNORE_CASE,
        ).find(header)
        if (utf8 != null) {
            return decode(utf8.groupValues[1].trim().trim('"'))
        }
        val quoted = Regex("""filename="([^"]+)"""", RegexOption.IGNORE_CASE).find(header)
        if (quoted != null) return quoted.groupValues[1]
        val plain = Regex("""filename=([^;]+)""", RegexOption.IGNORE_CASE).find(header)
        return plain?.groupValues?.get(1)?.trim()?.trim('"')
    }

    fun fromUrl(url: String): String? {
        val path = url.substringBefore('?').substringAfterLast('/')
        return path.takeIf { it.isNotBlank() && it.contains('.') }
    }

    fun choosePublicDirectory(mimeType: String?, filename: String): PublicDir {
        return if (isVideo(mimeType, filename)) PublicDir.MOVIES else PublicDir.DOWNLOADS
    }

    fun ensureExtension(filename: String, mimeType: String?): String {
        if (filename.contains('.')) return filename
        val ext = when (mimeType?.lowercase()) {
            "video/mp4" -> ".mp4"
            "video/quicktime" -> ".mov"
            "video/webm" -> ".webm"
            "video/x-matroska" -> ".mkv"
            else -> if (mimeType?.startsWith("video/") == true) ".mp4" else ""
        }
        return filename + ext
    }

    fun isVideo(mimeType: String?, filename: String): Boolean {
        val lower = filename.lowercase()
        return mimeType?.startsWith("video/") == true ||
            lower.endsWith(".mp4") ||
            lower.endsWith(".webm") ||
            lower.endsWith(".mov") ||
            lower.endsWith(".mkv") ||
            lower.endsWith(".avi")
    }

    private fun decode(value: String): String {
        return try {
            java.net.URLDecoder.decode(value, Charsets.UTF_8.name())
        } catch (_: Exception) {
            value
        }
    }

    enum class PublicDir(val relativeName: String) {
        MOVIES("Movies"),
        DOWNLOADS("Download"),
    }
}
