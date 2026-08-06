package com.fatmambo33.eclipsecam.media

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSessionIndexTest {
    @Test
    fun listsReadableJpegsAndIgnoresPlaceholdersAndUnknownFiles() {
        val root = Files.createTempDirectory("session-index").toFile()
        try {
            val session = File(root, "session-a").apply { mkdirs() }
            File(session, "000001_frame.jpg").writeBytes(byteArrayOf(1, 2, 3))
            File(session, "000002_pending.jpg").createNewFile()
            File(session, "notes.txt").writeText("ignored")
            File(session, "session.complete").writeText("ok")

            val indexed = LocalSessionIndex(root).listSessions().single()

            assertEquals("session-a", indexed.sessionId)
            assertEquals(listOf("000001_frame.jpg"), indexed.assets.map { it.file.name })
            assertEquals(3L, indexed.assets.single().sizeBytes)
            assertFalse(indexed.incomplete)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun preservesInterruptedAndEmptySessionsAsIncomplete() {
        val root = Files.createTempDirectory("session-index").toFile()
        try {
            val interrupted = File(root, "interrupted").apply { mkdirs() }
            File(interrupted, "000001_frame.JPG").writeBytes(byteArrayOf(7))
            File(root, "empty").mkdirs()

            val indexed = LocalSessionIndex(root).listSessions().associateBy { it.sessionId }

            assertEquals(setOf("interrupted", "empty"), indexed.keys)
            assertTrue(indexed.getValue("interrupted").incomplete)
            assertTrue(indexed.getValue("empty").incomplete)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun sortsNewestSessionFirstWithStableIdTieBreak() {
        val root = Files.createTempDirectory("session-index").toFile()
        try {
            val older = File(root, "older").apply { mkdirs() }
            val newer = File(root, "newer").apply { mkdirs() }
            File(older, "frame.jpg").apply {
                writeBytes(byteArrayOf(1))
                setLastModified(1_000L)
            }
            File(newer, "frame.jpg").apply {
                writeBytes(byteArrayOf(2))
                setLastModified(2_000L)
            }

            assertEquals(
                listOf("newer", "older"),
                LocalSessionIndex(root).listSessions().map { it.sessionId },
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingRootReturnsEmptyIndex() {
        val missing = File("build/test-missing-${System.nanoTime()}")
        assertTrue(LocalSessionIndex(missing).listSessions().isEmpty())
    }
}
