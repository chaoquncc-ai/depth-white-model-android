package com.chaoqun.baimo.remote

object ServerUrl {
    const val DEFAULT = "http://103.47.82.57:47860"

    fun normalize(raw: String): String {
        var value = raw.trim()
        if (value.isEmpty()) return DEFAULT
        if (!value.contains("://")) {
            value = "http://$value"
        }
        while (value.endsWith("/") && value.count { it == '/' } > 2) {
            value = value.dropLast(1)
        }
        return value
    }
}
