package com.chaoqun.baimo.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerUrlTest {
    @Test
    fun defaultIsLanGradio() {
        assertEquals("http://103.47.82.57:47860", ServerUrl.DEFAULT)
    }

    @Test
    fun blankBecomesDefault() {
        assertEquals(ServerUrl.DEFAULT, ServerUrl.normalize("  "))
    }

    @Test
    fun addsHttpWhenSchemeMissing() {
        assertEquals("http://103.47.82.57:47860", ServerUrl.normalize("103.47.82.57:47860"))
    }

    @Test
    fun keepsHttpsAndStripsTrailingSlash() {
        assertEquals("https://example.com:8443", ServerUrl.normalize("https://example.com:8443/"))
    }
}
