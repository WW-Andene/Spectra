package com.andene.spectra.data.codedb

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates the wire schema of the bundled ir_codes.json without
 * requiring an Android Context to open the asset. The repo path is
 * resolved relative to the working directory the JVM test runner sets
 * (the app module directory under Gradle).
 */
class IrCodeDatabaseSchemaTest {

    @Serializable
    private data class Db(val remotes: List<Entry> = emptyList())

    @Serializable
    private data class Entry(
        val brand: String = "",
        val model: String = "",
        val deviceType: String = "",
        val protocol: String = "",
        val carrierFrequency: Int = 0,
        val commands: Map<String, Cmd> = emptyMap(),
    )

    @Serializable
    private data class Cmd(
        val address: Int? = null,
        val command: Int? = null,
        val timings: List<Int>? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun loadDb(): Db {
        val candidates = listOf(
            File("src/main/assets/ir_codes.json"),
            File("app/src/main/assets/ir_codes.json"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Could not find ir_codes.json — looked in $candidates")
        return json.decodeFromString(file.readText())
    }

    @Test
    fun `bundled JSON parses cleanly`() {
        val db = loadDb()
        assertTrue("Database must contain at least one remote", db.remotes.isNotEmpty())
    }

    @Test
    fun `every entry has a brand, protocol, and commands`() {
        val db = loadDb()
        for ((idx, entry) in db.remotes.withIndex()) {
            assertTrue("entry $idx missing brand", entry.brand.isNotBlank())
            assertTrue("entry $idx missing protocol", entry.protocol.isNotBlank())
            assertTrue("entry ${entry.brand} has no commands", entry.commands.isNotEmpty())
        }
    }

    @Test
    fun `every command has either address+command or raw timings`() {
        val db = loadDb()
        for (entry in db.remotes) {
            for ((name, cmd) in entry.commands) {
                val compact = cmd.address != null && cmd.command != null
                val raw = cmd.timings != null
                assertTrue(
                    "${entry.brand}/${entry.model}/$name has neither compact form nor timings",
                    compact || raw,
                )
            }
        }
    }

    @Test
    fun `every entry has a power command - the entry-point button`() {
        val db = loadDb()
        for (entry in db.remotes) {
            assertTrue(
                "${entry.brand}/${entry.model} is missing a 'power' command",
                entry.commands.containsKey("power"),
            )
        }
    }

    @Test
    fun `protocols are from a known set`() {
        val knownProtocols = setOf(
            "NEC", "RC5", "RC6", "SIRC_12", "SIRC_15", "SIRC_20",
            "SAMSUNG", "SHARP", "LG", "PANASONIC", "RAW", "UNKNOWN",
        )
        val db = loadDb()
        for (entry in db.remotes) {
            assertTrue(
                "${entry.brand}/${entry.model}: unknown protocol '${entry.protocol}'",
                entry.protocol in knownProtocols,
            )
        }
    }
}
