package tv.own.owntv.core.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.model.SourceType

/**
 * Tests the pure parse/auth logic of the companion server (no socket bound). The JSON branch relies
 * on `org.json`, which is only a stub in plain JVM unit tests, so it is exercised on-device; the
 * form-encoded path (what the HTML form actually submits) is fully covered here.
 */
class CompanionHttpServerTest {

    private val protocol = CompanionHttpProtocol
    private val form = "application/x-www-form-urlencoded"

    // ---- PIN comparison ----

    @Test
    fun `pin matches only when equal and same length`() {
        assertTrue(protocol.pinEquals("123456", "123456"))
        assertFalse(protocol.pinEquals("123456", "123457"))
        assertFalse(protocol.pinEquals("12345", "123456"))
        assertFalse(protocol.pinEquals("", "123456"))
    }

    // ---- query / form parsing ----

    @Test
    fun `parseQuery decodes pairs and plus-as-space`() {
        val map = protocol.parseQuery("a=1&b=hello+world&c=")
        assertEquals("1", map["a"])
        assertEquals("hello world", map["b"])
        assertEquals("", map["c"])
    }

    @Test
    fun `only M3U payloads are accepted`() {
        assertNull(protocol.parsePayload(form, "type=xtream&server=h&user=u&pass=p", SourceType.XTREAM))
        assertNull(protocol.parsePayload(form, "type=stalker&portalUrl=h&mac=00", SourceType.STALKER))
    }

    // ---- M3U ----

    @Test
    fun `m3u form parses with url alias and only needs the url`() {
        val p = protocol.parsePayload(form, "type=m3u&name=P&url=http://x/p.m3u", SourceType.M3U)!!
        assertEquals(SourceType.M3U, p.type)
        assertEquals("http://x/p.m3u", p.server)
        assertEquals("P", p.name)
        assertEquals("HOURS_12", p.autoRefresh)
        assertTrue(p.isDefault)
    }

    @Test
    fun `remote m3u selection is passed through and unchecked default is honored`() {
        val selected = protocol.parsePayload(
            form,
            "type=m3u&server=http%3A%2F%2Fx%2Fp.m3u&autoRefresh=HOURS_12&isDefault=false&isDefault=true",
            SourceType.M3U,
        )!!
        assertEquals("HOURS_12", selected.autoRefresh)
        assertTrue(selected.isDefault)

        val custom = protocol.parsePayload(
            form,
            "type=m3u&server=http%3A%2F%2Fx%2Fp.m3u&autoRefresh=HOURS_6&isDefault=false",
            SourceType.M3U,
        )!!
        assertEquals("HOURS_6", custom.autoRefresh)
        assertFalse(custom.isDefault)
    }

    @Test
    fun `m3u without a url is rejected`() {
        assertNull(protocol.parsePayload(form, "type=m3u&name=P", SourceType.M3U))
    }

    /**
     * Uploading a playlist from the companion page: the browser reads the file and posts its text as
     * JSON, because a file input cannot ride in a urlencoded body and the TV has no multipart parser.
     * The URL is then legitimately absent — a submission with neither is still rejected below.
     */
    @Test
    fun `m3u accepts an uploaded playlist instead of a url`() {
        // Built with JSONObject rather than hand-written: the playlist text contains newlines, which
        // must be escaped, and that is exactly what the browser's JSON.stringify does.
        val playlist = "#EXTM3U\n#EXTINF:-1,A\nhttp://h/a.ts\n"
        val body = org.json.JSONObject()
            .put("type", "m3u").put("name", "Jio")
            .put("playlistFileName", "drm-test.m3u")
            .put("playlistFile", playlist)
            .toString()
        val p = protocol.parsePayload("application/json", body, SourceType.M3U)!!
        assertEquals(playlist, p.playlistContent)
        assertEquals(SourceType.M3U, p.type)
        assertEquals("drm-test.m3u", p.playlistFileName)
        assertTrue(p.playlistContent.startsWith("#EXTM3U"))
        // The controller fills this in once the file is on disk; the protocol leaves it blank.
        assertEquals("", p.server)
    }

