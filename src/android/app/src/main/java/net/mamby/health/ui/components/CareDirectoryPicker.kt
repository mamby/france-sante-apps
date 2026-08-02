package net.mamby.health.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.CareDirectoryEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareDirectoryPicker(
    entries: List<CareDirectoryEntry>,
    selectedId: UUID?,
    onSelected: (UUID?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = entries.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded, { expanded = it }, modifier) {
        OutlinedTextField(
            value = selected?.name.orEmpty(),
            onValueChange = {},
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.directory_no_link)) },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.name) },
                    onClick = {
                        onSelected(entry.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
