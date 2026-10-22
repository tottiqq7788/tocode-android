package app.tocode.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticLogTest {
    @Test
    fun sanitizeDropsSecretsAndBodies() {
        val cleaned = DiagnosticLog.sanitize(
            mapOf(
                "kind" to "chat",
                "request_id" to "r1",
                "token" to "secret-token",
                "binding_key" to "mac-key",
                "text" to "hello body",
                "path" to "/Users/me/secret.txt",
                "ok" to true,
                "retry_ms" to 2000,
            ),
        )
        assertEquals("chat", cleaned["kind"])
        assertEquals("r1", cleaned["request_id"])
        assertEquals(true, cleaned["ok"])
        assertEquals(2000, cleaned["retry_ms"])
        assertFalse(cleaned.containsKey("token"))
        assertFalse(cleaned.containsKey("binding_key"))
        assertFalse(cleaned.containsKey("text"))
        assertFalse(cleaned.containsKey("path"))
        val raw = cleaned.toString()
        assertFalse(raw.contains("secret-token"))
        assertFalse(raw.contains("mac-key"))
        assertFalse(raw.contains("hello body"))
        assertFalse(raw.contains("/Users/me"))
    }

    @Test
    fun sanitizeDropsNestedTokenKeys() {
        val cleaned = DiagnosticLog.sanitize(
            mapOf(
                "device_token" to "abc",
                "link_token" to "def",
                "authorization" to "Bearer x",
                "code" to "mac_offline",
            ),
        )
        assertEquals("mac_offline", cleaned["code"])
        assertTrue(cleaned.size == 1)
        assertFalse(cleaned.containsKey("device_token"))
        assertFalse(cleaned.containsKey("link_token"))
        assertFalse(cleaned.containsKey("authorization"))
    }
}
