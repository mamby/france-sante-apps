package net.mamby.health.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorStateViewModelTest {
    @Test
    fun routeEntryViewModelRetainsDirtyDraftAcrossRecompositionAndConfigurationChange() {
        val holder = EditorStateViewModel()
        val state = holder.state { Draft("initial") }
        state.value = Draft("edited")

        val restoredComposition = holder.state { Draft("replacement must not win") }

        assertEquals(Draft("edited"), restoredComposition.value)
        assertTrue(restoredComposition.isDirty)
    }

    @Test
    fun newRouteEntryStartsWithANewMemoryOnlyDraft() {
        val firstEntry = EditorStateViewModel().state { Draft("initial") }
        firstEntry.value = Draft("sensitive edit")

        val restoredAfterProcessDeath = EditorStateViewModel().state { Draft("initial") }

        assertEquals(Draft("initial"), restoredAfterProcessDeath.value)
        assertFalse(restoredAfterProcessDeath.isDirty)
    }

    private data class Draft(val value: String)
}
