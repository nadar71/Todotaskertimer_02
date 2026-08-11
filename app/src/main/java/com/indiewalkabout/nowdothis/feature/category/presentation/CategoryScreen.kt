package com.indiewalkabout.nowdothis.feature.category.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    state: CategoryUiState,
    snackbarHostState: SnackbarHostState,
    onEvent: (CategoryEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.category_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("category-back")) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.category_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onEvent(CategoryEvent.Add) },
                        modifier = Modifier.testTag("category-add")
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.category_add)
                        )
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            state.error != null -> CategoryLoadError(
                modifier = Modifier.padding(padding),
                onRetry = { onEvent(CategoryEvent.Retry) }
            )
            state.categories.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.category_empty))
            }
            else -> CategoryList(state.categories, padding, onEvent)
        }
    }

    state.editor?.let { editor ->
        CategoryEditorDialog(editor, onEvent)
    }
    state.pendingDelete?.let { category ->
        CategoryDeleteDialog(category, state.isDeleting, onEvent)
    }
}

@Composable
private fun CategoryList(
    categories: List<CategoryItem>,
    padding: PaddingValues,
    onEvent: (CategoryEvent) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("category-list"),
        contentPadding = PaddingValues(
            start = 8.dp,
            top = padding.calculateTopPadding() + 8.dp,
            end = 8.dp,
            bottom = padding.calculateBottomPadding() + 8.dp
        )
    ) {
        items(categories, key = CategoryItem::id) { category ->
            CategoryRow(category, onEvent)
        }
    }
}

@Composable
private fun CategoryRow(category: CategoryItem, onEvent: (CategoryEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryColorSwatch(
            color = category.color,
            modifier = Modifier.testTag("category-color-${category.id}")
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = category.name,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge
        )
        CategoryAction(
            tag = "category-up-${category.id}",
            enabled = category.canMoveUp,
            label = stringResource(R.string.category_move_up, category.name),
            onClick = { onEvent(CategoryEvent.MoveUp(category.id)) }
        ) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
        }
        CategoryAction(
            tag = "category-down-${category.id}",
            enabled = category.canMoveDown,
            label = stringResource(R.string.category_move_down, category.name),
            onClick = { onEvent(CategoryEvent.MoveDown(category.id)) }
        ) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        CategoryAction(
            tag = "category-edit-${category.id}",
            label = stringResource(R.string.category_edit, category.name),
            onClick = { onEvent(CategoryEvent.Edit(category.id)) }
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null)
        }
        CategoryAction(
            tag = "category-delete-${category.id}",
            label = stringResource(R.string.category_delete, category.name),
            onClick = { onEvent(CategoryEvent.RequestDelete(category.id)) }
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null)
        }
    }
}

@Composable
private fun CategoryAction(
    tag: String,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .testTag(tag)
            .semantics { contentDescription = label }
    ) {
        Box(contentAlignment = Alignment.Center) { icon() }
    }
}

@Composable
private fun CategoryColorSwatch(color: CategoryColor, modifier: Modifier = Modifier) {
    val label = stringResource(color.labelRes())
    Surface(
        modifier = modifier
            .size(24.dp)
            .semantics { contentDescription = label },
        shape = CircleShape,
        color = color.composeColor()
    ) {}
}

@Composable
private fun CategoryEditorDialog(editor: CategoryEditorState, onEvent: (CategoryEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!editor.isSaving) onEvent(CategoryEvent.DismissEditor) },
        title = {
            Text(
                stringResource(
                    if (editor.categoryId == null) {
                        R.string.category_editor_add_title
                    } else {
                        R.string.category_editor_edit_title
                    }
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = { onEvent(CategoryEvent.ChangeName(it)) },
                    modifier = Modifier.fillMaxWidth().testTag("category-name"),
                    label = { Text(stringResource(R.string.category_name)) },
                    supportingText = editor.nameError?.let { error ->
                        {
                            Text(
                                stringResource(
                                    when (error) {
                                        CategoryNameError.BLANK -> R.string.category_error_blank
                                        CategoryNameError.DUPLICATE -> R.string.category_error_duplicate
                                    }
                                )
                            )
                        }
                    },
                    isError = editor.nameError != null,
                    singleLine = true,
                    enabled = !editor.isSaving
                )
                Text(stringResource(R.string.category_color))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CategoryColor.entries.forEach { color ->
                        PaletteSwatch(
                            color = color,
                            selected = color == editor.selectedColor,
                            enabled = !editor.isSaving,
                            onClick = { onEvent(CategoryEvent.SelectColor(color)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(CategoryEvent.ConfirmEditor) },
                enabled = !editor.isSaving
            ) {
                Text(stringResource(R.string.category_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(CategoryEvent.DismissEditor) },
                enabled = !editor.isSaving
            ) {
                Text(stringResource(R.string.category_cancel))
            }
        }
    )
}

@Composable
private fun PaletteSwatch(
    color: CategoryColor,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val label = stringResource(color.labelRes())
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .testTag("category-palette-${color.name.lowercase()}")
            .semantics {
                this.selected = selected
                contentDescription = label
            },
        shape = CircleShape,
        color = color.composeColor(),
        contentColor = Color.White,
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Filled.Check, contentDescription = null)
        }
    }
}

@Composable
private fun CategoryDeleteDialog(
    category: CategoryItem,
    isDeleting: Boolean,
    onEvent: (CategoryEvent) -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onEvent(CategoryEvent.DismissDelete) },
        title = { Text(stringResource(R.string.category_delete_title, category.name)) },
        text = { Text(stringResource(R.string.category_delete_body)) },
        confirmButton = {
            TextButton(
                onClick = { onEvent(CategoryEvent.ConfirmDelete) },
                enabled = !isDeleting
            ) {
                Text(stringResource(R.string.category_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(CategoryEvent.DismissDelete) },
                enabled = !isDeleting
            ) {
                Text(stringResource(R.string.category_cancel))
            }
        }
    )
}

@Composable
private fun CategoryLoadError(modifier: Modifier, onRetry: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.category_load_failed))
        Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
            Text(stringResource(R.string.category_retry))
        }
    }
}

private fun CategoryColor.composeColor(): Color = when (this) {
    CategoryColor.BLUE -> Color(0xFF1565C0)
    CategoryColor.GREEN -> Color(0xFF2E7D32)
    CategoryColor.PINK -> Color(0xFFC2185B)
}

private fun CategoryColor.labelRes(): Int = when (this) {
    CategoryColor.BLUE -> R.string.category_color_blue
    CategoryColor.GREEN -> R.string.category_color_green
    CategoryColor.PINK -> R.string.category_color_pink
}