    @Test
    fun `m3u with neither a url nor a file is still rejected`() {
        assertNull(protocol.parsePayload("application/json", """{"type":"m3u","name":"P"}""", SourceType.M3U))
        assertNull(protocol.parsePayload("application/json", """{"type":"m3u","playlistFile":"  "}""", SourceType.M3U))
    }

    /** A playlist is far larger than a form field, so `/m3u` must get the upload-sized body limit. */
    @Test
    fun `the m3u endpoint accepts an upload-sized body`() {
        assertEquals(CompanionHttpProtocol.UPLOAD_BODY_LIMIT, protocol.maxBodyBytes("/m3u"))
        assertEquals(CompanionHttpProtocol.FORM_BODY_LIMIT, protocol.maxBodyBytes("/xtream"))
    }

    @Test
    fun `dynamic M3U endpoint does not require a file suffix`() {
        val p = protocol.parsePayload(form, "server=https://example.test/live?id=7", SourceType.M3U)!!
        assertEquals("https://example.test/live?id=7", p.server)
    }

    // ---- robustness ----

    // ---- body cap ----

    private fun read(body: String, contentLength: String, limit: Int): String? =
        protocol.readBody(
            java.io.BufferedInputStream(java.io.ByteArrayInputStream(body.toByteArray())),
            mapOf("content-length" to contentLength),
            limit,
        )

    @Test
    fun `uploads get the large cap and forms the small one`() {
        assertEquals(CompanionHttpProtocol.UPLOAD_BODY_LIMIT, protocol.maxBodyBytes("/backup"))
        assertEquals(CompanionHttpProtocol.UPLOAD_BODY_LIMIT, protocol.maxBodyBytes("/background"))
        assertEquals(CompanionHttpProtocol.FORM_BODY_LIMIT, protocol.maxBodyBytes("/"))
        assertEquals(CompanionHttpProtocol.FORM_BODY_LIMIT, protocol.maxBodyBytes("/xtream"))
    }

    @Test
    fun `a body within the cap is read whole`() {
        assertEquals("pin=123456", read("pin=123456", "10", CompanionHttpProtocol.FORM_BODY_LIMIT))
        assertEquals("", read("", "0", CompanionHttpProtocol.FORM_BODY_LIMIT))
    }

    @Test
    fun `an oversized Content-Length is rejected without allocating it`() {
        // The pre-fix code did ByteArray(2_000_000_000) here — an unauthenticated OOM from the LAN.
        assertNull(read("", "2000000000", CompanionHttpProtocol.FORM_BODY_LIMIT))
        assertNull(read("", (CompanionHttpProtocol.UPLOAD_BODY_LIMIT + 1).toString(), CompanionHttpProtocol.UPLOAD_BODY_LIMIT))
    }

    @Test
    fun `a body longer than the cap is cut off even when the header lies small`() {
        assertNull(read("x".repeat(200), "200", 64))
    }

    /** It must not hang either; the server catches the throw and drops the request unanswered. */
    @Test(expected = java.io.EOFException::class)
    fun `a body that stops short of its Content-Length is refused, not kept in part`() {
        read("abc", "500", CompanionHttpProtocol.FORM_BODY_LIMIT)
    }

    /** A JSON null must be dropped, not turned into the string "null" — the sender omits optional
     *  fields that way, and a literal "null" would be stored as the user-agent or EPG URL. */
    @Test
    fun `json null values are ignored rather than stringified`() {
        val p = protocol.parsePayload(
            "application/json",
            """{"type":"m3u","name":"P","url":"http://x/p.m3u","userAgent":null,"epgUrl":null}""",
            SourceType.M3U,
        )!!
        assertEquals("", p.userAgent)
        assertEquals("", p.epgUrl)
        assertEquals("P", p.name)
    }

    @Test
    fun `malformed json body does not throw and yields null`() {
        // org.json is stubbed in unit tests; parsePayload must swallow it, not crash the accept thread.
        assertNull(protocol.parsePayload("application/json", "{ this is not json", SourceType.XTREAM))
    }
}
