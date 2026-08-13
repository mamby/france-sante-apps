package net.mamby.health.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

class EditorState<T : Any> internal constructor(initialValue: T) {
    val initialValue: T = initialValue

    var value by mutableStateOf(initialValue)
    var isSaving by mutableStateOf(false)

    val isDirty: Boolean
        get() = value != initialValue
}

internal class EditorStateViewModel : ViewModel() {
    private var state: EditorState<*>? = null

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> state(initializer: () -> T): EditorState<T> =
        (state as? EditorState<T>) ?: EditorState(initializer()).also { state = it }

    override fun onCleared() {
        state = null
    }
}

/**
 * Returns a draft owned by the current Navigation 3 entry's ViewModel store.
 *
 * Draft values remain in memory only. Callers must not mirror them into saved state or route keys.
 */
@Composable
fun <T : Any> rememberEditorState(initializer: () -> T): EditorState<T> {
    val owner = LocalViewModelStoreOwner.current
    if (owner == null) return remember { EditorState(initializer()) }
    val stateViewModel = viewModel<EditorStateViewModel>(viewModelStoreOwner = owner)
    return remember(stateViewModel) { stateViewModel.state(initializer) }
}
