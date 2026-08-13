package net.mamby.health.navigation

import java.util.UUID

/**
 * Tracks editor routes whose sensitive drafts still exist in this process.
 *
 * The registry is intentionally memory-only. Restored routes therefore fail closed after process
 * death, and lock or vault-loss boundaries can invalidate every active editor atomically.
 */
internal class EditorSessionRegistry(
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val activeIds = mutableSetOf<String>()

    fun create(): String = newId().also(activeIds::add)

    fun contains(id: String): Boolean = id in activeIds

    fun close(id: String) {
        activeIds.remove(id)
    }

    fun clear() {
        activeIds.clear()
    }
}
