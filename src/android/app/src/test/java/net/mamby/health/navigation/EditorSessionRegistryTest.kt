package net.mamby.health.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorSessionRegistryTest {
    @Test
    fun closeInvalidatesOnlyTheRemovedRoute() {
        val ids = ArrayDeque(listOf("first", "second"))
        val registry = EditorSessionRegistry(ids::removeFirst)

        val first = registry.create()
        val second = registry.create()
        registry.close(first)

        assertFalse(registry.contains(first))
        assertTrue(registry.contains(second))
    }

    @Test
    fun clearInvalidatesEveryDraftAtTheLockBoundary() {
        val ids = ArrayDeque(listOf("first", "second"))
        val registry = EditorSessionRegistry(ids::removeFirst)
        val first = registry.create()
        val second = registry.create()

        registry.clear()

        assertFalse(registry.contains(first))
        assertFalse(registry.contains(second))
    }

    @Test
    fun aRestoredRouteIsMissingFromANewProcessRegistry() {
        val original = EditorSessionRegistry { "session" }
        val restoredRouteSession = original.create()

        val afterProcessDeath = EditorSessionRegistry { "unused" }

        assertFalse(afterProcessDeath.contains(restoredRouteSession))
    }
}
