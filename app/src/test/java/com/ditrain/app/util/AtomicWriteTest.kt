package com.ditrain.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AtomicWriteTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun `writes file with content`() {
        val target = File(tmp.root, "out.json")
        AtomicWrite.writeText(target, "hello")
        assertEquals("hello", target.readText())
    }

    @Test fun `removes tmp file after successful write`() {
        val target = File(tmp.root, "out.json")
        AtomicWrite.writeText(target, "x")
        val tmpFile = File(tmp.root, "out.json.tmp")
        assertFalse(tmpFile.exists())
    }

    @Test fun `overwrites existing file atomically`() {
        val target = File(tmp.root, "out.json")
        target.writeText("old content")
        AtomicWrite.writeText(target, "new content")
        assertEquals("new content", target.readText())
    }

    @Test fun `creates parent directory if missing`() {
        val nested = File(tmp.root, "a/b/c/out.json")
        AtomicWrite.writeText(nested, "deep")
        assertTrue(nested.exists())
        assertEquals("deep", nested.readText())
    }
}
