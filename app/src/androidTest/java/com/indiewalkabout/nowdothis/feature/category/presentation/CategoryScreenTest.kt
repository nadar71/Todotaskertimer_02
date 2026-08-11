package com.indiewalkabout.nowdothis.feature.category.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.unit.dp
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryColor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<CategoryTestActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun list_rendersNamesSwatchesAndStableMoveControls() {
        setScreen(state = populatedState())

        composeRule.onNodeWithText("Lavoro").assertIsDisplayed()
        composeRule.onNodeWithText("Clienti").assertIsDisplayed()
        composeRule.onNodeWithTag("category-color-1")
            .assertIsDisplayed()
            .assertContentDescriptionEquals(context.getString(R.string.category_color_blue))
        composeRule.onNodeWithTag("category-up-1").assertIsNotEnabled()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
        composeRule.onNodeWithTag("category-down-1").assertIsEnabled()
        composeRule.onNodeWithTag("category-up-2").assertIsEnabled()
        composeRule.onNodeWithTag("category-down-2").assertIsNotEnabled()
    }

    @Test
    fun toolbarAndRowControls_dispatchCommandsAndBack() {
        val events = mutableListOf<CategoryEvent>()
        var backCalls = 0
        setScreen(populatedState(), events::add) { backCalls += 1 }

        composeRule.onNodeWithTag("category-back").performClick()
        composeRule.onNodeWithTag("category-add").performClick()
        composeRule.onNodeWithTag("category-down-1").performClick()
        composeRule.onNodeWithTag("category-edit-1").performClick()
        composeRule.onNodeWithTag("category-delete-1").performClick()

        assertEquals(1, backCalls)
        assertEquals(
            listOf(
                CategoryEvent.Add,
                CategoryEvent.MoveDown(1),
                CategoryEvent.Edit(1),
                CategoryEvent.RequestDelete(1)
            ),
            events
        )
    }

    @Test
    fun editor_showsTypedErrorPaletteAndDispatchesChanges() {
        val events = mutableListOf<CategoryEvent>()
        setScreen(
            state = CategoryUiState(
                isLoading = false,
                editor = CategoryEditorState(
                    name = "Lavoro",
                    selectedColor = CategoryColor.BLUE,
                    nameError = CategoryNameError.DUPLICATE
                )
            ),
            onEvent = events::add
        )

        composeRule.onNodeWithText(context.getString(R.string.category_editor_add_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.category_error_duplicate))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("category-palette-green").performClick()
        composeRule.onNodeWithTag("category-palette-green")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
        composeRule.onNodeWithText(context.getString(R.string.category_save)).performClick()

        assertEquals(
            listOf(CategoryEvent.SelectColor(CategoryColor.GREEN), CategoryEvent.ConfirmEditor),
            events
        )
    }

    @Test
    fun deleteDialog_explainsUncategorizedOutcomeAndRequiresConfirmation() {
        val events = mutableListOf<CategoryEvent>()
        setScreen(
            state = CategoryUiState(
                isLoading = false,
                pendingDelete = categoryItem(3, "Casa", true, true)
            ),
            onEvent = events::add
        )

        composeRule.onNodeWithText(context.getString(R.string.category_delete_title, "Casa"))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.category_delete_body))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.category_delete_confirm)).performClick()

        assertEquals(listOf(CategoryEvent.ConfirmDelete), events)
    }

    @Test
    fun loadFailure_offersRetry() {
        val events = mutableListOf<CategoryEvent>()
        setScreen(
            state = CategoryUiState(isLoading = false, error = CategoryScreenError.LOAD_FAILED),
            onEvent = events::add
        )

        composeRule.onNodeWithText(context.getString(R.string.category_load_failed)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.category_retry)).performClick()

        assertEquals(listOf(CategoryEvent.Retry), events)
    }

    private fun setScreen(
        state: CategoryUiState,
        onEvent: (CategoryEvent) -> Unit = {},
        onBack: () -> Unit = {}
    ) {
        composeRule.runOnUiThread {
            CategoryTestContent.content = {
                MaterialTheme {
                    CategoryScreen(
                        state = state,
                        snackbarHostState = remember { SnackbarHostState() },
                        onEvent = onEvent,
                        onBack = onBack
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }
}

private fun populatedState() = CategoryUiState(
    isLoading = false,
    categories = listOf(
        categoryItem(1, "Lavoro", canMoveUp = false, canMoveDown = true),
        categoryItem(2, "Clienti", canMoveUp = true, canMoveDown = false)
    )
)

private fun categoryItem(
    id: Int,
    name: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean
) = CategoryItem(id, name, CategoryColor.BLUE, canMoveUp, canMoveDown)
