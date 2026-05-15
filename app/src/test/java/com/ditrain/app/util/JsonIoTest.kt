package com.ditrain.app.util

import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonIoTest {

    @Serializable
    data class Sample(val a: Int, val b: String? = null)

    @Test fun `unknown keys are ignored on decode`() {
        val raw = """{"a":1,"b":"x","unknown":"ignore me"}"""
        val parsed = JsonIo.json.decodeFromString(Sample.serializer(), raw)
        assertEquals(Sample(1, "x"), parsed)
    }

    @Test fun `encode uses pretty printing with two-space indent`() {
        val out = JsonIo.json.encodeToString(Sample.serializer(), Sample(1, "x"))
        assertTrue(out.contains("\n  \"a\""))
    }

    @Test fun `defaults are encoded so files are self-documenting`() {
        val out = JsonIo.json.encodeToString(Sample.serializer(), Sample(1, b = null))
        assertTrue(out.contains("\"b\": null"))
    }
}
